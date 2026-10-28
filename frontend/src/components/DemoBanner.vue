<script setup lang="ts">
import { ref } from 'vue'
import AppIcon from './AppIcon.vue'

// Only rendered in the demo build (see App.vue). Resetting clears every tenantpay.demo.* key,
// so the next request reseeds the sample data.
const resetting = ref(false)

async function reset(): Promise<void> {
  resetting.value = true
  const { resetDemoData } = await import('#demo-api')
  resetDemoData()
  window.location.reload()
}
</script>

<template>
  <div class="demo-banner" data-testid="demo-banner" role="region" aria-label="Demo mode">
    <p class="demo-text">
      <AppIcon name="info" :size="16" />
      <span><strong>Demo mode:</strong> data is mocked in your browser</span>
    </p>
    <button
      type="button"
      class="demo-reset"
      data-testid="demo-reset"
      :aria-busy="resetting"
      :disabled="resetting"
      @click="reset"
    >
      <span v-if="resetting" class="spinner" aria-hidden="true"></span>
      <AppIcon v-else name="refresh" :size="16" />
      Reset demo data
    </button>
  </div>
</template>

<style scoped>
.demo-banner {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: var(--space-1) var(--space-4);
  padding: 2px var(--space-4);
  background: var(--info-bg);
  color: var(--info-fg);
  border-bottom: 1px solid var(--info-border);
  font-size: var(--text-sm);
}

.demo-text {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
}

.demo-reset {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: var(--target-min);
  padding: 0 var(--space-2);
  background: transparent;
  border: 0;
  color: var(--info-fg);
  font-weight: 600;
  text-decoration: underline;
  text-underline-offset: 3px;
  cursor: pointer;
}

.demo-reset:hover:not(:disabled) {
  color: #1849a9;
}
</style>
