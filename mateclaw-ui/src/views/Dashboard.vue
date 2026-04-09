<template>
  <div class="mc-page-shell dashboard-shell">
    <div class="mc-page-frame">
      <div class="mc-page-inner">
        <div class="mc-page-header">
          <div>
            <div class="mc-page-kicker">Operations Pulse</div>
            <h1 class="mc-page-title">{{ t('dashboard.title') }}</h1>
            <p class="mc-page-desc">{{ t('dashboard.desc') }}</p>
          </div>
          <div class="hero-note mc-surface-card">
            <div class="hero-note__label">Today</div>
            <div class="hero-note__value">{{ formatTokens(todayStats.totalTokens) }}</div>
            <div class="hero-note__meta">{{ t('dashboard.tokens') }} · {{ todayStats.toolCalls }} {{ t('dashboard.toolCalls') }}</div>
          </div>
        </div>

        <div class="stats-grid">
          <div class="stat-card mc-surface-card">
            <div class="stat-icon">💬</div>
            <div class="stat-body">
              <div class="stat-value">{{ todayStats.conversations }}</div>
              <div class="stat-label">{{ t('dashboard.conversations') }}</div>
            </div>
          </div>
          <div class="stat-card mc-surface-card">
            <div class="stat-icon">📝</div>
            <div class="stat-body">
              <div class="stat-value">{{ todayStats.messages }}</div>
              <div class="stat-label">{{ t('dashboard.messages') }}</div>
            </div>
          </div>
          <div class="stat-card mc-surface-card">
            <div class="stat-icon">🎯</div>
            <div class="stat-body">
              <div class="stat-value">{{ formatTokens(todayStats.totalTokens) }}</div>
              <div class="stat-label">{{ t('dashboard.tokens') }}</div>
            </div>
          </div>
          <div class="stat-card mc-surface-card">
            <div class="stat-icon">🔧</div>
            <div class="stat-body">
              <div class="stat-value">{{ todayStats.toolCalls }}</div>
              <div class="stat-label">{{ t('dashboard.toolCalls') }}</div>
            </div>
          </div>
        </div>

        <div class="comparison-section">
          <div class="section-head">
            <h2 class="section-title">{{ t('dashboard.periodComparison') }}</h2>
            <p class="section-subtitle">A sharper view of how your system behaves across short, medium, and monthly horizons.</p>
          </div>
          <div class="comparison-grid">
            <div class="comparison-card mc-surface-card" v-for="(period, key) in overview" :key="key">
              <h3 class="comparison-title">{{ t('dashboard.periods.' + key) }}</h3>
              <div class="comparison-row">
                <span class="comparison-label">{{ t('dashboard.conversations') }}</span>
                <span class="comparison-value">{{ period.conversations }}</span>
              </div>
              <div class="comparison-row">
                <span class="comparison-label">{{ t('dashboard.messages') }}</span>
                <span class="comparison-value">{{ period.messages }}</span>
              </div>
              <div class="comparison-row">
                <span class="comparison-label">{{ t('dashboard.tokens') }}</span>
                <span class="comparison-value">{{ formatTokens(period.totalTokens) }}</span>
              </div>
              <div class="comparison-row">
                <span class="comparison-label">{{ t('dashboard.toolCalls') }}</span>
                <span class="comparison-value">{{ period.toolCalls }}</span>
              </div>
            </div>
          </div>
        </div>

        <div class="runs-section">
          <div class="section-head">
            <h2 class="section-title">{{ t('dashboard.recentRuns') }}</h2>
            <p class="section-subtitle">Execution should feel legible. If it runs, you should see its rhythm, cost, and outcome instantly.</p>
          </div>
          <div class="runs-table-wrapper mc-surface-card">
            <table v-if="recentRuns.length" class="runs-table">
              <thead>
                <tr>
                  <th>{{ t('dashboard.runColumns.time') }}</th>
                  <th>{{ t('dashboard.runColumns.job') }}</th>
                  <th>{{ t('dashboard.runColumns.status') }}</th>
                  <th>{{ t('dashboard.runColumns.trigger') }}</th>
                  <th>{{ t('dashboard.runColumns.duration') }}</th>
                  <th>{{ t('dashboard.runColumns.tokens') }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="run in recentRuns" :key="run.id">
                  <td class="cell-time">{{ formatTime(run.startedAt) }}</td>
                  <td class="cell-job">#{{ run.cronJobId }}</td>
                  <td>
                    <span class="status-badge" :class="'status-' + run.status">{{ run.status }}</span>
                  </td>
                  <td class="cell-trigger">{{ run.triggerType }}</td>
                  <td class="cell-duration">{{ calcDuration(run) }}</td>
                  <td class="cell-tokens">{{ run.tokenUsage || '-' }}</td>
                </tr>
              </tbody>
            </table>
            <div v-else class="empty-state">{{ t('dashboard.noRuns') }}</div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { dashboardApi } from '@/api'

const { t } = useI18n()

const overview = ref<Record<string, any>>({})
const recentRuns = ref<any[]>([])

const todayStats = reactive({
  conversations: 0,
  messages: 0,
  totalTokens: 0,
  toolCalls: 0,
  errors: 0,
})

onMounted(async () => {
  try {
    const [overviewRes, runsRes] = await Promise.all([
      dashboardApi.overview(),
      dashboardApi.recentRuns(10),
    ])
    overview.value = (overviewRes as any).data || {}
    const today = overview.value.today || {}
    Object.assign(todayStats, today)
    recentRuns.value = (runsRes as any).data || []
  } catch {
    // Dashboard data is non-critical
  }
})

function formatTokens(n: number): string {
  if (!n) return '0'
  if (n >= 1_000_000) return (n / 1_000_000).toFixed(1) + 'M'
  if (n >= 1_000) return (n / 1_000).toFixed(1) + 'K'
  return String(n)
}

function formatTime(dateStr: string) {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleString()
}

function calcDuration(run: any): string {
  if (!run.startedAt || !run.finishedAt) return '-'
  const ms = new Date(run.finishedAt).getTime() - new Date(run.startedAt).getTime()
  if (ms < 1000) return ms + 'ms'
  return (ms / 1000).toFixed(1) + 's'
}
</script>

<style scoped>
.dashboard-shell {
  background: transparent;
}

.hero-note {
  min-width: 220px;
  padding: 18px 20px;
}

.hero-note__label {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--mc-accent);
  margin-bottom: 10px;
}

