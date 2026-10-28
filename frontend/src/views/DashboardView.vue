<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import AppIcon from '../components/AppIcon.vue'
import StatusBadge from '../components/StatusBadge.vue'
import { api, errorMessage } from '../api'
import { daysUntil, formatCents, formatDate, monthlyCents, monthShort, relativeDue } from '../format'
import { me } from '../session'
import type { Dashboard, Plan, Subscription, SubscriptionStatus } from '../types'

const data = ref<Dashboard | null>(null)
const subscriptions = ref<Subscription[]>([])
const plans = ref<Plan[]>([])
const loading = ref(true)
const error = ref<string | null>(null)
const statuses: SubscriptionStatus[] = ['ACTIVE', 'PENDING', 'OVERDUE', 'CANCELED']

async function load(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    const [d, s, p] = await Promise.all([api.dashboard(), api.listSubscriptions(), api.listPlans()])
    data.value = d
    subscriptions.value = s
    plans.value = p
  } catch (err) {
    error.value = errorMessage(err)
  } finally {
    loading.value = false
  }
}

/** MRR: ACTIVE subscriptions only, yearly plans normalized to 1/12 per month. */
const mrrCents = computed(() => {
  const intervalByPlan = new Map(plans.value.map((p) => [p.id, p.interval]))
  return subscriptions.value
    .filter((s) => s.status === 'ACTIVE')
    .reduce((acc, s) => acc + monthlyCents(s.amountCents, intervalByPlan.get(s.planId)), 0)
})

const count = (s: SubscriptionStatus) => data.value?.subscriptionsByStatus[s] ?? 0
const overdueCents = computed(() =>
  subscriptions.value
    .filter((s) => s.status === 'OVERDUE')
    .reduce((acc, s) => acc + (s.latestCharge?.amountCents ?? s.amountCents), 0),
)

/** Next charge per open subscription (latestCharge.dueDate), soonest first. */
const upcoming = computed(() =>
  subscriptions.value
    .filter(
      (s) =>
        s.status !== 'CANCELED' &&
        s.latestCharge &&
        (s.latestCharge.status === 'PENDING' || s.latestCharge.status === 'OVERDUE'),
    )
    .sort((a, b) => a.latestCharge!.dueDate.localeCompare(b.latestCharge!.dueDate))
    .slice(0, 5),
)

// ---------- onboarding checklist (Zeigarnik + goal gradient) ----------
const steps = computed(() => {
  const d = data.value
  return [
    { key: 'plan', label: 'Create a plan', hint: 'Set a price and interval.', done: (d?.plans ?? 0) > 0, to: 'plans' },
    { key: 'customer', label: 'Add a customer', hint: 'CPF or CNPJ, any format.', done: (d?.customers ?? 0) > 0, to: 'customers' },
    { key: 'subscribe', label: 'Subscribe the customer', hint: 'Pick a plan on the customer row.', done: (d?.subscriptions ?? 0) > 0, to: 'customers' },
    {
      key: 'payment',
      label: 'Receive the first payment',
      hint: 'Confirm a payment to activate the subscription.',
      done: (d?.receivedAmountCents ?? 0) > 0 || count('ACTIVE') > 0,
      to: 'subscriptions',
    },
  ]
})
const doneCount = computed(() => steps.value.filter((s) => s.done).length)
const nextStep = computed(() => steps.value.find((s) => !s.done) ?? null)
const progressPct = computed(() => Math.round((doneCount.value / steps.value.length) * 100))

