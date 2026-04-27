<!--
  ~ Copyright 2024-2026 the original author or authors.
  ~
  ~ Licensed under the Apache License, Version 2.0 (the "License");
  ~ you may not use this file except in compliance with the License.
  ~ You may obtain a copy of the License at
  ~
  ~     http://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing, software
  ~ distributed under the License is distributed on an "AS IS" BASIS,
  ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  ~ See the License for the specific language governing permissions and
  ~ limitations under the License.
-->

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { Button, Tag } from 'ant-design-vue'
import { BulbFilled, DownOutlined, UpOutlined } from '@ant-design/icons-vue'
import MarkdownRenderer from './MarkdownRenderer.vue'
import { useChatStore } from '@/stores/chat'
import type { AssistantMessage, ThinkingTimelineStep } from '@/types'

const props = defineProps<{
  message: AssistantMessage
}>()

const { t } = useI18n()
const chatStore = useChatStore()

const hasThinking = computed(() => props.message.thinkingTimeline.length > 0)
const shouldRenderThoughts = computed(() => hasThinking.value || props.message.isStreaming)

const visibleSteps = computed(() => {
  return [...props.message.thinkingTimeline].sort((left, right) => left.sequence - right.sequence)
})

const normalizeEscapedText = (raw: string) => {
  if (!raw) {
    return ''
  }

  let text = raw.trim()
  if (text.startsWith('"') && text.endsWith('"')) {
    try {
      const parsed = JSON.parse(text)
      if (typeof parsed === 'string') {
        text = parsed
      }
    } catch {
      // Keep original text.
    }
  }

  return text
    .replace(/\\r\\n/g, '\n')
    .replace(/\\n/g, '\n')
    .replace(/\\t/g, '\t')
    .replace(/\\"/g, '"')
    .replace(/\\\\/g, '\\')
    .trim()
}

const toggleThoughts = () => {
  chatStore.setAssistantThoughtsExpanded(
    props.message.id,
    !props.message.thoughtsExpanded,
    true
  )
}

const timelineDotClass = (step: ThinkingTimelineStep) => {
  if (step.status === 'error' || step.kind === 'error') {
    return 'timeline-dot-error'
  }
  if (step.isOpen || step.status === 'pending') {
    return 'timeline-dot-pending'
  }
  if (step.kind === 'tool') return 'timeline-dot-tool'
  if (step.kind === 'handoff') return 'timeline-dot-handoff'
  if (step.kind === 'reasoning') return 'timeline-dot-reasoning'
  return 'timeline-dot-success'
}

const stepCardClass = (step: ThinkingTimelineStep) => {
  if (step.kind === 'error') return 'timeline-step-error'
  if (step.kind === 'tool') return 'timeline-step-tool'
  if (step.kind === 'handoff') return 'timeline-step-handoff'
  if (step.kind === 'reasoning') return 'timeline-step-reasoning'
  return ''
}
</script>

<template>
  <div class="assistant-card">
    <section v-if="shouldRenderThoughts" class="thoughts-card">
      <div class="thoughts-header">
        <div class="thoughts-title">
          <BulbFilled class="thoughts-icon" />
          <span>{{ t('chat.sections.thoughts') }}</span>
        </div>
        <Button
          v-if="message.thoughtsExpanded"
          type="text"
          size="small"
          class="thoughts-header-toggle"
          @click="toggleThoughts"
        >
          <template #icon><UpOutlined /></template>
        </Button>
      </div>

      <button
        v-if="!message.thoughtsExpanded"
        type="button"
        class="thoughts-collapsed-bar"
        @click="toggleThoughts"
      >
        <span>{{ t('chat.thoughtsCollapsedHint') }}</span>
        <DownOutlined />
      </button>

      <div v-else class="thoughts-body">
        <div v-if="!visibleSteps.length" class="thoughts-empty">
          {{ t('chat.thinking') }}
        </div>

        <div v-else class="timeline">
          <div
            v-for="step in visibleSteps"
            :key="step.id"
            class="timeline-step"
            :class="[stepCardClass(step), { 'timeline-step-indented': step.agent !== 'supervisor_agent' }]"
          >
            <div class="timeline-rail">
              <div class="timeline-dot" :class="timelineDotClass(step)" />
            </div>
            <div class="timeline-content">
              <Tag class="timeline-agent-tag" :bordered="false">{{ step.agentLabel }}</Tag>
              <div class="timeline-step-title">{{ step.title }}</div>
              <div class="timeline-step-description">
                <MarkdownRenderer
                  :content="normalizeEscapedText(step.description)"
                  :is-streaming="Boolean(message.isStreaming && step.isOpen)"
                />
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <section v-if="message.answer" class="result-section">
      <div class="result-title">{{ t('chat.sections.result') }}</div>
      <MarkdownRenderer :content="message.answer" :is-streaming="message.isStreaming || false" />
    </section>

    <section v-if="message.error" class="assistant-error">
      <div class="result-title">{{ t('common.error') }}</div>
      <div class="assistant-error-text">{{ message.error }}</div>
    </section>
  </div>
</template>

<style scoped>
.assistant-card {
  display: flex;
  flex-direction: column;
  gap: 18px;
  width: 100%;
}

.thoughts-card {
  overflow: hidden;
  border: 1px solid rgba(226, 232, 240, 0.8);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(16px);
  box-shadow: 0 4px 20px rgba(15, 23, 42, 0.05), inset 0 0 0 1px rgba(255, 255, 255, 0.5);
  transition: box-shadow 0.3s ease;
}

.thoughts-card:hover {
  box-shadow: 0 8px 30px rgba(15, 23, 42, 0.08), inset 0 0 0 1px rgba(255, 255, 255, 0.5);
}

.thoughts-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 20px 24px;
  border-bottom: 1px solid #eef2f7;
}

