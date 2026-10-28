<script setup lang="ts">
// Inline stroke icons (24x24 grid, 2px stroke). Decorative by default: screen readers get the
// visible text next to the icon instead.
export type IconName =
  | 'dashboard'
  | 'plans'
  | 'customers'
  | 'subscriptions'
  | 'charges'
  | 'webhooks'
  | 'menu'
  | 'close'
  | 'chevron-down'
  | 'chevron-right'
  | 'logout'
  | 'check'
  | 'check-circle'
  | 'clock'
  | 'alert'
  | 'x-circle'
  | 'refresh'
  | 'plus'
  | 'sparkles'
  | 'circle'
  | 'info'
  | 'calendar'
  | 'trend'
  | 'wallet'
  | 'bolt'

withDefaults(defineProps<{ name: IconName; size?: number }>(), { size: 18 })

const paths: Record<IconName, string[]> = {
  dashboard: ['M3 3h7v9H3z', 'M14 3h7v5h-7z', 'M14 12h7v9h-7z', 'M3 16h7v5H3z'],
  plans: ['M12 2 2 7l10 5 10-5-10-5z', 'm2 17 10 5 10-5', 'm2 12 10 5 10-5'],
  customers: [
    'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2',
    'M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8z',
    'M22 21v-2a4 4 0 0 0-3-3.87',
    'M16 3.13a4 4 0 0 1 0 7.75',
  ],
  subscriptions: ['m17 1 4 4-4 4', 'M3 11V9a4 4 0 0 1 4-4h14', 'm7 23-4-4 4-4', 'M21 13v2a4 4 0 0 1-4 4H3'],
  charges: ['M4 2v20l3-2 3 2 2-1.5L14 22l3-2 3 2V2l-3 2-3-2-2 1.5L10 2 7 4z', 'M8 9h8', 'M8 13h8', 'M8 17h5'],
  webhooks: ['M13 2 3 14h9l-1 8 10-12h-9l1-8z'],
  menu: ['M4 6h16', 'M4 12h16', 'M4 18h16'],
  close: ['M18 6 6 18', 'm6 6 12 12'],
  'chevron-down': ['m6 9 6 6 6-6'],
  'chevron-right': ['m9 18 6-6-6-6'],
  logout: ['M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4', 'm16 17 5-5-5-5', 'M21 12H9'],
  check: ['M20 6 9 17l-5-5'],
  'check-circle': ['M22 11.08V12a10 10 0 1 1-5.93-9.14', 'M22 4 12 14.01l-3-3'],
  clock: ['M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20z', 'M12 6v6l4 2'],
  alert: [
    'M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z',
    'M12 9v4',
    'M12 17h.01',
  ],
  'x-circle': ['M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20z', 'm15 9-6 6', 'm9 9 6 6'],
  refresh: ['M21 12a9 9 0 0 1-15.5 6.2L3 16', 'M3 12a9 9 0 0 1 15.5-6.2L21 8', 'M21 3v5h-5', 'M3 21v-5h5'],
  plus: ['M12 5v14', 'M5 12h14'],
  sparkles: [
    'M12 3l1.9 5.1L19 10l-5.1 1.9L12 17l-1.9-5.1L5 10l5.1-1.9z',
    'M19 15l.9 2.1L22 18l-2.1.9L19 21l-.9-2.1L16 18l2.1-.9z',
  ],
  circle: ['M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20z'],
  info: ['M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20z', 'M12 16v-4', 'M12 8h.01'],
  calendar: ['M5 4h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z', 'M16 2v4', 'M8 2v4', 'M3 10h18'],
  trend: ['m22 7-8.5 8.5-5-5L2 17', 'M16 7h6v6'],
  wallet: [
    'M20 7H5a2 2 0 0 1 0-4h14v4',
    'M3 5v14a2 2 0 0 0 2 2h16v-5',
    'M18 12a2 2 0 0 0 0 4h4v-4z',
  ],
  bolt: ['M13 2 3 14h9l-1 8 10-12h-9l1-8z'],
}
</script>

<template>
  <svg
    class="icon"
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="2"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
    focusable="false"
  >
    <path v-for="(d, i) in paths[name]" :key="i" :d="d" />
  </svg>
</template>

<style scoped>
.icon {
  flex: none;
}
</style>
