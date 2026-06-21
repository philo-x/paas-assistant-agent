# NetworkPolicy 规则语法与调试指南

本文档为 `networking_diagnosis` Skill 的参考资料，说明 NetworkPolicy 的规则结构和常见调试方法。

---

## NetworkPolicy 基本结构

```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: <policy-name>
  namespace: <namespace>
spec:
  podSelector:         # 该策略作用于哪些 Pod（空 = 覆盖所有 Pod）
    matchLabels:
      app: backend
  policyTypes:
    - Ingress          # 声明控制入站流量
    - Egress           # 声明控制出站流量
  ingress:             # 允许的入站规则（未声明 ingress 但 policyTypes 含 Ingress = 拒绝所有入站）
    - from:
        - podSelector:
            matchLabels:
              tier: frontend
        - namespaceSelector:
            matchLabels:
              env: prod
      ports:
        - protocol: TCP
          port: 8080
  egress:              # 允许的出站规则
    - to:
        - ipBlock:
            cidr: 10.0.0.0/8
      ports:
        - protocol: TCP
          port: 5432
```

---

## 拒绝模式识别

| 规则配置 | 效果 |
|---------|------|
| `policyTypes: [Ingress]` + `ingress: []`（空列表） | 拒绝所有入站流量（deny-all ingress） |
| `policyTypes: [Ingress]` + `ingress` 有规则 | 只允许匹配规则的入站流量 |
| 无 `policyTypes` | 隐式自动推导：若声明了 ingress 规则，默认生效 Ingress 控制；若声明了 egress 规则，默认生效 Egress 控制；若均未声明，默认不控制。 |
| `podSelector: {}`（空选择器）+ deny-all | 影响 Namespace 内所有 Pod |

---

## 调试方法

```
判断是否被 NetworkPolicy 拦截：
  → connection timed out（超时）= 被静默丢弃（可能是 NetworkPolicy 限制，也可能是路由、安全组防火墙、CNI 插件或节点网络异常）
  → connection refused = 连接被对端拒绝（表示网络本身可达但目标端口未正常监听，非 NetworkPolicy 拦截问题，需返回检查 targetPort）

验证步骤：
  1. list_k8s_resource(cluster, kind=NetworkPolicy) → 确认存在 Policy
  2. get_k8s_resource(cluster, kind=NetworkPolicy, name=<policy>) → 读取规则
  3. 对比 podSelector 是否覆盖 Target Pod
  4. 对比 ingress[].from[].podSelector 是否覆盖 Source Pod 的 Labels
  5. 对比 ports[] 是否包含目标端口
```

---

## from/to 选择器优先级

`from[]` 中的多个元素是 **OR 关系**（满足任一即放行）：
```yaml
from:
  - podSelector: ...       # 条件 A
  - namespaceSelector: ... # 条件 B → 满足 A OR B 即可
```

同一个 `from[]` 元素内的多个字段是 **AND 关系**：
```yaml
from:
  - podSelector: ...          # 条件 A
    namespaceSelector: ...    # 条件 B → 必须同时满足 A AND B
```
