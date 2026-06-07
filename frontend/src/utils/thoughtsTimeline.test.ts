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

import { describe, expect, it } from 'vitest'
import type { AssistantMessage, StructuredSseEvent } from '@/types'
import { applyStructuredThoughtsEvent, cleanLlmTokens, type ThoughtsTimelineLabels } from './thoughtsTimeline'

const labels: ThoughtsTimelineLabels = {
  agentLabels: {
    supervisor_agent: 'Supervisor',
    diagnosis_agent: 'Diagnosis Agent',
    analyze_agent: 'Quick Diagnosis Agent',
    guide_agent: 'Guide Agent'
  },
  titles: {
    supervisorInitial: '分析用户请求',
    diagnosisInitial: '诊断 Kubernetes 问题',
    analyzeInitial: '快速扫描集群状态',
    guideInitial: '整理解释与建议',
    afterTool: '分析工具结果',
    afterSubAgent: '整合子 Agent 结果',
    continueAnalysis: '继续分析',
    error: '处理异常'
  },
  fallbacks: {
    unknownAgent: 'Agent',
    emptyReasoning: '正在继续处理当前请求。',
    errorDescription: '处理过程中发生异常，请稍后重试。',
    syntheticThinking: '正在思考并选择合适的工具...',
    syntheticThinkingCompleted: '已决定调用工具进行排查',
    syntheticAnalysis: '正在分析工具的执行结果...',
    syntheticAnalysisCompleted: '工具返回结果，分析完毕',
    emptyAnswer: '诊断未完成。'
  }
}

const createAssistantMessage = (): AssistantMessage => ({
  id: 'assistant-1',
  type: 'assistant',
  answer: '',
  thinkingTimeline: [],
  thoughtsExpanded: false,
  thoughtsAutoManaged: true,
  thoughtsMeta: {
    nextReasoningTitleByAgent: {}
  },
  error: null,
  timestamp: Date.now(),
  isStreaming: true
})

const createEvent = (event: StructuredSseEvent['event'], data: Record<string, unknown>): StructuredSseEvent => ({
  event,
  data
})

