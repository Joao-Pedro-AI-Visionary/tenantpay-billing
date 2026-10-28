<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import AppIcon from '../components/AppIcon.vue'
import AsyncState from '../components/AsyncState.vue'
import StatusBadge from '../components/StatusBadge.vue'
import { api, errorMessage } from '../api'
import { formatCents, formatDate, formatDay, relativeDue } from '../format'
import { notify } from '../toast'
import type { Subscription, SubscriptionStatus } from '../types'

const POLL_INTERVAL_MS = 1000
const POLL_MAX_MS = 15000

const subscriptions = ref<Subscription[]>([])
const loading = ref(true)
const refreshing = ref(false)
const loadError = ref<string | null>(null)
const devTools = ref(false)
const notice = ref<{ kind: 'error' | 'info'; text: string } | null>(null)
/** Peak-end: a celebration card when a subscription becomes ACTIVE. */
const celebration = ref<{ customer: string; plan: string; amount: number } | null>(null)

const busy = reactive<Record<string, boolean>>({})
const confirmingCancel = reactive<Record<string, boolean>>({})

type Filter = 'ALL' | SubscriptionStatus
const filter = ref<Filter>('ALL')
const filters: { value: Filter; label: string }[] = [
  { value: 'ALL', label: 'All' },
  { value: 'ACTIVE', label: 'Active' },
  { value: 'PENDING', label: 'Pending' },
  { value: 'OVERDUE', label: 'Overdue' },
  { value: 'CANCELED', label: 'Canceled' },
]
const countFor = (f: Filter) =>
  f === 'ALL' ? subscriptions.value.length : subscriptions.value.filter((s) => s.status === f).length
const visible = computed(() =>
  filter.value === 'ALL' ? subscriptions.value : subscriptions.value.filter((s) => s.status === filter.value),
)

let pollTimer: ReturnType<typeof setTimeout> | null = null

