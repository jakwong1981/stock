<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink, RouterView, useRoute } from 'vue-router'
import { Bot, LayoutDashboard, Newspaper, Settings, Wallet } from '@lucide/vue'
import { useMarketStream } from '@/composables/useMarketStream'
import ConnectionBadge from '@/components/common/ConnectionBadge.vue'
import SearchBox from '@/components/common/SearchBox.vue'
import LanguageSwitcher from '@/components/common/LanguageSwitcher.vue'
import TickerTapeMarquee from '@/components/market/TickerTapeMarquee.vue'
import BreakingNewsTicker from '@/components/news/BreakingNewsTicker.vue'
import CyberBackdrop from '@/components/cyberpunk/CyberBackdrop.vue'

/**
 * Responsive shell:
 *  - ≥ lg (desktop / landscape tablet): top bar with inline nav
 *  - < lg (phone / portrait tablet): compact top bar + bottom tab bar
 * Safe-area insets are applied for notched devices.
 */
/** Market data provider reported by the backend: 'mock' | 'yahoo' | 'finnhub' ('' while loading). */
defineProps<{ dataSource: string }>()
const { t } = useI18n()
const route = useRoute()
const { status } = useMarketStream()

const nav = computed(() => [
  { to: '/', name: 'dashboard', label: t('nav.dashboard'), icon: LayoutDashboard },
  { to: '/news', name: 'news', label: t('nav.news'), icon: Newspaper },
  { to: '/portfolio', name: 'portfolio', label: t('nav.portfolio'), icon: Wallet },
  { to: '/ai', name: 'ai', label: t('nav.ai'), icon: Bot },
  { to: '/settings', name: 'settings', label: t('nav.settings'), icon: Settings },
])
const isActive = (name: string) => route.name === name
</script>

<template>
  <div class="relative flex min-h-full flex-col">
    <CyberBackdrop />
    <a href="#main" class="sr-only-focusable absolute left-2 top-2 z-50 rounded bg-neon-yellow px-3 py-2 text-void">
      {{ t('app.skipToContent') }}
    </a>

    <header class="glass pt-safe pl-safe pr-safe sticky top-0 z-40 border-x-0 border-t-0">
      <div class="mx-auto flex max-w-screen-2xl items-center gap-3 px-3 py-2 lg:px-6">
        <RouterLink to="/" class="flex shrink-0 items-center gap-2">
          <!-- Mini spinning 3D cube logo -->
          <span aria-hidden="true" class="logo-cube" style="perspective: 200px">
            <span class="cube3d" style="--s: 20px; animation-duration: 10s"><i v-for="f in 6" :key="f" /></span>
          </span>
          <span class="text-gradient hidden text-sm font-black tracking-[0.2em] sm:inline">CYBERSTOCK</span>
        </RouterLink>
        <div class="flex-1"><SearchBox /></div>
        <nav :aria-label="t('nav.main')" class="hidden items-center gap-1 lg:flex">
          <RouterLink
            v-for="n in nav"
            :key="n.name"
            :to="n.to"
            class="inline-flex min-h-11 items-center gap-1.5 rounded-lg px-3 text-sm"
            :class="isActive(n.name) ? 'nav-active text-ink' : 'text-ink-dim hover:text-ink'"
            :aria-current="isActive(n.name) ? 'page' : undefined"
          >
            <component :is="n.icon" :size="16" aria-hidden="true" />{{ n.label }}
          </RouterLink>
        </nav>
        <div class="hidden md:block"><LanguageSwitcher /></div>
        <ConnectionBadge :status="status" />
      </div>
      <div class="gradient-line" aria-hidden="true" />
      <TickerTapeMarquee />
      <div class="gradient-line" aria-hidden="true" />
      <p
        v-if="dataSource === 'mock'"
        class="bg-neon-yellow/10 py-0.5 text-center text-[11px] font-semibold tracking-wide text-neon-yellow"
      >
        {{ t('app.simulated') }}
      </p>
      <p v-else-if="dataSource === 'yahoo'" class="py-0.5 text-center text-[11px] text-ink-dim">
        {{ t('app.delayedYahoo') }}
      </p>
    </header>

    <BreakingNewsTicker />

    <!-- Safe-area insets live on a wrapper: on <main> they would override its px-* padding -->
    <div class="pl-safe pr-safe relative z-10 flex flex-1 flex-col">
    <main id="main" class="mx-auto w-full max-w-screen-2xl flex-1 px-3 pb-24 pt-3 lg:px-6 lg:pb-8">
      <!-- Page change: content swings in slightly in 3D -->
      <RouterView v-slot="{ Component, route: r }">
        <Transition name="page" mode="out-in">
          <component :is="Component" :key="r.fullPath" />
        </Transition>
      </RouterView>
    </main>
    </div>

    <!-- Mobile / tablet bottom tab bar -->
    <nav
      :aria-label="t('nav.main')"
      class="glass pb-safe fixed inset-x-0 bottom-0 z-40 grid grid-cols-5 border-x-0 border-b-0 lg:hidden"
    >
      <RouterLink
        v-for="n in nav"
        :key="n.name"
        :to="n.to"
        class="tab relative flex min-h-14 flex-col items-center justify-center gap-0.5 text-[11px]"
        :class="isActive(n.name) ? 'tab-active text-neon-cyan' : 'text-ink-dim'"
        :aria-current="isActive(n.name) ? 'page' : undefined"
      >
        <component :is="n.icon" :size="20" aria-hidden="true" class="tab-icon" />
        <span class="max-w-full truncate px-1">{{ n.label }}</span>
      </RouterLink>
    </nav>
  </div>
</template>
