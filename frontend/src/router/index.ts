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

import { createRouter, createWebHashHistory } from 'vue-router'
import { routes } from '@/router/defaultRoutes'
import { useConfigStore } from '@/stores/config'

const options = {
  history: createWebHashHistory('/paas-agent-assistant'),
  routes,
}

const router = createRouter(options)

// Navigation guard for token check and redirection
router.beforeEach((to, from, next) => {
  const configStore = useConfigStore()

  // Load config first (which extracts token from URL if present)
  configStore.loadConfig()

  // If going to LoginRequired
  if (to.name === 'LoginRequired') {
    if (configStore.token) {
      return next({ name: 'Chat' })
    }
    return next()
  }

  // If no token is set (neither in localStorage nor URL params), block access
  if (!configStore.token) {
    return next({ name: 'LoginRequired' })
  }

  next()
})

export default router
