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
import { applyStructuredThoughtsEvent, type ThoughtsTimelineLabels } from './thoughtsTimeline'

const labels: ThoughtsTimelineLabels = {
  agentLabels: {
    supervisor_agent: 'Supervisor',
    diagnosis_agent: 'Diagnosis Agent',
    guide_agent: 'Guide Agent'
  },
  titles: {
    supervisorInitial: '分析用户请求',
    diagnosisInitial: '诊断 Kubernetes 问题',
    guideInitial: '整理解释与建议',
    afterTool: '分析工具结果',
    afterSubAgent: '整合子 Agent 结果',
    continueAnalysis: '继续分析',
    error: '处理异常'
  },
  fallbacks: {
    unknownAgent: 'Agent',
    emptyReasoning: '正在继续处理当前请求。',
    errorDescription: '处理过程中发生异常，请稍后重试。'
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
})
