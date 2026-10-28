/**
 * Demo mode is ON only when the build was made with VITE_DEMO_MODE=true (`npm run build:demo`).
 * Vite replaces this expression at build time, so the normal build drops the mock API entirely.
 */
export const DEMO_MODE = import.meta.env.VITE_DEMO_MODE === 'true'

/** Fake credentials of the seeded demo tenant. They only exist inside the in-browser mock API. */
export const DEMO_CREDENTIALS = { email: 'demo@tenantpay.dev', password: 'demo-password' } as const