const statusTotal = computed(() => statuses.reduce((acc, s) => acc + count(s), 0))
const barColor: Record<SubscriptionStatus, string> = {
  ACTIVE: 'var(--success-fg)',
  PENDING: '#dc6803',
  OVERDUE: 'var(--danger-fg)',
  CANCELED: '#98a2b3',
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1>Dashboard</h1>
        <p>Billing overview for {{ me?.tenantName ?? 'your tenant' }}.</p>
      </div>
      <div class="page-actions">
        <button
          type="button"
          class="btn"
          data-testid="refresh-dashboard"
          :aria-busy="loading"
          :disabled="loading"
          @click="load"
        >
          <span v-if="loading" class="spinner" aria-hidden="true"></span>
          <AppIcon v-else name="refresh" /> Refresh
        </button>
        <RouterLink :to="{ name: nextStep?.to ?? 'customers' }" class="btn btn-primary">
          <AppIcon name="plus" /> {{ nextStep ? nextStep.label : 'New subscription' }}
        </RouterLink>
      </div>
    </div>

    <div v-if="error" class="alert alert-error" role="alert">
      <AppIcon name="alert" />
      <div>
        <p>{{ error }}</p>
        <button type="button" class="btn" style="margin-top: 8px" @click="load">Try again</button>
      </div>
    </div>

    <!-- KPI cards -->
    <section aria-label="Key metrics" class="kpis" :aria-busy="loading && !data">
      <template v-if="loading && !data">
        <div v-for="n in 4" :key="n" class="card kpi" aria-hidden="true">
          <span class="skeleton" style="width: 40%; height: 12px"></span>
          <span class="skeleton" style="width: 60%; height: 28px; margin-top: 12px"></span>
          <span class="skeleton" style="width: 50%; height: 10px; margin-top: 12px"></span>
        </div>
      </template>
      <template v-else-if="data">
        <div class="card kpi">
          <p class="kpi-label"><AppIcon name="trend" :size="16" /> Monthly recurring revenue</p>
          <p class="kpi-value num" data-testid="kpi-mrr">{{ formatCents(mrrCents) }}</p>
          <p class="kpi-foot">From active subscriptions, yearly plans / 12</p>
        </div>
        <div class="card kpi">
          <p class="kpi-label"><AppIcon name="check-circle" :size="16" /> Active subscriptions</p>
          <p class="kpi-value num" data-testid="dashboard-active">{{ count('ACTIVE') }}</p>
          <p class="kpi-foot">{{ count('PENDING') }} pending activation</p>
        </div>
        <div class="card kpi">
          <p class="kpi-label"><AppIcon name="alert" :size="16" /> Overdue</p>
          <p :class="['kpi-value', 'num', { 'kpi-danger': count('OVERDUE') > 0 }]" data-testid="kpi-overdue">
            {{ count('OVERDUE') }}
          </p>
          <p class="kpi-foot">{{ formatCents(overdueCents) }} at risk</p>
        </div>
        <div class="card kpi">
          <p class="kpi-label"><AppIcon name="wallet" :size="16" /> Received amount</p>
          <p class="kpi-value num" data-testid="dashboard-received">{{ formatCents(data.receivedAmountCents) }}</p>
          <p class="kpi-foot">Confirmed and received charges</p>
        </div>
      </template>
    </section>

    <div class="dash-grid">
      <!-- Onboarding checklist -->
      <section class="card onboarding" aria-labelledby="onboarding-title" data-testid="onboarding-checklist">
        <div class="card-header" style="margin-bottom: 12px">
          <div>
            <h2 id="onboarding-title">{{ doneCount === steps.length ? 'Setup complete' : 'Get started' }}</h2>
            <p class="card-sub" data-testid="onboarding-progress">{{ doneCount }} of {{ steps.length }} steps done</p>
          </div>
          <span v-if="doneCount === steps.length" class="pill tone-success"><AppIcon name="check" :size="14" /> All set</span>
        </div>
        <div
          class="progress"
          role="progressbar"
          aria-label="Setup progress"
          :aria-valuenow="progressPct"
          aria-valuemin="0"
          aria-valuemax="100"
        >
          <span class="progress-fill" :style="{ width: `${progressPct}%` }"></span>
        </div>
        <ol class="steps">
          <li v-for="(step, i) in steps" :key="step.key" :class="['step', { done: step.done, next: nextStep?.key === step.key }]">
            <span class="step-mark" aria-hidden="true">
              <AppIcon v-if="step.done" name="check" :size="14" />
              <template v-else>{{ i + 1 }}</template>
            </span>
            <span class="step-body">
              <span class="step-label">
                {{ step.label }}<span class="sr-only">{{ step.done ? ' (done)' : ' (to do)' }}</span>
              </span>
              <span class="step-hint">{{ step.hint }}</span>
            </span>
            <RouterLink v-if="!step.done" :to="{ name: step.to }" class="btn step-go" :aria-label="`${step.label}: go`">
              <AppIcon name="chevron-right" />
            </RouterLink>
          </li>
        </ol>
      </section>

      <!-- Next charges (subscription detail with next charge date) -->
      <section class="card card-flush" aria-labelledby="upcoming-title">
        <div class="card-header">
          <div>
            <h2 id="upcoming-title">Next charges</h2>
            <p class="card-sub">Open charges by due date</p>
          </div>
          <RouterLink :to="{ name: 'charges' }" class="btn btn-ghost">View all</RouterLink>
        </div>
        <div v-if="loading && !data" class="pad" aria-hidden="true">
          <span v-for="n in 3" :key="n" class="skeleton" style="height: 40px; margin-bottom: 10px"></span>
        </div>
        <ul v-else-if="upcoming.length > 0" class="upcoming" data-testid="upcoming-charges">
          <li v-for="s in upcoming" :key="s.id" class="upcoming-item">
            <span class="date-chip" aria-hidden="true">
              <span class="date-day">{{ s.latestCharge!.dueDate.slice(8, 10) }}</span>
              <span class="date-mon">{{ monthShort(s.latestCharge!.dueDate) }}</span>
            </span>
            <span class="upcoming-body">
              <span class="cell-title">{{ s.customerName }}</span>
              <span class="muted">
                {{ s.planName }} · Next charge {{ formatDate(s.latestCharge!.dueDate) }}
                ({{ relativeDue(s.latestCharge!.dueDate) }})
              </span>
            </span>
            <span class="upcoming-right">
              <span class="num cell-title">{{ formatCents(s.latestCharge!.amountCents) }}</span>
              <StatusBadge :status="daysUntil(s.latestCharge!.dueDate) < 0 ? 'OVERDUE' : s.latestCharge!.status" />
            </span>
          </li>
        </ul>
        <div v-else class="empty">
          <span class="empty-icon"><AppIcon name="calendar" :size="22" /></span>
          <strong>No open charges</strong>
          <span>New subscriptions create their first charge automatically.</span>
        </div>
      </section>

      <!-- Status breakdown -->
      <section class="card status-card" aria-labelledby="by-status-title">
        <div class="card-header">
          <div>
            <h2 id="by-status-title">Subscriptions by status</h2>
            <p class="card-sub">{{ statusTotal }} in total</p>
          </div>
        </div>
        <div v-if="statusTotal > 0" class="stack-bar" aria-hidden="true">
          <span
            v-for="s in statuses"
            v-show="count(s) > 0"
            :key="s"
            :style="{ flexGrow: count(s), background: barColor[s] }"
          ></span>
        </div>
        <ul class="status-list">
          <li v-for="s in statuses" :key="s" :data-testid="`dashboard-status-${s}`">
            <StatusBadge :status="s" />
            <span class="num cell-title">{{ count(s) }}</span>
          </li>
        </ul>

        <h3 class="workspace-title">Workspace</h3>
        <dl class="workspace">
          <div>
            <dt>Plans</dt>
            <dd class="num" data-testid="dashboard-plans">{{ data?.plans ?? 0 }}</dd>
          </div>
          <div>
            <dt>Customers</dt>
            <dd class="num" data-testid="dashboard-customers">{{ data?.customers ?? 0 }}</dd>
          </div>
          <div>
            <dt>Subscriptions</dt>
            <dd class="num" data-testid="dashboard-subscriptions">{{ data?.subscriptions ?? 0 }}</dd>
          </div>
        </dl>
      </section>
    </div>
  </div>
</template>

<style scoped>
.kpis {
  display: grid;
  gap: var(--space-4);
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
}

.kpi {
  display: grid;
  align-content: start;
  padding: var(--space-5);
}

.kpi-label {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  color: var(--text-muted);
  font-size: var(--text-sm);
  font-weight: 500;
}

.kpi-value {
  margin-top: var(--space-2);
  font-size: var(--text-3xl);
  font-weight: 700;
  letter-spacing: -0.02em;
  line-height: 1.2;
}

.kpi-danger {
  color: var(--danger-fg);
}

.kpi-foot {
  margin-top: var(--space-2);
  color: var(--text-muted);
  font-size: var(--text-xs);
}

.dash-grid {
  display: grid;
  gap: var(--space-6);
  align-items: start;
}

@media (min-width: 1100px) {
  .dash-grid {
    grid-template-columns: minmax(0, 1fr) minmax(0, 1.4fr);
  }
  .status-card {
    grid-column: 1 / 2;
  }
  .card-flush {
    grid-row: span 2;
  }
}

.pad {
  padding: var(--space-4) var(--space-6);
  display: grid;
}

.progress {
  height: 8px;
  border-radius: var(--radius-pill);
  background: var(--neutral-bg);
  overflow: hidden;
}

.progress-fill {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: var(--success-fg);
  transition: width var(--motion-slow) var(--ease-out);
}

.steps {
  list-style: none;
  margin: var(--space-4) 0 0;
  padding: 0;
  display: grid;
  gap: var(--space-1);
}

.step {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  min-height: 52px;
  padding: var(--space-2) var(--space-2) var(--space-2) var(--space-1);
  border-radius: var(--radius-sm);
}

.step.next {
  background: var(--surface-subtle);
}

.step-mark {
  display: grid;
  place-items: center;
  flex: none;
  width: 26px;
  height: 26px;
  border-radius: 50%;
  border: 1.5px solid var(--border-strong);
  font-size: var(--text-xs);
  font-weight: 700;
  color: var(--text-muted);
}

.step.done .step-mark {
  background: var(--success-fg);
  border-color: var(--success-fg);
  color: var(--text-inverse);
}

.step-body {
  flex: 1;
  display: grid;
  min-width: 0;
}

.step-label {
  font-weight: 600;
  font-size: var(--text-sm);
}

.step.done .step-label {
  color: var(--text-muted);
  text-decoration: line-through;
  text-decoration-color: var(--border-strong);
}

.step-hint {
  font-size: var(--text-xs);
  color: var(--text-muted);
}

.step-go {
  width: var(--target-min);
  padding: 0;
}

.upcoming {
  list-style: none;
  margin: 0;
  padding: 0;
}

.upcoming-item {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  padding: var(--space-3) var(--space-6);
  border-top: 1px solid var(--border);
}

.date-chip {
  display: grid;
  place-items: center;
  flex: none;
  width: 48px;
  height: 48px;
  border-radius: var(--radius-sm);
  background: var(--surface-subtle);
  border: 1px solid var(--border);
  line-height: 1.1;
}

.date-day {
  font-weight: 700;
}

.date-mon {
  font-size: var(--text-xs);
  color: var(--text-muted);
}

.upcoming-body {
  flex: 1;
  display: grid;
  min-width: 0;
}

.upcoming-right {
  display: grid;
  justify-items: end;
  gap: 4px;
}

@media (max-width: 479px) {
  .upcoming-item {
    flex-wrap: wrap;
    padding: var(--space-3) var(--space-4);
  }
  .upcoming-right {
    width: 100%;
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding-left: 64px;
  }
}

.stack-bar {
  display: flex;
  gap: 3px;
  height: 10px;
  margin-bottom: var(--space-4);
  border-radius: var(--radius-pill);
  overflow: hidden;
}

.stack-bar span {
  flex-basis: 0;
  min-width: 6px;
}

.status-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: var(--space-2);
}

.status-list li {
  display: flex;
  justify-content: space-between;
  align-items: center;
  min-height: 32px;
}

.workspace-title {
  margin-top: var(--space-5);
  padding-top: var(--space-4);
  border-top: 1px solid var(--border);
  font-size: var(--text-xs);
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: var(--text-muted);
}

.workspace {
  margin: var(--space-3) 0 0;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--space-3);
}

.workspace div {
  padding: var(--space-3);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--surface-subtle);
}

.workspace dt {
  font-size: var(--text-xs);
  color: var(--text-muted);
}

.workspace dd {
  margin: 0;
  font-size: var(--text-xl);
  font-weight: 700;
}
</style>
