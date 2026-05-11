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

export type ToolTraceStatus = 'pending' | 'success' | 'error'

export type ThinkingTimelineStepKind = 'reasoning' | 'tool' | 'handoff' | 'error'

export interface ThinkingTimelineStep {
  id: string
  sequence: number
  kind: ThinkingTimelineStepKind
  agent: string
  agentLabel: string
  title: string
  description: string
  status: ToolTraceStatus
  startedAt: number
  finishedAt?: number
  isOpen: boolean
}

export interface BaseMessage {
  id: string
  type: 'user' | 'assistant'
  timestamp: number
  isStreaming?: boolean
}

export interface UserMessage extends BaseMessage {
  type: 'user'
  question: string
}

export interface AssistantMessage extends BaseMessage {
  type: 'assistant'
  answer: string
  thinkingTimeline: ThinkingTimelineStep[]
  thoughtsExpanded: boolean
  thoughtsAutoManaged?: boolean
  thoughtsMeta?: {
    nextReasoningTitleByAgent: Record<string, string>
  }
  error: string | null
}

export type Message = UserMessage | AssistantMessage

export interface ChatConfig {
  baseUrl: string
  userId: string
  chatId: string
}

export interface ApiResponse<T = any> {
  success: boolean
  data?: T
  error?: string
  message?: string
}

export interface ChatRequest {
  chat_id: string
  user_id: string
  user_query: string
  cluster_id: string
}

export type StructuredChatRequest = ChatRequest

export interface ChatResponse {
  success: boolean
  data?: string
  error?: string
}

export type StructuredSseEventName =
  | 'reasoning_delta'
  | 'tool_start'
  | 'tool_result'
  | 'answer_delta'
  | 'done'
  | 'error'

export interface StructuredSseEvent<T = any> {
  event: StructuredSseEventName
  data: T
}

export interface ExampleQuestion {
  id: string
  text: string
  category: 'menu' | 'order' | 'price' | 'feedback'
}

export interface Feature {
  id: string
  title: string
  description: string
  icon: string
  color: string
}
