import { request } from './payments'

export function getAccounts() {
  return request('/api/accounts')
}
