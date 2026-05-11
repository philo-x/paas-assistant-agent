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
import { ref, computed } from 'vue'

export interface ConfigState {
  baseUrl: string
  userId: string
  chatId: string
  clusterId: string
}

export const useConfigStore = defineStore('config', () => {
  // Read initial baseUrl from window config (injected at runtime) or use default
  const getInitialBaseUrl = () => {
    const windowConfig = (window as any).__APP_CONFIG__
    if (windowConfig && windowConfig.API_BASE_URL && windowConfig.API_BASE_URL !== '__API_BASE_URL__') {
      return windowConfig.API_BASE_URL
    }

    // Smart default:
    // 1. If we're on port 9999 (standard for this project's Vite), assume local dev and use :10008
    // 2. Otherwise, assume we're served by the backend or a proxy, and use the current origin
    const isLocalVite = window.location.port === '9999' || window.location.port === '5173'
    return isLocalVite ? 'http://localhost:10008' : window.location.origin
  }

  // State
  const baseUrl = ref(getInitialBaseUrl())
  const userId = ref('')
  const chatId = ref('')
  const clusterId = ref('')

  // Getters
  const structuredApiUrl = computed(() => `${baseUrl.value}/api/assistant/chat/structured`)

  const persist = () => {
    localStorage.setItem('paas-agent-config', JSON.stringify({
      baseUrl: baseUrl.value,
      userId: userId.value,
      clusterId: clusterId.value
    }))
  }

  // Actions
  function updateConfig(newConfig: Partial<ConfigState>) {
    if (newConfig.baseUrl !== undefined) {
      baseUrl.value = newConfig.baseUrl
    }
    if (newConfig.userId !== undefined) {
      userId.value = newConfig.userId
    }
    if (newConfig.chatId !== undefined) {
      chatId.value = newConfig.chatId
    }
    if (newConfig.clusterId !== undefined) {
      clusterId.value = newConfig.clusterId
    }

    persist()
  }

  function loadConfig() {
    const saved = localStorage.getItem('paas-agent-config')
    if (saved) {
      try {
        const config = JSON.parse(saved)
        let savedBaseUrl = config.baseUrl

        // Protection: If the saved URL is 'localhost' but the user is accessing via IP/Domain,
        // it means the saved config is likely from a different environment. Reset it.
        if (
          savedBaseUrl &&
          savedBaseUrl.includes('localhost') &&
          !window.location.hostname.includes('localhost') &&
          !window.location.hostname.includes('127.0.0.1')
        ) {
          savedBaseUrl = getInitialBaseUrl()
        }

        baseUrl.value = savedBaseUrl || getInitialBaseUrl()
        userId.value = config.userId || ''
        clusterId.value = config.clusterId || ''
      } catch (error) {
        console.error('Failed to load config:', error)
      }
    }
  }

  function setChatId(id: string) {
    chatId.value = id
  }

  function generateNewChatId() {
    chatId.value = Date.now().toString()
    updateConfig({ chatId: chatId.value })
  }

  function initializeChatId() {
    // Generate new chat_id on each initialization
    generateNewChatId()
  }

  return {
    baseUrl,
    userId,
    chatId,
    clusterId,
    structuredApiUrl,
    updateConfig,
    loadConfig,
    setChatId,
    generateNewChatId,
    initializeChatId
  }
})
