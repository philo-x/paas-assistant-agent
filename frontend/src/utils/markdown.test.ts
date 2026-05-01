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
import { marked } from 'marked'
import { normalizeMarkdownContent } from './markdown'

describe('normalizeMarkdownContent', () => {
  it('repairs malformed LLM pipe tables with a stray pipe and missing index header', async () => {
    const content = [
      '当前集群共有 7 个命名空间，全部处于 `Active` 状态：',
      '|',
      '| 命名空间 | 创建时间 | 类型 |',
      '|---|----------|----------|------|',
      '| 1 | default | 2026-04-17 07:26:23 | 默认命名空间 |'
    ].join('\n')

    const normalized = normalizeMarkdownContent(content)
    const html = await marked(normalized)

    expect(normalized).not.toContain('\n|\n')
    expect(normalized).toContain('|  | 命名空间 | 创建时间 | 类型 |')
    expect(html).toContain('<table>')
    expect(html).toContain('<td>default</td>')
  })

  it('leaves valid pipe tables unchanged', () => {
    const content = [
      '| 名称 | 状态 |',
      '|---|---|',
      '| default | Active |'
    ].join('\n')

    expect(normalizeMarkdownContent(content)).toBe(content)
  })

  it('does not rewrite pipe text inside fenced code blocks', () => {
    const content = [
      '```',
      '|',
      '| not | a table |',
      '|---|---|---|',
      '```'
    ].join('\n')

    expect(normalizeMarkdownContent(content)).toBe(content)
  })
})
