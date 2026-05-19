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
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import {
  Avatar,
  Button,
  Dropdown,
  Input,
  message,
  Popover,
  Select,
  Space,
  Tag,
  Tooltip
} from 'ant-design-vue'
import {
  AlertOutlined,
  ClearOutlined,
  CodeOutlined,
  GlobalOutlined,
  SearchOutlined,
  SendOutlined,
  SettingOutlined,
  UserOutlined
} from '@ant-design/icons-vue'
import { useChatStore } from '@/stores/chat'
import { useConfigStore } from '@/stores/config'
import { chatApiService } from '@/api/chat'
import AssistantMessageCard from './AssistantMessageCard.vue'
import intelligentAssistant from '@/assets/icons/intelligent_assistant.svg'
import { getLocale, setLocale } from '@/base/i18n'
import { createThoughtsTimelineLabels } from '@/utils/thoughtsTimeline'
import type {
  AssistantMessage,
  Message as ChatMessage,
  StructuredSseEvent,
  UserMessage
} from '@/types'

const { t } = useI18n()
const router = useRouter()
const route = useRoute()
const chatStore = useChatStore()
const configStore = useConfigStore()

const inputValue = ref('')
const chatContainer = ref<HTMLElement>()
const userIdInput = ref('')
const showUserIdInput = ref(false)
const clusterIdInput = ref('')
const showClusterIdInput = ref(false)

const currentLocale = computed(() => getLocale())

const languageMenuItems = computed(() => [
  { key: 'zh', label: t('common.chinese') },
  { key: 'en', label: t('common.english') }
])

const chatExamples = computed(() => [
  { text: t('chat.examples.diagnoseDeployments'), icon: AlertOutlined },
  { text: t('chat.examples.diagnoseFinops'), icon: SearchOutlined },
  { text: t('chat.examples.explainYaml'), icon: CodeOutlined }
])

const hasBaseUrl = computed(() => configStore.baseUrl.trim().length > 0)
const hasUserId = computed(() => configStore.userId.trim().length > 0)
const hasClusterId = computed(() => configStore.clusterId.trim().length > 0)

const canSend = computed(() => {
  return inputValue.value.trim().length > 0 && !chatStore.isLoading && hasBaseUrl.value && hasUserId.value && hasClusterId.value
})

const sendButtonTooltip = computed(() => {
  if (!hasBaseUrl.value) {
    return t('chat.tooltip.noBaseUrl')
  }
  if (!hasUserId.value) {
    return t('chat.tooltip.noUserId')
  }
  if (!hasClusterId.value) {
    return t('chat.tooltip.noClusterId')
  }
  return ''
})

const handleLanguageChange = (info: { key: string }) => {
  setLocale(info.key)
}

const isUserMessage = (message: ChatMessage): message is UserMessage => message.type === 'user'
const isAssistantMessage = (message: ChatMessage): message is AssistantMessage => message.type === 'assistant'

const scrollToBottom = () => {
  nextTick(() => {
    if (chatContainer.value) {
      chatContainer.value.scrollTop = chatContainer.value.scrollHeight
    }
  })
}

const focusChatInputTextArea = () => {
  const input = document.getElementById('chatInputTextArea')
  if (input) {
    input.focus()
  }
}

const setUserId = () => {
  if (userIdInput.value.trim()) {
    configStore.updateConfig({ userId: userIdInput.value.trim() })
    showUserIdInput.value = false
    userIdInput.value = ''
    message.success(t('chat.userIdSetSuccess'))
  } else {
    message.warning(t('chat.userIdRequired'))
  }
}

const showUserIdInputDialog = () => {
  showUserIdInput.value = true
  userIdInput.value = configStore.userId
}

const setClusterId = () => {
  if (clusterIdInput.value.trim()) {
    configStore.updateConfig({ clusterId: clusterIdInput.value.trim() })
    showClusterIdInput.value = false
    clusterIdInput.value = ''
    message.success(t('chat.clusterSetSuccess'))
  } else {
    message.warning(t('chat.clusterRequired'))
  }
}

const showClusterIdInputDialog = () => {
  showClusterIdInput.value = true
  clusterIdInput.value = configStore.clusterId
}

const handleExampleClick = (example: string) => {
  inputValue.value = example
  focusChatInputTextArea()
}

const clearChat = () => {
  const newSessionId = Date.now().toString()
  router.push(`/chat/${newSessionId}`)
  message.success(t('chat.chatCleared'))
}

