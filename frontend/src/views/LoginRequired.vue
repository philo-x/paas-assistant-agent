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
import { Button, Card, Typography, Dropdown } from 'ant-design-vue'
import { LockOutlined, GlobalOutlined, ArrowLeftOutlined } from '@ant-design/icons-vue'
import { setLocale, getLocale } from '@/base/i18n'

const { t } = useI18n()
const currentLocale = computed(() => getLocale())

const languageMenuItems = computed(() => [
  {
    key: 'zh',
    label: t('common.chinese')
  },
  {
    key: 'en',
    label: t('common.english')
  }
])

const handleLanguageChange = (info: { key: string }) => {
  setLocale(info.key)
}

const handleGoBack = () => {
  // Attempt to go back to the referrer if it is an external site (not the assistant itself)
  const hasExternalReferrer = document.referrer && !document.referrer.startsWith(window.location.origin)
  
  if (hasExternalReferrer) {
    window.location.href = document.referrer
  } else {
    // Read from window config with fallback to the default platform URL
    const windowConfig = (window as any).__APP_CONFIG__
    let paasUrl = 'http://dev-apaas-app.mis.bcs/'
    if (windowConfig && windowConfig.PAAS_PLATFORM_URL && windowConfig.PAAS_PLATFORM_URL !== '__PAAS_PLATFORM_URL__') {
      paasUrl = windowConfig.PAAS_PLATFORM_URL
    }
    window.location.href = paasUrl
  }
}
</script>

<template>
  <div class="login-required-page">
    <!-- Language Switcher -->
    <div class="language-switcher">
      <Dropdown :trigger="['click']" placement="bottomRight">
        <Button type="text" class="lang-btn">
          <template #icon>
            <GlobalOutlined />
          </template>
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
    </div>

    <!-- Main Content Card -->
    <div class="content-container">
      <Card class="glass-card" :bordered="false">
        <div class="auth-block">
          <div class="lock-icon-wrapper">
            <div class="pulse-ring"></div>
            <LockOutlined class="lock-icon" />
          </div>
          
          <Typography.Title :level="2" class="title">
            {{ t('auth.loginRequired') }}
          </Typography.Title>
          
          <Typography.Paragraph class="description">
            {{ t('auth.loginRequiredDesc') }}
          </Typography.Paragraph>

          <div class="action-buttons">
            <Button
              type="primary"
              size="large"
              class="platform-btn"
              @click="handleGoBack"
            >
              <template #icon>
                <ArrowLeftOutlined />
              </template>
              {{ t('auth.backToPaaS') }}
            </Button>
          </div>
        </div>
      </Card>
    </div>

    <!-- Background decorative blur shapes -->
    <div class="blur-shape shape-1"></div>
    <div class="blur-shape shape-2"></div>
  </div>
</template>

<style scoped>
.login-required-page {
  min-height: 100vh;
  background: linear-gradient(135deg, #1e1b4b 0%, #311042 100%);
  position: relative;
  display: flex;
  justify-content: center;
  align-items: center;
  overflow: hidden;
  font-family: 'Inter', -apple-system, sans-serif;
  padding: 24px;
}

.language-switcher {
  position: absolute;
  top: 24px;
  right: 24px;
  z-index: 100;
}

.lang-btn {
  color: rgba(255, 255, 255, 0.85);
  font-size: 14px;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  height: auto;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.08);
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.12);
  transition: all 0.3s;
}

.lang-btn:hover {
  background: rgba(255, 255, 255, 0.18);
  color: white;
}

.lang-menu {
  background: #1e1b4b;
  border-radius: 8px;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.4);
  overflow: hidden;
  min-width: 100px;
  border: 1px solid rgba(255, 255, 255, 0.1);
}

.lang-menu-item {
  padding: 12px 18px;
  cursor: pointer;
  color: rgba(255, 255, 255, 0.8);
  transition: all 0.2s;
  font-size: 14px;
}

.lang-menu-item:hover {
  background: rgba(255, 255, 255, 0.1);
  color: white;
}

.lang-menu-item.active {
  background: rgba(99, 102, 241, 0.25);
  color: #818cf8;
  font-weight: 500;
}

.content-container {
  z-index: 10;
  width: 100%;
  max-width: 520px;
}

.glass-card {
  background: rgba(255, 255, 255, 0.04);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 24px;
  box-shadow: 0 20px 50px rgba(0, 0, 0, 0.3);
  padding: 24px;
}

.auth-block {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  padding: 16px;
}

.lock-icon-wrapper {
  position: relative;
  width: 80px;
  height: 80px;
  border-radius: 50%;
  background: rgba(99, 102, 241, 0.15);
  display: flex;
  justify-content: center;
  align-items: center;
  margin-bottom: 32px;
}

.lock-icon {
  font-size: 36px;
  color: #818cf8;
  z-index: 2;
}

.pulse-ring {
  position: absolute;
  width: 100%;
  height: 100%;
  border-radius: 50%;
  border: 2px solid rgba(99, 102, 241, 0.3);
  animation: pulse-ring-anim 2s infinite ease-out;
  z-index: 1;
}

@keyframes pulse-ring-anim {
  0% {
    transform: scale(0.95);
    opacity: 0.8;
  }
  100% {
    transform: scale(1.6);
    opacity: 0;
  }
}

.title {
  color: white !important;
  font-size: 24px !important;
  font-weight: 600 !important;
  margin-bottom: 16px !important;
  letter-spacing: -0.5px;
}

.description {
  color: rgba(255, 255, 255, 0.7) !important;
  font-size: 15px !important;
  line-height: 1.6 !important;
  margin-bottom: 36px !important;
  max-width: 400px;
}

.platform-btn {
  height: 48px;
  padding: 0 32px;
  font-size: 16px;
  font-weight: 600;
  border-radius: 12px;
  background: #6366f1;
  border-color: #6366f1;
  box-shadow: 0 4px 15px rgba(99, 102, 241, 0.35);
  transition: all 0.3s;
}

.platform-btn:hover {
  background: #4f46e5;
  border-color: #4f46e5;
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(99, 102, 241, 0.45);
}

.platform-btn:active {
  transform: translateY(0);
}

/* Background blur decorations */
.blur-shape {
  position: absolute;
  border-radius: 50%;
  filter: blur(120px);
  opacity: 0.15;
  z-index: 1;
}

.shape-1 {
  width: 400px;
  height: 400px;
  background: #4f46e5;
  top: -100px;
  left: -100px;
}

.shape-2 {
  width: 450px;
  height: 450px;
  background: #c084fc;
  bottom: -150px;
  right: -100px;
}

/* Responsive */
@media (max-width: 576px) {
  .login-required-page {
    padding: 16px;
  }
  
  .glass-card {
    border-radius: 20px;
    padding: 12px;
  }
  
  .title {
    font-size: 20px !important;
  }
  
  .description {
    font-size: 14px !important;
  }
}
</style>
