---
name: configuration_security
version: "1.0"
category: Configuration
description: >
  诊断 Kubernetes 安全与配置类问题：RBAC 权限缺失（403 Forbidden）、
  ConfigMap/Secret Key 引用错误（CreateContainerConfigError）、
  SecurityContext 与镜像用户冲突（runAsNonRoot），以及 Pod 违反
  Pod Security Standards 被准入控制器拒绝等场景。
  本 Skill 为只读诊断，不执行任何变更。
scope: read-only
triggers:
  - "应用调用 K8s API 返回 403 Forbidden 或 User cannot get/list/watch"
  - "Pod 状态为 CreateContainerConfigError，Events 含 couldn't find key"
  - "Pod 创建失败：container has runAsNonRoot and image will run as root"
  - "Pod 被准入控制器拒绝，Events 含 violates PodSecurity"
  - "应用启动后读取不到期望的环境变量值或配置项"
references:
  - references/rbac_model_guide.md          # RBAC 模型结构与权限查询方法
  - references/pod_security_standards.md    # PSS 三个级别的约束对照表
---

# configuration_security — 配置与安全诊断

> **⚠️ 只读模式**：本 Skill 仅输出诊断报告，不执行任何修复变更。  
> **🔒 安全提示**：`get_k8s_resource(kind=Secret)` 返回 Base64 编码内容，**禁止**在诊断报告中输出 Secret 明文值。

## 诊断工作流

### Step 0：配置与安全自动化筛查（K8sGPT）

> **💡 最佳实践**：在繁杂的 Events 和 RBAC 绑定关系中迷失前，先尝试让 K8sGPT 一键发现缺失的配置或被拦截的准入控制。

```
工具：analyze(namespace=<ns>, name=<pod-or-workload>, filters=["Pod", "MutatingWebhookConfiguration", "ValidatingWebhookConfiguration"])
→ K8sGPT 可以快速识别 ConfigMap/Secret Key 缺失、Webhook 拦截或者部分权限安全导致的启动失败，作为手工检索 Events 之前的有效补充。
```

---

### Step 1：识别问题类型

```
工具：list_k8s_pod_event(cluster, namespace, name=<pod>)
→ 读取 Events 关键字

"Forbidden" / "is forbidden: User" → Step 2A（RBAC 权限）
"couldn't find key" / "missing key" → Step 2B（ConfigMap/Secret Key）
"runAsNonRoot and image will run as root" → Step 2C（SecurityContext）
"violates PodSecurity" → Step 2D（Pod Security Standards）
环境变量异常 / 配置读取失败 → Step 2E（Env 注入诊断）
```

---

### Step 2A：RBAC 权限缺失诊断

```
① 确认 Pod 使用的 ServiceAccount
   工具：describe_k8s_pod(cluster, namespace, name=<pod>)
   → 读取 Service Account 字段

② 查看该 SA 绑定的 RoleBinding
   工具：list_k8s_resource(cluster, namespace, kind=RoleBinding,
         group=rbac.authorization.k8s.io, version=v1)
   或使用 kubectl 兜底命令批量查询 RoleBinding 和 ClusterRoleBinding：
   工具：kubectl(cluster, cmd="get rolebinding,clusterrolebinding -n <namespace> -o wide")
   → 找 subjects 中包含该 ServiceAccount 的 RoleBinding
   ⚠️ SRE 提醒：在刚刚应用新 YAML（如刚刚创建/更新了 RoleBinding）的瞬间，API-Server 的鉴权或 Kubelet 鉴权缓存可能存在 10-30 秒的短时间延迟。若发现 RoleBinding 配置正确但仍然偶发/瞬间报 403，需考虑缓存同步延迟因素。

③ 查看 RoleBinding 引用的 Role/ClusterRole
   工具：get_k8s_resource(cluster, namespace, kind=RoleBinding,
         group=rbac.authorization.k8s.io, version=v1, name=<binding>)
   → 读取 roleRef.name

④ 查看 Role 的权限定义
   工具：get_k8s_resource(cluster, namespace, kind=Role,
         group=rbac.authorization.k8s.io, version=v1, name=<role>)
   → 读取 rules[].{apiGroups, resources, verbs}
   → 对比应用实际调用的 API 资源和操作是否在 rules 中

⑤ 查看 ClusterRoleBinding（集群级别权限）
   工具：list_k8s_resource(cluster, kind=ClusterRoleBinding,
         group=rbac.authorization.k8s.io, version=v1)
   → 找关联该 SA 的 ClusterRoleBinding
```

> 参考：[RBAC 模型结构说明](references/rbac_model_guide.md)

---

### Step 2B：ConfigMap/Secret Key 缺失诊断

```
① 查看 Pod 引用的 ConfigMap/Secret 键名
   工具：describe_k8s_pod(cluster, namespace, name=<pod>)
   → 读取 Environment Variables 中的 valueFrom.configMapKeyRef / secretKeyRef
   → 记录 name（ConfigMap/Secret 名称）和 key（键名）

② 查看 ConfigMap 实际包含的键名
   工具：get_k8s_resource(cluster, namespace, kind=ConfigMap,
         name=<cm>, version=v1)
   或使用 kubectl 兜底命令查看 ConfigMap 详情：
   工具：kubectl(cluster, cmd="get configmap <cm> -n <namespace> -o yaml")
   → 读取 data 字段的所有 key

③ 对比 Pod 引用的 key 与 ConfigMap 实际 key 是否一致
   → 若 Pod 引用的 key 不在 ConfigMap data 中 → 确认问题根因
```