watch(() => route.params.sessionId, (newSessionId) => {
  if (newSessionId && newSessionId !== configStore.chatId) {
    configStore.setChatId(newSessionId as string)
    chatStore.clearMessages()
    chatStore.addAssistantMessage({
      answer: t('chat.welcome')
    })
    nextTick(() => {
      focusChatInputTextArea()
    })
  }
})

const thoughtsTimelineLabels = computed(() => createThoughtsTimelineLabels(t))

const handleStructuredEvent = (event: StructuredSseEvent) => {
  switch (event.event) {
    case 'reasoning_delta':
    case 'tool_start':
    case 'tool_result':
    case 'error':
    case 'done':
      chatStore.applyAssistantStructuredEvent(event, thoughtsTimelineLabels.value)
      if (event.event === 'error') {
        chatStore.setAssistantError(event.data?.message || t('chat.error'))
      }
      break
    case 'answer_delta':
      chatStore.appendAssistantAnswer(event.data?.text || '')
      break
    default:
      break
  }

  scrollToBottom()
}

const sendMessage = async () => {
  if (!canSend.value) {
    return
  }

  const userMessage = inputValue.value.trim()

  await nextTick(() => {
    inputValue.value = ''
  })

  chatStore.addUserMessage(userMessage)
  chatStore.addAssistantMessage({ isStreaming: true })
  scrollToBottom()

  try {
    chatStore.setLoading(true)
    chatStore.setError(null)

    await chatApiService.sendStructuredMessage(userMessage, handleStructuredEvent)
  } catch (error: any) {
    console.error('Structured chat error details:', {
      error,
      message: error?.message,
      stack: error?.stack,
      name: error?.name
    })
    chatStore.setError(t('chat.error'))
    chatStore.setAssistantError(error?.message || t('chat.unknownError'))
    message.error(`${t('chat.sendError')}: ${error?.message || t('chat.unknownError')}`)
  } finally {
    chatStore.finalizeAssistantMessage()
    chatStore.setLoading(false)
    nextTick(() => {
      focusChatInputTextArea()
    })
  }
}

onMounted(() => {
  configStore.loadConfig()

  const sessionId = route.params.sessionId as string
  if (!sessionId) {
    const newSessionId = Date.now().toString()
    router.replace(`/chat/${newSessionId}`)
    configStore.setChatId(newSessionId)
  } else {
    configStore.setChatId(sessionId)
  }

  if (chatStore.messages.length === 0) {
    chatStore.addAssistantMessage({
      answer: t('chat.welcome')
    })
  }

  nextTick(() => {
    focusChatInputTextArea()
  })
})
</script>

