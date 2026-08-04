export const SPENDING_METHODS = [
  { key: 'creditCard', label: 'Credit Card' },
  { key: 'debitCard', label: 'Debit Card' },
  { key: 'upi', label: 'UPI' },
  { key: 'transfer', label: 'Transfer' },
  { key: 'others', label: 'Others' },
]

export function normalizeAccountId(value) {
  return String(value || '').trim().toUpperCase()
}

export function normalizeAmount(value) {
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : 0
}

export function resolveMethodBucket(type) {
  const normalizedType = String(type || '').trim().toUpperCase()

  if (normalizedType.includes('CREDIT')) {
    return 'creditCard'
  }

  if (normalizedType.includes('DEBIT')) {
    return 'debitCard'
  }

  if (normalizedType.includes('UPI')) {
    return 'upi'
  }

  if (normalizedType.includes('TRANSFER')) {
    return 'transfer'
  }

  return 'others'
}
