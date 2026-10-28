<script setup lang="ts">
import AppIcon from './AppIcon.vue'
import BrandMark from './BrandMark.vue'
</script>

<template>
  <div class="auth">
    <section class="auth-aside" aria-label="About TenantPay Billing">
      <div class="auth-brand"><BrandMark /> TenantPay <span class="brand-sub">Billing</span></div>
      <div class="auth-pitch">
        <h2>Recurring billing that runs itself.</h2>
        <p>Plans, customers and subscriptions for every tenant, with payment webhooks processed safely.</p>
        <ul class="auth-points">
          <li><AppIcon name="check-circle" /> Isolated data per tenant</li>
          <li><AppIcon name="check-circle" /> Idempotent payment webhooks</li>
          <li><AppIcon name="check-circle" /> Live MRR and overdue tracking</li>
        </ul>
      </div>
      <p class="auth-foot">Amounts in BRL. Sample data only.</p>
    </section>
    <div class="auth-main">
      <div class="auth-card">
        <div class="auth-brand auth-brand-mobile"><BrandMark /> TenantPay <span class="brand-sub">Billing</span></div>
        <slot />
      </div>
    </div>
  </div>
</template>

<style scoped>
.auth {
  min-height: 100vh;
  display: grid;
}

@media (min-width: 960px) {
  .auth {
    grid-template-columns: minmax(360px, 44%) 1fr;
  }
}

.auth-aside {
  display: none;
}

@media (min-width: 960px) {
  .auth-aside {
    display: flex;
    flex-direction: column;
    justify-content: space-between;
    gap: var(--space-8);
    padding: var(--space-10);
    background: var(--surface);
    border-right: 1px solid var(--border);
  }
}

.auth-brand {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  font-weight: 700;
  font-size: var(--text-lg);
}

.brand-sub {
  color: var(--text-muted);
  font-weight: 500;
}

.auth-pitch h2 {
  font-size: var(--text-3xl);
  font-weight: 700;
  letter-spacing: -0.02em;
  max-width: 14ch;
}

.auth-pitch p {
  margin-top: var(--space-3);
  color: var(--text-muted);
  max-width: 40ch;
}

.auth-points {
  list-style: none;
  margin: var(--space-6) 0 0;
  padding: 0;
  display: grid;
  gap: var(--space-3);
}

.auth-points li {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  font-weight: 500;
}

.auth-points .icon {
  color: var(--success-fg);
}

.auth-foot {
  color: var(--text-muted);
  font-size: var(--text-sm);
}

.auth-main {
  display: grid;
  place-items: center;
  padding: var(--space-8) var(--space-4);
}

.auth-card {
  width: min(420px, 100%);
  display: grid;
  gap: var(--space-6);
}

.auth-brand-mobile {
  justify-content: center;
}

@media (min-width: 960px) {
  .auth-brand-mobile {
    display: none;
  }
}
</style>