<template>
  <div class="chat-interface">
    <div class="chat-header">
      <div class="header-content">
        <div class="title-section">
          <img :src="intelligentAssistant" alt="Platform Assistant" class="svg-icon" />
          <div class="title-copy">
            <h2>{{ t('chat.title') }}</h2>
            <div class="session-item">
              <span class="label">{{ t('chat.sessionId') }}:</span>
              <span class="session-id">{{ configStore.chatId }}</span>
            </div>
            <div class="session-item">
              <span class="label">{{ t('chat.clusterId') }}:</span>
              <span v-if="hasClusterId" class="session-id">{{ configStore.clusterId }}</span>
              <Tag v-else color="error" style="cursor: pointer" @click="showClusterIdInputDialog">
                {{ t('common.set') }}
              </Tag>
            </div>
          </div>
        </div>

        <div class="header-actions">
          <Button type="text" @click="clearChat" :disabled="chatStore.messages.length <= 1">
            <template #icon><ClearOutlined /></template>
            {{ t('chat.clear') }}
          </Button>
          <Button type="text" @click="router.push('/settings')">
            <template #icon><SettingOutlined /></template>
            {{ t('chat.settings') }}
          </Button>
          <Dropdown :trigger="['click']" placement="bottomRight">
            <Button type="text" class="lang-btn">
              <template #icon><GlobalOutlined /></template>
              {{ currentLocale === 'zh' ? '中文' : 'EN' }}
            </Button>
            <template #overlay>
              <div class="lang-menu">
                <div
                  v-for="item in languageMenuItems"
                  :key="item.key"
                  class="lang-menu-item"
                  :class="{ active: currentLocale === item.key }"
                  @click="handleLanguageChange({ key: item.key })"
                >
                  {{ item.label }}
                </div>
              </div>
            </template>
          </Dropdown>
          <Popover placement="bottomRight" trigger="hover">
            <Avatar class="user-avatar-header" :class="{ 'user-avatar-set': hasUserId }">
              <template #icon><UserOutlined /></template>
            </Avatar>
            <template #content>
              <div class="user-info-content">
                <div class="user-info-item">
                  <span class="label">{{ t('chat.userId') }}:</span>
                  <span v-if="hasUserId" class="user-id">{{ configStore.userId }}</span>
                  <Button v-else type="link" size="small" @click="showUserIdInputDialog">
                    {{ t('common.set') }}
                  </Button>
                </div>
                <div class="user-info-item" style="margin-top: 8px">
                  <span class="label">{{ t('chat.clusterId') }}:</span>
                  <span v-if="hasClusterId" class="user-id">{{ configStore.clusterId }}</span>
                  <Button v-else type="link" size="small" @click="showClusterIdInputDialog">
                    {{ t('common.set') }}
                  </Button>
                </div>
              </div>
            </template>
          </Popover>
        </div>
      </div>
    </div>

    <div class="chat-messages" ref="chatContainer">
      <div class="messages-container">
        <div
          v-for="msg in chatStore.messages"
          :key="msg.id"
          class="message-wrapper"
          :class="{ 'user-message': msg.type === 'user' }"
        >
          <div class="message-content">
            <Avatar v-if="isUserMessage(msg)" class="user-avatar">
              <template #icon><UserOutlined /></template>
            </Avatar>
            <Avatar v-else class="assistant-avatar">
              <img :src="intelligentAssistant" alt="Assistant" class="svg-icon" />
            </Avatar>

            <div class="message-bubble" :class="{ 'assistant-bubble': isAssistantMessage(msg) }">
              <template v-if="isUserMessage(msg)">
                <div class="user-question">{{ msg.question }}</div>
              </template>

              <AssistantMessageCard v-else :message="msg" />
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="chat-input">
      <div class="input-container">
        <div v-if="chatStore.messages.length <= 1" class="chat-examples">
          <Space wrap>
            <Tag
              v-for="(example, index) in chatExamples"
              :key="index"
              class="example-tag"
              @click="handleExampleClick(example.text)"
            >
              <template #icon><component :is="example.icon" /></template>
              {{ example.text }}
            </Tag>
          </Space>
        </div>

        <div class="input-wrapper">
          <Input.TextArea
            id="chatInputTextArea"
            v-model:value="inputValue"
            :placeholder="t('chat.placeholder')"
            :auto-size="{ minRows: 2, maxRows: 5 }"
            :disabled="chatStore.isLoading"
            class="message-input"
          />
          <Tooltip :title="sendButtonTooltip" placement="top">
            <span class="send-button-wrapper" :class="{ 'show-tooltip': !hasBaseUrl || !hasUserId }">
              <Button
                type="primary"
                class="send-button"
                :disabled="!canSend"
                :loading="chatStore.isLoading"
                @click="sendMessage"
              >
                <template #icon><SendOutlined /></template>
                {{ t('chat.send') }}
              </Button>
            </span>
          </Tooltip>
        </div>
      </div>
    </div>

    <div v-if="showUserIdInput" class="user-id-modal">
      <div class="modal-content">
        <div class="modal-title">{{ t('chat.setUserId') }}</div>
        <div class="modal-body">
          <p>{{ t('chat.userIdPrompt') }}</p>
          <Input
            v-model:value="userIdInput"
            :placeholder="t('chat.userIdPlaceholder')"
            class="user-id-input"
            @keydown.enter="setUserId"
          />
          <div class="modal-actions">
            <Button type="primary" @click="setUserId" :disabled="!userIdInput.trim()">
              {{ t('common.confirm') }}
            </Button>
            <Button @click="showUserIdInput = false">
              {{ t('common.cancel') }}
            </Button>
          </div>
        </div>
      </div>
    </div>

    <div v-if="showClusterIdInput" class="user-id-modal">
      <div class="modal-content">
        <div class="modal-title">{{ t('chat.setClusterId') }}</div>
        <div class="modal-body">
          <p>{{ t('chat.clusterPrompt') }}</p>
          <Select
            v-model:value="clusterIdInput"
            :placeholder="t('chat.clusterPlaceholder')"
            class="user-id-input"
            :options="[
              { value: 'dev', label: 'dev' },
              { value: 'sit', label: 'sit' },
              { value: 'uat', label: 'uat' },
              { value: 'st', label: 'st' }
            ]"
          />
          <div class="modal-actions">
            <Button type="primary" @click="setClusterId" :disabled="!clusterIdInput.trim()">
              {{ t('common.confirm') }}
            </Button>
            <Button @click="showClusterIdInput = false">
              {{ t('common.cancel') }}
            </Button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.chat-interface {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: #f3f6f9;
}

