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

import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useChatStore } from './chat'
import { createThoughtsTimelineLabels } from '@/utils/thoughtsTimeline'

const labels = createThoughtsTimelineLabels((key: string) => key)

describe('chat store structured events', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('appends answer deltas only to the final answer field', () => {
    const store = useChatStore()
    store.addAssistantMessage({ isStreaming: true })

    store.appendAssistantAnswer('hello')
    store.appendAssistantAnswer(' world')

    expect(store.messages[0]).toMatchObject({
      type: 'assistant',
      answer: 'hello world',
      thinkingTimeline: []
    })
  })

  it('tracks process events without changing the final answer', () => {
    const store = useChatStore()
    store.addAssistantMessage({ isStreaming: true })

    store.applyAssistantStructuredEvent({
      event: 'tool_result',
      data: {
        sequence: 1,
        agent: 'diagnosis_agent',
        tool: 'resource-list',
        title: '查询资源列表',
        summary: '已查询资源。',
        status: 'success'
      }
    }, labels)

    expect(store.messages[0]).toMatchObject({
      type: 'assistant',
      answer: ''
    })
    expect((store.messages[0] as any).thinkingTimeline).toHaveLength(1)
  })

  it('records structured error events as process errors', () => {
    const store = useChatStore()
    store.addAssistantMessage({ isStreaming: true })

    store.applyAssistantStructuredEvent({
      event: 'error',
      data: {
        sequence: 1,
        agent: 'supervisor_agent',
        message: 'failed'
      }
    }, labels)

    expect((store.messages[0] as any).thinkingTimeline[0]).toMatchObject({
      kind: 'error',
      status: 'error',
      description: 'failed'
    })
  })
})
