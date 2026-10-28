<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { RouterLink } from 'vue-router'
import AppIcon from '../components/AppIcon.vue'
import AsyncState from '../components/AsyncState.vue'
import StatusBadge from '../components/StatusBadge.vue'
import { api, errorMessage } from '../api'
import { documentDigits, formatCents, formatDocument, maskDocument } from '../format'
import { notify } from '../toast'
import type { Customer, Plan, Subscription } from '../types'

const customers = ref<Customer[]>([])
const plans = ref<Plan[]>([])
const subscriptions = ref<Subscription[]>([])
const loading = ref(true)
const loadError = ref<string | null>(null)

const form = reactive({ name: '', email: '', document: '' })
const submitting = ref(false)
const formError = ref<string | null>(null)
const documentInvalid = ref(false)

// Per customer row state for the Subscribe disclosure (progressive disclosure: closed by default).
const openRow = ref<string | null>(null)
const selectedPlan = reactive<Record<string, string>>({})
const subscribing = reactive<Record<string, boolean>>({})
const rowMessage = reactive<Record<string, { kind: 'error' | 'success'; text: string }>>({})

const activePlans = computed(() => plans.value.filter((p) => p.active))
const subsByCustomer = computed(() => {
  const map = new Map<string, Subscription[]>()
  for (const s of subscriptions.value) {
    if (s.status === 'CANCELED') continue
    map.set(s.customerId, [...(map.get(s.customerId) ?? []), s])
  }
  return map
})

const documentKind = computed(() => {
  const n = documentDigits(form.document).length
  if (n === 0) return 'CPF (11 digits) or CNPJ (14 digits). Any format is accepted.'
  if (n === 11) return 'Looks like a CPF.'
  if (n === 14) return 'Looks like a CNPJ.'
  return `${n} digits so far.`
})

function onDocumentInput(event: Event): void {
  const input = event.target as HTMLInputElement
  form.document = maskDocument(input.value)
  input.value = form.document
}

async function load(): Promise<void> {
  loading.value = true
  loadError.value = null
  try {
    const [c, p, s] = await Promise.all([api.listCustomers(), api.listPlans(), api.listSubscriptions()])
    customers.value = c
    plans.value = p
    subscriptions.value = s
    for (const customer of c) {
      if (selectedPlan[customer.id] === undefined) selectedPlan[customer.id] = ''
    }
  } catch (err) {
    loadError.value = errorMessage(err)
  } finally {
    loading.value = false
  }
}

async function submit(): Promise<void> {
  formError.value = null
  const digits = documentDigits(form.document)
  documentInvalid.value = digits.length !== 11 && digits.length !== 14
  if (!form.name.trim() || !form.email.trim()) {
    formError.value = 'Name and email are required.'
    return
  }
  if (documentInvalid.value) {
    formError.value = 'Document must be a CPF (11 digits) or CNPJ (14 digits).'
    return
  }
  submitting.value = true
  try {
    const created = await api.createCustomer({ name: form.name.trim(), email: form.email.trim(), document: digits })
    notify('success', 'Customer added', `${created.name} is ready to subscribe.`)
    form.name = ''
    form.email = ''
    form.document = ''
    await load()
  } catch (err) {
    formError.value = errorMessage(err)
  } finally {
    submitting.value = false
  }
}

async function toggleRow(customer: Customer): Promise<void> {
  openRow.value = openRow.value === customer.id ? null : customer.id
  if (openRow.value) {
    delete rowMessage[customer.id]
    await nextTick()
    document.getElementById(`plan-for-${customer.id}`)?.focus()
  }
}

