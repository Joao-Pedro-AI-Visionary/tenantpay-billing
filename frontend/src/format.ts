const brl = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })

export function formatCents(cents: number | null | undefined): string {
  return brl.format((cents ?? 0) / 100)
}

/**
 * Converts a reais input such as "49,90", "49.90" or "1.234,56" into integer cents.
 * Returns null when the input is not a valid amount with at most 2 decimals.
 */
export function reaisToCents(input: string): number | null {
  let s = input.trim().replace(/\s/g, '').replace(/^R\$/i, '')
  if (s.includes(',')) {
    // Brazilian format: dots are thousand separators, comma is the decimal separator.
    s = s.replace(/\./g, '').replace(',', '.')
  }
  if (!/^\d+(\.\d{1,2})?$/.test(s)) return null
  return Math.round(Number(s) * 100)
}

export function formatDate(value: string | null | undefined): string {
  if (!value) return '-'
  // Plain dates (yyyy-MM-dd) are rendered without Date parsing to avoid timezone shifts.
  const plain = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value)
  if (plain) return `${plain[3]}/${plain[2]}/${plain[1]}`
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('pt-BR')
}

/** Formats cents as a plain reais amount without the currency symbol, for example 4990 -> "49,90". */
export function centsToReaisInput(cents: number): string {
  return new Intl.NumberFormat('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(cents / 100)
}

/**
 * Postel: accept CPF/CNPJ typed with or without dots, dashes, slashes or spaces and show it
 * formatted. Up to 11 digits is formatted as CPF (000.000.000-00), above that as CNPJ
 * (00.000.000/0000-00). Only the digits are sent to the API.
 */
export function maskDocument(input: string): string {
  const d = input.replace(/\D/g, '').slice(0, 14)
  if (d.length <= 11) {
    return d
      .replace(/^(\d{3})(\d)/, '$1.$2')
      .replace(/^(\d{3})\.(\d{3})(\d)/, '$1.$2.$3')
      .replace(/\.(\d{3})(\d{1,2})$/, '.$1-$2')
  }
  return d
    .replace(/^(\d{2})(\d)/, '$1.$2')
    .replace(/^(\d{2})\.(\d{3})(\d)/, '$1.$2.$3')
    .replace(/\.(\d{3})(\d)/, '.$1/$2')
    .replace(/(\d{4})(\d{1,2})$/, '$1-$2')
}

export function documentDigits(input: string): string {
  return input.replace(/\D/g, '')
}

/** Display helper for stored documents (digits only in the API). */
export function formatDocument(digits: string): string {
  return digits.length === 11 || digits.length === 14 ? maskDocument(digits) : digits
}

/** Normalizes a plan amount to a monthly value for MRR: yearly plans count 1/12 per month. */
export function monthlyCents(amountCents: number, interval: 'MONTHLY' | 'YEARLY' | undefined): number {
  return interval === 'YEARLY' ? Math.round(amountCents / 12) : amountCents
}

/** Whole days from today (local) to a yyyy-MM-dd date. Negative means the date is in the past. */
export function daysUntil(date: string): number {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(date)
  if (!m) return 0
  const target = new Date(Number(m[1]), Number(m[2]) - 1, Number(m[3]))
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  return Math.round((target.getTime() - today.getTime()) / 86_400_000)
}

export function relativeDue(date: string): string {
  const d = daysUntil(date)
  if (d === 0) return 'due today'
  if (d === 1) return 'due tomorrow'
  if (d > 1) return `due in ${d} days`
  if (d === -1) return '1 day late'
  return `${-d} days late`
}

/** Short English month name for a yyyy-MM-dd date, for example "Oct". */
export function monthShort(date: string): string {
  const m = /^(\d{4})-(\d{2})-\d{2}$/.exec(date)
  if (!m) return ''
  return new Date(Number(m[1]), Number(m[2]) - 1, 1).toLocaleString('en-US', { month: 'short' })
}

/** Date only (dd/MM/yyyy) for timestamps or plain dates. */
export function formatDay(value: string | null | undefined): string {
  if (!value) return '-'
  if (/^\d{4}-\d{2}-\d{2}$/.test(value)) return formatDate(value)
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleDateString('pt-BR')
}
