<script setup lang="ts">
import { reactive, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import AppIcon from '../components/AppIcon.vue'
import AuthShell from '../components/AuthShell.vue'
import { api, errorMessage } from '../api'
import { setToken } from '../auth'

const router = useRouter()
const form = reactive({ tenantName: '', email: '', password: '' })
const submitting = ref(false)
const error = ref<string | null>(null)

async function submit(): Promise<void> {
  error.value = null
  submitting.value = true
  try {
    const res = await api.signup(form.tenantName.trim(), form.email.trim(), form.password)
    setToken(res.accessToken)
    await router.push({ name: 'dashboard' })
  } catch (err) {
    error.value = errorMessage(err)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <AuthShell>
    <section class="card" aria-labelledby="signup-title">
      <h1 id="signup-title" class="auth-title">Create your workspace</h1>
      <p class="card-sub">One tenant, isolated data, recurring billing in minutes.</p>

      <form class="form" style="margin-top: 20px" novalidate @submit.prevent="submit">
        <fieldset class="form-section">
          <legend>Business</legend>
          <div class="field">
            <label for="signup-tenant">Company name</label>
            <input
              id="signup-tenant"
              v-model="form.tenantName"
              class="input"
              data-testid="signup-tenant-name"
              autocomplete="organization"
              required
              minlength="2"
              maxlength="120"
            />
          </div>
        </fieldset>
        <fieldset class="form-section">
          <legend>Your login</legend>
          <div class="field">
            <label for="signup-email">Email</label>
            <input
              id="signup-email"
              v-model="form.email"
              class="input"
              type="email"
              data-testid="signup-email"
              autocomplete="email"
              required
            />
          </div>
          <div class="field">
            <label for="signup-password">Password</label>
            <input
              id="signup-password"
              v-model="form.password"
              class="input"
              type="password"
              data-testid="signup-password"
              autocomplete="new-password"
              aria-describedby="signup-password-hint"
              required
              minlength="8"
              maxlength="72"
            />
            <span id="signup-password-hint" class="hint">8 to 72 characters.</span>
          </div>
        </fieldset>

        <div v-if="error" class="alert alert-error" role="alert" data-testid="form-error">
          <AppIcon name="alert" /> {{ error }}
        </div>

        <button
          type="submit"
          class="btn btn-primary btn-block"
          data-testid="signup-submit"
          :aria-busy="submitting"
          :disabled="submitting"
        >
          <span v-if="submitting" class="spinner" aria-hidden="true"></span>
          {{ submitting ? 'Creating account...' : 'Create account' }}
        </button>
      </form>
    </section>
    <p class="auth-switch">
      Already have an account? <RouterLink :to="{ name: 'login' }" data-testid="go-login">Log in</RouterLink>
    </p>
  </AuthShell>
</template>

<style scoped>
.auth-title {
  font-size: var(--text-2xl);
  font-weight: 700;
}

.auth-switch {
  text-align: center;
  color: var(--text-muted);
}
</style>
