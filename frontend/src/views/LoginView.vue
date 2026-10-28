<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import AppIcon from '../components/AppIcon.vue'
import AuthShell from '../components/AuthShell.vue'
import { api, errorMessage } from '../api'
import { setToken } from '../auth'
import { DEMO_MODE } from '../config'

interface Credentials {
  email: string
  password: string
}

const route = useRoute()
const router = useRouter()
const form = reactive({ email: '', password: '' })
const submitting = ref(false)
const enteringDemo = ref(false)
const error = ref<string | null>(null)
const expired = computed(() => route.query.expired === '1')

async function logIn(email: string, password: string): Promise<void> {
  error.value = null
  try {
    const res = await api.login(email.trim(), password)
    setToken(res.accessToken)
    await router.push({ name: 'dashboard' })
  } catch (err) {
    error.value = errorMessage(err)
  }
}

async function submit(): Promise<void> {
  submitting.value = true
  await logIn(form.email, form.password)
  submitting.value = false
}

// The demo password is generated in the browser when the mock data is seeded, so it is read from the
// mock API (loaded on demand, absent from the normal build) instead of living in the source.
const demoCreds = ref<Credentials | null>(null)

async function loadDemoCredentials(): Promise<Credentials | null> {
  const { demoCredentials } = await import('#demo-api')
  demoCreds.value = demoCredentials()
  return demoCreds.value
}

onMounted(() => {
  if (DEMO_MODE) void loadDemoCredentials()
})

async function enterDemo(): Promise<void> {
  enteringDemo.value = true
  // Read again on click: a reset in another tab reseeds the data with a new password.
  const creds = await loadDemoCredentials()
  if (creds) {
    form.email = creds.email
    form.password = creds.password
    await logIn(creds.email, creds.password)
  }
  enteringDemo.value = false
}
</script>

<template>
  <AuthShell>
    <section v-if="DEMO_MODE" class="card demo-card" aria-labelledby="demo-title" data-testid="demo-login">
      <div>
        <h2 id="demo-title" class="card-title">Try the demo</h2>
        <p class="card-sub">Explore a sample gym with plans, customers and payments. These credentials are fake.</p>
      </div>
      <dl class="demo-creds">
        <div>
          <dt>Email</dt>
          <dd class="mono" data-testid="demo-email">{{ demoCreds?.email ?? '...' }}</dd>
        </div>
        <div>
          <dt>Password</dt>
          <dd class="mono" data-testid="demo-password">{{ demoCreds?.password ?? '...' }}</dd>
        </div>
      </dl>
      <button
        type="button"
        class="btn btn-primary btn-block"
        data-testid="demo-enter"
        :aria-busy="enteringDemo"
        :disabled="enteringDemo || submitting"
        @click="enterDemo"
      >
        <span v-if="enteringDemo" class="spinner" aria-hidden="true"></span>
        <AppIcon v-else name="bolt" />
        {{ enteringDemo ? 'Opening demo...' : 'Enter demo' }}
      </button>
    </section>

    <section class="card" aria-labelledby="login-title">
      <h1 id="login-title" class="auth-title">Log in</h1>
      <p class="card-sub">Access your billing workspace.</p>

      <div v-if="expired && !error" class="alert alert-info" role="status" style="margin-top: 16px">
        <AppIcon name="info" /> Your session ended. Please log in again.
      </div>

      <form class="form" style="margin-top: 20px" novalidate @submit.prevent="submit">
        <div class="field">
          <label for="login-email">Email</label>
          <input
            id="login-email"
            v-model="form.email"
            class="input"
            type="email"
            data-testid="login-email"
            autocomplete="email"
            required
          />
        </div>
        <div class="field">
          <label for="login-password">Password</label>
          <input
            id="login-password"
            v-model="form.password"
            class="input"
            type="password"
            data-testid="login-password"
            autocomplete="current-password"
            required
          />
        </div>

        <div v-if="error" class="alert alert-error" role="alert" data-testid="form-error">
          <AppIcon name="alert" /> {{ error }}
        </div>

        <!-- In demo mode "Enter demo" is the primary call to action, so this one stays neutral. -->
        <button
          type="submit"
          :class="['btn', 'btn-block', { 'btn-primary': !DEMO_MODE }]"
          data-testid="login-submit"
          :aria-busy="submitting"
          :disabled="submitting || enteringDemo"
        >
          <span v-if="submitting" class="spinner" aria-hidden="true"></span>
          {{ submitting ? 'Logging in...' : 'Log in' }}
        </button>
      </form>
    </section>

    <p class="auth-switch">
      New here? <RouterLink :to="{ name: 'signup' }" data-testid="go-signup">Create an account</RouterLink>
    </p>
  </AuthShell>
</template>

<style scoped>
.auth-title {
  font-size: var(--text-2xl);
  font-weight: 700;
}

.demo-card {
  display: grid;
  gap: var(--space-4);
}

.demo-creds {
  margin: 0;
  display: grid;
  gap: var(--space-2);
  padding: var(--space-3) var(--space-4);
  background: var(--surface-subtle);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
}

.demo-creds div {
  display: flex;
  justify-content: space-between;
  gap: var(--space-3);
  align-items: baseline;
}

.demo-creds dt {
  font-size: var(--text-sm);
  color: var(--text-muted);
}

.demo-creds dd {
  margin: 0;
  font-size: var(--text-sm);
}

.auth-switch {
  text-align: center;
  color: var(--text-muted);
}
</style>