describe('applyStructuredThoughtsEvent', () => {
  it('merges consecutive reasoning chunks from the same agent into one open step', () => {
    const message = createAssistantMessage()

    applyStructuredThoughtsEvent(message, createEvent('reasoning_delta', {
      sequence: 1,
      agent: 'supervisor_agent',
      text: '先分析用户请求。'
    }), labels)
    applyStructuredThoughtsEvent(message, createEvent('reasoning_delta', {
      sequence: 2,
      agent: 'supervisor_agent',
      text: '再确认上下文。'
    }), labels)

    expect(message.thinkingTimeline).toHaveLength(1)
    expect(message.thinkingTimeline[0].kind).toBe('reasoning')
    expect(message.thinkingTimeline[0].title).toBe('分析用户请求')
    expect(message.thinkingTimeline[0].description).toContain('先分析用户请求。')
    expect(message.thinkingTimeline[0].description).toContain('再确认上下文。')
    expect(message.thinkingTimeline[0].isOpen).toBe(true)
  })

  it('creates a handoff step and suppresses duplicated delegation completion', () => {
    const message = createAssistantMessage()

    applyStructuredThoughtsEvent(message, createEvent('reasoning_delta', {
      sequence: 1,
      agent: 'supervisor_agent',
      text: '准备转交诊断子 Agent。'
    }), labels)
    applyStructuredThoughtsEvent(message, createEvent('tool_start', {
      sequence: 2,
      agent: 'supervisor_agent',
      tool: 'callDiagnosisAgent',
      title: '转交 Diagnosis Agent',
      summary: '已将请求交给 diagnosis_agent 做 Kubernetes 诊断。',
      delegation: true
    }), labels)
    applyStructuredThoughtsEvent(message, createEvent('tool_result', {
      sequence: 3,
      agent: 'supervisor_agent',
      tool: 'callDiagnosisAgent',
      title: '转交 Diagnosis Agent',
      summary: 'diagnosis_agent 已返回诊断结论。',
      delegation: true,
      status: 'success'
    }), labels)
    applyStructuredThoughtsEvent(message, createEvent('reasoning_delta', {
      sequence: 4,
      agent: 'supervisor_agent',
      text: '开始整合诊断结果。'
    }), labels)

    expect(message.thinkingTimeline).toHaveLength(3)
    expect(message.thinkingTimeline[1].kind).toBe('handoff')
    expect(message.thinkingTimeline[1].title).toBe('转交 Diagnosis Agent')
    expect(message.thinkingTimeline[2].title).toBe('整合子 Agent 结果')
  })

  it('auto collapses thoughts on done when auto-managed', () => {
    const message = createAssistantMessage()

    applyStructuredThoughtsEvent(message, createEvent('reasoning_delta', {
      sequence: 1,
      agent: 'guide_agent',
      text: '正在整理解释。'
    }), labels)
    expect(message.thoughtsExpanded).toBe(true)

    applyStructuredThoughtsEvent(message, createEvent('done', {
      sequence: 2,
      status: 'completed'
    }), labels)

    expect(message.thoughtsExpanded).toBe(false)
    expect(message.thinkingTimeline[0].isOpen).toBe(false)
  })

  it('mixes child agent reasoning and tool steps into one chronological timeline', () => {
    const message = createAssistantMessage()

    applyStructuredThoughtsEvent(message, createEvent('reasoning_delta', {
      sequence: 1,
      agent: 'supervisor_agent',
      text: '准备把诊断任务转给子 Agent。'
    }), labels)
    applyStructuredThoughtsEvent(message, createEvent('tool_start', {
      sequence: 2,
      agent: 'supervisor_agent',
      tool: 'callDiagnosisAgent',
      title: '转交 Diagnosis Agent',
      summary: '已将请求交给 diagnosis_agent 做 Kubernetes 诊断。',
      delegation: true
    }), labels)
    applyStructuredThoughtsEvent(message, createEvent('reasoning_delta', {
      sequence: 3,
      agent: 'diagnosis_agent',
      text: '先筛查 default 命名空间中的 Deployment。'
    }), labels)
    applyStructuredThoughtsEvent(message, createEvent('tool_result', {
      sequence: 4,
      agent: 'diagnosis_agent',
      tool: 'resource-list',
      title: '查询资源列表',
      summary: '已查询目标资源列表，用于筛查异常对象。',
      status: 'success'
    }), labels)
    applyStructuredThoughtsEvent(message, createEvent('reasoning_delta', {
      sequence: 5,
      agent: 'diagnosis_agent',
      text: '继续根据资源列表识别异常 Deployment。'
    }), labels)
    applyStructuredThoughtsEvent(message, createEvent('tool_result', {
      sequence: 6,
      agent: 'supervisor_agent',
      tool: 'callDiagnosisAgent',
      title: '转交 Diagnosis Agent',
      summary: 'diagnosis_agent 已返回诊断结论。',
      delegation: true,
      status: 'success'
    }), labels)
    applyStructuredThoughtsEvent(message, createEvent('reasoning_delta', {
      sequence: 7,
      agent: 'supervisor_agent',
      text: '现在整合 diagnosis_agent 返回的结果。'
    }), labels)

    expect(message.thinkingTimeline).toHaveLength(6)
    expect(message.thinkingTimeline.map((step) => step.title)).toEqual([
      '分析用户请求',
      '转交 Diagnosis Agent',
      '诊断 Kubernetes 问题',
      '查询资源列表',
      '分析工具结果',
      '整合子 Agent 结果'
    ])
    expect(message.thinkingTimeline[2].agent).toBe('diagnosis_agent')
    expect(message.thinkingTimeline[3].kind).toBe('tool')
    expect(message.thinkingTimeline[4].agent).toBe('diagnosis_agent')
    expect(message.thinkingTimeline[4].description).toContain('继续根据资源列表识别异常 Deployment。')
    expect(message.thinkingTimeline[4].isOpen).toBe(false)
    expect(message.thinkingTimeline[5].agent).toBe('supervisor_agent')
  })

  it('generates synthetic thinking and analysis steps when tool result has no preceding reasoning', () => {
    const message = createAssistantMessage()

    // Simulate tool result without reasoning delta
    applyStructuredThoughtsEvent(message, createEvent('tool_result', {
      sequence: 2,
      agent: 'diagnosis_agent',
      tool: 'list_k8s_pod',
      title: '执行 list_k8s_pod',
      summary: '已完成执行 list_k8s_pod。',
      status: 'success'
    }), labels)

    expect(message.thinkingTimeline).toHaveLength(3)
    expect(message.thinkingTimeline[0].kind).toBe('reasoning')
    expect(message.thinkingTimeline[0].description).toBe('已决定调用工具进行排查')
    expect(message.thinkingTimeline[0].isOpen).toBe(false)

    expect(message.thinkingTimeline[1].kind).toBe('tool')
    expect(message.thinkingTimeline[1].title).toBe('执行 list_k8s_pod')

    expect(message.thinkingTimeline[2].kind).toBe('reasoning')
    expect(message.thinkingTimeline[2].description).toBe('正在分析工具的执行结果...')
    expect(message.thinkingTimeline[2].isOpen).toBe(true)

    // Now send a tool_result for a second tool
    applyStructuredThoughtsEvent(message, createEvent('tool_result', {
      sequence: 4,
      agent: 'diagnosis_agent',
      tool: 'describe_k8s_pod',
      title: '查看 Pod 详情',
      summary: '查看 pod-xxx 详情',
      status: 'success'
    }), labels)

    // The open synthetic analysis from the first tool is closed and its text becomes 'completed' representation
    expect(message.thinkingTimeline[2].isOpen).toBe(false)
    expect(message.thinkingTimeline[2].description).toBe('工具返回结果，分析完毕')

    // Pushed new steps for the second tool
    expect(message.thinkingTimeline).toHaveLength(6)
    expect(message.thinkingTimeline[3].kind).toBe('reasoning')
    expect(message.thinkingTimeline[3].description).toBe('已决定调用工具进行排查')
    expect(message.thinkingTimeline[3].isOpen).toBe(false)

    expect(message.thinkingTimeline[4].kind).toBe('tool')
    expect(message.thinkingTimeline[4].title).toBe('查看 Pod 详情')

    expect(message.thinkingTimeline[5].kind).toBe('reasoning')
    expect(message.thinkingTimeline[5].description).toBe('正在分析工具的执行结果...')
    expect(message.thinkingTimeline[5].isOpen).toBe(true)
  })
})