.chat-header {
  background: #ffffff;
  border-bottom: 1px solid #e5ebf2;
  padding: 16px 24px;
}

.header-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.title-section {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.title-copy {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.title-copy h2 {
  margin: 0;
  font-size: 20px;
  color: #0f172a;
}

.session-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.label {
  color: #64748b;
  font-size: 12px;
  font-weight: 600;
}

.session-id,
.user-id {
  padding: 2px 8px;
  border-radius: 8px;
  background: #eef5ff;
  color: #2563eb;
  font-size: 12px;
  line-height: 1.5;
  word-break: break-word;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-wrap: wrap;
}

.lang-menu {
  min-width: 120px;
  background: #ffffff;
  border: 1px solid #e5ebf2;
  border-radius: 8px;
  padding: 6px;
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.08);
}

.lang-menu-item {
  padding: 8px 10px;
  border-radius: 6px;
  cursor: pointer;
  color: #334155;
}

.lang-menu-item.active,
.lang-menu-item:hover {
  background: #eff6ff;
  color: #1d4ed8;
}

.user-avatar-header {
  cursor: pointer;
  background: #e2e8f0;
  color: #334155;
}

.user-avatar-set {
  background: #d1fae5;
  color: #047857;
}

.user-info-content {
  min-width: 180px;
}

.user-info-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 24px 0;
}

.messages-container {
  width: min(780px, calc(100vw - 32px));
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.message-wrapper {
  display: flex;
}

.message-wrapper.user-message {
  justify-content: flex-end;
}

.message-content {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  max-width: 100%;
  width: 100%;
}

.message-wrapper.user-message .message-content {
  flex-direction: row-reverse;
  width: auto;
  max-width: 85%;
}

.assistant-avatar,
.user-avatar {
  flex-shrink: 0;
  margin-top: 2px;
}

/* --- Assistant message: no bubble, full width (ChatGPT / Gemini style) --- */
.message-bubble {
  min-width: 0;
  max-width: 100%;
  width: 100%;
  padding: 0;
  border-radius: 0;
  border: none;
  background: transparent;
  box-shadow: none;
}

.message-bubble.assistant-bubble {
  /* Full-width, chrome-less – content fills the container column */
  padding: 0;
}

/* --- User message: refined pill bubble --- */
.message-wrapper.user-message .message-bubble {
  width: auto;
  padding: 12px 18px;
  border-radius: 20px 20px 4px 20px;
  background: #2563eb;
  border: none;
  color: #ffffff;
  box-shadow: 0 2px 8px rgba(37, 99, 235, 0.18);
}

/* --- Unified typography --- */
.user-question {
  font-size: 15px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

.chat-input {
  border-top: 1px solid #e5ebf2;
  background: #ffffff;
  padding: 16px 0 20px;
}

.input-container {
  width: min(780px, calc(100vw - 32px));
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.chat-examples {
  display: flex;
  flex-wrap: wrap;
}

.example-tag {
  cursor: pointer;
  border-radius: 8px;
  padding: 6px 10px;
  border-color: #d6e3f2;
  background: #f8fbff;
  color: #1f4b7a;
}

.input-wrapper {
  display: flex;
  align-items: flex-end;
  gap: 12px;
}

.message-input {
  flex: 1;
}

.send-button-wrapper {
  display: inline-flex;
}

.send-button {
  height: 44px;
  border-radius: 8px;
}

.user-id-modal {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.28);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
}

.modal-content {
  width: min(420px, 100%);
  border-radius: 8px;
  background: #ffffff;
  padding: 18px;
  box-shadow: 0 24px 48px rgba(15, 23, 42, 0.18);
}

.modal-title {
  font-size: 16px;
  font-weight: 600;
  color: #0f172a;
  margin-bottom: 12px;
}

.modal-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.modal-body p {
  margin: 0;
  color: #475569;
  line-height: 1.6;
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.svg-icon {
  width: 28px;
  height: 28px;
}

@media (max-width: 768px) {
  .chat-header {
    padding: 14px 16px;
  }

  .header-content {
    align-items: flex-start;
    flex-direction: column;
  }

  .messages-container,
  .input-container {
    width: calc(100vw - 24px);
  }

  .message-wrapper.user-message .message-bubble {
    max-width: calc(100vw - 84px);
    padding: 10px 14px;
  }

  .input-wrapper {
    flex-direction: column;
    align-items: stretch;
  }

  .send-button {
    width: 100%;
  }
}
</style>
