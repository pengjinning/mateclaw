<template>
  <div class="wiki-config">
    <div class="config-header">
      <h3 class="config-title">{{ t('wiki.configTitle') }}</h3>
      <p class="config-desc">{{ t('wiki.configDesc') }}</p>
    </div>

    <!-- Embedding 模型绑定（RFC Embedding UI） -->
    <div class="embedding-config">
      <label class="embedding-label">
        Embedding 模型
        <span class="embedding-hint">用于该知识库的语义检索；留空走系统默认</span>
      </label>
      <div class="embedding-row">
        <select v-model="embeddingModelId" class="embedding-select" :disabled="savingEmbedding">
          <option value="">跟随系统默认</option>
          <option v-for="m in embeddingOptions" :key="m.id" :value="String(m.id)">
            {{ m.name }} ({{ m.modelName }})
          </option>
        </select>
        <button class="btn-secondary" @click="saveEmbeddingBinding" :disabled="savingEmbedding">
          {{ savingEmbedding ? '保存中...' : '保存绑定' }}
        </button>
      </div>
    </div>

    <textarea
      v-model="configContent"
      class="config-editor"
      rows="20"
      :placeholder="t('wiki.configPlaceholder')"
    ></textarea>

    <div class="config-actions">
      <button class="btn-secondary" @click="loadConfig">{{ t('common.reset') }}</button>
      <button class="btn-primary" @click="saveConfig" :disabled="saving">
        {{ saving ? t('wiki.saving') : t('common.save') }}
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useWikiStore } from '@/stores/useWikiStore'
import { wikiApi, modelApi } from '@/api/index'

const { t } = useI18n()
const store = useWikiStore()

const configContent = ref('')
const saving = ref(false)

// RFC Embedding UI: KB 级 embedding 模型绑定
interface EmbeddingOption { id: string | number; name: string; modelName: string }
const embeddingModelId = ref<string>('')
const embeddingOptions = ref<EmbeddingOption[]>([])
const savingEmbedding = ref(false)

async function loadEmbeddingOptions() {
  try {
    const res = await modelApi.listByType('embedding')
    embeddingOptions.value = ((res.data as any[]) || []).filter(m => m.enabled !== false)
  } catch (e) {
    console.error('[WikiConfig] Failed to load embedding options', e)
  }
}

function loadEmbeddingBinding() {
  const kb: any = store.currentKB
  embeddingModelId.value = kb?.embeddingModelId ? String(kb.embeddingModelId) : ''
}

async function saveEmbeddingBinding() {
  if (!store.currentKB) return
  savingEmbedding.value = true
  try {
    await wikiApi.updateKB(store.currentKB.id, {
      embeddingModelId: embeddingModelId.value === '' ? null : embeddingModelId.value,
    })
    // 同步 store 里的当前 KB
    const kb: any = store.currentKB
    kb.embeddingModelId = embeddingModelId.value === '' ? null : Number(embeddingModelId.value)
  } catch (e) {
    console.error('[WikiConfig] Failed to save embedding binding', e)
  } finally {
    savingEmbedding.value = false
  }
}

async function loadConfig() {
  if (!store.currentKB) return
  try {
    const res: any = await wikiApi.getConfig(store.currentKB.id)
    configContent.value = res.data?.content || ''
  } catch (e) {
    console.error('Failed to load config', e)
  }
}

async function saveConfig() {
  if (!store.currentKB) return
  saving.value = true
  try {
    await wikiApi.updateConfig(store.currentKB.id, configContent.value)
  } catch (e) {
    console.error('Failed to save config', e)
  } finally {
    saving.value = false
  }
}

watch(() => store.currentKB, () => {
  loadConfig()
  loadEmbeddingBinding()
}, { immediate: true })

loadEmbeddingOptions()
</script>

<style scoped>
.wiki-config {
  display: flex;
  flex-direction: column;
  gap: 14px;
  height: 100%;
  min-height: 0;
}

/* Header */
.config-header { padding-bottom: 10px; border-bottom: 1px solid var(--mc-border-light); }
.config-title { font-size: 18px; font-weight: 700; color: var(--mc-text-primary); margin: 0 0 6px; letter-spacing: -0.02em; }
.config-desc { font-size: 13px; color: var(--mc-text-tertiary); margin: 0; line-height: 1.6; }

/* Embedding binding */
.embedding-config { display: flex; flex-direction: column; gap: 8px; padding: 12px 14px; background: var(--mc-bg-sunken); border-radius: 10px; border: 1px solid var(--mc-border-light); }
.embedding-label { font-size: 13px; font-weight: 600; color: var(--mc-text-primary); display: flex; align-items: baseline; gap: 8px; }
.embedding-hint { font-size: 11px; font-weight: 400; color: var(--mc-text-tertiary); }
.embedding-row { display: flex; gap: 8px; align-items: center; }
.embedding-select { flex: 1; padding: 7px 12px; border: 1px solid var(--mc-border); border-radius: 8px; background: var(--mc-bg-elevated); color: var(--mc-text-primary); font-size: 13px; outline: none; }
.embedding-select:focus { border-color: var(--mc-primary); }
.embedding-select:disabled { opacity: 0.6; cursor: not-allowed; }

/* Editor */
.config-editor { width: 100%; flex: 1; min-height: 0; padding: 16px; border: 1px solid var(--mc-border); border-radius: 14px; font-family: 'JetBrains Mono', 'Fira Code', Consolas, monospace; font-size: 13px; line-height: 1.7; resize: none; overflow: auto; background: var(--mc-bg-elevated); color: var(--mc-text-primary); outline: none; }
.config-editor:focus { border-color: var(--mc-primary); box-shadow: 0 0 0 2px rgba(217,119,87,0.1); }

/* Actions */
.config-actions { display: flex; justify-content: flex-end; gap: 10px; flex-shrink: 0; }

/* Buttons */
.btn-primary { display: inline-flex; align-items: center; gap: 6px; padding: 8px 16px; background: var(--mc-primary); color: white; border: none; border-radius: 10px; font-size: 14px; font-weight: 500; cursor: pointer; }
.btn-primary:hover { background: var(--mc-primary-hover); }
.btn-primary:disabled { background: var(--mc-border); cursor: not-allowed; }
.btn-secondary { display: inline-flex; align-items: center; gap: 6px; padding: 8px 16px; background: var(--mc-bg-elevated); color: var(--mc-text-primary); border: 1px solid var(--mc-border); border-radius: 10px; font-size: 14px; cursor: pointer; }
.btn-secondary:hover { background: var(--mc-bg-sunken); }

@media (max-width: 768px) {
  .config-editor {
    min-height: 42vh;
    flex: none;
    resize: vertical;
  }

  .config-actions {
    flex-direction: column-reverse;
  }

  .config-actions .btn-primary,
  .config-actions .btn-secondary {
    width: 100%;
    justify-content: center;
  }
}
</style>