async function load(): Promise<void> {
  refreshing.value = true
  loadError.value = null
  const before = new Map(subscriptions.value.map((s) => [s.id, s.status]))
  try {
    const next = await api.listSubscriptions()
    // Any subscription that just turned ACTIVE (simulated payment or a real webhook) is celebrated.
    const activated = next.find((s) => s.status === 'ACTIVE' && before.has(s.id) && before.get(s.id) !== 'ACTIVE')
    subscriptions.value = next
    if (activated) celebrate(activated)
  } catch (err) {
    loadError.value = errorMessage(err)
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

function celebrate(sub: Subscription): void {
  celebration.value = { customer: sub.customerName, plan: sub.planName, amount: sub.amountCents }
  notice.value = null
}

async function loadDevConfig(): Promise<void> {
  try {
    devTools.value = (await api.devConfig()).devTools === true
  } catch {
    // Dev tools are optional; hide the button when the config is unavailable.
    devTools.value = false
  }
}

function stopPolling(): void {
  if (pollTimer) clearTimeout(pollTimer)
  pollTimer = null
}

/** Webhook processing is asynchronous, so poll the list until the subscription leaves PENDING. */
function pollUntilActive(subscriptionId: string): void {
  stopPolling()
  const startedAt = Date.now()
  const tick = async (): Promise<void> => {
    await load()
    const sub = subscriptions.value.find((s) => s.id === subscriptionId)
    if (sub && sub.status === 'ACTIVE') {
      stopPolling()
      return
    }
    if (Date.now() - startedAt >= POLL_MAX_MS) {
      notice.value = { kind: 'info', text: 'The webhook is still being processed. Use Refresh in a few seconds.' }
      stopPolling()
      return
    }
    pollTimer = setTimeout(() => void tick(), POLL_INTERVAL_MS)
  }
  pollTimer = setTimeout(() => void tick(), POLL_INTERVAL_MS)
}

async function simulate(sub: Subscription): Promise<void> {
  busy[sub.id] = true
  notice.value = null
  celebration.value = null
  try {
    const ack = await api.simulatePayment(sub.id)
    notice.value = { kind: 'info', text: `Webhook ${ack.eventId} accepted. Waiting for processing...` }
    pollUntilActive(sub.id)
  } catch (err) {
    notice.value = { kind: 'error', text: errorMessage(err) }
  } finally {
    busy[sub.id] = false
  }
}

async function cancel(sub: Subscription): Promise<void> {
  busy[sub.id] = true
  notice.value = null
  try {
    const updated = await api.cancelSubscription(sub.id)
    notify('info', 'Subscription canceled', `${updated.customerName} will not be charged again.`)
    await load()
  } catch (err) {
    notice.value = { kind: 'error', text: errorMessage(err) }
  } finally {
    busy[sub.id] = false
    confirmingCancel[sub.id] = false
  }
}

onMounted(() => {
  void load()
  void loadDevConfig()
})
onBeforeUnmount(stopPolling)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1>Subscriptions</h1>
        <p>Status changes when the payment gateway sends a webhook.</p>
      </div>
      <button
        type="button"
        class="btn"
        data-testid="refresh-subscriptions"
        :aria-busy="refreshing"
        :disabled="refreshing"
        @click="load"
      >
        <span v-if="refreshing" class="spinner" aria-hidden="true"></span>
        <AppIcon v-else name="refresh" />
        Refresh
      </button>
    </div>

    <div aria-live="polite" class="notice-region">
      <Transition name="pop">
        <div v-if="celebration" class="celebrate" data-testid="subscriptions-notice">
          <span class="celebrate-icon" aria-hidden="true"><AppIcon name="sparkles" :size="26" /></span>
          <div class="celebrate-body" data-testid="celebration">
            <p class="celebrate-title">Payment confirmed</p>
            <p>
              {{ celebration.customer }} is now ACTIVE on {{ celebration.plan }}.
              {{ formatCents(celebration.amount) }} added to your recurring revenue.
            </p>
          </div>
          <button type="button" class="btn btn-ghost btn-icon" aria-label="Dismiss" @click="celebration = null">
            <AppIcon name="close" />
          </button>
        </div>
        <div v-else-if="notice" :class="['alert', `alert-${notice.kind}`]" data-testid="subscriptions-notice">
          <span v-if="notice.kind === 'info'" class="spinner" aria-hidden="true" style="margin-top: 2px"></span>
          <AppIcon v-else name="alert" />
          {{ notice.text }}
        </div>
      </Transition>
    </div>

    <section class="card card-flush" aria-labelledby="subs-title">
      <div class="card-header">
        <div>
          <h2 id="subs-title">All subscriptions</h2>
          <p class="card-sub">Next charge date comes from the latest charge.</p>
        </div>
        <span v-if="devTools" class="pill tone-info" title="Dev tools are enabled on the API">
          <AppIcon name="bolt" :size="14" /> Dev tools on
        </span>
      </div>

      <div class="filters" role="group" aria-label="Filter by status">
        <button
          v-for="f in filters"
          :key="f.value"
          type="button"
          :class="['filter', { selected: filter === f.value }]"
          :aria-pressed="filter === f.value"
          :data-testid="`filter-${f.value}`"
          @click="filter = f.value"
        >
          {{ f.label }} <span class="filter-count num">{{ countFor(f.value) }}</span>
        </button>
      </div>

      <AsyncState
        :loading="loading"
        :error="loadError"
        :empty="visible.length === 0"
        :empty-title="subscriptions.length === 0 ? 'No subscriptions yet' : 'Nothing in this filter'"
        :empty-text="subscriptions.length === 0 ? 'Open Customers and subscribe a customer to a plan.' : 'Choose another status above.'"
        empty-icon="subscriptions"
        @retry="load"
      >
        <div class="table-wrap">
          <table class="table table-stack">
            <thead>
              <tr>
                <th scope="col">Customer</th>
                <th scope="col">Plan</th>
                <th scope="col">Status</th>
                <th scope="col">Next charge</th>
                <th scope="col" class="cell-right">Actions</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="sub in visible" :key="sub.id" data-testid="subscription-row" :data-subscription-id="sub.id">
                <td class="cell-full">
                  <div class="cell-title">{{ sub.customerName }}</div>
                  <div class="muted">Since {{ formatDay(sub.createdAt) }}</div>
                </td>
                <td data-label="Plan">
                  <div>
                    <div>{{ sub.planName }}</div>
                    <div class="num muted">{{ formatCents(sub.amountCents) }}</div>
                  </div>
                </td>
                <td data-label="Status"><StatusBadge :status="sub.status" test-id="subscription-status" /></td>
                <td data-label="Next charge">
                  <div v-if="sub.latestCharge" class="charge">
                    <div class="num cell-title">{{ formatDate(sub.latestCharge.dueDate) }}</div>
                    <div v-if="sub.latestCharge.status === 'PENDING' || sub.latestCharge.status === 'OVERDUE'" class="muted">
                      {{ relativeDue(sub.latestCharge.dueDate) }}
                    </div>
                    <div class="charge-line">
                      <span class="num muted">{{ formatCents(sub.latestCharge.amountCents) }}</span>
                      <StatusBadge :status="sub.latestCharge.status" test-id="charge-status" />
                    </div>
                    <div class="mono muted" data-testid="charge-gateway-id">{{ sub.latestCharge.gatewayPaymentId }}</div>
                  </div>
                  <span v-else class="muted">No charge yet</span>
                </td>
                <td class="cell-full">
                  <div class="row-actions">
                    <button
                      v-if="devTools && sub.status !== 'CANCELED' && sub.status !== 'ACTIVE'"
                      type="button"
                      class="btn"
                      data-testid="simulate-payment"
                      :aria-busy="busy[sub.id]"
                      :disabled="busy[sub.id]"
                      @click="simulate(sub)"
                    >
                      <span v-if="busy[sub.id]" class="spinner" aria-hidden="true"></span>
                      <AppIcon v-else name="bolt" />
                      Simulate payment
                    </button>
                    <template v-if="sub.status !== 'CANCELED'">
                      <button
                        v-if="!confirmingCancel[sub.id]"
                        type="button"
                        class="btn btn-danger"
                        data-testid="cancel-subscription"
                        :disabled="busy[sub.id]"
                        @click="confirmingCancel[sub.id] = true"
                      >
                        Cancel
                      </button>
                      <template v-else>
                        <button
                          type="button"
                          class="btn btn-danger-solid"
                          data-testid="confirm-cancel-subscription"
                          :aria-busy="busy[sub.id]"
                          :disabled="busy[sub.id]"
                          @click="cancel(sub)"
                        >
                          Confirm cancel
                        </button>
                        <button type="button" class="btn" :disabled="busy[sub.id]" @click="confirmingCancel[sub.id] = false">
                          Keep
                        </button>
                      </template>
                    </template>
                    <span v-else class="muted">Canceled {{ formatDay(sub.canceledAt) }}</span>
                  </div>
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
.notice-region:empty {
  display: none;
}

.celebrate {
  display: flex;
  align-items: flex-start;
  gap: var(--space-4);
  padding: var(--space-5);
  background: var(--success-bg);
  border: 1px solid var(--success-border);
  border-radius: var(--radius-lg);
  color: var(--success-fg);
}

.celebrate-icon {
  display: grid;
  place-items: center;
  flex: none;
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: var(--surface);
  border: 1px solid var(--success-border);
  animation: pop-in var(--motion-slow) var(--ease-out);
}

.celebrate-body {
  flex: 1;
  min-width: 0;
}

.celebrate-title {
  font-size: var(--text-lg);
  font-weight: 700;
}

@keyframes pop-in {
  from {
    transform: scale(0.6) rotate(-12deg);
    opacity: 0;
  }
  to {
    transform: none;
    opacity: 1;
  }
}

@media (prefers-reduced-motion: reduce) {
  .celebrate-icon {
    animation: none;
  }
}

.pop-enter-active,
.pop-leave-active {
  transition:
    opacity var(--motion-base) var(--ease-out),
    transform var(--motion-base) var(--ease-out);
}

.pop-enter-from,
.pop-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}

.filters {
  display: flex;
  gap: var(--space-2);
  padding: 0 var(--space-6) var(--space-4);
  overflow-x: auto;
}

.filter {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  min-height: var(--target-min);
  padding: 0 var(--space-3);
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-pill);
  font-size: var(--text-sm);
  font-weight: 500;
  color: var(--text-muted);
  cursor: pointer;
  white-space: nowrap;
}

.filter:hover {
  background: var(--surface-hover);
}

.filter.selected {
  background: var(--text);
  border-color: var(--text);
  color: var(--text-inverse);
}

.filter-count {
  font-size: var(--text-xs);
  padding: 0 6px;
  border-radius: var(--radius-pill);
  background: var(--neutral-bg);
  color: var(--text-muted);
}

.filter.selected .filter-count {
  background: rgb(255 255 255 / 0.2);
  color: var(--text-inverse);
}

.charge {
  display: grid;
  gap: 4px;
}

.charge-line {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

@media (max-width: 767px) {
  .charge {
    justify-items: end;
  }
  .row-actions {
    justify-content: stretch;
    margin-top: var(--space-2);
  }
  .row-actions .btn {
    flex: 1;
  }
}
</style>
