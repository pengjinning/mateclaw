<template>
  <div class="about-page">
    <section class="about-hero mc-surface-card">
      <div class="about-hero__brand">
        <img src="/logo/mateclaw_logo_s.png" alt="MateClaw" class="about-logo" />
        <div class="about-badge">v{{ appVersion }}</div>
      </div>

      <div class="about-hero__content">
        <div class="hero-kicker">{{ t('settings.about.heroKicker') }}</div>
        <h2 class="hero-title">{{ t('settings.about.heroTitle') }}</h2>
        <p class="hero-desc">{{ t('settings.about.heroDesc') }}</p>

        <div class="hero-pillars">
          <div v-for="pillar in pillars" :key="pillar.title" class="pillar-card">
            <div class="pillar-icon">{{ pillar.icon }}</div>
            <div class="pillar-title">{{ pillar.title }}</div>
            <div class="pillar-desc">{{ pillar.desc }}</div>
          </div>
        </div>
      </div>
    </section>

    <section class="about-grid">
      <div class="about-manifesto mc-surface-card">
        <div class="section-kicker">{{ t('settings.about.manifestoKicker') }}</div>
        <h3 class="section-title">{{ t('settings.about.manifestoTitle') }}</h3>
        <p class="section-desc">{{ t('settings.about.manifestoDesc') }}</p>

        <div class="manifesto-list">
          <div v-for="item in manifesto" :key="item.title" class="manifesto-item">
            <div class="manifesto-title">{{ item.title }}</div>
            <div class="manifesto-desc">{{ item.desc }}</div>
          </div>
        </div>
      </div>

      <div class="about-system mc-surface-card">
        <div class="section-kicker">{{ t('settings.about.systemKicker') }}</div>
        <h3 class="section-title">{{ t('settings.about.systemTitle') }}</h3>
        <p class="section-desc">{{ t('settings.about.systemDesc') }}</p>

        <div class="system-list">
          <div v-for="item in systemBlocks" :key="item.title" class="system-item">
            <div class="system-title">{{ item.title }}</div>
            <div class="system-desc">{{ item.desc }}</div>
          </div>
        </div>
      </div>
    </section>

    <section class="about-foundation mc-surface-card">
      <div class="foundation-copy">
        <div class="section-kicker">{{ t('settings.about.foundationKicker') }}</div>
        <h3 class="section-title">{{ t('settings.about.foundationTitle') }}</h3>
        <p class="section-desc">{{ t('settings.about.foundationDesc') }}</p>
      </div>

      <div class="tech-grid">
        <div class="tech-item" v-for="tech in techStack" :key="tech.name">
          <div class="tech-icon">{{ tech.icon }}</div>
          <div class="tech-meta">
            <div class="tech-name">{{ tech.name }}</div>
            <div class="tech-version">{{ tech.version }}</div>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { version as appVersion } from '../../../../package.json'

const { t } = useI18n()

const pillars = computed(() => [
  {
    icon: '01',
    title: t('settings.about.pillars.contextTitle'),
    desc: t('settings.about.pillars.contextDesc'),
  },
  {
    icon: '02',
    title: t('settings.about.pillars.executionTitle'),
    desc: t('settings.about.pillars.executionDesc'),
  },
  {
    icon: '03',
    title: t('settings.about.pillars.memoryTitle'),
    desc: t('settings.about.pillars.memoryDesc'),
  },
])

const manifesto = computed(() => [
  {
    title: t('settings.about.manifestoItems.runtimeTitle'),
    desc: t('settings.about.manifestoItems.runtimeDesc'),
  },
  {
    title: t('settings.about.manifestoItems.knowledgeTitle'),
    desc: t('settings.about.manifestoItems.knowledgeDesc'),
  },
  {
    title: t('settings.about.manifestoItems.multimodalTitle'),
    desc: t('settings.about.manifestoItems.multimodalDesc'),
  },
])

const systemBlocks = computed(() => [
  {
    title: t('settings.about.systemItems.workspaceTitle'),
    desc: t('settings.about.systemItems.workspaceDesc'),
  },
  {
    title: t('settings.about.systemItems.governanceTitle'),
    desc: t('settings.about.systemItems.governanceDesc'),
  },
  {
    title: t('settings.about.systemItems.deliveryTitle'),
    desc: t('settings.about.systemItems.deliveryDesc'),
  },
])