.thoughts-title {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  font-size: 15px;
  font-weight: 700;
  color: #111827;
}

.thoughts-icon {
  font-size: 16px;
  color: #5b7cff;
}

.thoughts-header-toggle {
  color: #475569;
}

.thoughts-collapsed-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
  padding: 18px 24px;
  border: 0;
  background: #ffffff;
  color: #1f2937;
  font-size: 15px;
  text-align: left;
  cursor: pointer;
}

.thoughts-collapsed-bar:hover {
  background: #f8fafc;
}

.thoughts-body {
  padding: 24px;
}

.thoughts-empty {
  color: #64748b;
  font-size: 14px;
}

.timeline {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.timeline-step {
  display: grid;
  grid-template-columns: 18px minmax(0, 1fr);
  gap: 16px;
}

.timeline-rail {
  position: relative;
  display: flex;
  justify-content: center;
}

.timeline-rail::after {
  content: '';
  position: absolute;
  top: 18px;
  bottom: -20px;
  left: 50%;
  width: 1px;
  background: #e2e8f0;
  transform: translateX(-50%);
}

.timeline-step:last-child .timeline-rail::after {
  display: none;
}

.timeline-dot {
  position: relative;
  z-index: 1;
  width: 10px;
  height: 10px;
  margin-top: 6px;
  border-radius: 999px;
  background: #94a3b8;
}

.timeline-dot-pending {
  background: #6366f1;
  box-shadow: 0 0 0 4px rgba(99, 102, 241, 0.15);
  animation: pulse 2s cubic-bezier(0.4, 0, 0.6, 1) infinite;
}

@keyframes pulse {
  0%, 100% {
    opacity: 1;
  }
  50% {
    opacity: 0.6;
  }
}

.timeline-dot-success {
  background: #10b981;
}

.timeline-dot-reasoning {
  background: #0284c7;
}

.timeline-dot-tool {
  background: #d97706;
}

.timeline-dot-handoff {
  background: #7c3aed;
  box-shadow: 0 0 0 4px rgba(124, 58, 237, 0.12);
}

.timeline-dot-error {
  background: #dc2626;
  box-shadow: 0 0 0 4px rgba(220, 38, 38, 0.12);
}

.timeline-content {
  min-width: 0;
}

.timeline-agent-tag {
  margin-bottom: 10px;
  padding: 3px 10px;
  border-radius: 999px;
  background: #eef2ff;
  color: #4338ca;
  font-size: 12px;
  font-weight: 600;
}

.timeline-step-title {
  margin-bottom: 8px;
  font-size: 15px;
  font-weight: 700;
  color: #111827;
  line-height: 1.5;
}

.timeline-step-description {
  color: #4b5563;
  font-size: 14px;
  line-height: 1.6;
}

.timeline-step-description :deep(.markdown-content) {
  color: #4b5563;
  font-size: 14px;
  line-height: 1.6;
}

.timeline-step-description :deep(h1),
.timeline-step-description :deep(h2),
.timeline-step-description :deep(h3),
.timeline-step-description :deep(h4),
.timeline-step-description :deep(h5),
.timeline-step-description :deep(h6) {
  font-size: 0.95em;
  font-weight: 600;
  border-bottom: none;
  padding-bottom: 0;
  margin: 0.4em 0 0.2em 0;
  color: #374151;
}

.timeline-step-description :deep(p) {
  margin: 0.2em 0;
}

.timeline-step-reasoning .timeline-step-description,
.timeline-step-reasoning .timeline-step-description :deep(.markdown-content) {
  color: #6b7280;
}

/* Compact table within the thoughts panel so it doesn't overwhelm the step card */
.timeline-step-description :deep(table) {
  margin: 0.5em 0;
  font-size: 13px;
  box-shadow: none;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
}

.timeline-step-description :deep(th),
.timeline-step-description :deep(td) {
  padding: 5px 10px;
}

.timeline-step-description :deep(pre) {
  margin: 0.5em 0;
  padding: 10px 12px;
  font-size: 12px;
}

.timeline-step-description :deep(hr) {
  height: 1px;
  margin: 0.4em 0;
}

.timeline-step-plain-text {
  white-space: pre-wrap;
  word-break: break-word;
}

.timeline-step-indented {
  margin-left: 20px;
}

.timeline-step-reasoning .timeline-agent-tag {
  background: #e0f2fe;
  color: #0284c7;
}

.timeline-step-tool .timeline-agent-tag {
  background: #fef3c7;
  color: #d97706;
}

.timeline-step-handoff .timeline-agent-tag {
  background: #ede9fe;
  color: #7c3aed;
}

.timeline-step-error .timeline-agent-tag {
  background: #fee2e2;
  color: #dc2626;
}

.result-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.result-section :deep(.markdown-content) {
  font-size: 15px;
  line-height: 1.7;
  color: #1f2937;
}

.result-title {
  font-size: 12px;
  font-weight: 700;
  color: #94a3b8;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.assistant-error {
  padding: 14px 16px;
  border: 1px solid #fecaca;
  border-radius: 12px;
  background: #fff7f7;
}

.assistant-error-text {
  color: #b91c1c;
  font-size: 13px;
  line-height: 1.6;
}

@media (max-width: 768px) {
  .thoughts-header,
  .thoughts-body,
  .thoughts-collapsed-bar {
    padding-left: 16px;
    padding-right: 16px;
  }

  .timeline-step {
    gap: 12px;
  }

  .timeline-step-title {
    font-size: 15px;
  }
}
</style>
