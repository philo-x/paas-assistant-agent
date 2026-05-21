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
  token?: string
}

function parseJwt(token: string): any {
  try {
    const parts = token.split('.')
    if (parts.length !== 3) {
      return null
    }
    const base64Url = parts[1]
    let base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/')
    const pad = base64.length % 4
    if (pad) {
      base64 += '='.repeat(4 - pad)
    }
    const jsonPayload = decodeURIComponent(
      window.atob(base64)
        .split('')
        .map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
        .join('')
    )
    return JSON.parse(jsonPayload)
  } catch (error) {
    console.error('Failed to parse JWT payload:', error)
    return null
  }
}

function cleanUrlParameters(keysToRemove: string[]) {
  try {
    const url = new URL(window.location.href)
    let searchChanged = false
    keysToRemove.forEach(key => {
      if (url.searchParams.has(key)) {
        url.searchParams.delete(key)
        searchChanged = true
      }
    })

    let hashChanged = false
    const hash = url.hash
    if (hash && hash.includes('?')) {
      const parts = hash.split('?')
      const hashParams = new URLSearchParams(parts[1])
      keysToRemove.forEach(key => {
        if (hashParams.has(key)) {
          hashParams.delete(key)
          hashChanged = true
        }
      })
      if (hashChanged) {
        const newParams = hashParams.toString()
        url.hash = newParams ? `${parts[0]}?${newParams}` : parts[0]
      }
    }

    if (searchChanged || hashChanged) {
      window.history.replaceState({}, document.title, url.toString())
    }
  } catch (e) {
    console.error('Failed to clean URL parameters:', e)
  }
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
  const token = ref('')
  const initialPrompt = ref('')

  // Getters
  const structuredApiUrl = computed(() => `${baseUrl.value}/api/assistant/chat/structured`)

  const persist = () => {
    localStorage.setItem('paas-agent-config', JSON.stringify({
      baseUrl: baseUrl.value,
      userId: userId.value,
      clusterId: clusterId.value,
      token: token.value
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
    if (newConfig.token !== undefined) {
      token.value = newConfig.token
    }

    persist()
  }

  function loadConfig() {
    // 1. Load saved config
    const saved = localStorage.getItem('paas-agent-config')
    let savedBaseUrl = ''
    let savedUserId = ''
    let savedClusterId = ''
    let savedToken = ''

    if (saved) {
      try {
        const config = JSON.parse(saved)
        savedBaseUrl = config.baseUrl || ''
        savedUserId = config.userId || ''
        savedClusterId = config.clusterId || ''
        savedToken = config.token || ''
      } catch (error) {
        console.error('Failed to load config:', error)
      }
    }

    // 2. Extract from URL query/hash parameters (overrides)
    const urlParams = new URLSearchParams(window.location.search)
    let hashSearch = ''
    const hashIndex = window.location.hash.indexOf('?')
    if (hashIndex !== -1) {
      hashSearch = window.location.hash.slice(hashIndex)
    }
    const hashParams = new URLSearchParams(hashSearch)

    const tokenFromUrl = urlParams.get('token') || hashParams.get('token') || urlParams.get('id_token') || hashParams.get('id_token')
    const userIdFromUrl = urlParams.get('userId') || hashParams.get('userId') || urlParams.get('user_id') || hashParams.get('user_id')
    const clusterIdFromUrl = urlParams.get('clusterId') || hashParams.get('clusterId') || urlParams.get('cluster_id') || hashParams.get('cluster_id')

    const keysToClean: string[] = []

    if (tokenFromUrl) {
      savedToken = tokenFromUrl
      keysToClean.push('token', 'id_token')

      // If token is JWT, decode and pre-populate
      const decoded = parseJwt(tokenFromUrl)
      if (decoded) {
        // Prioritize human-readable preferred_username/email over subject (sub)
        const jwtUserId = decoded.preferred_username || decoded.email || decoded.userId || decoded.user_id || decoded.sub || decoded.username || decoded.uid
        if (jwtUserId) {
          savedUserId = String(jwtUserId)
        }
        const jwtClusterId = decoded.clusterId || decoded.cluster_id
        if (jwtClusterId) {
          savedClusterId = String(jwtClusterId)
        }
      }
    }

    // Query param overrides take priority, independent of token presence
    if (userIdFromUrl) {
      savedUserId = userIdFromUrl
      keysToClean.push('userId', 'user_id')
    }
    if (clusterIdFromUrl) {
      savedClusterId = clusterIdFromUrl
      keysToClean.push('clusterId', 'cluster_id')
    }

    const promptFromUrl = urlParams.get('prompt') || hashParams.get('prompt')
    if (promptFromUrl) {
      initialPrompt.value = promptFromUrl
      keysToClean.push('prompt')
    }

    if (keysToClean.length > 0) {
      cleanUrlParameters(keysToClean)
    }

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
    userId.value = savedUserId || ''
    clusterId.value = savedClusterId || ''
    token.value = savedToken || ''

    persist()
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
    token,
    initialPrompt,
    structuredApiUrl,
    updateConfig,
    loadConfig,
    setChatId,
    generateNewChatId,
    initializeChatId
  }
})
