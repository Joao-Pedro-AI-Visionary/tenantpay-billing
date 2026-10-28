import { ref } from 'vue'
import { api, errorMessage } from './api'
import type { Me } from './types'

/** Current user and tenant, loaded from GET /api/v1/me after login. */
export const me = ref<Me | null>(null)
export const meError = ref<string | null>(null)

export async function loadMe(): Promise<void> {
  meError.value = null
  try {
    me.value = await api.me()
  } catch (err) {
    me.value = null
    meError.value = errorMessage(err)
  }
}

export function resetSession(): void {
  me.value = null
  meError.value = null
}
