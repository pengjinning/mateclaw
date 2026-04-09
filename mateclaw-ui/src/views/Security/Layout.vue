<template>
  <div class="mc-page-shell security-shell">
    <div class="mc-page-frame security-frame">
      <div class="mc-page-inner security-layout">
        <div class="settings-nav mc-surface-card">
          <div class="settings-nav__intro">
            <div class="mc-page-kicker">{{ t('security.kicker') }}</div>
            <h2 class="nav-title">{{ t('security.title') }}</h2>
            <p class="nav-desc">{{ t('security.layoutDesc') }}</p>
          </div>
          <router-link
            v-for="section in sections"
            :key="section.id"
            :to="section.path"
            class="nav-item"
            :class="{ active: isActive(section.path) }"
          >
            <span class="nav-icon" v-html="section.icon"></span>
            {{ section.label }}
          </router-link>
        </div>

        <div class="settings-content mc-surface-card">
          <div class="settings-content__inner">
            <router-view />
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'

const route = useRoute()
const { t } = useI18n()

const sections = computed(() => [
  {
    id: 'toolGuard',
    path: '/security/tool-guard',
    label: t('security.sections.toolGuard'),
    icon: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>',
  },
  {
    id: 'fileGuard',
    path: '/security/file-guard',
    label: t('security.sections.fileGuard'),
    icon: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="9" y1="15" x2="15" y2="15"/></svg>',
  },
  {
    id: 'auditLogs',
    path: '/security/audit-logs',
    label: t('security.sections.auditLogs'),
    icon: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/></svg>',
  },
])

function isActive(path: string) {
  return route.path === path
}
</script>

<style scoped>
.security-shell {
  background: transparent;
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

.security-frame {
  height: min(calc(100vh - 28px), 100%);
  min-height: 0;
  overflow: hidden;
}

.security-layout {
  display: flex;
  height: 100%;
  min-height: 0;
  gap: 18px;
}

.settings-nav {
  width: 286px;
  min-width: 286px;
  padding: 18px 14px;
  overflow-y: auto;
  align-self: stretch;
}

.settings-nav__intro {
  padding: 6px 8px 16px;
  border-bottom: 1px solid var(--mc-border-light);
  margin-bottom: 8px;
}

.nav-title {
  font-size: 28px;
  font-weight: 800;
  color: var(--mc-text-primary);
  letter-spacing: -0.04em;
  margin: 0 0 6px;
}

.nav-desc {
  color: var(--mc-text-secondary);
  font-size: 13px;
  line-height: 1.65;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 10px 12px;
  border: none;
  background: transparent;
  color: var(--mc-text-secondary);
  font-size: 14px;
  border-radius: 14px;
  cursor: pointer;
  text-align: left;
  text-decoration: none;
  margin-bottom: 2px;
  font-weight: 500;
}

.nav-item:hover { background: var(--mc-bg-muted); color: var(--mc-text-primary); }
.nav-item.active { background: var(--mc-primary-bg); color: var(--mc-primary); font-weight: 600; box-shadow: inset 0 0 0 1px rgba(217, 109, 70, 0.08); }
.nav-icon { display: flex; align-items: center; flex-shrink: 0; }

.settings-content {
  flex: 1;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  padding: 22px;
}

.settings-content__inner {
  height: 100%;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
  padding-right: 4px;
}

@media (max-width: 900px) {
  .security-frame {
    height: auto;
    min-height: calc(100vh - 28px);
    overflow: visible;
  }

  .security-layout {
    flex-direction: column;
    height: auto;
  }

  .settings-nav {
    width: 100%;
    min-width: 100%;
    align-self: auto;
  }

  .settings-content {
    overflow: visible;
  }

  .settings-content__inner {
    overflow: visible;
    padding-right: 0;
  }
}
</style>
