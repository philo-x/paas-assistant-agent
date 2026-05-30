# K8s DNS 命名规则与 FQDN 格式

本文档为 `networking_diagnosis` Skill 的参考资料，说明集群内 DNS 名称解析规则。

---

## DNS 名称格式速查

| 访问场景 | DNS 格式 | 示例 |
|---------|---------|------|
| 同 Namespace 内 | `<service-name>` | `my-svc` |
| 跨 Namespace | `<service-name>.<namespace>.svc.cluster.local` | `my-svc.prod.svc.cluster.local` |
| StatefulSet Pod 直连 | `<pod-name>.<headless-svc>.<namespace>.svc.cluster.local` | `mysql-0.mysql-headless.prod.svc.cluster.local` |

---

## Headless Service DNS 机制

普通 Service（有 ClusterIP）的 DNS 解析到 ClusterIP，由 kube-proxy 做负载均衡。

Headless Service（`clusterIP: None`）的 DNS 直接解析到 Pod IP：
- `<headless-svc>.<ns>.svc.cluster.local` → 返回所有 Ready Pod 的 IP 列表（随机）
- `<pod-name>.<headless-svc>.<ns>.svc.cluster.local` → 返回特定 Pod 的 IP（稳定 DNS，StatefulSet 必用）

---

## 常见 DNS 故障排查命令

在 Pod 内执行以下诊断（通过 `run_command_in_k8s_pod`）。
⚠️ SRE 提醒：精简镜像（如 distroless、scratch）内可能缺少 nslookup 工具。若提示 "executable file not found"，请利用临时调试容器（如 ephemeral container 或 busybox 临时 Pod）代为运行探测：

```bash
# 测试短名称
nslookup my-svc

# 测试 FQDN（跨 Namespace）
nslookup my-svc.other-ns.svc.cluster.local

# 确认 DNS 服务器地址（应为 CoreDNS ClusterIP，通常是 10.96.0.10）
cat /etc/resolv.conf

# 测试 CoreDNS 是否响应
nslookup kubernetes.default.svc.cluster.local
```

---

## CoreDNS 健康检查

```
正常情况：CoreDNS Pod 在 kube-system 中处于 Running 状态
          list_k8s_pod(namespace=kube-system) → 找 coredns-xxxx Pod

故障表现：
  - nslookup 超时 → CoreDNS Pod 无响应或 CrashLoop
  - nslookup 返回 NXDOMAIN → 服务名称或 Namespace 拼写错误
  - 短名称失败，FQDN 成功 → 跨 Namespace 访问未使用 FQDN，或 Namespace/search domain 使用不当
```
