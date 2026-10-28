<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import AppIcon from '../components/AppIcon.vue'
import AsyncState from '../components/AsyncState.vue'
import { api, errorMessage } from '../api'
import { centsToReaisInput, formatCents, formatDay, monthlyCents, reaisToCents } from '../format'
import { notify } from '../toast'
import type { Interval, Plan } from '../types'

const plans = ref<Plan[]>([])
const loading = ref(true)
const loadError = ref<string | null>(null)

const form = reactive({ name: '', description: '', amount: '', interval: 'MONTHLY' as Interval })
const submitting = ref(false)
const formError = ref<string | null>(null)
const amountInvalid = ref(false)
const nameInvalid = ref(false)

/** Live preview of the parsed amount (Tesler: the panel does the currency work, not the user). */
const parsedCents = computed(() => reaisToCents(form.amount))
const amountPreview = computed(() => {
  const cents = parsedCents.value
  if (cents === null) return null
  const per = form.interval === 'YEARLY' ? 'per year' : 'per month'
  const extra = form.interval === 'YEARLY' ? ` (${formatCents(monthlyCents(cents, 'YEARLY'))} MRR)` : ''
  return `${formatCents(cents)} ${per}${extra}`
})

/** Postel: accept "49", "49,9", "49.90", "R$ 1.234,56" and normalize to "49,00" style on blur. */
function normalizeAmount(): void {
  const cents = reaisToCents(form.amount)
  if (cents !== null) form.amount = centsToReaisInput(cents)
}

async function load(): Promise<void> {
  loading.value = true
  loadError.value = null
  try {
    plans.value = await api.listPlans()
  } catch (err) {
    loadError.value = errorMessage(err)
  } finally {
    loading.value = false
  }
}

async function submit(): Promise<void> {
  formError.value = null
  nameInvalid.value = form.name.trim().length === 0
  const cents = reaisToCents(form.amount)
  amountInvalid.value = cents === null || cents < 100
  if (nameInvalid.value) {
    formError.value = 'Give the plan a name.'
    return
  }
  if (amountInvalid.value || cents === null) {
    formError.value = 'Amount must be at least R$ 1,00 (for example 49,90).'
    return
  }
  submitting.value = true
  try {
    const created = await api.createPlan({
      name: form.name.trim(),
      description: form.description.trim() || undefined,
      amountCents: cents,
      interval: form.interval,
    })
    notify('success', 'Plan created', `${created.name} at ${formatCents(created.amountCents)}.`)
    form.name = ''
    form.description = ''
    form.amount = ''
    form.interval = 'MONTHLY'
    await load()
  } catch (err) {
    formError.value = errorMessage(err)
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1>Plans</h1>
        <p>Prices your customers can subscribe to.</p>
      </div>
    </div>

    <div class="grid-2">
      <section class="card" aria-labelledby="new-plan-title">
        <div class="card-header">
          <h2 id="new-plan-title">New plan</h2>
        </div>
        <form class="form" novalidate @submit.prevent="submit">
          <fieldset class="form-section">
            <legend>Basics</legend>
            <div class="field">
              <label for="plan-name">Name</label>
              <input
                id="plan-name"
                v-model="form.name"
                class="input"
                data-testid="plan-name"
                placeholder="Pro"
                :aria-invalid="nameInvalid"
                required
                maxlength="120"
              />
            </div>
            <div class="field">
              <label for="plan-description">Description <span class="optional">(optional)</span></label>
              <input id="plan-description" v-model="form.description" class="input" data-testid="plan-description" />
            </div>
          </fieldset>

          <fieldset class="form-section">
            <legend>Pricing</legend>
            <div class="form-row form-row-2">
              <div class="field">
                <label for="plan-amount">Amount</label>
                <div class="input-group">
                  <span class="input-prefix" aria-hidden="true">R$</span>
                  <input
                    id="plan-amount"
                    v-model="form.amount"
                    class="input num"
                    inputmode="decimal"
                    placeholder="49,90"
                    data-testid="plan-amount"
                    aria-describedby="plan-amount-hint"
                    :aria-invalid="amountInvalid"
                    required
                    @blur="normalizeAmount"
                  />
                </div>
              </div>
              <div class="field">
                <label for="plan-interval">Billing interval</label>
                <select id="plan-interval" v-model="form.interval" class="select" data-testid="plan-interval">
                  <option value="MONTHLY">Monthly</option>
                  <option value="YEARLY">Yearly</option>
                </select>
              </div>
            </div>
            <p id="plan-amount-hint" class="hint" aria-live="polite">
              <span v-if="amountPreview" class="hint-preview" data-testid="plan-amount-preview">{{ amountPreview }}</span>
              <span v-else>In reais, minimum R$ 1,00. Dots, commas and "R$" are accepted.</span>
            </p>
          </fieldset>

          <div v-if="formError" class="alert alert-error" role="alert" data-testid="form-error">
            <AppIcon name="alert" /> {{ formError }}
          </div>

          <button
            type="submit"
            class="btn btn-primary btn-block"
            data-testid="plan-submit"
            :aria-busy="submitting"
            :disabled="submitting"
          >
            <span v-if="submitting" class="spinner" aria-hidden="true"></span>
            <AppIcon v-else name="plus" />
            {{ submitting ? 'Saving...' : 'Create plan' }}
          </button>
        </form>
      </section>

      <section class="card card-flush" aria-labelledby="plans-list-title">
        <div class="card-header">
          <div>
            <h2 id="plans-list-title">All plans</h2>
            <p class="card-sub">{{ plans.length }} {{ plans.length === 1 ? 'plan' : 'plans' }}</p>
          </div>
          <button type="button" class="btn btn-ghost" :aria-busy="loading" :disabled="loading" @click="load">
            <AppIcon name="refresh" /> Refresh
          </button>
        </div>
        <AsyncState
          :loading="loading && plans.length === 0"
          :error="loadError"
          :empty="plans.length === 0"
          empty-title="No plans yet"
          empty-text="Create your first plan with the form."
          empty-icon="plans"
          @retry="load"
        >
          <div class="table-wrap">
            <table class="table table-stack">
              <thead>
                <tr>
                  <th scope="col">Plan</th>
                  <th scope="col" class="cell-right">Price</th>
                  <th scope="col">State</th>
                  <th scope="col">Created</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="plan in plans" :key="plan.id" data-testid="plan-row">
                  <td class="cell-full">
                    <div class="cell-title">{{ plan.name }}</div>
                    <div v-if="plan.description" class="muted">{{ plan.description }}</div>
                  </td>
                  <td class="cell-right" data-label="Price">
                    <div>
                      <div class="num cell-title">{{ formatCents(plan.amountCents) }}</div>
                      <div class="muted">{{ plan.interval === 'YEARLY' ? 'per year' : 'per month' }}</div>
                    </div>
                  </td>
                  <td data-label="State">
                    <span :class="['pill', plan.active ? 'tone-success' : 'tone-neutral']">
                      <AppIcon :name="plan.active ? 'check-circle' : 'x-circle'" :size="14" />
                      {{ plan.active ? 'Active' : 'Inactive' }}
                    </span>
                  </td>
                  <td class="num muted" data-label="Created">{{ formatDay(plan.createdAt) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </AsyncState>
      </section>
    </div>
  </div>
</template>
