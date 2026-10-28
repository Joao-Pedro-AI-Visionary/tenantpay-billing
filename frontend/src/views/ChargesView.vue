<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import AppIcon from '../components/AppIcon.vue'
import AsyncState from '../components/AsyncState.vue'
import StatusBadge from '../components/StatusBadge.vue'
import { api, errorMessage } from '../api'
import { daysUntil, formatCents, formatDate, formatDay, relativeDue } from '../format'
import type { Charge, Customer, Subscription } from '../types'

const charges = ref<Charge[]>([])
const customers = ref<Customer[]>([])
const subscriptions = ref<Subscription[]>([])
const loading = ref(true)
const loadError = ref<string | null>(null)

async function load(): Promise<void> {
  loading.value = true
  loadError.value = null
  try {
    const [ch, cu, su] = await Promise.all([api.listCharges(), api.listCustomers(), api.listSubscriptions()])
    charges.value = ch
    customers.value = cu
    subscriptions.value = su
  } catch (err) {
    loadError.value = errorMessage(err)
  } finally {
    loading.value = false
  }
}

const customerName = computed(() => new Map(customers.value.map((c) => [c.id, c.name])))
const planName = computed(() => new Map(subscriptions.value.map((s) => [s.id, s.planName])))

const sum = (pred: (c: Charge) => boolean) => charges.value.filter(pred).reduce((acc, c) => acc + c.amountCents, 0)
const paidCents = computed(() => sum((c) => c.status === 'CONFIRMED' || c.status === 'RECEIVED'))
const openCents = computed(() => sum((c) => c.status === 'PENDING'))
const overdueCents = computed(() => sum((c) => c.status === 'OVERDUE'))

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1>Charges</h1>
        <p>Every invoice issued to your customers through the payment gateway.</p>
      </div>
      <button type="button" class="btn" data-testid="refresh-charges" :aria-busy="loading" :disabled="loading" @click="load">
        <span v-if="loading" class="spinner" aria-hidden="true"></span>
        <AppIcon v-else name="refresh" /> Refresh
      </button>
    </div>

    <section class="summary" aria-label="Charge totals">
      <div class="card summary-item">
        <p class="summary-label"><AppIcon name="check-circle" :size="16" /> Paid</p>
        <p class="summary-value num" data-testid="charges-paid">{{ formatCents(paidCents) }}</p>
      </div>
      <div class="card summary-item">
        <p class="summary-label"><AppIcon name="clock" :size="16" /> Open</p>
        <p class="summary-value num" data-testid="charges-open">{{ formatCents(openCents) }}</p>
      </div>
      <div class="card summary-item">
        <p class="summary-label"><AppIcon name="alert" :size="16" /> Overdue</p>
        <p :class="['summary-value', 'num', { danger: overdueCents > 0 }]" data-testid="charges-overdue">
          {{ formatCents(overdueCents) }}
        </p>
      </div>
    </section>

    <section class="card card-flush" aria-labelledby="charges-title">
      <div class="card-header">
        <div>
          <h2 id="charges-title">Invoices</h2>
          <p class="card-sub">{{ charges.length }} {{ charges.length === 1 ? 'charge' : 'charges' }}, newest first</p>
        </div>
      </div>
      <AsyncState
        :loading="loading && charges.length === 0"
        :error="loadError"
        :empty="charges.length === 0"
        empty-title="No charges yet"
        empty-text="A charge is created for every new subscription."
        empty-icon="charges"
        @retry="load"
      >
        <div class="table-wrap">
          <table class="table table-stack">
            <thead>
              <tr>
                <th scope="col">Customer</th>
                <th scope="col" class="cell-right">Amount</th>
                <th scope="col">Status</th>
                <th scope="col">Due date</th>
                <th scope="col">Paid at</th>
                <th scope="col">Gateway id</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="charge in charges" :key="charge.id" data-testid="charge-row">
                <td class="cell-full">
                  <div class="cell-title">{{ customerName.get(charge.customerId) ?? 'Customer' }}</div>
                  <div class="muted">{{ planName.get(charge.subscriptionId) ?? 'Subscription' }}</div>
                </td>
                <td class="num cell-right cell-title" data-label="Amount">{{ formatCents(charge.amountCents) }}</td>
                <td data-label="Status"><StatusBadge :status="charge.status" /></td>
                <td data-label="Due date">
                  <div class="num">{{ formatDate(charge.dueDate) }}</div>
                  <div
                    v-if="charge.status === 'PENDING' || charge.status === 'OVERDUE'"
                    :class="['muted', { late: daysUntil(charge.dueDate) < 0 }]"
                  >
                    {{ relativeDue(charge.dueDate) }}
                  </div>
                </td>
                <td class="num muted" data-label="Paid at">{{ formatDay(charge.paidAt) }}</td>
                <td class="mono muted" data-label="Gateway id">{{ charge.gatewayPaymentId }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </AsyncState>
    </section>
  </div>
</template>

<style scoped>
.summary {
  display: grid;
  gap: var(--space-4);
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
}

.summary-item {
  padding: var(--space-5);
}

.summary-label {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  color: var(--text-muted);
  font-size: var(--text-sm);
  font-weight: 500;
}

.summary-value {
  margin-top: var(--space-2);
  font-size: var(--text-2xl);
  font-weight: 700;
}

.danger,
.late {
  color: var(--danger-fg);
}
</style>
