---
name: storage_diagnosis
version: "1.0"
category: Storage
description: >
  诊断 Kubernetes 持久化存储问题：PVC 无法绑定（StorageClass/AccessMode 不匹配）、
  Volume 挂载路径错误、fsGroup 权限缺失、StatefulSet 共用 PVC 数据损坏，
  以及 emptyDir 误用导致 Pod 重启后数据消失。
  本 Skill 为只读诊断，不执行任何变更。
scope: read-only
triggers:
  - "PVC 状态长期处于 Pending，且用户询问原因或要求排查"
  - "Pod 状态为 ContainerCreating，Events 含 Unable to mount volumes"
  - "容器日志报 Permission denied 写入挂载目录"
  - "StatefulSet 多副本之间数据相互损坏"
  - "Pod 重启后挂载卷中的数据消失"
references:
  - references/pvc_binding_rules.md       # PVC/PV 绑定三要素与诊断规则
  - references/reclaim_policy_guide.md    # PV 回收策略说明与数据风险
---

# storage_diagnosis — 存储诊断

> **⚠️ 只读模式**：本 Skill 仅输出诊断报告，不执行任何修复变更。  
> **🔴 数据风险提示**：存储变更操作（删除 PVC、更换 StorageClass）可能导致数据永久丢失，诊断报告需明确标注数据风险等级。

## 诊断工作流

### Step 0：存储状态自动化巡检（K8sGPT）

> **💡 最佳实践**：在深入追踪 PV 和挂载事件前，优先让 K8sGPT 进行定界分析。

```
工具：analyze(cluster, namespace=<ns>, name=<pvc-name>, filters=["PersistentVolumeClaim"])
→ K8sGPT 能够自动分析 PVC Pending 的原因（如 StorageClass 不存在、容量不足等），是最快速的一键定界手段。
```

---

### Step 1：确认存储问题阶段

```
① 查看 PVC 状态
   工具：list_k8s_resource(cluster, namespace, kind=PersistentVolumeClaim, version=v1)
   → STATUS = Pending → Step 2A（PVC 绑定失败）
   → STATUS = Bound → PVC 正常，问题在挂载层 → Step 2B

② 查看 Pod 挂载状态
   工具：list_k8s_pod(cluster, namespace)
   → STATUS = ContainerCreating → Step 2B（挂载失败）
   → STATUS = Running 但报 Permission denied → Step 2C（权限问题）

③ 查看 Pod 关联的 PVC 和 PV
   工具：get_k8s_pod_linked_pvc(cluster, namespace, name=<pod>)
   工具：get_k8s_pod_linked_pv(cluster, namespace, name=<pod>)
```

---

### Step 2A：PVC Pending（绑定失败）

```
① 查看 PVC 详情（关键：storageClassName / accessModes / capacity）
   工具：get_k8s_resource(cluster, namespace, kind=PersistentVolumeClaim,
         name=<pvc>, version=v1)
   → 读取：spec.storageClassName / spec.accessModes / spec.resources.requests.storage

② 查看可用 StorageClass 列表
   工具：list_k8s_resource(cluster, kind=StorageClass,
         group=storage.k8s.io, version=v1)
   → 确认 PVC 引用的 storageClassName 是否在列表中
   → 确认是否有 default StorageClass（带 annotation is-default-class=true）

③ 查看可用 PV 列表（静态绑定场景）
   工具：list_k8s_resource(cluster, kind=PersistentVolume, version=v1)
   → 对比 PV 的 storageClassName / accessModes / capacity.storage

④ 查看 StorageClass 下的 PV/PVC 数量（了解资源池规模）
   工具：get_k8s_storageclass_pv_count(cluster, name=<sc>)
   工具：get_k8s_storageclass_pvc_count(cluster, name=<sc>)
```

> 参考：[PVC/PV 绑定三要素](references/pvc_binding_rules.md)

---

### Step 2B：Volume 挂载失败诊断

```
① 查看 Pod Events（获取挂载失败的具体错误）
   工具：list_k8s_event(cluster, namespace, involvedObjectName=<pod>, involvedObjectKind="Pod")
   → 找 "Unable to mount volumes" / "FailedMount" / "Multi-Attach error" 事件
   ⚠️ SRE 提醒：生产中极其常见的一种挂载卡死情况是 PVC 状态已为 Bound，但 Pod 启动卡在 ContainerCreating 并报 "Volume is already exclusively attached to one node and can't be attached to another"。这通常由于云盘（如 AWS EBS / 阿里云 ESSD 等 RWO 盘）在上一个死掉的 Node 上未能及时解挂（Multi-Attach error）。

② 诊断 CSI 驱动故障及挂载卡死
   工具：diagnose_k8s_csi_driver(cluster, namespace, pvc=<pvc>)
   → 该工具可以深度探测 PVC 关联的 PV, VolumeAttachment 状况以及 CSI Controller Pod 日志/事件，精确定位 CSI 故障。

③ 若存在 Multi-Attach 错误，查看 VolumeAttachment 状态
   工具：list_k8s_resource(cluster, kind=VolumeAttachment, group=storage.k8s.io, version=v1)
   → 确认是否有残留的旧 VolumeAttachment（其 nodeName 依然是指向死掉的旧节点）

③ 查看 Pod 的 volumeMounts 配置
   工具：describe_k8s_pod(cluster, namespace, name=<pod>)
   → 读取 Containers[].Mounts 字段（mountPath / name / readOnly）

④ 查看挂载目录下的实际内容（若容器无法启动此步可能会失败，需提示由于 ContainerCreating 无法探测）
   工具：list_files_in_k8s_pod(cluster, namespace, name=<pod>,
         container=<c>, path=<mountPath>)
   ⚠️ SRE 提醒：如果执行返回 "executable file not found"，说明镜像缺失 ls/find 常用工具；若因 ContainerCreating 状态导致连接/执行失败，请结合卷挂载 Event 进行推断，不要直接报错。
   → 若目录为空或缺少期望的文件 → mountPath 配置错误

⑤ 确认 PVC 是否成功 Bound
   工具：get_k8s_pod_linked_pvc(cluster, namespace, name=<pod>)
   → 若 PVC STATUS = Pending → 回到 Step 2A
```

