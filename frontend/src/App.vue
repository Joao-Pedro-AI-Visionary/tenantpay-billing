<script setup lang="ts">
import { computed, defineAsyncComponent, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import AppIcon, { type IconName } from './components/AppIcon.vue'
import BrandMark from './components/BrandMark.vue'
import ToastHost from './components/ToastHost.vue'
import { clearToken, token } from './auth'
import { DEMO_MODE } from './config'
import { loadMe, me, meError, resetSession } from './session'

// DEMO_MODE is a build time constant, so the banner (and the mock API it imports) is not part of the
// normal build at all.
const DemoBanner = DEMO_MODE ? defineAsyncComponent(() => import('./components/DemoBanner.vue')) : null

const route = useRoute()
const router = useRouter()

const isPublic = computed(() => route.meta.public === true)
const pageTitle = computed(() => route.meta.title ?? '')

interface NavItem {
  name: string
  label: string
  testId: string
  icon: IconName
  adminOnly?: boolean
}

// Miller and Hick: six destinations at most, grouped by the billing workflow order.
const navItems: NavItem[] = [
  { name: 'dashboard', label: 'Dashboard', testId: 'nav-dashboard', icon: 'dashboard' },
  { name: 'plans', label: 'Plans', testId: 'nav-plans', icon: 'plans' },
  { name: 'customers', label: 'Customers', testId: 'nav-customers', icon: 'customers' },
  { name: 'subscriptions', label: 'Subscriptions', testId: 'nav-subscriptions', icon: 'subscriptions' },
  { name: 'charges', label: 'Charges', testId: 'nav-charges', icon: 'charges' },
  { name: 'webhooks', label: 'Webhooks', testId: 'nav-webhooks', icon: 'webhooks', adminOnly: true },
]

const visibleNav = computed(() => navItems.filter((i) => !i.adminOnly || !me.value || me.value.role === 'ADMIN'))

const tenantName = computed(() => me.value?.tenantName ?? (meError.value ? 'Unavailable' : 'Loading...'))
const initials = computed(() => {
  const name = me.value?.tenantName ?? ''
  const parts = name.split(/\s+/).filter(Boolean)
  return ((parts[0]?.[0] ?? '') + (parts[1]?.[0] ?? '')).toUpperCase() || 'TP'
})

watch(
  token,
  (value) => {
    if (value) void loadMe()
    else resetSession()
  },
  { immediate: true },
)

// ---------- mobile navigation drawer ----------
const navOpen = ref(false)
const navToggle = ref<HTMLButtonElement | null>(null)
const sidebar = ref<HTMLElement | null>(null)
const isDesktop = ref(true)
let mq: MediaQueryList | null = null

function syncViewport(): void {
  isDesktop.value = mq?.matches ?? true
  if (isDesktop.value) navOpen.value = false
}

async function openNav(): Promise<void> {
  navOpen.value = true
  await nextTick()
  sidebar.value?.querySelector<HTMLElement>('a')?.focus()
}

function closeNav(returnFocus = true): void {
  if (!navOpen.value) return
  navOpen.value = false
  if (returnFocus) navToggle.value?.focus()
}

// ---------- user menu ----------
const menuOpen = ref(false)
const menuButton = ref<HTMLButtonElement | null>(null)
const menuRoot = ref<HTMLElement | null>(null)

async function toggleMenu(): Promise<void> {
  menuOpen.value = !menuOpen.value
  if (menuOpen.value) {
    await nextTick()
    menuRoot.value?.querySelector<HTMLElement>('[role="menuitem"]')?.focus()
  }
}

function closeMenu(returnFocus = true): void {
  if (!menuOpen.value) return
  menuOpen.value = false
  if (returnFocus) menuButton.value?.focus()
}

function onDocumentClick(event: MouseEvent): void {
  if (menuOpen.value && menuRoot.value && !menuRoot.value.contains(event.target as Node)) closeMenu(false)
}

function onKeydown(event: KeyboardEvent): void {
  if (event.key !== 'Escape') return
  if (menuOpen.value) closeMenu()
  else if (navOpen.value) closeNav()
}

onMounted(() => {
  mq = window.matchMedia('(min-width: 1024px)')
  syncViewport()
  mq.addEventListener('change', syncViewport)
  document.addEventListener('click', onDocumentClick)
  document.addEventListener('keydown', onKeydown)
})

onBeforeUnmount(() => {
  mq?.removeEventListener('change', syncViewport)
  document.removeEventListener('click', onDocumentClick)
  document.removeEventListener('keydown', onKeydown)
})

watch(
  () => route.fullPath,
  () => {
    closeNav(false)
    closeMenu(false)
  },
)

async function logout(): Promise<void> {
  menuOpen.value = false
  clearToken()
  await router.push({ name: 'login' })
}
</script>

<template>
  <a class="skip-link" href="#main">Skip to content</a>

  <template v-if="isPublic">
    <component :is="DemoBanner" v-if="DemoBanner" />
    <main id="main" tabindex="-1">
      <RouterView />
    </main>
  </template>

  <div v-else class="shell">
    <div v-if="navOpen" class="backdrop" aria-hidden="true" @click="closeNav()"></div>

    <aside
      id="sidebar"
      ref="sidebar"
      :class="['sidebar', { open: navOpen }]"
      :inert="!isDesktop && !navOpen ? true : undefined"
      aria-label="Sidebar"
    >
      <div class="sidebar-head">
        <RouterLink :to="{ name: 'dashboard' }" class="brand" aria-label="TenantPay Billing home">
          <BrandMark />
          <span class="brand-text">TenantPay <span class="brand-sub">Billing</span></span>
        </RouterLink>
        <button
          v-if="!isDesktop"
          type="button"
          class="btn btn-ghost btn-icon"
          aria-label="Close navigation"
          @click="closeNav()"
        >
          <AppIcon name="close" :size="20" />
        </button>
      </div>

      <nav aria-label="Main">
        <ul class="nav-list">
          <li v-for="item in visibleNav" :key="item.name">
            <RouterLink :to="{ name: item.name }" class="nav-link" :data-testid="item.testId">
              <AppIcon :name="item.icon" :size="20" />
              <span>{{ item.label }}</span>
            </RouterLink>
          </li>
        </ul>
      </nav>

      <p class="sidebar-foot muted">Recurring billing for multi-tenant teams</p>
    </aside>

    <div class="main-col">
      <header class="topbar-wrap">
        <component :is="DemoBanner" v-if="DemoBanner" />
        <div class="topbar">
          <div class="topbar-left">
            <button
              v-if="!isDesktop"
              ref="navToggle"
              type="button"
              class="btn btn-ghost btn-icon"
              data-testid="nav-toggle"
              aria-controls="sidebar"
              :aria-expanded="navOpen"
              aria-label="Open navigation"
              @click="openNav"
            >
              <AppIcon name="menu" :size="22" />
            </button>
            <RouterLink v-if="!isDesktop" :to="{ name: 'dashboard' }" class="brand" aria-label="TenantPay Billing home">
              <BrandMark />
            </RouterLink>
            <p class="topbar-title" aria-hidden="true">{{ pageTitle }}</p>
          </div>

          <div ref="menuRoot" class="user-menu">
            <button
              ref="menuButton"
              type="button"
              class="user-button"
              data-testid="user-menu"
              aria-haspopup="menu"
              aria-controls="user-menu-list"
              :aria-expanded="menuOpen"
              @click.stop="toggleMenu"
            >
              <span class="avatar" aria-hidden="true">{{ initials }}</span>
              <span class="user-text">
                <span class="user-label">Tenant</span>
                <span class="user-tenant" data-testid="current-tenant">{{ tenantName }}</span>
              </span>
              <AppIcon name="chevron-down" :size="16" />
            </button>
            <Transition name="fade">
              <div v-if="menuOpen" id="user-menu-list" class="menu" role="menu" aria-label="Account">
                <div class="menu-head">
                  <p class="menu-name">{{ me?.tenantName ?? tenantName }}</p>
                  <p class="muted menu-email">{{ me?.email }}</p>
                  <p v-if="me" class="menu-role">Role: {{ me.role }}</p>
                </div>
                <button type="button" class="menu-item" role="menuitem" data-testid="logout" @click="logout">
                  <AppIcon name="logout" /> Log out
                </button>
              </div>
            </Transition>
          </div>
        </div>
      </header>

      <main id="main" tabindex="-1">
        <RouterView />
      </main>
    </div>
  </div>

  <ToastHost />
</template>

<style scoped>
.shell {
  min-height: 100vh;
}

@media (min-width: 1024px) {
  .shell {
    /* Paint the sidebar column on the shell too, so full page captures keep a continuous sidebar. */
    background: linear-gradient(
      90deg,
      var(--surface) calc(var(--sidebar-w) - 1px),
      var(--border) calc(var(--sidebar-w) - 1px),
      var(--border) var(--sidebar-w),
      transparent var(--sidebar-w)
    );
    display: grid;
    grid-template-columns: var(--sidebar-w) minmax(0, 1fr);
    align-items: start;
  }
}

/* ---------- Sidebar ---------- */

.sidebar {
  position: sticky;
  top: 0;
  height: 100vh;
  z-index: 40;
  width: var(--sidebar-w);
  display: flex;
  flex-direction: column;
  gap: var(--space-6);
  padding: var(--space-4) var(--space-3);
  background: var(--surface);
  border-right: 1px solid var(--border);
  overflow-y: auto;
  overscroll-behavior: contain;
}

.sidebar-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 var(--space-2);
}

