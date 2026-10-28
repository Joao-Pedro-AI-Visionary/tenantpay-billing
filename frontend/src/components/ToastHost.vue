<script setup lang="ts">
import AppIcon from './AppIcon.vue'
import { dismiss, toasts } from '../toast'
</script>

<template>
  <div class="toast-host" role="status" aria-live="polite">
    <TransitionGroup name="toast">
      <div v-for="t in toasts" :key="t.id" :class="['toast', `toast-${t.kind}`]" data-testid="toast">
        <AppIcon :name="t.kind === 'success' ? 'check-circle' : t.kind === 'error' ? 'alert' : 'info'" :size="20" />
        <div class="toast-body">
          <p class="toast-title">{{ t.title }}</p>
          <p v-if="t.text" class="toast-text">{{ t.text }}</p>
        </div>
        <button type="button" class="btn btn-ghost btn-icon" aria-label="Dismiss notification" @click="dismiss(t.id)">
          <AppIcon name="close" />
        </button>
      </div>
    </TransitionGroup>
  </div>
</template>

<style scoped>
.toast-host {
  position: fixed;
  right: var(--space-4);
  bottom: var(--space-4);
  left: var(--space-4);
  z-index: 60;
  display: grid;
  justify-items: end;
  gap: var(--space-2);
  pointer-events: none;
}

.toast {
  pointer-events: auto;
  display: flex;
  align-items: flex-start;
  gap: var(--space-3);
  width: min(380px, 100%);
  padding: var(--space-3) var(--space-2) var(--space-3) var(--space-4);
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-pop);
}

.toast > .icon {
  margin-top: 2px;
}

.toast-success > .icon {
  color: var(--success-fg);
}

.toast-error > .icon {
  color: var(--danger-fg);
}

.toast-info > .icon {
  color: var(--info-fg);
}

.toast-body {
  flex: 1;
  min-width: 0;
  padding-top: 2px;
}

.toast-title {
  font-weight: 600;
  font-size: var(--text-sm);
}

.toast-text {
  color: var(--text-muted);
  font-size: var(--text-sm);
  overflow-wrap: anywhere;
}

.toast .btn-icon {
  min-height: 36px;
  width: 36px;
  margin: -4px 0;
}

.toast-enter-active,
.toast-leave-active {
  transition:
    opacity var(--motion-base) var(--ease-out),
    transform var(--motion-base) var(--ease-out);
}

.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translateY(8px);
}
</style>
