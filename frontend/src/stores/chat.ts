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

import { defineStore } from 'pinia'
import { ref } from 'vue'
import type {
  AssistantMessage,
  Message,
  StructuredSseEvent
} from '@/types'
import { applyStructuredThoughtsEvent, type ThoughtsTimelineLabels } from '@/utils/thoughtsTimeline'

export const useChatStore = defineStore('chat', () => {
  // State
  const messages = ref<Message[]>([])
  const isLoading = ref(false)
  const error = ref<string | null>(null)

  const buildId = (prefix: string) => `${prefix}-${Date.now()}-${Math.random().toString(16).slice(2, 8)}`

  function addUserMessage(question: string) {
    messages.value.push({
      id: buildId('user'),
      type: 'user',
      question,
      timestamp: Date.now()
    })
  }

  function addAssistantMessage(initial?: Partial<Omit<AssistantMessage, 'id' | 'type' | 'timestamp'>>) {
    messages.value.push({
      id: buildId('assistant'),
      type: 'assistant',
      answer: initial?.answer || '',
      thinkingTimeline: initial?.thinkingTimeline || [],
      thoughtsExpanded: initial?.thoughtsExpanded || false,
      thoughtsAutoManaged: initial?.thoughtsAutoManaged ?? true,
      thoughtsMeta: initial?.thoughtsMeta || {
        nextReasoningTitleByAgent: {}
      },
      error: initial?.error || null,
      isStreaming: initial?.isStreaming || false,
      timestamp: Date.now()
    })
  }

  function getLastAssistantMessage(): AssistantMessage | null {
    const lastMessage = messages.value[messages.value.length - 1]
    if (lastMessage && lastMessage.type === 'assistant') {
      return lastMessage
    }
    return null
  }

  function appendAssistantAnswer(content: string) {
    const lastMessage = getLastAssistantMessage()
    if (lastMessage) {
      lastMessage.answer += content
      lastMessage.isStreaming = true
    }
  }

  function applyAssistantStructuredEvent(
    event: StructuredSseEvent,
    labels: ThoughtsTimelineLabels
  ) {
    const lastMessage = getLastAssistantMessage()
    if (!lastMessage) {
      return
    }

    applyStructuredThoughtsEvent(lastMessage, event, labels)
    lastMessage.isStreaming = event.event !== 'done' && event.event !== 'error'
  }

  function setAssistantError(errorMessage: string | null) {
    const lastMessage = getLastAssistantMessage()
    if (lastMessage) {
      lastMessage.error = errorMessage
      lastMessage.isStreaming = false
    }
  }

  function finalizeAssistantMessage() {
    const lastMessage = getLastAssistantMessage()
    if (lastMessage) {
      lastMessage.isStreaming = false
    }
  }

  function setAssistantThoughtsExpanded(id: string, expanded: boolean, manual = true) {
    const target = messages.value.find((message): message is AssistantMessage => {
      return message.type === 'assistant' && message.id === id
    })

    if (!target) {
      return
    }

    target.thoughtsExpanded = expanded
    if (manual) {
      target.thoughtsAutoManaged = false
    }
  }

  function clearMessages() {
    messages.value = []
    error.value = null
  }

  function setLoading(loading: boolean) {
    isLoading.value = loading
  }

  function setError(errorMessage: string | null) {
    error.value = errorMessage
  }

  function removeMessage(id: string) {
    const index = messages.value.findIndex(msg => msg.id === id)
    if (index > -1) {
      messages.value.splice(index, 1)
    }
  }

  return {
    messages,
    isLoading,
    error,
    addUserMessage,
    addAssistantMessage,
    appendAssistantAnswer,
    applyAssistantStructuredEvent,
    setAssistantError,
    setAssistantThoughtsExpanded,
    finalizeAssistantMessage,
    clearMessages,
    setLoading,
    setError,
    removeMessage
  }
})
