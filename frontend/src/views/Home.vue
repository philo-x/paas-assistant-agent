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
import { useRouter } from 'vue-router'
import { Button, Card, Row, Col, Typography, Space, Dropdown } from 'ant-design-vue'
import {
  MessageOutlined,
  BookOutlined,
  SearchOutlined,
  AuditOutlined,
  SettingOutlined,
  GlobalOutlined,
  WarningOutlined
} from '@ant-design/icons-vue'
import { setLocale, getLocale } from '@/base/i18n'

const { t } = useI18n()
const router = useRouter()

const features = computed(() => [
  {
    icon: BookOutlined,
    title: t('home.features.consult'),
    description: t('home.features.consultDesc')
  },
  {
    icon: SearchOutlined,
    title: t('home.features.order'),
    description: t('home.features.orderDesc')
  },
  {
    icon: AuditOutlined,
    title: t('home.features.support'),
    description: t('home.features.supportDesc')
  },
  {
    icon: WarningOutlined,
    title: t('home.features.feedback'),
    description: t('home.features.feedbackDesc')
  }
])

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

const goToChat = () => {
  router.push('/chat')
}

const goToSettings = () => {
  router.push('/settings')
}
</script>

<template>
  <div class="home-page">
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

    <!-- Hero Section -->
    <div class="hero-section">
      <div class="hero-content">
        <div class="hero-text">
          <h1 class="hero-title">{{ t('home.title') }}</h1>
          <h2 class="hero-subtitle">{{ t('home.subtitle') }}</h2>
          <p class="hero-description">{{ t('home.description') }}</p>
          <Space size="large">
            <Button
              type="primary"
              size="large"
              @click="goToChat"
              class="cta-button"
            >
              <template #icon>
                <MessageOutlined />
              </template>
              {{ t('home.startChat') }}
            </Button>
            <Button
              size="large"
              @click="goToSettings"
              class="secondary-button"
            >
              <template #icon>
                <SettingOutlined />
              </template>
              {{ t('home.systemSettings') }}
            </Button>
          </Space>
        </div>
        <div class="hero-image">
          <div class="platform-illustration">
            <div class="platform-layer control-plane">
              <div class="platform-node wide">Supervisor</div>
            </div>
            <div class="platform-layer service-layer">
              <div class="platform-node">Guide</div>
              <div class="platform-node">Diagnosis</div>
            </div>
            <div class="platform-layer data-layer">
              <div class="platform-node wide">Platform MCP + Audit</div>
            </div>
            <div class="connection connection-left"></div>
            <div class="connection connection-right"></div>
            <div class="connection connection-down"></div>
            <div class="signal signal-one"></div>
            <div class="signal signal-two"></div>
            <div class="signal signal-three"></div>
          </div>
        </div>
      </div>
    </div>

    <!-- Features Section -->
    <div class="features-section">
      <div class="container">
        <Typography.Title :level="2" class="section-title">
          {{ t('home.features.title') }}
        </Typography.Title>
        <Row :gutter="[24, 24]">
          <Col
            v-for="(feature, index) in features"
            :key="index"
            :xs="24"
            :sm="12"
            :lg="6"
          >
            <Card class="feature-card" hoverable>
              <div class="feature-content">
                <div class="feature-icon">
                  <component :is="feature.icon" />
                </div>
                <h3 class="feature-title">{{ feature.title }}</h3>
                <p class="feature-description">{{ feature.description }}</p>
              </div>
            </Card>
          </Col>
        </Row>
      </div>
    </div>

    <!-- CTA Section -->
    <div class="cta-section">
      <div class="container">
        <div class="cta-content">
          <h2>{{ t('home.cta.title') }}</h2>
          <p>{{ t('home.cta.description') }}</p>
          <Button
            type="primary"
            size="large"
            @click="goToChat"
            class="cta-button"
          >
            <template #icon>
              <MessageOutlined />
            </template>
            {{ t('home.startChat') }}
          </Button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.home-page {
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  position: relative;
}

.language-switcher {
  position: absolute;
  top: 20px;
  right: 24px;
  z-index: 100;
}

.lang-btn {
  color: white;
  font-size: 14px;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  height: auto;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.15);
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.2);
  transition: all 0.3s;
}

.lang-btn:hover {
  background: rgba(255, 255, 255, 0.25);
  color: white;
}

.lang-menu {
  background: white;
  border-radius: 8px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15);
  overflow: hidden;
  min-width: 100px;
}

.lang-menu-item {
  padding: 10px 16px;
  cursor: pointer;
  transition: all 0.2s;
  font-size: 14px;
}

.lang-menu-item:hover {
  background: #f5f5f5;
}

.lang-menu-item.active {
  background: #f0f5ff;
  color: #667eea;
  font-weight: 500;
}

