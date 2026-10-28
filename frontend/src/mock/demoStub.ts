/*
 * Stand-in for the demo mock API in the normal build (see vite.config.ts alias "#demo-api").
 * It keeps the mock code and sample data out of the production bundle. Never called at runtime,
 * because DEMO_MODE is false in that build.
 */
export function demoFetch(): Promise<Response> {
  return Promise.reject(new Error('Demo mode is not enabled in this build'))
}

export function resetDemoData(): void {
  // Nothing to reset outside demo mode.
}

export function demoCredentials(): { email: string; password: string } | null {
  return null
}
