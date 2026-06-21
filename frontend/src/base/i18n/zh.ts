/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

export default {
  common: {
    confirm: '确认',
    cancel: '取消',
    save: '保存',
    delete: '删除',
    edit: '编辑',
    add: '添加',
    search: '搜索',
    loading: '加载中...',
    error: '错误',
    success: '成功',
    warning: '警告',
    info: '信息',
    back: '返回',
    reset: '重置',
    refresh: '刷新',
    view: '查看',
    close: '关闭',
    set: '设置',
    language: '语言',
    chinese: '中文',
    english: 'English'
  },
  home: {
    title: 'PaaS 平台 Agent 助手',
    subtitle: 'Kubernetes 诊断与指南',
    description: '欢迎使用 PaaS 平台 Agent 助手。您可以在这里进行 K8s 诊断、查看资源解释、获取 YAML 字段说明与排障命令建议。',
    startChat: '开始对话',
    systemSettings: '系统设置',
    features: {
      title: '服务功能',
      consult: '资源指南',
      consultDesc: '字段释义、Describe 解读和 YAML 编写建议',
      order: '故障诊断',
      orderDesc: '聚焦 Pod、Deployment、Service 等核心资源的读诊断能力',
      feedback: '根因分析',
      feedbackDesc: '结合时间轴与爆炸半径，输出置信度及修复风险提示',
      support: '诊断 SOP',
      supportDesc: '内置 Nodes、Pods、Workloads 等 7 大核心资源的专项排查决策树'
    },
    cta: {
      title: '准备开始一次平台诊断或咨询？',
      description: '点击下方按钮，进入 PaaS 平台 Agent 助手对话'
    }
  },
  chat: {
    mode: {
      diagnosis: "诊断",
      guide: "咨询",
      diagnosisDesc: "深度分析与诊断集群中的故障问题",
      guideDesc: "解释 YAML 字段、解读输出或推荐命令"
    },
    title: 'PaaS 平台 Agent 助手',
    placeholder: {
      diagnosis: '请输入需要深度诊断的故障或现象，例如：诊断 kube-system 命名空间下的 Deployment coredns',
      guide: '请输入需要咨询的资源字段或命令，例如：解释 Deployment 的 spec.strategy 字段'
    },
    send: '发送',
    clear: '清空对话',
    settings: '设置',
    thinking: 'Agent 正在处理中...',
    error: '发送失败，请重试',
    sendError: '发送失败',
    unknownError: '未知错误',
    welcome: '您好！我是 PaaS 平台 Agent 助手，可以帮您做 K8s 故障诊断、资源解释、命令建议和资源使用指南。',
    chatCleared: '对话已清空',
    sessionId: '对话ID',
    userId: '用户ID',
    clusterId: '集群ID',
    setUserId: '设置用户ID',
    setClusterId: '设置集群ID',
    userIdSetSuccess: '用户ID设置成功',
    clusterSetSuccess: '集群ID设置成功',
    userIdRequired: '请输入有效的用户ID',
    clusterRequired: '请输入有效的集群ID',
    userIdPrompt: '请输入您的用户ID，用于记录审批与审计：',
    clusterPrompt: '请选择目标 Kubernetes 集群环境，否则无法发起诊断：',
    userIdPlaceholder: '请输入平台用户ID',
    clusterPlaceholder: '请选择集群环境',
    tooltip: {
      noBaseUrlAndUserId: '请在右上角的设置页面设置后端地址和用户ID',
      noBaseUrl: '请在右上角的设置页面设置后端地址',
      noUserId: '请在右上角的设置页面设置用户ID',
      noClusterId: '请先设置集群ID'
    },
    sections: {
      answer: '最终回答',
      tools: '工具调用',
      reasoning: 'Agent 思考过程',
      thinking: 'Thinking',
      thoughts: 'Thoughts',
      conclusion: '最终结论',
      result: 'Agent'
    },
    showTools: '展开查看',
    hideTools: '收起',
    showReasoning: '展开查看',
    hideReasoning: '收起',
    showThinking: '展开',
    hideThinking: '收起',
    thoughtsCollapsedHint: '展现思考',
    toolInput: '输入：',
    toolResult: '结果：',
    toolFallback: '已完成 {tool}。',
    toolStatus: {
      pending: '执行中',
      success: '已完成',
      error: '失败'
    },
    agentNames: {
      supervisor: 'Supervisor',
      diagnosis: 'Diagnosis Agent',
      guide: 'Guide Agent'
    },
    timelineTitles: {
      supervisorInitial: '分析用户请求',
      diagnosisInitial: '诊断 Kubernetes 问题',
      guideInitial: '整理解释与建议',
      afterTool: '分析工具结果',
      afterSubAgent: '整合子 Agent 结果',
      continueAnalysis: '继续分析',
      error: '处理异常'
    },
    timelineFallbacks: {
      unknownAgent: 'Agent',
      emptyReasoning: '正在继续处理当前请求。',
      errorDescription: '处理过程中发生异常，请稍后重试。',
      syntheticThinking: '正在思考并选择合适的工具...',
      syntheticThinkingCompleted: '已确定下一步诊断方向，准备调用工具进行排查',
      syntheticAnalysis: '正在分析工具的执行结果...',
      syntheticAnalysisCompleted: '已完成工具返回结果的结构化分析，继续下一步诊断动作',
      emptyAnswer: '诊断未完成。可能由于命名空间资源过多导致请求超时被取消，请更换为更具体的诊断范围或查看思考时间线中的执行轨迹。'
    },
    toolNames: {
      callDiagnosisAgent: '调用 diagnosis_agent',
      callGuideAgent: '调用 guide_agent'
    }
  },
  settings: {
    title: '系统设置',
    apiConfig: {
      title: 'API 配置',
      baseUrl: '后端服务地址',
      baseUrlPlaceholder: '请输入后端服务地址，如：http://localhost:10008',
      testConnection: '测试连接',
      connectionSuccess: '连接成功',
      connectionFailed: '连接失败'
    },
    userConfig: {
      title: '用户配置',
      userId: '用户ID',
      userIdPlaceholder: '请输入平台用户ID',
      chatId: '对话ID',
      chatIdPlaceholder: '请输入对话ID（可选，留空将自动生成）'
    },
    validation: {
      baseUrlRequired: '请输入后端服务地址',
      baseUrlInvalid: '请输入有效的URL地址',
      userIdRequired: '请输入用户ID',
      baseUrlMissing: '请先输入后端服务地址'
    },
    saveConfig: '保存配置',
    saveSuccess: '配置保存成功',
    saveFailed: '配置保存失败',
    help: {
      title: '使用说明',
      baseUrlHelp: '请输入 Supervisor 后端服务地址，例如：http://localhost:10008',
      userIdHelp: '用于标识平台操作者，并关联审批与执行审计',
      chatIdHelp: '用于标识当前会话，留空将自动生成',
      apiHelp: '聊天页将调用 {url}/api/assistant/chat/structured'
    }
  }
}
