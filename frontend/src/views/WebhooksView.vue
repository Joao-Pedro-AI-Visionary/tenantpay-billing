<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import AppIcon from '../components/AppIcon.vue'
import AsyncState from '../components/AsyncState.vue'
import StatusBadge from '../components/StatusBadge.vue'
import { api, errorMessage } from '../api'
import { formatDate } from '../format'
import { me } from '../session'
import { notify } from '../toast'
import type { WebhookEvent } from '../types'

const events = ref<WebhookEvent[]>([])
const loading = ref(true)
const refreshing = ref(false)
const loadError = ref<string | null>(null)
const busy = reactive<Record<string, boolean>>({})
let refreshTimer: ReturnType<typeof setTimeout> | null = null

async function load(): Promise<void> {
  refreshing.value = true
  loadError.value = null
  try {
    events.value = await api.listWebhookEvents()
  } catch (err) {
    loadError.value = errorMessage(err)
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

const canReprocess = (e: WebhookEvent) => e.status === 'FAILED' || e.status === 'DEAD'

async function reprocess(e: WebhookEvent): Promise<void> {
  busy[e.id] = true
  try {
    const updated = await api.reprocessWebhookEvent(e.id)
    events.value = events.value.map((x) => (x.id === updated.id ? updated : x))
    notify('info', 'Event queued again', `${updated.eventType} will be processed in the background.`)
    // The inbox processes asynchronously; refresh once shortly after to show the outcome.
    if (refreshTimer) clearTimeout(refreshTimer)
    refreshTimer = setTimeout(() => void load(), 2000)
  } catch (err) {
    notify('error', 'Could not reprocess', errorMessage(err))
  } finally {
    busy[e.id] = false
  }
}

onMounted(load)
onBeforeUnmount(() => {
  if (refreshTimer) clearTimeout(refreshTimer)
})
</script>

<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1>Webhooks</h1>
        <p>Payment events received from the gateway inbox. Failed or dead events can be queued again.</p>
      </div>
      <button
        type="button"
        class="btn"
        data-testid="refresh-webhooks"
        :aria-busy="refreshing"
        :disabled="refreshing"
        @click="load"
      >
        <span v-if="refreshing" class="spinner" aria-hidden="true"></span>
        <AppIcon v-else name="refresh" /> Refresh
      </button>
    </div>

    <div v-if="me && me.role !== 'ADMIN'" class="alert alert-info" role="status">
      <AppIcon name="info" /> Only admins can see the webhook inbox.
    </div>

    <section class="card card-flush" aria-labelledby="events-title">
      <div class="card-header">
        <div>
          <h2 id="events-title">Inbox events</h2>
          <p class="card-sub">Newest first. Duplicated deliveries are stored once.</p>
        </div>
      </div>
      <AsyncState
        :loading="loading"
        :error="loadError"
        :empty="events.length === 0"
        empty-title="No events yet"
        empty-text="Events appear here when the gateway calls the webhook."
        empty-icon="webhooks"
        @retry="load"
      >
        <div class="table-wrap">
          <table class="table table-stack">
            <thead>
              <tr>
                <th scope="col">Event</th>
                <th scope="col">Status</th>
                <th scope="col" class="cell-right">Attempts</th>
                <th scope="col">Received</th>
                <th scope="col" class="cell-right">Action</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="e in events" :key="e.id" data-testid="webhook-row">
                <td class="cell-full">
                  <div class="cell-title">{{ e.eventType }}</div>
                  <div class="mono muted">{{ e.eventId }}</div>
                  <div v-if="e.lastError" class="error-text">{{ e.lastError }}</div>
                </td>
                <td data-label="Status"><StatusBadge :status="e.status" kind="inbox" test-id="webhook-status" /></td>
                <td class="num cell-right" data-label="Attempts">{{ e.attempts }}</td>
                <td class="num muted" data-label="Received">{{ formatDate(e.receivedAt) }}</td>
                <td class="cell-right" data-label="Action">
                  <button
                    v-if="canReprocess(e)"
                    type="button"
                    class="btn"
                    data-testid="webhook-reprocess"
                    :aria-busy="busy[e.id]"
                    :disabled="busy[e.id]"
                    @click="reprocess(e)"
                  >
                    <span v-if="busy[e.id]" class="spinner" aria-hidden="true"></span>
                    <AppIcon v-else name="refresh" /> Reprocess
                  </button>
                  <span v-else class="muted">No action</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </AsyncState>
    </section>
  </div>
</template>

<style scoped>
.error-text {
  margin-top: 4px;
  font-size: var(--text-xs);
  color: var(--danger-fg);
  overflow-wrap: anywhere;
}
</style>
