import { createRouter, createWebHistory } from 'vue-router'
import { getToken } from './auth'

declare module 'vue-router' {
  interface RouteMeta {
    public?: boolean
    title?: string
  }
}

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: { name: 'dashboard' } },
    {
      path: '/login',
      name: 'login',
      component: () => import('./views/LoginView.vue'),
      meta: { public: true, title: 'Log in' },
    },
    {
      path: '/signup',
      name: 'signup',
      component: () => import('./views/SignupView.vue'),
      meta: { public: true, title: 'Create account' },
    },
    {
      path: '/dashboard',
      name: 'dashboard',
      component: () => import('./views/DashboardView.vue'),
      meta: { title: 'Dashboard' },
    },
    { path: '/plans', name: 'plans', component: () => import('./views/PlansView.vue'), meta: { title: 'Plans' } },
    {
      path: '/customers',
      name: 'customers',
      component: () => import('./views/CustomersView.vue'),
      meta: { title: 'Customers' },
    },
    {
      path: '/subscriptions',
      name: 'subscriptions',
      component: () => import('./views/SubscriptionsView.vue'),
      meta: { title: 'Subscriptions' },
    },
    {
      path: '/charges',
      name: 'charges',
      component: () => import('./views/ChargesView.vue'),
      meta: { title: 'Charges' },
    },
    {
      // Not /webhooks: that prefix is proxied to the API (Asaas webhook endpoint) by nginx and Vite.
      path: '/webhook-events',
      name: 'webhooks',
      component: () => import('./views/WebhooksView.vue'),
      meta: { title: 'Webhooks' },
    },
    { path: '/:pathMatch(.*)*', redirect: { name: 'dashboard' } },
  ],
  scrollBehavior: () => ({ top: 0 }),
})

router.beforeEach((to) => {
  const authed = !!getToken()
  if (!to.meta.public && !authed) return { name: 'login' }
  if (to.meta.public && authed) return { name: 'dashboard' }
  return true
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} | TenantPay Billing` : 'TenantPay Billing'
})
