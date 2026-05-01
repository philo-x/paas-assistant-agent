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

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

describe('parseStructuredEvent', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('parses known structured events', async () => {
    const { parseStructuredEvent } = await import('./chat')

    const event = parseStructuredEvent('event: answer_delta\ndata: {"text":"hello"}')

    expect(event).toEqual({
      event: 'answer_delta',
      data: {
        text: 'hello'
      }
    })
  })

  it('ignores unknown events and empty data', async () => {
    const { parseStructuredEvent } = await import('./chat')

    expect(parseStructuredEvent('event: user\ndata: {"question":"hello"}')).toBeNull()
    expect(parseStructuredEvent('event: answer_delta\ndata: ')).toBeNull()
  })

  it('keeps data-only chunks compatible with the previous answer stream', async () => {
    const { parseStructuredEvent } = await import('./chat')

    const event = parseStructuredEvent('data: {"text":"legacy answer"}')

    expect(event).toEqual({
      event: 'answer_delta',
      data: {
        text: 'legacy answer'
      }
    })
  })

  it('turns invalid JSON data into a frontend error event', async () => {
    const { parseStructuredEvent } = await import('./chat')
    vi.spyOn(console, 'error').mockImplementation(() => {})

    const event = parseStructuredEvent('event: answer_delta\ndata: [DONE]')

    expect(event?.event).toBe('error')
    expect(event?.data).toMatchObject({
      message: '[DONE]',
      stage: 'frontend'
    })
  })
})
