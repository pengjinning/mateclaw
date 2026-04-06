<template>
  <div v-if="isLoading && !hideBar" class="stream-loading-bar">
    <div class="stream-loading-content">
      <span class="loading-icon" :class="phaseIconClass">{{ phaseIcon }}</span>
      <div class="loading-copy">
        <span class="loading-text" :class="phaseTextClass">{{ statusText }}</span>
        <span v-if="runningToolName" class="loading-tool">{{ runningToolName }}</span>
        <span v-if="statusDetail" class="loading-detail">{{ statusDetail }}</span>
        <span v-if="slowHint" class="loading-slow">{{ slowHint }}</span>
      </div>
    </div>
    <div class="loading-right">
      <!-- 排队指示器 -->
      <span v-if="hasQueued" class="queued-badge">
        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <line x1="8" y1="6" x2="21" y2="6"/><line x1="8" y1="12" x2="21" y2="12"/><line x1="8" y1="18" x2="21" y2="18"/>
          <line x1="3" y1="6" x2="3.01" y2="6"/><line x1="3" y1="12" x2="3.01" y2="12"/><line x1="3" y1="18" x2="3.01" y2="18"/>
        </svg>
        {{ t('chat.queuedBadge', { count: 1 }) }}
      </span>
      <div v-if="showStats" class="loading-stats">
        <span class="stat">{{ elapsedTime }}</span>
        <span v-if="tokenDisplay > 0" class="stat">↓ {{ tokenDisplay }} tokens</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onBeforeUnmount } from 'vue'
import { useI18n } from 'vue-i18n'
import type { PhaseEventData, StreamPhase } from '@/types'

interface Props {
  isLoading: boolean
  toolCount?: number
  hideBar?: boolean
  showStats?: boolean
  completionTokens?: number
  promptTokens?: number
  /** 当前流阶段 */
  phase?: StreamPhase
  /** 最近一次阶段事件 */
  phaseInfo?: PhaseEventData | null
  /** 当前正在执行的工具名称 */
  runningToolName?: string
  /** 是否有排队消息 */
  hasQueued?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  toolCount: 0,
  hideBar: false,
  showStats: true,
  completionTokens: 0,
  promptTokens: 0,
  phase: 'thinking',
  phaseInfo: null,
  runningToolName: '',
  hasQueued: false,
})

const { t } = useI18n()

// Phase-aware 状态文本（i18n）
const phaseI18nMap: Record<string, string> = {
  preparing_context: 'chat.streamPreparingContext',
  reading_memory: 'chat.streamReadingMemory',
  reasoning: 'chat.streamReasoning',
  drafting_answer: 'chat.streamDraftingAnswer',
  summarizing_observations: 'chat.streamSummarizingObservations',
  thinking: 'chat.streamThinking',
  streaming: 'chat.streamGenerating',
  executing_tool: 'chat.streamExecutingTool',
  awaiting_approval: 'chat.streamAwaitingApproval',
  finalizing: 'chat.streamFinalizing',
  failed: 'chat.streamFailed',
  interrupting: 'chat.streamInterrupting',
  queued: 'chat.streamQueued',
  reconnecting: 'chat.streamReconnecting',
  stopped: 'chat.streamStopped',
  completed: 'chat.streamCompleted',
  idle: '',
}

const statusText = computed(() => {
  const key = phaseI18nMap[props.phase]
  if (!key) return ''
  return t(key)
})

const phaseIcon = computed(() => {
  switch (props.phase) {
    case 'preparing_context': return '◔'
    case 'reading_memory': return '⌕'
    case 'reasoning': return '◐'
    case 'drafting_answer': return '✎'
    case 'summarizing_observations': return '≋'
    case 'thinking': return '◐'
    case 'streaming': return '▸'
    case 'executing_tool': return '⚙'
    case 'awaiting_approval': return '⏸'
    case 'finalizing': return '✓'
    case 'failed': return '!'
    case 'interrupting': return '⊘'
    case 'queued': return '◷'
    case 'reconnecting': return '↻'
    default: return '+'
  }
})

const phaseIconClass = computed(() => {
  switch (props.phase) {
    case 'awaiting_approval': return 'icon-paused'
    case 'failed': return 'icon-warning'
    case 'interrupting': return 'icon-warning'
    case 'queued': return 'icon-queued'
    default: return 'icon-active'
  }
})

const phaseTextClass = computed(() => {
  switch (props.phase) {
    case 'awaiting_approval': return 'text-amber'
    case 'failed': return 'text-red'
    case 'interrupting': return 'text-red'
    case 'queued': return 'text-blue'
    default: return ''
  }
})

