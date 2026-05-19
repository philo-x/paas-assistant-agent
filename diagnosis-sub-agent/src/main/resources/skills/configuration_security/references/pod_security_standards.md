# Pod Security Standards 三级约束对照表

本文档为 `configuration_security` Skill 的参考资料，列出 PSS 各级别的具体限制项。

---

## PSS 三个级别

| 级别 | 限制程度 | 适用场景 |
|------|---------|---------|
| `privileged` | 无限制 | 系统组件（CNI/CSI/监控 Agent） |
| `baseline` | 最小限制，防止已知高危配置 | 通用业务应用 |
| `restricted` | 最严格，遵循 Pod 安全最佳实践 | 安全敏感业务 |

---

## baseline 级别禁止的配置

| 违规字段 | 禁止的值 |
|---------|---------|
| `spec.hostPID` | `true` |
| `spec.hostIPC` | `true` |
| `spec.hostNetwork` | `true` |
| `spec.containers[].securityContext.privileged` | `true` |
| `spec.containers[].securityContext.capabilities.add` | 超出允许列表（如 NET_ADMIN 除外） |
| `spec.volumes[].hostPath` | 任何 hostPath 卷 |
| `spec.containers[].securityContext.allowPrivilegeEscalation` | `true` |
| `spec.securityContext.sysctls` | 非安全的 sysctl |

---

## restricted 级别额外限制（baseline 的超集）

| 违规字段 | 要求 |
|---------|------|
| `spec.containers[].securityContext.runAsNonRoot` | 必须为 `true` |
| `spec.containers[].securityContext.runAsUser` | 不能为 `0`（root） |
| `spec.containers[].securityContext.allowPrivilegeEscalation` | 必须明确设置为 `false` |
| `spec.containers[].securityContext.capabilities` | 必须 drop ALL，只允许 NET_BIND_SERVICE |
| `spec.securityContext.seccompProfile` | 必须为 RuntimeDefault 或 Localhost |
| `spec.volumes[]` | 只允许：configMap、emptyDir、projected、secret、serviceAccountToken、persistentVolumeClaim |

---

## Namespace 标签说明

```yaml
metadata:
  labels:
    pod-security.kubernetes.io/enforce: restricted   # 强制执行（Pod 被拒绝创建）
    pod-security.kubernetes.io/warn: restricted       # 仅警告（Pod 允许创建但有 Warning）
    pod-security.kubernetes.io/audit: restricted      # 记录审计日志
```

---

## 常见违规修复建议（供运维人员参考）

| 违规点 | 修复建议 |
|-------|---------|
| `privileged: true` | 移除 privileged，改用所需的最小 capabilities |
| `runAsUser: 0` 或未设置 runAsNonRoot | 设置 `runAsNonRoot: true` + `runAsUser: 1000` |
| `allowPrivilegeEscalation` 未设置 | 明确设置 `allowPrivilegeEscalation: false` |
| `hostNetwork: true` | 移除 hostNetwork，改用 Service 暴露端口 |
| `capabilities.add: [NET_ADMIN]` | 评估是否真正需要，通过豁免或调整 PSS 级别处理 |
