const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? ''

const PAYMENT_STATUSES = ['CREATED', 'VALIDATED', 'SENT', 'COMPLETED', 'FAILED']

export async function request(path, options = {}) {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    headers: {
      'Content-Type': 'application/json',
      ...(options.headers || {}),
    },
    ...options,
  })

  if (!response.ok) {
    let message = 'Request failed'
    try {
      const data = await response.json()
      message = data.message || data.error || message
    } catch {
      const text = await response.text()
      if (text) {
        message = text
      }
    }
    throw new Error(message)
  }

  if (response.status === 204) {
    return null
  }

  return response.json()
}

export function getPayments(status = 'ALL') {
  const query = status && status !== 'ALL' ? `?status=${encodeURIComponent(status)}` : ''
  return request(`/api/payments${query}`)
}

export function createPayment(payload) {
  return request('/api/payments', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function getPaymentHistory(paymentId) {
  return request(`/api/payments/${paymentId}/history`)
}

export function validatePayment(paymentId) {
  return request(`/api/payments/${paymentId}/validate`, { method: 'POST' })
}

export function sendOtp(paymentId) {
  return request(`/api/payments/${paymentId}/send-otp`, { method: 'POST' })
}

export function processPayment(paymentId, otpCode) {
  return request(`/api/payments/${paymentId}/process`, {
    method: 'POST',
    body: JSON.stringify({ otpCode }),
  })
}

export { PAYMENT_STATUSES }