const detailI18nMap: Record<string, string> = {
  preparing_context: 'chat.streamPreparingContextDetail',
  reading_memory: 'chat.streamReadingMemoryDetail',
  reasoning: 'chat.streamReasoningDetail',
  drafting_answer: 'chat.streamDraftingAnswerDetail',
  summarizing_observations: 'chat.streamSummarizingObservationsDetail',
  thinking: 'chat.streamThinkingDetail',
  streaming: 'chat.streamGeneratingDetail',
  executing_tool: 'chat.streamExecutingToolDetail',
  awaiting_approval: 'chat.streamAwaitingApprovalDetail',
  finalizing: 'chat.streamFinalizingDetail',
  failed: 'chat.streamFailedDetail',
}

const statusDetail = computed(() => {
  if (props.phaseInfo?.phase) {
    const key = detailI18nMap[props.phaseInfo.phase]
    if (key) return t(key)
  }
  const key = detailI18nMap[props.phase]
  return key ? t(key) : ''
})

const slowHint = computed(() => {
  const secs = elapsedSeconds.value
  if (props.phase === 'summarizing_observations' && secs >= 15) {
    return t('chat.streamSlowSummarizing')
  }
  if ((props.phase === 'reasoning' || props.phase === 'thinking') && secs >= 20) {
    return t('chat.streamSlowReasoning')
  }
  if (secs >= 45) {
    return t('chat.streamSlowGeneral')
  }
  if (secs >= 8) {
    return t('chat.streamSlowShort')
  }
  return ''
})

// 耗时统计
const elapsedSeconds = ref(0)
const elapsedTime = ref('0s')

// Token 计数
const estimatedTokens = ref(0)
const tokenDisplay = computed(() => {
  if (props.completionTokens && props.completionTokens > 0) {
    return props.completionTokens
  }
  return estimatedTokens.value
})

let timerInterval: ReturnType<typeof setInterval> | null = null

watch(() => props.isLoading, (loading) => {
  if (loading) {
    if (timerInterval) clearInterval(timerInterval)
    elapsedSeconds.value = 0
    elapsedTime.value = '0s'
    estimatedTokens.value = 0

    timerInterval = setInterval(() => {
      elapsedSeconds.value += 1
      const secs = elapsedSeconds.value
      if (secs < 60) {
        elapsedTime.value = `${secs}s`
      } else {
        const mins = Math.floor(secs / 60)
        const remainSecs = secs % 60
        elapsedTime.value = `${mins}m ${remainSecs}s`
      }
    }, 1000)
  } else {
    if (timerInterval) {
      clearInterval(timerInterval)
      timerInterval = null
    }
  }
}, { immediate: true })

watch(() => props.completionTokens, (tokens) => {
  if (tokens && tokens > 0) {
    estimatedTokens.value = 0
  }
})

onBeforeUnmount(() => {
  if (timerInterval) {
    clearInterval(timerInterval)
    timerInterval = null
  }
})
</script>

<style scoped>
.stream-loading-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 16px;
  font-size: 14px;
  animation: subtle-pulse 1.5s ease-in-out infinite;
  color: var(--mc-text-secondary, #64748b);
}

.stream-loading-content {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  color: #f97316;
}

.loading-copy {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.loading-icon {
  font-size: 16px;
  font-weight: bold;
}

.icon-active {
  animation: icon-pulse 1s ease-in-out infinite;
  color: #f97316;
}

.icon-paused {
  color: #f59e0b;
  animation: none;
}

.icon-warning {
  color: #ef4444;
  animation: icon-pulse 0.5s ease-in-out infinite;
}

.icon-queued {
  color: #3b82f6;
  animation: none;
}

.loading-text {
  font-weight: 500;
  color: #f97316;
}

.text-amber { color: #f59e0b; }
.text-red { color: #ef4444; }
.text-blue { color: #3b82f6; }

.loading-tool {
  align-self: flex-start;
  font-family: ui-monospace, 'SFMono-Regular', Consolas, monospace;
  font-size: 12px;
  background: rgba(249, 115, 22, 0.1);
  padding: 1px 6px;
  border-radius: 4px;
  color: #f97316;
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.loading-detail {
  font-size: 12px;
  color: var(--mc-text-secondary, #94a3b8);
}

.loading-slow {
  font-size: 12px;
  color: #f59e0b;
}

.loading-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.queued-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #3b82f6;
  background: rgba(59, 130, 246, 0.08);
  padding: 2px 8px;
  border-radius: 10px;
}

.loading-stats {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: #cbd5e1;
}

.stat {
  display: flex;
  align-items: center;
}

@keyframes icon-pulse {
  0%, 100% {
    opacity: 1;
    transform: scale(1);
  }
  50% {
    opacity: 0.7;
    transform: scale(1.1);
  }
}

@keyframes subtle-pulse {
  0%, 100% {
    opacity: 1;
  }
  50% {
    opacity: 0.85;
  }
}
</style>
