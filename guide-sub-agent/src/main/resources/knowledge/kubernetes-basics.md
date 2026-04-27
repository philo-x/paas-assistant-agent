# Kubernetes 基础说明

- Pod 是最小调度单元，容器生命周期、探针、卷挂载和日志都围绕 Pod 展开。
- Deployment 适合无状态副本管理，变更镜像或 PodTemplate 会触发滚动发布。
- StatefulSet 适合有稳定网络标识和持久卷需求的工作负载。
- Service 负责稳定访问入口，ClusterIP、NodePort、LoadBalancer 的行为不同。
- Ingress 负责七层流量路由，通常依赖 Ingress Controller 生效。
