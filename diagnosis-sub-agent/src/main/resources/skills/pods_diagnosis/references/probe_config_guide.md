# 探针配置字段说明与常见误配置

本文档为 `pods_diagnosis` Skill 的参考资料，包含 Liveness/Readiness/Startup Probe 的字段定义和常见错误模式。

---

## 探针类型对比

| 探针类型 | 失败后动作 | 典型用途 |
|---------|----------|---------|
| **Liveness Probe** | kubelet 重启容器 | 检测容器是否"死锁"（运行但无法处理请求） |
| **Readiness Probe** | 从 Service Endpoints 中摘除 Pod | 检测容器是否"就绪"（可以接收流量） |
| **Startup Probe** | 在 Startup 成功前不运行 Liveness | 给启动慢的应用留出初始化时间 |

---

## 探针公共字段

| 字段 | 默认值 | 含义 |
|------|--------|------|
| `initialDelaySeconds` | 0 | 容器启动后等待多少秒开始探测 |
| `periodSeconds` | 10 | 每隔多少秒探测一次 |
| `timeoutSeconds` | 1 | 探测超时时间（超过视为失败） |
| `successThreshold` | 1 | 连续成功多少次视为探测通过 |
| `failureThreshold` | 3 | 连续失败多少次执行动作（重启/摘流量） |

---

## 探针执行方式

### HTTPGet 探针
```yaml
httpGet:
  path: /healthz
  port: 8080
  httpHeaders:
    - name: X-Health-Check
      value: "true"
```
> 响应状态码 200-399 视为成功，其他视为失败。

### TCPSocket 探针
```yaml
tcpSocket:
  port: 3306
```
> 能建立 TCP 连接视为成功，不验证应用层协议。

### Exec 探针
```yaml
exec:
  command:
    - cat
    - /tmp/healthy
```
> 命令退出码为 0 视为成功。

---

## 常见误配置案例

### 案例 1：initialDelaySeconds 过短（最常见）
```
现象：应用启动需要 60 秒（加载配置、建立 DB 连接），
      但 initialDelaySeconds=5，Liveness 在应用就绪前开始探测。
结果：CrashLoopBackOff，容器反复重启，实际应用没有问题。

诊断：get_k8s_pod_logs(cluster, previous=true) → 日志显示应用每次都在初始化阶段被终止
修复建议：将 initialDelaySeconds 增大到 > 应用启动时间（如 90s）
```

### 案例 2：探针路径与应用路由不一致
```
现象：应用健康检查接口是 /api/health，探针配置的是 /health。
结果：Liveness 返回 404，触发重启。

诊断：
  1. 通过 `list_k8s_event` 查找该 Pod 涉及的 "Liveness probe failed" 事件详细描述。kubelet 会记录具体的 HTTP 返回状态码（例如："HTTP probe failed with statuscode: 404"），这是高置信度且非侵入式的判定方式。
  2. 使用非侵入式网络连通性诊断工具测试端口：
     `diagnose_k8s_pod_network(cluster, namespace, pod=<pod>, targetIP="127.0.0.1", targetPort=8080)`
修复建议：将 httpGet.path 修改为 /api/health
```

### 案例 3：探针端口与容器监听端口不一致
```
现象：容器监听 8080，探针配置 port: 80。
结果：connection refused，Liveness 失败。

诊断：
  describe_k8s_pod → livenessProbe.httpGet.port = 80
  describe_k8s_pod → Containers.Ports = 8080
修复建议：将 httpGet.port 修改为 8080
```

### 案例 4：Readiness Probe 缺失（新版本流量冲击）
```
现象：新 Deployment 发布后出现大量 502，几秒后恢复。

原因：无 Readiness Probe 时，Pod 一旦进入 Running 状态就被加入 Endpoints，
      但应用实际上还在初始化，无法处理请求。

诊断：
  describe_k8s_pod → Readiness Probe 字段为空
  get_k8s_pod_logs → 日志显示应用仍在 "Starting server..." 阶段时 502 发生

修复建议：添加 readinessProbe，确保应用真正就绪后才接收流量
```

### 案例 5：缺少 Startup Probe 导致慢启动应用反复重启
```
现象：Java Spring Boot 应用启动需要 120 秒，Liveness 的 failureThreshold=3
      意味着 30 秒内（3次×10秒）就会重启，应用永远起不来。

修复建议：添加 startupProbe，设置 failureThreshold=30 + periodSeconds=10
          → 允许最多 300 秒启动时间，期间不触发 Liveness
```