.hero-section {
  padding: 80px 24px;
  min-height: 80vh;
  display: flex;
  align-items: center;
}

.hero-content {
  max-width: 1200px;
  margin: 0 auto;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 60px;
  align-items: center;
}

.hero-text {
  color: white;
}

.hero-title {
  font-size: 3.5rem;
  font-weight: 700;
  margin: 0 0 16px 0;
  line-height: 1.2;
}

.hero-subtitle {
  font-size: 2rem;
  font-weight: 400;
  margin: 0 0 24px 0;
  opacity: 0.9;
}

.hero-description {
  font-size: 1.2rem;
  line-height: 1.6;
  margin: 0 0 40px 0;
  opacity: 0.8;
}

.cta-button {
  height: 48px;
  padding: 0 32px;
  font-size: 16px;
  font-weight: 600;
  border-radius: 24px;
  background: white;
  color: #667eea;
  border: none;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
}

.cta-button:hover {
  background: #f8f9fa;
  transform: translateY(-2px);
  box-shadow: 0 6px 25px rgba(0, 0, 0, 0.15);
}

.secondary-button {
  height: 48px;
  padding: 0 32px;
  font-size: 16px;
  font-weight: 600;
  border-radius: 24px;
  background: transparent;
  color: white;
  border: 2px solid white;
}

.secondary-button:hover {
  background: white;
  color: #667eea;
}

.hero-image {
  display: flex;
  justify-content: center;
  align-items: center;
}

.platform-illustration {
  position: relative;
  width: 320px;
  height: 260px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.platform-layer {
  display: flex;
  justify-content: center;
  gap: 24px;
}

.platform-node {
  min-width: 110px;
  padding: 16px 18px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.18);
  border: 1px solid rgba(255, 255, 255, 0.24);
  color: white;
  text-align: center;
  font-weight: 600;
  box-shadow: 0 12px 30px rgba(0, 0, 0, 0.18);
  backdrop-filter: blur(10px);
}

.platform-node.wide {
  min-width: 220px;
}

.connection {
  position: absolute;
  background: rgba(255, 255, 255, 0.7);
  border-radius: 999px;
}

.connection-left,
.connection-right {
  top: 86px;
  width: 2px;
  height: 38px;
}

.connection-left {
  left: 111px;
}

.connection-right {
  right: 111px;
}

.connection-down {
  top: 170px;
  left: 50%;
  width: 2px;
  height: 34px;
  transform: translateX(-50%);
}

.signal {
  position: absolute;
  width: 10px;
  height: 10px;
  border-radius: 999px;
  background: white;
  opacity: 0.8;
  animation: pulse 2.4s infinite ease-in-out;
}

.signal-one {
  top: 70px;
  left: 72px;
}

.signal-two {
  top: 126px;
  right: 62px;
  animation-delay: 0.8s;
}

.signal-three {
  bottom: 14px;
  left: 58px;
  animation-delay: 1.5s;
}

@keyframes pulse {
  0%, 100% { transform: scale(0.8); opacity: 0.45; }
  50% { transform: scale(1.25); opacity: 1; }
}

.features-section {
  padding: 80px 24px;
  background: white;
}

.container {
  max-width: 1200px;
  margin: 0 auto;
}

.section-title {
  text-align: center;
  margin-bottom: 60px;
  color: #333;
}

.feature-card {
  height: 100%;
  border-radius: 12px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
  transition: all 0.3s ease;
}

.feature-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.12);
}

.feature-content {
  text-align: center;
  padding: 20px;
}

.feature-icon {
  font-size: 48px;
  color: #667eea;
  margin-bottom: 20px;
}

.feature-title {
  font-size: 18px;
  font-weight: 600;
  margin: 0 0 12px 0;
  color: #333;
}

.feature-description {
  color: #666;
  line-height: 1.6;
  margin: 0;
}

.cta-section {
  padding: 80px 24px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  text-align: center;
}

.cta-content h2 {
  color: white;
  font-size: 2.5rem;
  margin: 0 0 16px 0;
  font-weight: 600;
}

.cta-content p {
  color: white;
  font-size: 1.2rem;
  margin: 0 0 40px 0;
  opacity: 0.9;
}

/* Responsive */
@media (max-width: 768px) {
  .hero-content {
    grid-template-columns: 1fr;
    gap: 40px;
    text-align: center;
  }

  .hero-title {
    font-size: 2.5rem;
  }

  .hero-subtitle {
    font-size: 1.5rem;
  }

  .platform-illustration {
    width: 240px;
    height: 220px;
  }

  .platform-node {
    min-width: 90px;
    padding: 12px 14px;
    font-size: 14px;
  }

  .platform-node.wide {
    min-width: 180px;
  }

  .language-switcher {
    top: 10px;
    right: 16px;
  }
}
</style>
