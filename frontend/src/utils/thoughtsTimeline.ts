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

import type { AssistantMessage, StructuredSseEvent, ThinkingTimelineStep, ToolTraceStatus } from '@/types'

export interface ThoughtsTimelineLabels {
  agentLabels: Record<string, string>
  titles: {
    supervisorInitial: string
    diagnosisInitial: string
    guideInitial: string
    afterTool: string
    afterSubAgent: string
    continueAnalysis: string
    error: string
  }
  fallbacks: {
    unknownAgent: string
    emptyReasoning: string
    errorDescription: string
  }
}

interface StructuredEventData {
  sequence?: number
  agent?: string
  tool?: string
  text?: string
  status?: ToolTraceStatus
  summary?: string
  title?: string
  message?: string
  delegation?: boolean
}

const DELEGATION_TOOLS = new Set(['callDiagnosisAgent', 'callGuideAgent'])

const normalizeText = (raw: string | undefined | null) => {
  if (!raw) {
    return ''
  }

  let text = raw

  if (text.startsWith('"') && text.endsWith('"')) {
    try {
      const parsed = JSON.parse(text)
      if (typeof parsed === 'string') {
        text = parsed
      }
    } catch {
      // Keep original text when it is not valid JSON string content.
    }
  }

  return text
    .replace(/\\r\\n/g, '\n')
    .replace(/\\n/g, '\n')
    .replace(/\\t/g, '\t')
    .replace(/\\"/g, '"')
    .replace(/\\\\/g, '\\')
}

const appendReasoningText = (existing: string, incoming: string) => {
  if (!existing) {
    return normalizeReasoningDescription(incoming)
  }
  if (!incoming) {
    return existing
  }
  // Concatenate without trimming — trailing \n from `existing` is block separation for the next chunk.
  // Backtick repair is deferred to closeOpenReasoningSteps to avoid breaking spans split across chunks.
  return existing + incoming
}

const normalizeReasoningDescription = (value: string) => {
  // Do not trimStart — preserve leading indentation for nested lists.
  return value
}

const repairUnclosedBacktick = (text: string) => {
  if ((text.match(/`/g) ?? []).length % 2 !== 0) {
    return text + '`'
  }
  return text
}

const ensureThoughtsMeta = (message: AssistantMessage) => {
  if (!message.thoughtsMeta) {
    message.thoughtsMeta = {
      nextReasoningTitleByAgent: {}
    }
  }
  return message.thoughtsMeta
}

const closeOpenReasoningSteps = (message: AssistantMessage, finishedAt: number) => {
  message.thinkingTimeline.forEach((step) => {
    if (step.kind === 'reasoning' && step.isOpen) {
      step.description = repairUnclosedBacktick(step.description)
      step.isOpen = false
      step.status = step.status === 'error' ? 'error' : 'success'
      step.finishedAt = step.finishedAt || finishedAt
    }
  })
}

const findLastOpenReasoningStep = (message: AssistantMessage) => {
  return [...message.thinkingTimeline].reverse().find(
    (step) => step.kind === 'reasoning' && step.isOpen
  )
}

const findLastReasoningStepForAgent = (message: AssistantMessage, agent: string) => {
  return [...message.thinkingTimeline].reverse().find(
    (step) => step.kind === 'reasoning' && step.agent === agent
  )
}

const resolveAgentLabel = (agent: string, labels: ThoughtsTimelineLabels) => {
  return labels.agentLabels[agent] || labels.fallbacks.unknownAgent
}

const resolveInitialReasoningTitle = (agent: string, labels: ThoughtsTimelineLabels) => {
  if (agent === 'supervisor_agent') {
    return labels.titles.supervisorInitial
  }
  if (agent === 'diagnosis_agent') {
    return labels.titles.diagnosisInitial
  }
  if (agent === 'guide_agent') {
    return labels.titles.guideInitial
  }
  return labels.titles.continueAnalysis
}

const resolveReasoningTitle = (
  message: AssistantMessage,
  agent: string,
  labels: ThoughtsTimelineLabels
) => {
  const meta = ensureThoughtsMeta(message)
  const pendingTitle = meta.nextReasoningTitleByAgent[agent]
  if (pendingTitle) {
    delete meta.nextReasoningTitleByAgent[agent]
    return pendingTitle
  }

  const previousReasoning = findLastReasoningStepForAgent(message, agent)
  if (!previousReasoning) {
    return resolveInitialReasoningTitle(agent, labels)
  }

  return labels.titles.continueAnalysis
}

const buildTimelineStep = (
  step: Omit<ThinkingTimelineStep, 'id'>
) => {
  return {
    ...step,
    id: `${step.kind}-${step.sequence}-${Math.random().toString(16).slice(2, 8)}`
  }
}

const getEventSequence = (event: StructuredSseEvent) => {
  const rawSequence = Number((event.data as StructuredEventData)?.sequence)
  if (Number.isFinite(rawSequence) && rawSequence > 0) {
    return rawSequence
  }
  return Date.now()
}

const markNextReasoningTitle = (
  message: AssistantMessage,
  agent: string,
  title: string
) => {
  ensureThoughtsMeta(message).nextReasoningTitleByAgent[agent] = title
}

const isDelegationEvent = (data: StructuredEventData) => {
  if (typeof data.delegation === 'boolean') {
    return data.delegation
  }
  return data.tool ? DELEGATION_TOOLS.has(data.tool) : false
}

export const createThoughtsTimelineLabels = (translate: (key: string) => string): ThoughtsTimelineLabels => ({
  agentLabels: {
    supervisor_agent: translate('chat.agentNames.supervisor'),
    diagnosis_agent: translate('chat.agentNames.diagnosis'),
    guide_agent: translate('chat.agentNames.guide')
  },
  titles: {
    supervisorInitial: translate('chat.timelineTitles.supervisorInitial'),
    diagnosisInitial: translate('chat.timelineTitles.diagnosisInitial'),
    guideInitial: translate('chat.timelineTitles.guideInitial'),
    afterTool: translate('chat.timelineTitles.afterTool'),
    afterSubAgent: translate('chat.timelineTitles.afterSubAgent'),
    continueAnalysis: translate('chat.timelineTitles.continueAnalysis'),
    error: translate('chat.timelineTitles.error')
  },
  fallbacks: {
    unknownAgent: translate('chat.timelineFallbacks.unknownAgent'),
    emptyReasoning: translate('chat.timelineFallbacks.emptyReasoning'),
    errorDescription: translate('chat.timelineFallbacks.errorDescription')
  }
})

export const applyStructuredThoughtsEvent = (
  message: AssistantMessage,
  event: StructuredSseEvent,
  labels: ThoughtsTimelineLabels
) => {
  const data = (event.data || {}) as StructuredEventData
  const sequence = getEventSequence(event)
  const now = Date.now()
  const agent = data.agent || 'supervisor_agent'

  if (message.thoughtsAutoManaged) {
    message.thoughtsExpanded = true
  }

  switch (event.event) {
    case 'reasoning_delta': {
      const text = normalizeText(data.text)
      if (!text) {
        return
      }

      const lastOpenReasoning = findLastOpenReasoningStep(message)
      if (lastOpenReasoning && lastOpenReasoning.agent === agent) {
        lastOpenReasoning.description = appendReasoningText(lastOpenReasoning.description, text)
        return
      }

      closeOpenReasoningSteps(message, now)
      message.thinkingTimeline.push(
        buildTimelineStep({
          sequence,
          kind: 'reasoning',
          agent,
          agentLabel: resolveAgentLabel(agent, labels),
          title: resolveReasoningTitle(message, agent, labels),
          description: normalizeReasoningDescription(text || labels.fallbacks.emptyReasoning),
          status: 'pending',
          startedAt: now,
          isOpen: true
        })
      )
      return
    }
    case 'tool_start': {
      closeOpenReasoningSteps(message, now)
      if (!isDelegationEvent(data)) {
        return
      }

      message.thinkingTimeline.push(
        buildTimelineStep({
          sequence,
          kind: 'handoff',
          agent,
          agentLabel: resolveAgentLabel(agent, labels),
          title: normalizeText(data.title) || data.tool || labels.titles.continueAnalysis,
          description: normalizeText(data.summary),
          status: 'success',
          startedAt: now,
          finishedAt: now,
          isOpen: false
        })
      )
      return
    }
    case 'tool_result': {
      closeOpenReasoningSteps(message, now)
      if (isDelegationEvent(data)) {
        markNextReasoningTitle(message, 'supervisor_agent', labels.titles.afterSubAgent)
        return
      }

      message.thinkingTimeline.push(
        buildTimelineStep({
          sequence,
          kind: 'tool',
          agent,
          agentLabel: resolveAgentLabel(agent, labels),
          title: normalizeText(data.title) || data.tool || labels.titles.continueAnalysis,
          description: normalizeText(data.summary),
          status: data.status || 'success',
          startedAt: now,
          finishedAt: now,
          isOpen: false
        })
      )
      markNextReasoningTitle(message, agent, labels.titles.afterTool)
      return
    }
    case 'error': {
      closeOpenReasoningSteps(message, now)
      message.thinkingTimeline.push(
        buildTimelineStep({
          sequence,
          kind: 'error',
          agent,
          agentLabel: resolveAgentLabel(agent, labels),
          title: labels.titles.error,
          description: normalizeText(data.message) || labels.fallbacks.errorDescription,
          status: 'error',
          startedAt: now,
          finishedAt: now,
          isOpen: false
        })
      )
      return
    }
    case 'done': {
      closeOpenReasoningSteps(message, now)
      if (message.thoughtsAutoManaged) {
        message.thoughtsExpanded = false
      }
      return
    }
    default:
      return
  }
}
