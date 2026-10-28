import { ref } from 'vue'

/** localStorage key that holds the JWT access token (the e2e suite reads it too). */
export const TOKEN_STORAGE_KEY = 'tenantpay.token'

/** Reactive mirror of the token so the layout reacts to login and logout. */
export const token = ref<string | null>(localStorage.getItem(TOKEN_STORAGE_KEY))

export function setToken(value: string): void {
  localStorage.setItem(TOKEN_STORAGE_KEY, value)
  token.value = value
}

export function clearToken(): void {
  localStorage.removeItem(TOKEN_STORAGE_KEY)
  token.value = null
}

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_STORAGE_KEY)
}