const techStack = [
  { icon: '☕', name: 'Spring Boot', version: '3.3.x' },
  { icon: '🤖', name: 'Spring AI', version: '1.1.x' },
  { icon: '🌿', name: 'Spring AI Alibaba', version: '1.1.x' },
  { icon: '💚', name: 'Vue 3', version: '3.5.x' },
  { icon: '⚡', name: 'Vite', version: '7.x' },
  { icon: '🗄️', name: 'MyBatis Plus', version: '3.5.x' },
]
</script>

<style scoped>
.about-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.about-hero {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  gap: 24px;
  padding: 28px;
  overflow: hidden;
  position: relative;
}

.about-hero::before {
  content: '';
  position: absolute;
  inset: 0;
  background:
    radial-gradient(circle at top left, rgba(217, 109, 70, 0.14), transparent 34%),
    radial-gradient(circle at bottom right, rgba(24, 74, 69, 0.14), transparent 38%);
  pointer-events: none;
}

.about-hero__brand,
.about-hero__content {
  position: relative;
  z-index: 1;
}

.about-hero__brand {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  padding: 18px;
  border-radius: 24px;
  background: linear-gradient(180deg, var(--mc-panel-raised), var(--mc-surface-overlay));
  box-shadow: inset 0 0 0 1px var(--mc-border-light);
}

.about-logo {
  width: 110px;
  height: 110px;
  object-fit: contain;
  filter: drop-shadow(0 12px 28px rgba(217, 109, 70, 0.28));
}

.about-badge {
  padding: 8px 12px;
  border-radius: 999px;
  background: rgba(217, 109, 70, 0.12);
  color: var(--mc-primary);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.hero-kicker,
.section-kicker {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--mc-accent);
}

.hero-title,
.section-title {
  margin: 10px 0 0;
  color: var(--mc-text-primary);
  letter-spacing: -0.05em;
}

.hero-title {
  font-size: clamp(34px, 5vw, 52px);
  line-height: 0.96;
  max-width: 760px;
}

.hero-desc,
.section-desc {
  margin: 14px 0 0;
  color: var(--mc-text-secondary);
  line-height: 1.72;
}

.hero-desc {
  max-width: 760px;
  font-size: 15px;
}

.hero-pillars {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  margin-top: 24px;
}

.pillar-card {
  padding: 18px;
  border-radius: 20px;
  background: var(--mc-panel-raised);
  border: 1px solid var(--mc-border-light);
  box-shadow: 0 10px 30px rgba(124, 63, 30, 0.08);
}

.pillar-icon {
  color: var(--mc-primary);
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.pillar-title,
.manifesto-title,
.system-title,
.tech-name {
  color: var(--mc-text-primary);
  font-weight: 700;
}

.pillar-title {
  margin-top: 14px;
  font-size: 15px;
}

.pillar-desc,
.manifesto-desc,
.system-desc,
.tech-version {
  margin-top: 8px;
  color: var(--mc-text-secondary);
  font-size: 13px;
  line-height: 1.65;
}

.about-grid {
  display: grid;
  grid-template-columns: 1.2fr 1fr;
  gap: 20px;
}

.about-manifesto,
.about-system,
.about-foundation {
  padding: 24px;
}

.section-title {
  font-size: 28px;
}

.manifesto-list,
.system-list {
  display: grid;
  gap: 14px;
  margin-top: 22px;
}

.manifesto-item,
.system-item {
  padding: 18px;
  border-radius: 18px;
  background: var(--mc-bg-muted);
  border: 1px solid var(--mc-border-light);
}

.about-foundation {
  display: grid;
  grid-template-columns: 1fr 1.1fr;
  gap: 24px;
  align-items: start;
}

.tech-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.tech-item {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 16px;
  border-radius: 18px;
  background: linear-gradient(180deg, var(--mc-panel-raised), var(--mc-surface-overlay));
  border: 1px solid var(--mc-border-light);
}

.tech-icon {
  width: 42px;
  height: 42px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 14px;
  background: rgba(217, 109, 70, 0.1);
  font-size: 19px;
  flex-shrink: 0;
}

@media (max-width: 1080px) {
  .about-hero,
  .about-foundation,
  .about-grid {
    grid-template-columns: 1fr;
  }

  .about-hero__brand {
    max-width: 320px;
  }
}

@media (max-width: 760px) {
  .about-hero {
    padding: 22px;
  }

  .hero-pillars,
  .tech-grid {
    grid-template-columns: 1fr;
  }

  .hero-title {
    font-size: 34px;
  }

  .section-title {
    font-size: 24px;
  }
}
</style>