async function subscribe(customer: Customer): Promise<void> {
  const planId = selectedPlan[customer.id]
  delete rowMessage[customer.id]
  if (!planId) {
    rowMessage[customer.id] = { kind: 'error', text: 'Choose a plan first.' }
    return
  }
  subscribing[customer.id] = true
  try {
    const sub = await api.createSubscription(customer.id, planId)
    rowMessage[customer.id] = { kind: 'success', text: `Subscribed to ${sub.planName} (${sub.status}).` }
    selectedPlan[customer.id] = ''
    openRow.value = null
    subscriptions.value = [sub, ...subscriptions.value]
    notify('success', 'Subscription created', `${customer.name} on ${sub.planName}. First charge is pending.`)
  } catch (err) {
    rowMessage[customer.id] = { kind: 'error', text: errorMessage(err) }
  } finally {
    subscribing[customer.id] = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1>Customers</h1>
        <p>People and companies you bill. Each one is also registered in the payment gateway.</p>
      </div>
    </div>

    <div class="grid-2">
      <section class="card" aria-labelledby="new-customer-title">
        <div class="card-header">
          <h2 id="new-customer-title">New customer</h2>
        </div>
        <form class="form" novalidate @submit.prevent="submit">
          <fieldset class="form-section">
            <legend>Contact</legend>
            <div class="field">
              <label for="customer-name">Full name</label>
              <input
                id="customer-name"
                v-model="form.name"
                class="input"
                data-testid="customer-name"
                autocomplete="off"
                required
              />
            </div>
            <div class="field">
              <label for="customer-email">Email</label>
              <input
                id="customer-email"
                v-model="form.email"
                class="input"
                type="email"
                data-testid="customer-email"
                autocomplete="off"
                required
              />
            </div>
          </fieldset>
          <fieldset class="form-section">
            <legend>Tax ID</legend>
            <div class="field">
              <label for="customer-document">CPF or CNPJ</label>
              <input
                id="customer-document"
                :value="form.document"
                class="input num"
                inputmode="numeric"
                placeholder="000.000.000-00"
                data-testid="customer-document"
                aria-describedby="customer-document-hint"
                :aria-invalid="documentInvalid"
                required
                @input="onDocumentInput"
              />
              <span id="customer-document-hint" class="hint">{{ documentKind }}</span>
            </div>
          </fieldset>

          <div v-if="formError" class="alert alert-error" role="alert" data-testid="form-error">
            <AppIcon name="alert" /> {{ formError }}
          </div>

          <button
            type="submit"
            class="btn btn-primary btn-block"
            data-testid="customer-submit"
            :aria-busy="submitting"
            :disabled="submitting"
          >
            <span v-if="submitting" class="spinner" aria-hidden="true"></span>
            <AppIcon v-else name="plus" />
            {{ submitting ? 'Saving...' : 'Create customer' }}
          </button>
        </form>
      </section>

      <section class="card card-flush" aria-labelledby="customers-list-title">
        <div class="card-header">
          <div>
            <h2 id="customers-list-title">All customers</h2>
            <p class="card-sub">{{ customers.length }} {{ customers.length === 1 ? 'customer' : 'customers' }}</p>
          </div>
          <button type="button" class="btn btn-ghost" :aria-busy="loading" :disabled="loading" @click="load">
            <AppIcon name="refresh" /> Refresh
          </button>
        </div>

        <div
          v-if="!loading && !loadError && customers.length > 0 && activePlans.length === 0"
          class="alert alert-info"
          style="margin: 0 24px 16px"
        >
          <AppIcon name="info" />
          <span>Create an active plan before subscribing customers. <RouterLink :to="{ name: 'plans' }">Go to plans</RouterLink></span>
        </div>

        <AsyncState
          :loading="loading && customers.length === 0"
          :error="loadError"
          :empty="customers.length === 0"
          empty-title="No customers yet"
          empty-text="Create your first customer with the form."
          empty-icon="customers"
          @retry="load"
        >
          <div class="table-wrap">
            <table class="table table-stack">
              <thead>
                <tr>
                  <th scope="col">Customer</th>
                  <th scope="col">Document</th>
                  <th scope="col" class="cell-right">Subscription</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="customer in customers" :key="customer.id" data-testid="customer-row">
                  <td class="cell-full">
                    <div class="cell-title">{{ customer.name }}</div>
                    <div class="muted">{{ customer.email }}</div>
                    <div v-if="subsByCustomer.get(customer.id)" class="current-subs">
                      <span v-for="s in subsByCustomer.get(customer.id)" :key="s.id" class="current-sub">
                        {{ s.planName }} <StatusBadge :status="s.status" />
                      </span>
                    </div>
                  </td>
                  <td class="num" data-label="Document">{{ formatDocument(customer.document) }}</td>
                  <td class="cell-full cell-right">
                    <button
                      type="button"
                      class="btn"
                      data-testid="subscribe-open"
                      :aria-expanded="openRow === customer.id"
                      :aria-controls="`subscribe-panel-${customer.id}`"
                      :disabled="activePlans.length === 0"
                      @click="toggleRow(customer)"
                    >
                      <AppIcon :name="openRow === customer.id ? 'close' : 'plus'" />
                      {{ openRow === customer.id ? 'Close' : 'Subscribe' }}
                    </button>

                    <Transition name="fade">
                      <form
                        v-if="openRow === customer.id"
                        :id="`subscribe-panel-${customer.id}`"
                        class="inline-panel"
                        @submit.prevent="subscribe(customer)"
                      >
                        <div class="field">
                          <label :for="`plan-for-${customer.id}`">Plan for {{ customer.name }}</label>
                          <select
                            :id="`plan-for-${customer.id}`"
                            v-model="selectedPlan[customer.id]"
                            class="select"
                            data-testid="subscribe-plan-select"
                          >
                            <option value="" disabled>Select a plan</option>
                            <option v-for="plan in activePlans" :key="plan.id" :value="plan.id">
                              {{ plan.name }} ({{ formatCents(plan.amountCents) }}{{ plan.interval === 'YEARLY' ? '/yr' : '/mo' }})
                            </option>
                          </select>
                          <span class="hint">The first charge is created now and is due in 3 days.</span>
                        </div>
                        <button
                          type="submit"
                          class="btn btn-primary"
                          data-testid="subscribe-submit"
                          :aria-busy="subscribing[customer.id]"
                          :disabled="subscribing[customer.id]"
                        >
                          <span v-if="subscribing[customer.id]" class="spinner" aria-hidden="true"></span>
                          Confirm subscription
                        </button>
                      </form>
                    </Transition>

                    <p
                      v-if="rowMessage[customer.id]"
                      :class="['alert', 'row-msg', rowMessage[customer.id]!.kind === 'error' ? 'alert-error' : 'alert-success']"
                      :role="rowMessage[customer.id]!.kind === 'error' ? 'alert' : 'status'"
                      data-testid="subscribe-message"
                    >
                      <span>
                        {{ rowMessage[customer.id]!.text }}
                        <RouterLink v-if="rowMessage[customer.id]!.kind === 'success'" :to="{ name: 'subscriptions' }">
                          View subscriptions
                        </RouterLink>
                      </span>
                    </p>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </AsyncState>
      </section>
    </div>
  </div>
</template>

<style scoped>
.current-subs {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-top: var(--space-2);
}

.current-sub {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: var(--text-xs);
  color: var(--text-muted);
}

.row-msg {
  margin: var(--space-3) 0 0;
  text-align: left;
}

@media (max-width: 767px) {
  td.cell-right .btn {
    width: 100%;
  }
}
</style>
