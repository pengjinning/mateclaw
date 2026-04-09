<script setup lang="ts">
import { computed } from 'vue'
import { useMarkdownRenderer } from '@/composables/useMarkdownRenderer'
import TypingCursor from './TypingCursor.vue'
import type { MessageSegment } from '@/types'

const props = defineProps<{
  segment: MessageSegment
  showCursor?: boolean
}>()

const { renderMarkdown } = useMarkdownRenderer()

const renderedContent = computed(() => {
  return renderMarkdown(props.segment.text || '')
})

const isRunning = computed(() => props.segment.status === 'running')
</script>

<template>
  <div class="segment segment--content">
    <div class="markdown-body" v-html="renderedContent"></div>
    <TypingCursor v-if="isRunning && showCursor" />
  </div>
</template>

<style scoped>
.segment--content {
  padding: 4px 0;
  margin-top: 4px;
}
</style>