> ⚠️ 若引用的是 Secret，同样使用 `get_k8s_resource(kind=Secret)` 查看 data 字段的 key 列表，但**不得输出具体的 Base64 值**。

---

### Step 2C：SecurityContext 与镜像用户冲突

```
① 查看 Pod SecurityContext 配置
   工具：describe_k8s_pod(cluster, namespace, name=<pod>)
   → 读取 Security Context 字段（runAsNonRoot / runAsUser / runAsGroup）

② 分析冲突原因
   → 若 runAsNonRoot=true 但 runAsUser 未配置 → K8s 要求非 root，
     但未指定具体 UID，如果镜像默认以 root（uid=0）运行 → 被拒绝

③ 查看 Pod Events 确认错误详情
   工具：list_k8s_pod_event(cluster, namespace, name=<pod>)
   → 确认 "container has runAsNonRoot and image will run as root"
```

---

### Step 2D：违反 Pod Security Standards

```
① 查看 Namespace 的 PSS 标签
   工具：get_k8s_resource(cluster, kind=Namespace, name=<ns>, version=v1)
   → 读取 metadata.labels：
     pod-security.kubernetes.io/enforce  = 强制执行的安全级别
     pod-security.kubernetes.io/warn     = 仅警告的安全级别

② 查看拒绝原因
   工具：list_k8s_pod_event(cluster, namespace, name=<pod>)
   → 读取 "violates PodSecurity" 事件的具体违规点

③ 查看 Pod 的 securityContext 配置
   工具：describe_k8s_pod(cluster, namespace, name=<pod>)
   → 读取 Security Context / Container Security Context
```

> 参考：[Pod Security Standards 三级对照表](references/pod_security_standards.md)

---

### Step 2E：环境变量注入诊断

```
① 查看 Pod 定义中的环境变量来源配置
   工具：get_pod_linked_env_from_yaml(cluster, namespace, name=<pod>)
   → 确认 env / envFrom 配置是否正确引用了 ConfigMap/Secret
   ⚠️ SRE 提醒：除了逐个引用的 `valueFrom` 之外，Pod 可能通过 `envFrom`（例如 `envFrom[].configMapRef` / `secretRef`）将整个 ConfigMap/Secret 隐式且完整地导入为环境变量。在此模式下，ConfigMap/Secret 的所有 Key 均会直接作为环境变量的 Key。

② 查看 Pod 运行时实际注入的环境变量值
   工具：get_k8s_pod_linked_env(cluster, namespace, name=<pod>)
   → 查看运行时通过 exec env 获取 of 实际环境变量列表

③ 对比两者差异
   - 如果使用 `env` 逐个注入：对比指定的 `key` 与 ConfigMap/Secret 的 `data` 键名。
   - 如果使用 `envFrom` 批量注入：获取 ConfigMap/Secret 的全部 Key 列表，核对应用所需的变量是否包含在其中。
   → 若值异常/缺失 → 注入链路有问题，回到 Step 2B 排查。
```

---

## 诊断报告格式

```
**Root Cause:** [因果链中最早的失败事件或错误配置]
**Evidence Chain:** [事件] → [触发] → [影响] → [用户观察到的症状]
**Confidence:** [High / Medium / Low — 置信度依据]
**Recommended Fix:** [面向运维人员的修复建议]
```

⚠️ **SRE 提醒**：K8s Events 默认只有 1 小时 TTL。如果未查询到相关 Events，不能断定没发生过故障，需在报告中声明 Events 可能已过 TTL 自动清理，并将 Confidence（置信度）适当降级。

**示例（ConfigMap Key 缺失）**：
> **Root Cause:** Pod 通过 `valueFrom.configMapKeyRef` 引用 ConfigMap "app-config" 的键 "database_host"，但该 ConfigMap 中实际不存在此键（仅有 "app_name" 和 "app_version"）。  
> **Evidence Chain:** Pod spec 引用 `configMapKeyRef.key="database_host"` → K8s Kubelet 在启动容器前验证 ConfigMap 键存在性失败 → 容器配置注入失败 → Pod 进入 `CreateContainerConfigError` 状态，无法启动。  
> **Confidence:** High — `describe_k8s_pod` Events 含 "couldn't find key database_host in ConfigMap default/app-config"；`get_k8s_resource(ConfigMap, app-config)` data 字段中无 database_host 键。  
> **Recommended Fix:** 建议运维人员在 ConfigMap "app-config" 的 `data` 字段中补充 `database_host: "<正确的数据库地址>"`，补充后需重启 Pod（通过 rollout restart）使变更生效。

---

## 兜底排查机制（kubectl）

在排查安全性准入或 RBAC/配置项时遇到专用 MCP 工具失效时，可使用只读 `kubectl` 兜底工具执行命令：
- 宏观排查 Namespace 内的 RBAC 绑定关系：
  `工具：kubectl(cluster, cmd="get role,rolebinding,serviceaccount -n <namespace> -o wide")`
- 详细查看 ClusterRoleBinding 和全局权限（只读）：
  `工具：kubectl(cluster, cmd="get clusterrolebinding -o wide")`
- 检查 ConfigMap / Secret 的数据键名（只读查看，禁止在报告中明文打印 Secret 具体值）：
  `工具：kubectl(cluster, cmd="get configmap <cm-name> -n <namespace> -o yaml")`
  `工具：kubectl(cluster, cmd="get secret <secret-name> -n <namespace> -o yaml")`