.brand {
  display: inline-flex;
  align-items: center;
  gap: var(--space-3);
  min-height: var(--target-min);
  color: var(--text);
  text-decoration: none;
  font-weight: 700;
  font-size: var(--text-lg);
}

.brand-sub {
  color: var(--text-muted);
  font-weight: 500;
}

.nav-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 2px;
}

.nav-link {
  position: relative;
  display: flex;
  align-items: center;
  gap: var(--space-3);
  min-height: 44px;
  padding: 0 var(--space-3);
  border-radius: var(--radius-sm);
  color: var(--text-muted);
  font-weight: 500;
  font-size: var(--text-sm);
  text-decoration: none;
  transition:
    background-color var(--motion-fast) var(--ease-out),
    color var(--motion-fast) var(--ease-out);
}

.nav-link:hover {
  background: var(--surface-hover);
  color: var(--text);
}

.nav-link.router-link-active {
  background: var(--surface-hover);
  color: var(--text);
  font-weight: 600;
}

.nav-link.router-link-active::before {
  content: '';
  position: absolute;
  left: -12px;
  top: 10px;
  bottom: 10px;
  width: 3px;
  border-radius: 0 3px 3px 0;
  background: var(--text);
}

.sidebar-foot {
  margin-top: auto;
  padding: 0 var(--space-3);
  font-size: var(--text-xs);
}

