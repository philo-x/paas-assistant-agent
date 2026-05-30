# RBAC 模型结构与权限查询方法

本文档为 `configuration_security` Skill 的参考资料，说明 K8s RBAC 的模型结构和诊断查询方法。

---

## RBAC 核心对象

```
ServiceAccount（身份）
    ↓ 通过 RoleBinding / ClusterRoleBinding 关联
Role / ClusterRole（权限集合）
    ↓ 定义可访问的资源和操作
rules: [{apiGroups, resources, verbs}]
```

| 对象 | 作用范围 | 说明 |
|------|---------|------|
| `Role` | Namespace 内 | 定义 Namespace 级别的资源访问权限 |
| `ClusterRole` | 集群全局 | 定义跨 Namespace 或集群级资源的访问权限 |
| `RoleBinding` | Namespace 内 | 将 Role/ClusterRole 绑定到 SA/User/Group |
| `ClusterRoleBinding` | 集群全局 | 将 ClusterRole 绑定到 SA/User/Group（全局生效） |

---

## verbs 操作动词速查

| verb | 对应 kubectl 操作 | MCP 工具支持状态 |
|------|-----------------|-----------------|
| `get` | kubectl get (单个资源) | ✅ 支持 |
| `list` | kubectl get (列表) | ✅ 支持 |
| `watch` | kubectl get --watch | ❌ 不支持 (MCP 限制长连接) |
| `create` | kubectl create / apply (新建) | ❌ 不支持 (MCP 限制只读) |
| `update` | kubectl apply (更新) | ❌ 不支持 (MCP 限制只读) |
| `patch` | kubectl patch | ❌ 不支持 (MCP 限制只读) |
| `delete` | kubectl delete | ❌ 不支持 (MCP 限制只读) |
| `deletecollection` | kubectl delete (批量) | ❌ 不支持 (MCP 限制只读) |
| `*` | 所有操作 | ❌ 不支持 (仅限只读子命令) |

---

## 诊断查询路径

```
Step 1：确认 Pod 的 ServiceAccount
  describe_k8s_pod → 读取 "Service Account" 字段

Step 2：查找 SA 绑定的 RoleBinding（Namespace 级）
  list_k8s_resource(kind=RoleBinding, namespace=<ns>)
  → 找 subjects[].name == <sa-name> AND subjects[].kind == ServiceAccount

Step 3：查找 SA 绑定的 ClusterRoleBinding（集群级）
  list_k8s_resource(kind=ClusterRoleBinding)
  → 找 subjects[].name == <sa-name> AND subjects[].namespace == <ns>

Step 4：查看 Role/ClusterRole 的权限规则
  get_k8s_resource(kind=Role 或 ClusterRole, name=<role>)
  → 读取 rules[].{apiGroups, resources, verbs}

Step 5：对比应用实际需要的权限
  应用报错：403 Forbidden: ... pods "xxx" is forbidden: User "system:serviceaccount:ns:sa" cannot list resource "pods"
  → 需要：apiGroups=[""], resources=["pods"], verbs=["list"]
  → 检查 rules 中是否有上述配置
```

---

## 常见权限缺失场景

| 应用操作 | 需要的 rules 配置 |
|---------|----------------|
| 读取 Pod 列表 | `apiGroups: [""], resources: ["pods"], verbs: ["list", "get"]` |
| 读取 ConfigMap | `apiGroups: [""], resources: ["configmaps"], verbs: ["get", "list"]` |
| 读取 Secret | `apiGroups: [""], resources: ["secrets"], verbs: ["get"]` |
| 操作 Deployment | `apiGroups: ["apps"], resources: ["deployments"], verbs: ["get", "list", "update"]` |
| 读取 Events | `apiGroups: [""], resources: ["events"], verbs: ["list", "watch"]` |