---

### Step 2C：Volume 文件权限问题

```
① 检查容器安全上下文（SecurityContext）
   工具：describe_k8s_pod(cluster, namespace, name=<pod>)
   → 读取 Security Context 字段（runAsUser / fsGroup / runAsGroup）
   
   ⚠️ SRE 判定逻辑：
   - 若 `runAsUser` 配置为非 root 用户（例如 uid=1000），且 `fsGroup` 缺失，同时挂载卷默认权限非 777，可能导致容器进程因无权限写入挂载卷而报 `Permission denied`。此为高置信度的无侵入式静态推断候选原因。
   - 若缺失 fsGroup 且未配置 runAsUser，则检查容器的日志和运行错误。

② 结合 Pod 运行日志进行印证
   工具：get_k8s_pod_logs(cluster, namespace, name=<pod>, tail=50)
   → 查找 `Permission denied` 相关的写入错误日志以进行确证。
```

**fsGroup 机制说明**：
- `securityContext.fsGroup` 指定后，kubelet 挂载卷时自动将卷的 GID 改为该值
- 容器进程（`runAsUser`）属于该 GID → 具备写权限
- 若 `fsGroup` 缺失且镜像默认用户为非 root → `Permission denied`

---

### Step 2D：StatefulSet 多副本存储诊断

```
① 查看各 Pod 关联的 PVC
   工具：get_k8s_pod_linked_pvc(cluster, namespace, name=<pod-0>)
   工具：get_k8s_pod_linked_pvc(cluster, namespace, name=<pod-1>)
   → 若两个 Pod 指向同一个 PVC → 存在数据竞争风险

② 查看 StatefulSet spec 中的存储配置
   工具：get_k8s_resource(cluster, namespace, kind=StatefulSet,
         group=apps, version=v1, name=<name>)
   → 读取 spec.volumeClaimTemplates 字段
   → 若为空但 spec.template.spec.volumes 引用固定 PVC → 错误配置

③ 确认 PV 的 AccessMode 是否支持多写
   工具：list_k8s_resource(cluster, kind=PersistentVolume, version=v1)
   → 读取 spec.accessModes
   → ReadWriteOnce (RWO) = 只允许一个节点挂载写入
   → ReadWriteMany (RWX) = 支持多节点多 Pod 同时写入
```

---

### Step 2E：emptyDir 误用诊断（数据丢失）

```
① 查看 Pod 的 volumes 配置
   工具：describe_k8s_pod(cluster, namespace, name=<pod>)
   → 读取 Volumes 区块
   → 若显示 emptyDir: {} → 临时存储，Pod 删除/重启后数据清空

② 确认是否有 PVC
   工具：get_k8s_pod_linked_pvc(cluster, namespace, name=<pod>)
   → 若无 PVC → 完全无持久化存储
```

---


**示例（PVC StorageClass 不存在）**：
> **Root Cause:** PVC `spec.storageClassName` 配置为 "fast-ssd"，但该 StorageClass 在集群中不存在（仅有 "standard"）。  
> **Evidence Chain:** PVC storageClassName="fast-ssd" 写入 → PVC 控制器找不到匹配 StorageClass → 无法触发动态制备 PV → PVC 持续 Pending → Pod 因 Volume 未绑定无法完成挂载，卡在 ContainerCreating。  
> **Confidence:** High — `list_k8s_resource(cluster, kind=StorageClass)` 仅返回 "standard"；`get_k8s_resource(cluster, kind=PersistentVolumeClaim)` 显示 storageClassName="fast-ssd"。  
> **Recommended Fix:** ⚠️【数据安全操作，需运维人员确认】建议将 PVC 的 `storageClassName` 修改为 "standard"（注意：部分字段修改需删除并重建 PVC，重建前需确认是否有数据需要迁移）。

---

## 兜底排查机制（kubectl）

在 PV/PVC、CSI 挂载遇到特定接口不可用或返回字段不足时，可使用只读 `kubectl` 兜底工具执行命令：
- 宏观排查 PV/PVC 状态、所属 StorageClass 和容量大小：
  `工具：kubectl(cluster, args=["get", "pvc,pv", "-n", "<namespace>", "-o", "wide"])`
- 检查存储类（StorageClass）定义及制备器类型：
  `工具：kubectl(cluster, args=["get", "storageclass", "-o", "yaml"])`
- 检查具体 PV 的 ReclaimPolicy、卷路径等细节信息：
  `工具：kubectl(cluster, args=["describe", "pv", "<pv-name>"])`
- 查看持久卷的卷挂载挂起状态（排查多节点挂载卡死）：
  `工具：kubectl(cluster, args=["get", "volumeattachment", "-o", "wide"])`
