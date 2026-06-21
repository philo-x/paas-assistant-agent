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

使用专用的 DNS 解析测试工具，它能够自动绕过容器精简镜像的命令限制：
* **解析测试**：
  `test_k8s_dns_resolve(cluster, namespace, pod=<pod-name>, host="<target-host>")`
  
⚠️ SRE 提醒：请勿直接通过 `run_command_in_k8s_pod` 运行容器内的 `nslookup` 或 `cat` 命令，因为精简应用镜像中极大概率不具备这些工具。请统一使用 `test_k8s_dns_resolve` 进行非侵入式探测。

---

## CoreDNS 健康检查

```
正常情况：CoreDNS Pod 在 kube-system 中处于 Running 状态
          list_k8s_pod(cluster, namespace=kube-system) → 找 coredns-xxxx Pod

故障表现：
  - nslookup 超时 → CoreDNS Pod 无响应或 CrashLoop
  - nslookup 返回 NXDOMAIN → 服务名称或 Namespace 拼写错误
  - 短名称失败，FQDN 成功 → 跨 Namespace 访问未使用 FQDN，或 Namespace/search domain 使用不当
```
