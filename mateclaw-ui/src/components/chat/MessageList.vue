<template>
  <div
    ref="scrollRef"
    class="message-list"
    :class="{ 'is-scrolling': !isAtBottom }"
  >
    <div ref="contentRef" class="message-list-content">
      <!-- 空状态 -->
      <div v-if="messages.length === 0 && !loading" class="empty-state">
        <slot name="empty" :title="title" :subtitle="subtitle" :suggestions="suggestions">
          <div class="welcome-screen">
            <div class="welcome-logo">
              <div class="welcome-logo__glow"></div>
              <img src="/logo/mateclaw_logo_s.png" alt="MateClaw" class="welcome-logo__icon" />
            </div>
            <h2 class="welcome-title">Mate<span class="welcome-title-highlight">Claw</span></h2>
            <p class="welcome-subtitle">{{ subtitle }}</p>
            <div v-if="suggestions.length" class="welcome-suggestions">
              <button
                v-for="(s, i) in suggestions"
                :key="s"
                class="suggestion-card"
                @click="$emit('suggestion-click', s)"
              >
                <span class="suggestion-card__icon">{{ ['💬', '✍️', '💻', '📊'][i % 4] }}</span>
                <span class="suggestion-card__text">{{ s }}</span>
                <svg class="suggestion-card__arrow" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <line x1="5" y1="12" x2="19" y2="12"/>
                  <polyline points="12 5 19 12 12 19"/>
                </svg>
              </button>
            </div>
          </div>
        </slot>
      </div>

      <!-- 消息列表 -->
      <template v-else>
        <MessageBubble
          v-for="(msg, index) in messages"
          :key="msg.id || index"
          :message="msg"
          :is-last="index === messages.length - 1"
          :assistant-icon="assistantIcon"
          :user-icon="userIcon"
          :show-cursor="showCursorForMessage(msg)"
          @regenerate="$emit('regenerate', msg)"
          @toggle-thinking="(expanded) => $emit('toggle-thinking', msg, expanded)"
          @approve="(pendingId) => $emit('approve', pendingId)"
          @deny="(pendingId) => $emit('deny', pendingId)"
        />
      </template>

      <!-- 加载指示器：只在无消息时显示（有消息时由输入框显示停止按钮） -->
      <div v-if="loading && messages.length === 0" class="loading-more">
        <slot name="loading">
          <div class="typing-indicator">
            <span></span><span></span><span></span>
          </div>
        </slot>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, watch, nextTick } from 'vue'
import MessageBubble from './MessageBubble.vue'
import { useStickToBottom } from '@/composables/chat/useStickToBottom'
import type { Message } from '@/types'

interface Props {
  /** 消息列表 */
  messages: Message[]
  /** 是否加载中 */
  loading?: boolean
  /** 助手图标 */
  assistantIcon?: string
  /** 用户图标 */
  userIcon?: string
  /** 标题（空状态） */
  title?: string
  /** 副标题（空状态） */
  subtitle?: string
  /** 建议提示（空状态） */
  suggestions?: string[]
  /** 是否自动滚动到底部 */
  autoScroll?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  loading: false,
  assistantIcon: '🤖',
  userIcon: 'U',
  title: 'MateClaw',
  subtitle: '',
  suggestions: () => [],
  autoScroll: true,
})

const emit = defineEmits<{
  regenerate: [message: Message]
  'toggle-thinking': [message: Message, expanded: boolean]
  'suggestion-click': [suggestion: string]
  scroll: [event: Event]
  approve: [pendingId: string]
  deny: [pendingId: string]
}>()

// 智能滚动
const { scrollRef, contentRef, isAtBottom, scrollToBottom } = useStickToBottom({
  enabled: props.autoScroll,
  offset: 70,
  smooth: true,
})

// 判断消息是否显示光标
const showCursorForMessage = (msg: Message) => {
  // 只有正在生成的最后一条助手消息显示光标
  const isLastAssistant = msg.role === 'assistant' && 
    msg.id === props.messages[props.messages.length - 1]?.id
  return isLastAssistant && msg.status === 'generating'
}

// 监听消息变化，自动滚动
watch(
  () => props.messages,
  async () => {
    await nextTick()
    scrollToBottom()
  },
  { deep: true }
)

// 监听生成状态
watch(
  () => props.loading,
  async (isLoading) => {
    if (isLoading) {
      await nextTick()
      scrollToBottom()
    }
  }
)
</script>

