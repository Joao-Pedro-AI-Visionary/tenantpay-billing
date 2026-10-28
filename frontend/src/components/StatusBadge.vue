<script setup lang="ts">
import { computed } from 'vue'
import AppIcon, { type IconName } from './AppIcon.vue'

const props = defineProps<{ status: string; testId?: string; kind?: 'inbox' }>()

// Status is conveyed by text (the exact enum), a shape (icon) and a color, never by color alone.
const look = computed<{ tone: string; icon: IconName }>(() => {
  switch (props.status) {
    case 'ACTIVE':
    case 'CONFIRMED':
    case 'PROCESSED':
      return { tone: 'tone-success', icon: 'check-circle' }
    case 'RECEIVED':
      // A RECEIVED charge is paid; a RECEIVED inbox event is only queued for processing.
      return props.kind === 'inbox' ? { tone: 'tone-info', icon: 'clock' } : { tone: 'tone-success', icon: 'check-circle' }
    case 'PENDING':
    case 'PROCESSING':
      return { tone: 'tone-warning', icon: 'clock' }
    case 'FAILED':
      return { tone: 'tone-warning', icon: 'alert' }
    case 'OVERDUE':
    case 'DEAD':
      return { tone: 'tone-danger', icon: 'alert' }
    case 'CANCELED':
    case 'IGNORED':
      return { tone: 'tone-neutral', icon: 'x-circle' }
    default:
      return { tone: 'tone-info', icon: 'info' }
  }
})
</script>

<template>
  <!-- The visible text is exactly the enum value so tests and screen readers get the real status. -->
  <span :class="['pill', look.tone]" :data-testid="testId"><AppIcon :name="look.icon" :size="14" />{{ status }}</span>
</template>
