# 容器退出码（Exit Code）参考

本文档为 `pods_diagnosis` Skill 的参考资料，列出常见的容器退出码及其诊断含义。

---

## Exit Code 速查表

| Exit Code | 信号/来源 | 含义 | 典型排查方向 |
|-----------|---------|------|------------|
| `0` | 正常退出 | 容器进程正常结束 | 检查是否是一次性 Job，若是长期服务则检查 CMD |
| `1` | 应用逻辑错误 | 应用主进程以非零码退出 | 查看 `get_k8s_pod_logs(cluster, previous=true)` |
| `2` | Shell 语法错误 | bash/sh entrypoint 脚本语法错误 | 检查 command/args 中的 shell 脚本 |
| `126` | 权限拒绝 | 命令存在但无执行权限 | 检查镜像内文件系统权限 |
| `127` | 命令不存在 | PATH 中找不到指定命令 | 检查 `command` 字段，确认命令在镜像中存在 |
| `128` | 无效退出参数 | exit() 收到非法参数 | 查看应用代码逻辑 |
| `130` | SIGINT (2) | 进程收到 Ctrl+C / SIGINT | 通常是手动终止，非故障 |
| `137` | SIGKILL (9) | **OOMKilled**：内存超 limits 被强制终止 | `get_k8s_pod_resource_usage` 确认内存用量 |
| `139` | SIGSEGV (11) | 段错误（内存非法访问） | 应用代码或 native 库 bug |
| `143` | SIGTERM (15) | 正常终止信号（graceful shutdown 超时） | 检查 `terminationGracePeriodSeconds` |
| `255` | 进程初始化失败 | 通常是配置加载失败或端口冲突 | 查看日志找 "address already in use" 等错误 |

---

## OOMKilled 诊断详解

OOMKilled（Exit Code 137）是最常见的崩溃原因之一。

```
诊断步骤：
1. describe_k8s_pod → Last State.Exit Code = 137
2. get_k8s_pod_resource_usage → 实际内存使用量接近或超过 limits.memory
3. get_k8s_pod_logs(cluster, previous=true) → 查看 OOM 前的应用行为（通常是内存持续增长）

关键字段对比：
  spec.containers[].resources.limits.memory  = 内存上限（如 512Mi）
  实际使用量（kubectl top pod 等效）          = 峰值内存

修复建议（运维人员）：
  方案A：增大 limits.memory（适用于应用内存需求合理）
  方案B：排查应用内存泄漏（适用于内存持续增长无上限）
  方案C：调整 JVM heap size / GC 策略（适用于 Java 应用）
```

---

## 镜像拉取失败错误码

| 错误关键字 | 详细含义 | 排查方向 |
|-----------|---------|---------|
| `manifest unknown` | 指定的镜像 Tag 在 Registry 中不存在 | 确认镜像名称和 Tag 是否正确 |
| `not found` | Registry 中不存在该镜像（Repository 不存在） | 确认 Registry 地址和镜像名 |
| `unauthorized` | Registry 认证失败，没有拉取权限 | 检查 imagePullSecrets 配置 |
| `pull access denied` | 同上（Docker Hub 的表现形式） | 同上 |
| `no basic auth credentials` | Secret 未挂载或 Secret 类型不对 | 检查 Secret type=kubernetes.io/dockerconfigjson |
| `context deadline exceeded` | 网络超时，无法连接 Registry | 检查网络策略和 Registry 可达性 |
| `connection refused` | Registry 地址错误或服务未运行 | 确认 Registry 地址和端口 |
