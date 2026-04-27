# 平台运行手册摘要

- 故障定位优先顺序: get -> describe -> events -> logs -> analyze。
- 任何变更类动作都必须经过审批记录，先计划后执行。
- Patch 仅允许白名单字段，超范围 patch 必须拒绝。
- 当用户只是在问字段、命令或 describe 解释时，不应触发执行链路。