.hero-note__value {
  font-size: 34px;
  font-weight: 800;
  letter-spacing: -0.05em;
  color: var(--mc-text-primary);
}

.hero-note__meta {
  margin-top: 8px;
  color: var(--mc-text-secondary);
  font-size: 13px;
  line-height: 1.5;
}

.section-head {
  margin-bottom: 16px;
}

.section-title {
  font-size: 18px;
  font-weight: 700;
  color: var(--mc-text-primary);
  letter-spacing: -0.03em;
  margin: 0 0 4px;
}

.section-subtitle {
  color: var(--mc-text-secondary);
  font-size: 13px;
  line-height: 1.6;
}

.stats-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 18px; margin-bottom: 36px; }
.stat-card {
  display: flex; align-items: center; gap: 14px;
  padding: 22px;
}
.stat-icon {
  width: 52px;
  height: 52px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 16px;
  background: linear-gradient(135deg, rgba(217, 109, 70, 0.12), rgba(24, 74, 69, 0.08));
  font-size: 25px;
}
.stat-body { display: flex; flex-direction: column; }
.stat-value { font-size: 30px; font-weight: 800; color: var(--mc-text-primary); line-height: 1; letter-spacing: -0.05em; }
.stat-label { font-size: 12px; color: var(--mc-text-tertiary); margin-top: 6px; text-transform: uppercase; letter-spacing: 0.08em; }

.comparison-section { margin-bottom: 32px; }
.comparison-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 18px; }
.comparison-card {
  padding: 18px 18px 12px;
}
.comparison-title { font-size: 12px; font-weight: 700; color: var(--mc-accent); margin: 0 0 12px; text-transform: uppercase; letter-spacing: 0.09em; }
.comparison-row { display: flex; justify-content: space-between; padding: 10px 0; border-bottom: 1px solid var(--mc-border-light); }
.comparison-row:last-child { border-bottom: none; }
.comparison-label { font-size: 13px; color: var(--mc-text-tertiary); }
.comparison-value { font-size: 14px; font-weight: 700; color: var(--mc-text-primary); }

/* Runs Section */
.runs-section { margin-bottom: 32px; }
.runs-table-wrapper { overflow: hidden; }
.runs-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.runs-table th {
  padding: 10px 14px; text-align: left; font-weight: 600; font-size: 12px;
  color: var(--mc-text-tertiary); text-transform: uppercase; letter-spacing: 0.03em;
  background: var(--mc-bg-muted); border-bottom: 1px solid var(--mc-border-light);
}
.runs-table td { padding: 10px 14px; border-bottom: 1px solid var(--mc-border-light); color: var(--mc-text-primary); }
.runs-table tr:last-child td { border-bottom: none; }
.runs-table tbody tr:hover { background: rgba(217, 109, 70, 0.04); }

.cell-time { font-size: 12px; color: var(--mc-text-tertiary); white-space: nowrap; }
.cell-job { font-family: 'SF Mono', monospace; font-size: 12px; color: var(--mc-text-secondary); }
.cell-trigger { font-size: 12px; color: var(--mc-text-tertiary); }
.cell-duration { font-family: 'SF Mono', monospace; font-size: 12px; }
.cell-tokens { font-family: 'SF Mono', monospace; font-size: 12px; }

.status-badge { display: inline-block; padding: 2px 8px; border-radius: 4px; font-size: 11px; font-weight: 600; }
.status-running { background: rgba(59, 130, 246, 0.12); color: #3b82f6; }
.status-completed { background: rgba(16, 185, 129, 0.12); color: #10b981; }
.status-failed { background: rgba(239, 68, 68, 0.12); color: #ef4444; }

.empty-state { padding: 48px; text-align: center; color: var(--mc-text-tertiary); font-size: 14px; }

@media (max-width: 768px) {
  .hero-note {
    width: 100%;
    min-width: 0;
  }
  .stats-grid { grid-template-columns: repeat(2, 1fr); }
  .comparison-grid { grid-template-columns: 1fr; }
}
</style>
