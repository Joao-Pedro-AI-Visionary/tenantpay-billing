import { ref } from 'vue'

export interface Toast {
  id: number
  kind: 'success' | 'error' | 'info'
  title: string
  text?: string
}

/** Small global toast queue. Messages are announced through a polite live region in ToastHost. */
export const toasts = ref<Toast[]>([])
let seq = 0

export function notify(kind: Toast['kind'], title: string, text?: string, timeoutMs = 4500): void {
  seq += 1
  const id = seq
  toasts.value = [...toasts.value.slice(-2), { id, kind, title, text }]
  window.setTimeout(() => dismiss(id), timeoutMs)
}

export function dismiss(id: number): void {
  toasts.value = toasts.value.filter((t) => t.id !== id)
}
