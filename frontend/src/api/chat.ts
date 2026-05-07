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

import axios from 'axios'
import { useConfigStore } from '@/stores/config'
import type { StructuredChatRequest, StructuredSseEvent, StructuredSseEventName } from '@/types'

const KNOWN_STRUCTURED_EVENTS = new Set<string>([
  'reasoning_delta',
  'tool_start',
  'tool_result',
  'answer_delta',
  'done',
  'error'
])

export const parseStructuredEvent = (chunk: string): StructuredSseEvent | null => {
  const lines = chunk
    .split('\n')
    .map((line) => line.replace(/\r$/, ''))
    .filter((line) => line.length > 0)

  if (lines.length === 0) {
    return null
  }

  let eventName = 'answer_delta'
  let hasExplicitEvent = false
  const dataLines: string[] = []

  for (const line of lines) {
    if (line.startsWith('event:')) {
      hasExplicitEvent = true
      eventName = line.slice(6).trim()
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).replace(/^ /, ''))
    }
  }

  if (hasExplicitEvent && !KNOWN_STRUCTURED_EVENTS.has(eventName)) {
    return null
  }

  const rawData = dataLines.join('\n').trim()
  if (!rawData) {
    return null
  }

  try {
    return {
      event: eventName as StructuredSseEventName,
      data: JSON.parse(rawData)
    }
  } catch (error) {
    console.error('Failed to parse structured SSE payload:', error, rawData)
    return {
      event: 'error',
      data: {
        message: rawData || 'Failed to parse structured SSE payload',
        stage: 'frontend'
      }
    }
  }
}

export class ChatApiService {
  private get configStore() {
    return useConfigStore()
  }

  async testConnection(): Promise<boolean> {
    try {
      const response = await axios.get(`${this.configStore.baseUrl}/actuator/health`, {
        timeout: 5000
      })
      return response.status === 200
    } catch (error) {
      console.error('Connection test failed:', error)
      return false
    }
  }

  async sendStructuredMessage(
    query: string,
    onEvent: (event: StructuredSseEvent) => void
  ): Promise<void> {
    const payload: StructuredChatRequest = {
      chat_id: this.configStore.chatId,
      user_id: this.configStore.userId,
      user_query: query
    }

    const response = await fetch(this.configStore.structuredApiUrl, {
      method: 'POST',
      mode: 'cors',
      credentials: 'omit',
      headers: {
        'Accept': 'text/event-stream',
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(payload)
    })

    if (!response.ok) {
      const errorText = await response.text()
      throw new Error(`HTTP error! status: ${response.status}, message: ${errorText}`)
    }

    if (!response.body) {
      throw new Error('No response body')
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

    // eslint-disable-next-line no-constant-condition
    while (true) {
      const { done, value } = await reader.read()
      if (done) {
        break
      }

      buffer += decoder.decode(value, { stream: true })
      const chunks = buffer.split('\n\n')
      buffer = chunks.pop() || ''

      for (const chunk of chunks) {
        const event = parseStructuredEvent(chunk)
        if (event) {
          onEvent(event)
        }
      }
    }

    if (buffer.trim()) {
      const event = parseStructuredEvent(buffer)
      if (event) {
        onEvent(event)
      }
    }
  }
}

export const chatApiService = new ChatApiService()