<style scoped>
.message-list {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 28px 28px 20px;
  scroll-behavior: smooth;
}

.message-list::-webkit-scrollbar {
  width: 6px;
}

.message-list::-webkit-scrollbar-thumb {
  background: var(--mc-scrollbar-thumb, #cbd5e1);
  border-radius: 3px;
}

.message-list-content {
  display: flex;
  flex-direction: column;
  gap: 18px;
  min-height: 100%;
}

/* ==================== 空状态 / 欢迎屏 ==================== */
.empty-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 400px;
}

.welcome-screen {
  text-align: center;
  padding: 40px 20px;
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 100%;
  box-sizing: border-box;
}

.welcome-logo {
  position: relative;
  margin-bottom: 20px;
}

.welcome-logo__glow {
  position: absolute;
  inset: -20px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(217, 119, 87, 0.12) 0%, transparent 70%);
  animation: logo-glow 3s ease-in-out infinite;
}

@keyframes logo-glow {
  0%, 100% { opacity: 0.6; transform: scale(1); }
  50% { opacity: 1; transform: scale(1.1); }
}

.welcome-logo__icon {
  position: relative;
  width: 64px;
  height: 64px;
  object-fit: contain;
  display: block;
  filter: drop-shadow(0 4px 12px rgba(217, 119, 87, 0.2));
}

.welcome-title {
  font-size: 26px;
  font-weight: 700;
  color: var(--mc-text-primary, #1e293b);
  margin: 0 0 8px;
  letter-spacing: -0.02em;
}

.welcome-title-highlight {
  color: var(--mc-primary);
}

.welcome-subtitle {
  font-size: 14px;
  color: var(--mc-text-secondary, #64748b);
  margin: 0 0 36px;
  max-width: 360px;
  line-height: 1.6;
}

.welcome-suggestions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  max-width: 720px;
  width: 100%;
}

.suggestion-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  background: var(--mc-bg-elevated, #f8fafc);
  border: 1px solid var(--mc-border, #e2e8f0);
  border-radius: 12px;
  font-size: 13px;
  color: var(--mc-text-primary, #1e293b);
  cursor: pointer;
  transition: all 0.2s ease;
  text-align: left;
}

.suggestion-card:hover {
  border-color: var(--mc-primary, #D97757);
  background: var(--mc-primary-bg, rgba(217, 119, 87, 0.06));
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(217, 119, 87, 0.08);
}

.suggestion-card__icon {
  font-size: 18px;
  flex-shrink: 0;
}

.suggestion-card__text {
  flex: 1;
  overflow: hidden;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.suggestion-card__arrow {
  flex-shrink: 0;
  color: var(--mc-text-tertiary, #94a3b8);
  opacity: 0;
  transform: translateX(-4px);
  transition: all 0.2s ease;
}

.suggestion-card:hover .suggestion-card__arrow {
  opacity: 1;
  transform: translateX(0);
  color: var(--mc-primary, #D97757);
}

/* 加载更多 */
.loading-more {
  display: flex;
  justify-content: center;
  padding: 20px;
}

.typing-indicator {
  display: flex;
  gap: 4px;
  padding: 4px 0;
}

.typing-indicator span {
  width: 6px;
  height: 6px;
  background: var(--mc-text-tertiary, #94a3b8);
  border-radius: 50%;
  animation: bounce 1.2s infinite;
}

.typing-indicator span:nth-child(2) {
  animation-delay: 0.2s;
}

.typing-indicator span:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes bounce {
  0%, 60%, 100% {
    transform: translateY(0);
  }
  30% {
    transform: translateY(-6px);
  }
}

/* 滚动提示 */
.is-scrolling .scroll-to-bottom {
  opacity: 1;
  pointer-events: auto;
}

/* ===== 移动端适配 ===== */
@media (max-width: 768px) {
  .message-list {
    padding: 16px 12px 12px;
  }

  .message-list-content {
    gap: 12px;
  }

  .empty-state {
    min-height: 300px;
  }

  .welcome-screen {
    padding: 24px 12px;
  }

  .welcome-logo__icon {
    width: 48px;
    height: 48px;
  }

  .welcome-title {
    font-size: 22px;
  }

  .welcome-subtitle {
    margin-bottom: 24px;
    max-width: 100%;
  }

  .welcome-suggestions {
    grid-template-columns: 1fr;
    max-width: 100%;
  }

  .suggestion-card {
    padding: 10px 12px;
  }
}
</style>
