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
import { computed, onMounted, ref } from 'vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import { normalizeMarkdownContent } from '@/utils/markdown'

interface Props {
  content: string
  isStreaming?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  isStreaming: false
})

const htmlContent = ref('')
let renderTimeout: number | null = null

// Configure marked
marked.setOptions({
  breaks: true,
  gfm: true
})

const processedContent = computed(() => {
  if (!props.content) return ''

  // Add streaming indicator
  let content = props.content
  if (props.isStreaming) {
    content += '<span class="streaming-cursor">▋</span>'
  }

  return content
})

const renderMarkdown = async () => {
  if (!processedContent.value) {
    htmlContent.value = ''
    return
  }

  // If streaming, use debounce mechanism
  if (props.isStreaming) {
    if (renderTimeout) {
      clearTimeout(renderTimeout)
    }
    renderTimeout = window.setTimeout(async () => {
      await performRender()
    }, 100) // 100ms debounce
  } else {
    await performRender()
  }
}

const performRender = async () => {
  try {
    // Always perform content preprocessing to ensure correct markdown format
    let content = normalizeMarkdownContent(processedContent.value)

    // Auto-fix tables that lack a preceding blank line (a common LLM formatting mistake that breaks marked.js)
    content = content.replace(/([^\n])\n([ \t]*\|.*\|[ \t]*\n[ \t]*\|[-:| \t]+\|)/g, '$1\n\n$2')

    // Fix: Code blocks that lack a preceding blank line
    content = content.replace(/([^\n])\n([ \t]*```)/g, '$1\n\n$2')

    // Fix: Lists that lack a preceding blank line
    content = content.replace(/([^\n])\n([ \t]*[*+-] |\d+\. )/g, '$1\n\n$2')

    // Fix: --- on its own line immediately after text is interpreted by marked as a setext h2 heading.
    // Insert blank lines around it so it becomes an <hr> instead.
    content = content.replace(/([^\n])\n(-{3,})\s*\n/g, '$1\n\n$2\n\n')
    // Handle --- at end of string (no trailing newline)
    content = content.replace(/([^\n])\n(-{3,})\s*$/, '$1\n\n$2')

    // Fix: ATX headings (# ## ###) that immediately follow a non-blank line need a blank line before them
    content = content.replace(/([^\n])\n(#{1,6}[ \t\S])/g, '$1\n\n$2')
    // Case 2: no \n before heading at all (text## → text\n\n##)
    content = content.replace(/([^\n#`])(#{1,6}[ \t\S])/g, '$1\n\n$2')

    // Fix: ATX headings missing a space after # (e.g., ##📊 -> ## 📊)
    content = content.replace(/^(#{1,6})([^ \t\n#])/gm, '$1 $2')

    // Trust the GFM content. Remove destructive regex hacks.
    const rawHtml = await marked(content)
    htmlContent.value = DOMPurify.sanitize(rawHtml, {
      ALLOWED_TAGS: [
        'p', 'br', 'strong', 'em', 'u', 's', 'del', 'ins',
        'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
        'ul', 'ol', 'li', 'blockquote', 'pre', 'code',
        'a', 'img', 'table', 'thead', 'tbody', 'tr', 'th', 'td',
        'div', 'span', 'hr'
      ],
      ALLOWED_ATTR: [
        'href', 'title', 'alt', 'src', 'class', 'id',
        'target', 'rel'
      ]
    })
  } catch (error) {
    console.error('Markdown rendering error:', error)
    htmlContent.value = props.content
  }
}

// Expose method for parent component to call
const forceRender = async () => {
  if (renderTimeout) {
    clearTimeout(renderTimeout)
    renderTimeout = null
  }
  await performRender()
}

// Expose methods
defineExpose({
  forceRender
})

onMounted(() => {
  renderMarkdown()
})

// Watch for content changes
import { watch } from 'vue'
watch(processedContent, renderMarkdown, { immediate: true })

// Watch for streaming state changes
watch(() => props.isStreaming, (newVal, oldVal) => {
  // When streaming ends, immediately render the final result
  if (oldVal === true && newVal === false) {
    if (renderTimeout) {
      clearTimeout(renderTimeout)
      renderTimeout = null
    }
    performRender()
  }
})
</script>

<template>
  <div
    class="markdown-content"
    v-html="htmlContent"
  />
</template>

<style scoped>
.markdown-content {
  line-height: 1.7;
  word-wrap: break-word;
}

.markdown-content :deep(h1),
.markdown-content :deep(h2),
.markdown-content :deep(h3),
.markdown-content :deep(h4),
.markdown-content :deep(h5),
.markdown-content :deep(h6) {
  margin: 1em 0 0.5em 0;
  font-weight: 600;
  line-height: 1.25;
}

.markdown-content :deep(h1) {
  font-size: 1.5em;
  border-bottom: 1px solid #eaecef;
  padding-bottom: 0.3em;
}

.markdown-content :deep(h2) {
  font-size: 1.25em;
  border-bottom: 1px solid #eaecef;
  padding-bottom: 0.3em;
}

.markdown-content :deep(p) {
  margin: 0.5em 0;
  line-height: 1.6;
}

.markdown-content :deep(p:first-child) {
  margin-top: 0;
}

.markdown-content :deep(p:last-child) {
  margin-bottom: 0;
}

.markdown-content :deep(ul),
.markdown-content :deep(ol) {
  margin: 0.8em 0;
  padding-left: 1.8em;
}

.markdown-content :deep(li) {
  margin: 0.4em 0;
  line-height: 1.6;
  position: relative;
}

.markdown-content :deep(li p) {
  margin: 0.25em 0;
}

.markdown-content :deep(li strong) {
  font-weight: 600;
  color: #1890ff;
}

.markdown-content :deep(li::marker) {
  color: #1890ff;
  font-weight: bold;
}

.markdown-content :deep(ul li) {
  list-style-type: disc;
}

.markdown-content :deep(ol li) {
  list-style-type: decimal;
}

.markdown-content :deep(li:not(:last-child)) {
  margin-bottom: 0.8em;
}

.markdown-content :deep(li) {
  margin-bottom: 0.6em;
  padding-left: 0.2em;
}


.markdown-content :deep(blockquote) {
  margin: 0.5em 0;
  padding: 0 1em;
  color: #6a737d;
  border-left: 0.25em solid #dfe2e5;
  background: #f6f8fa;
}

.markdown-content :deep(pre) {
  margin: 1em 0;
  padding: 16px;
  background: #1e293b;
  color: #f8fafc;
  border-radius: 8px;
  overflow-x: auto;
  box-shadow: inset 0 0 0 1px rgba(255,255,255,0.1);
}

.markdown-content :deep(code) {
  padding: 3px 6px;
  margin: 0 2px;
  font-size: 13px;
  color: #d946ef;
  background: #f1f5f9;
  border-radius: 4px;
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
}

.markdown-content :deep(pre code) {
  padding: 0;
  margin: 0;
  background: transparent;
  color: inherit;
}

.markdown-content :deep(table) {
  border-collapse: separate;
  border-spacing: 0;
  margin: 1.5em 0;
  width: 100%;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 0 0 1px #e2e8f0;
}

.markdown-content :deep(th),
.markdown-content :deep(td) {
  padding: 12px 16px;
  text-align: left;
  border-bottom: 1px solid #e2e8f0;
}

.markdown-content :deep(tr:last-child td) {
  border-bottom: none;
}

.markdown-content :deep(th) {
  background: #f8fafc;
  font-weight: 600;
  color: #0f172a;
}

.markdown-content :deep(a) {
  color: #0366d6;
  text-decoration: none;
}

.markdown-content :deep(a:hover) {
  text-decoration: underline;
}

.markdown-content :deep(img) {
  max-width: 100%;
  height: auto;
  border-radius: 6px;
}

.markdown-content :deep(hr) {
  height: 0.25em;
  margin: 1.5em 0;
  background: #e1e4e8;
  border: 0;
}

.streaming-cursor {
  animation: blink 1s infinite;
  color: #667eea;
  font-weight: bold;
}

@keyframes blink {
  0%, 50% { opacity: 1; }
  51%, 100% { opacity: 0; }
}
</style>