.backdrop {
  position: fixed;
  inset: 0;
  z-index: 35;
  background: rgb(16 24 40 / 0.4);
}

@media (max-width: 1023px) {
  .sidebar {
    position: fixed;
    inset: 0 auto 0 0;
    height: auto;
    width: min(300px, 86vw);
    box-shadow: var(--shadow-pop);
    transform: translateX(-100%);
    visibility: hidden;
    transition:
      transform var(--motion-slow) var(--ease-out),
      visibility 0s linear var(--motion-slow);
  }
  .sidebar.open {
    transform: none;
    visibility: visible;
    transition: transform var(--motion-slow) var(--ease-out);
  }
}

/* ---------- Main column and top bar ---------- */

.main-col {
  min-width: 0;
}

.topbar-wrap {
  position: sticky;
  top: 0;
  z-index: 30;
  background: rgb(255 255 255 / 0.92);
  backdrop-filter: saturate(1.4) blur(8px);
  border-bottom: 1px solid var(--border);
}

.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  min-height: var(--topbar-h);
  padding: 0 var(--space-4);
}

@media (min-width: 768px) {
  .topbar {
    padding: 0 var(--space-8);
  }
}

.topbar-left {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  min-width: 0;
}

.topbar-title {
  font-weight: 600;
  color: var(--text-muted);
  font-size: var(--text-sm);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

@media (max-width: 479px) {
  .topbar-title {
    display: none;
  }
}

/* ---------- User menu (Jakob: account controls top right) ---------- */

.user-menu {
  position: relative;
  min-width: 0;
}

.user-button {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  min-height: var(--target-min);
  max-width: 280px;
  padding: 4px var(--space-3) 4px 4px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-pill);
  cursor: pointer;
  text-align: left;
  transition: background-color var(--motion-fast) var(--ease-out);
}

.user-button:hover {
  background: var(--surface-hover);
}

.avatar {
  display: grid;
  place-items: center;
  flex: none;
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: var(--text);
  color: var(--text-inverse);
  font-size: var(--text-xs);
  font-weight: 700;
}

.user-text {
  display: grid;
  min-width: 0;
  line-height: 1.2;
}

.user-label {
  font-size: var(--text-xs);
  color: var(--text-muted);
}

.user-tenant {
  font-weight: 600;
  font-size: var(--text-sm);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

@media (max-width: 479px) {
  .user-button {
    max-width: 180px;
  }
}

.menu {
  position: absolute;
  right: 0;
  top: calc(100% + 8px);
  z-index: 50;
  width: 260px;
  padding: var(--space-2);
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-pop);
}

.menu-head {
  padding: var(--space-2) var(--space-3) var(--space-3);
  border-bottom: 1px solid var(--border);
  margin-bottom: var(--space-2);
}

.menu-name {
  font-weight: 600;
  overflow-wrap: anywhere;
}

.menu-email {
  overflow-wrap: anywhere;
}

.menu-role {
  font-size: var(--text-xs);
  color: var(--text-muted);
  margin-top: 2px;
}

.menu-item {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  width: 100%;
  min-height: var(--target-min);
  padding: 0 var(--space-3);
  background: transparent;
  border: 0;
  border-radius: var(--radius-sm);
  font-weight: 500;
  font-size: var(--text-sm);
  cursor: pointer;
  text-align: left;
}

.menu-item:hover,
.menu-item:focus-visible {
  background: var(--surface-hover);
}
</style>