describe('cleanLlmTokens', () => {
  it('should clean Qwen style tokens', () => {
    expect(cleanLlmTokens('<|im_start|>user\nHello<|im_end|>')).toBe('user\nHello')
    expect(cleanLlmTokens('<|endoftext|>Some content')).toBe('Some content')
  })

  it('should clean DeepSeek style U+FF5C full-width pipe tokens', () => {
    expect(cleanLlmTokens('<｜begin▁of▁sentence｜>Normal text')).toBe('Normal text')
    expect(cleanLlmTokens('<｜tool▁calls▁begin｜><｜tool▁sep｜>Output')).toBe('Output')
    expect(cleanLlmTokens('<｜User｜>User message<｜Assistant｜>')).toBe('User message')
  })

  it('should clean DSML style tool tags', () => {
    expect(cleanLlmTokens('<|DSML|call:k8s_list_pods>Hello')).toBe('Hello')
    expect(cleanLlmTokens('DSML|call:xxx Hello')).toBe('Hello')
  })

  it('should clean compound DSML + begin of sentence/text tokens', () => {
    expect(cleanLlmTokens('<｜DSML｜<｜begin_of_sentence｜>')).toBe('')
    expect(cleanLlmTokens('<|DSML|<|begin_of_sentence|>')).toBe('')
    expect(cleanLlmTokens('我来诊断命名空间 \'pt<｜DSML｜<｜begin_of_sentence｜>一些文字')).toBe('我来诊断命名空间 \'pt一些文字')
  })

  it('should clean [HOOK] logs', () => {
    expect(cleanLlmTokens('[HOOK] PreCallEvent - Agent started: diagnosis_agent')).toBe('')
    expect(cleanLlmTokens('好的。[HOOK] PostActingEvent - Tool: list_k8s_resource, Result: {"items":null}')).toBe('好的。')
    expect(cleanLlmTokens('   [HOOK] Spaced hook log\nHello world')).toBe('Hello world')
  })

  it('should clean DeepSeek-V4 EOT token (half-width pipe)', () => {
    expect(cleanLlmTokens('Final answer<|EOT|>')).toBe('Final answer')
    expect(cleanLlmTokens('<|EOT|>Trailing text')).toBe('Trailing text')
  })

  it('should clean DeepSeek-V4 dsml: XML-style tags', () => {
    expect(cleanLlmTokens('<dsml:function_call>Hello')).toBe('Hello')
    expect(cleanLlmTokens('Result</dsml:output>Done')).toBe('ResultDone')
  })

  it('should return empty string if input is empty or undefined', () => {
    expect(cleanLlmTokens('')).toBe('')
  })
})

