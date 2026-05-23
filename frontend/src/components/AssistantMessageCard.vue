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
import { BulbFilled, DownOutlined, UpOutlined, AlertOutlined } from '@ant-design/icons-vue'
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

const showEmptyAnswerFallback = computed(() => {
  return !props.message.isStreaming && !props.message.answer && !props.message.error
})

const normalizeEscapedText = (raw: string) => {
  if (!raw) {
    return ''
  }

  let text = raw
  if (text.trim().startsWith('"') && text.trim().endsWith('"')) {
    try {
      const parsed = JSON.parse(text.trim())
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
    <!-- Thoughts Module (Restored Original Style) -->
    <section v-if="shouldRenderThoughts" class="thoughts-card">
      <div class="thoughts-header">
        <button
          type="button"
          class="thoughts-toggle-btn"
          @click="toggleThoughts"
        >
          <div class="thoughts-toggle-content">
            <span class="thoughts-hint">{{ t('chat.thoughtsCollapsedHint') }}</span>
            <component :is="message.thoughtsExpanded ? UpOutlined : DownOutlined" class="toggle-icon" />
          </div>
        </button>
      </div>

      <Transition name="fade-slide">
        <div v-if="message.thoughtsExpanded" class="thoughts-body">
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
      </Transition>
    </section>

    <!-- Result Module (New Separate Module) -->
    <section v-if="message.answer || showEmptyAnswerFallback" class="result-module">
      <div class="result-header">
        <div class="result-label-wrapper">
          <span class="result-label">{{ t('chat.sections.result') }}</span>
        </div>
      </div>
      <div class="result-content">
        <MarkdownRenderer
          v-if="message.answer"
          :content="message.answer"
          :is-streaming="message.isStreaming || false"
        />
        <div v-else class="empty-answer-fallback">
          <BulbFilled class="fallback-icon" />
          <span class="fallback-text">{{ t('chat.timelineFallbacks.emptyAnswer') }}</span>
        </div>
      </div>
    </section>

    <!-- Error Module -->
    <section v-if="message.error" class="error-module">
      <div class="error-header">
        <AlertOutlined class="error-icon" />
        <span class="error-label">{{ t('common.error') }}</span>
      </div>
      <div class="error-text">{{ message.error }}</div>
    </section>
  </div>
</template>

<style scoped>
.assistant-card {
  display: flex;
  flex-direction: column;
  gap: 16px;
  width: 100%;
}

/* --- Thoughts Module (Restored Timeline Style) --- */
.thoughts-card {
  display: flex;
  flex-direction: column;
}

.thoughts-header {
  margin-bottom: 8px;
}

.thoughts-toggle-btn {
  display: flex;
  align-items: center;
  padding: 6px 12px;
  border: 1px solid #e5ebf2;
  border-radius: 20px;
  background: #ffffff;
  color: #475569;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
}

.thoughts-toggle-btn:hover {
  background: #f8fafc;
  border-color: #cbd5e1;
}

.thoughts-toggle-content {
  display: flex;
  align-items: center;
  gap: 6px;
}

.toggle-icon {
  font-size: 10px;
}

.thoughts-body {
  padding: 12px 16px;
  background: rgba(248, 250, 252, 0.5);
  border-radius: 12px;
  margin-bottom: 8px;
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
  0%, 100% { opacity: 1; }
  50% { opacity: 0.6; }
}

.timeline-dot-success { background: #10b981; }
.timeline-dot-reasoning { background: #0284c7; }
.timeline-dot-tool { background: #d97706; }
.timeline-dot-handoff { background: #7c3aed; }
.timeline-dot-error { background: #dc2626; }

.timeline-agent-tag {
  margin-bottom: 8px;
  padding: 2px 8px;
  border-radius: 4px;
  background: #eef2ff;
  color: #4338ca;
  font-size: 11px;
  font-weight: 600;
}

.timeline-step-title {
  margin-bottom: 4px;
  font-size: 14px;
  font-weight: 700;
  color: #1e293b;
}

.timeline-step-description {
  color: #475569;
  font-size: 14px;
  line-height: 1.6;
}

.timeline-step-indented {
  margin-left: 20px;
}

/* --- Result Module --- */
.result-module {
  margin-top: 4px;
  padding: 0;
}

.result-header {
  display: flex;
  align-items: center;
  margin-bottom: 8px;
}

.result-label {
  font-size: 12px;
  font-weight: 700;
  color: #94a3b8;
  letter-spacing: 0.1em;
  text-transform: uppercase;
}

.result-content :deep(.markdown-content) {
  font-size: 16px;
  line-height: 1.7;
  color: #0f172a;
}

/* --- Error Module --- */
.error-module {
  margin-top: 8px;
  padding: 12px 16px;
  border: 1px solid #fee2e2;
  border-radius: 12px;
  background: #fef2f2;
}

.error-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.error-icon { color: #dc2626; }
.error-label {
  font-size: 12px;
  font-weight: 700;
  color: #991b1b;
  text-transform: uppercase;
}
.error-text { color: #b91c1c; font-size: 13px; }

.empty-answer-fallback {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 14px 18px;
  background: rgba(241, 245, 249, 0.6);
  border: 1px dashed #cbd5e1;
  border-radius: 12px;
  color: #475569;
  font-size: 14px;
  line-height: 1.6;
}

.fallback-icon {
  margin-top: 3px;
  color: #eab308;
  font-size: 16px;
  flex-shrink: 0;
}

.fallback-text {
  flex-grow: 1;
}

/* --- Transitions --- */
.fade-slide-enter-active,
.fade-slide-leave-active {
  transition: all 0.3s ease;
  max-height: 2000px;
  opacity: 1;
}

.fade-slide-enter-from,
.fade-slide-leave-to {
  max-height: 0;
  opacity: 0;
  overflow: hidden;
}

@media (max-width: 768px) {
  .timeline-step { gap: 12px; }
}
</style>
