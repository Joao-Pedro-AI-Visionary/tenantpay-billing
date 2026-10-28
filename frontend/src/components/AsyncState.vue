<script setup lang="ts">
import AppIcon, { type IconName } from './AppIcon.vue'

// Shared loading / error / empty state so every list behaves the same way.
// Loading uses skeleton rows that keep the layout stable (no jump when data arrives).
withDefaults(
  defineProps<{
    loading: boolean
    error: string | null
    empty: boolean
    emptyTitle: string
    emptyText?: string
    emptyIcon?: IconName
    skeletonRows?: number
  }>(),
  { emptyText: undefined, emptyIcon: 'circle', skeletonRows: 4 },
)
defineEmits<{ retry: [] }>()
</script>

<template>
  <div v-if="loading" class="skeleton-list" role="status" aria-busy="true" aria-live="polite">
    <span class="sr-only">Loading...</span>
    <div v-for="n in skeletonRows" :key="n" class="skeleton-row" aria-hidden="true">
      <span class="skeleton" style="width: 36px; height: 36px; border-radius: 10px"></span>
      <span class="skeleton-lines">
        <span class="skeleton" :style="{ width: `${50 + ((n * 13) % 30)}%`, height: '12px' }"></span>
        <span class="skeleton" :style="{ width: `${25 + ((n * 7) % 20)}%`, height: '10px' }"></span>
      </span>
      <span class="skeleton" style="width: 72px; height: 22px; border-radius: 999px"></span>
    </div>
  </div>
  <div v-else-if="error" class="state-pad">
    <div class="alert alert-error" role="alert">
      <AppIcon name="alert" />
      <div>
        <p>{{ error }}</p>
        <button type="button" class="btn" style="margin-top: 8px" @click="$emit('retry')">
          <AppIcon name="refresh" /> Try again
        </button>
      </div>
    </div>
  </div>
  <div v-else-if="empty" class="empty">
    <span class="empty-icon"><AppIcon :name="emptyIcon" :size="22" /></span>
    <strong>{{ emptyTitle }}</strong>
    <span v-if="emptyText">{{ emptyText }}</span>
    <slot name="empty-action" />
  </div>
  <slot v-else />
</template>

<style scoped>
.skeleton-list {
  display: grid;
}

.skeleton-row {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  padding: var(--space-4) var(--space-6);
  border-bottom: 1px solid var(--border);
}

.skeleton-row:last-child {
  border-bottom: 0;
}

.skeleton-lines {
  flex: 1;
  display: grid;
  gap: 8px;
}

.state-pad {
  padding: var(--space-4) var(--space-6) var(--space-6);
}
</style>
