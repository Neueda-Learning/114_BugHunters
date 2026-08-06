import { useEffect, useMemo, useState } from 'react'
import {
  PAYMENT_STATUSES,
  createPayment,
  getPaymentById,
  getPaymentHistory,
  getPayments,
  processPayment,
  sendOtp,
  validatePayment,
} from './api/payments'
import { getAccounts } from './api/accounts'
import AccountDashboard from './features/payments/components/AccountDashboard'
import OtpModal from './features/payments/components/OtpModal'
import './App.css'

const CURRENCY_OPTIONS = ['USD', 'EUR', 'GBP', 'INR']
const PAYMENT_TYPE_OPTIONS = ['TRANSFER', 'UPI', 'DEBIT_CARD', 'CREDIT_CARD']

function App() {
  const [activeTab, setActiveTab] = useState('dashboard')
  const [payments, setPayments] = useState([])
  const [accounts, setAccounts] = useState([])
  const [accountsLoading, setAccountsLoading] = useState(false)
  const [accountsError, setAccountsError] = useState('')
  const [listStatusFilter, setListStatusFilter] = useState('ALL')
  const [listLoading, setListLoading] = useState(false)
  const [listError, setListError] = useState('')

  const [historyItems, setHistoryItems] = useState([])
  const [historyPaymentId, setHistoryPaymentId] = useState('')
  const [historyPaymentDetails, setHistoryPaymentDetails] = useState(null)
  const [historyLoading, setHistoryLoading] = useState(false)
  const [historyError, setHistoryError] = useState('')

  const [otpModal, setOtpModal] = useState({ paymentId: null, sending: false, submitting: false, error: '' })

  const [submitLoading, setSubmitLoading] = useState(false)
  const [formError, setFormError] = useState('')
  const [formSuccess, setFormSuccess] = useState('')
  const [formData, setFormData] = useState({
    currency: 'USD',
    amount: '',
    accountFrom: '',
    accountTo: '',
    type: 'TRANSFER',
  })

  useEffect(() => {
    void loadPayments('ALL')
    void loadAccounts()
  }, [])

  useEffect(() => {
    if (activeTab !== 'create') {
      setFormSuccess('')
      setFormError('')
    }
  }, [activeTab])

  useEffect(() => {
    if (!formSuccess) return
    const id = setTimeout(() => setFormSuccess(''), 5000)
    return () => clearTimeout(id)
  }, [formSuccess])

  useEffect(() => {
    if (activeTab !== 'list') return
    const id = setInterval(() => {
      void loadPayments(listStatusFilter)
    }, 10000)
    return () => clearInterval(id)
  }, [activeTab, listStatusFilter])

  useEffect(() => {
    const id = setInterval(() => {
      void loadAccounts()
    }, 30000)
    return () => clearInterval(id)
  }, [])

  async function loadAccounts() {
    setAccountsLoading(true)
    setAccountsError('')
    try {
      const data = await getAccounts()
      setAccounts(data)
      setFormData((current) => {
        if (data.length === 0) {
          return current
        }

        const defaultSource = current.accountFrom || data[0].accountNumber
        const fallbackDestination = data.find((account) => account.accountNumber !== defaultSource)?.accountNumber || ''
        return {
          ...current,
          accountFrom: defaultSource,
          accountTo: current.accountTo || fallbackDestination,
        }
      })
    } catch (error) {
      setAccounts([])
      setAccountsError(error.message || 'Unable to load accounts')
    } finally {
      setAccountsLoading(false)
    }
  }

  async function loadPayments(status) {
    setListLoading(true)
    setListError('')
    try {
      const data = await getPayments(status)
      setPayments(data)
    } catch (error) {
      setListError(error.message || 'Unable to load payments')
    } finally {
      setListLoading(false)
    }
  }

  async function loadHistory(paymentId) {
    if (!paymentId) {
      setHistoryError('Please provide a payment ID.')
      setHistoryPaymentDetails(null)
      return
    }

    setHistoryLoading(true)
    setHistoryError('')
    try {
      const [data, paymentDetails] = await Promise.all([
        getPaymentHistory(paymentId),
        getPaymentById(paymentId),
      ])
      const sortedHistory = [...data].sort((a, b) => {
        const dateA = new Date(a.changedAt).getTime()
        const dateB = new Date(b.changedAt).getTime()

        if (Number.isFinite(dateA) && Number.isFinite(dateB) && dateA !== dateB) {
          return dateB - dateA
        }

        return (b.id ?? 0) - (a.id ?? 0)
      })
      setHistoryItems(sortedHistory)
      setHistoryPaymentDetails(paymentDetails)
    } catch (error) {
      setHistoryItems([])
      setHistoryPaymentDetails(null)
      setHistoryError(error.message || 'Unable to load payment history')
    } finally {
      setHistoryLoading(false)
    }
  }

  function handleInputChange(event) {
    const { name, value } = event.target
    setFormData((current) => ({
      ...current,
      [name]: value,
    }))
  }

  async function handleCreatePayment(event) {
    event.preventDefault()
    setFormError('')
    setFormSuccess('')

    const amountValue = Number(formData.amount)
    if (!Number.isFinite(amountValue) || amountValue <= 0) {
      setFormError('Amount must be greater than zero.')
      return
    }

    if (!formData.accountFrom || !formData.accountTo || !formData.currency || !formData.type) {
      setFormError('Please fill all required fields.')
      return
    }

    setSubmitLoading(true)
    try {
      const payload = {
        currency: formData.currency.trim().toUpperCase(),
        amount: amountValue,
        accountFrom: formData.accountFrom.trim(),
        accountTo: formData.accountTo.trim(),
        type: formData.type.trim().toUpperCase(),
      }

      const created = await createPayment(payload)
      await validatePayment(created.id)
      await sendOtp(created.id)

      setFormSuccess(`Payment #${created.id} created, validated, and OTP sent successfully.`)
      setFormData({
        currency: formData.currency,
        amount: '',
        accountFrom: '',
        accountTo: '',
        type: formData.type,
      })

      setOtpModal({ paymentId: created.id, sending: false, submitting: false, error: '' })
      setActiveTab('list')
      await loadPayments(listStatusFilter)
    } catch (error) {
      setFormError(error.message || 'Unable to create payment')
    } finally {
      setSubmitLoading(false)
    }
  }

  function openHistoryForPayment(paymentId) {
    setActiveTab('history')
    setHistoryPaymentId(String(paymentId))
    void loadHistory(paymentId)
  }

  async function handleOtpSubmit(otpCode) {
    setOtpModal((prev) => ({ ...prev, submitting: true, error: '' }))
    try {
      await processPayment(otpModal.paymentId, otpCode)
      setOtpModal({ paymentId: null, sending: false, submitting: false, error: '' })
      await loadPayments(listStatusFilter)
    } catch (error) {
      setOtpModal((prev) => ({ ...prev, submitting: false, error: error.message || 'OTP verification failed' }))
    }
  }

  async function handleProcessValidatedPayment(paymentId) {
    setListError('')
    setOtpModal({ paymentId, sending: true, submitting: false, error: '' })

    try {
      await sendOtp(paymentId)
      setOtpModal((prev) => ({ ...prev, sending: false }))
    } catch (error) {
      setOtpModal({ paymentId: null, sending: false, submitting: false, error: '' })
      setListError(error.message || 'Unable to send OTP for this payment')
    }
  }

  function handleOtpCancel() {
    setOtpModal({ paymentId: null, sending: false, submitting: false, error: '' })
  }

  function formatDate(value) {
    if (!value) {
      return '-'
    }

    const date = new Date(value)
    if (Number.isNaN(date.getTime())) {
      return value
    }
    return date.toLocaleString()
  }

  function formatAmount(value) {
    const amount = Number(value)
    if (!Number.isFinite(amount)) {
      return '-'
    }

    return amount.toLocaleString(undefined, {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    })
  }

  const latestHistoryEntry = useMemo(() => {
    if (historyItems.length === 0) {
      return null
    }

    return historyItems[0]
  }, [historyItems])

  const selectedSourceAccount = useMemo(
    () => accounts.find((account) => account.accountNumber === formData.accountFrom) || null,
    [accounts, formData.accountFrom],
  )

  return (
    <div className="app-shell">
      <header className="app-header">
        <p className="eyebrow">Payment Processing Console</p>
        <h1>Payment Console</h1>
        <p className="subtitle">
          Create payments, list them with filters, and inspect payment history.
        </p>
      </header>

      <nav className="tabbar" aria-label="Payment pages">
        <button
          type="button"
          className={activeTab === 'dashboard' ? 'tab active' : 'tab'}
          onClick={() => setActiveTab('dashboard')}
        >
          Home
        </button>
        <button
          type="button"
          className={activeTab === 'create' ? 'tab active' : 'tab'}
          onClick={() => setActiveTab('create')}
        >
          Create Payment
        </button>
        <button
          type="button"
          className={activeTab === 'list' ? 'tab active' : 'tab'}
          onClick={() => setActiveTab('list')}
        >
          Payments List
        </button>
        <button
          type="button"
          className={activeTab === 'history' ? 'tab active' : 'tab'}
          onClick={() => setActiveTab('history')}
        >
          Payment History
        </button>
      </nav>

      {activeTab === 'create' && (
        <section className="panel">
          <h2>Create Payment</h2>
          <form className="grid-form" onSubmit={handleCreatePayment}>
            <label>
              Currency
              <select
                name="currency"
                value={formData.currency}
                onChange={handleInputChange}
                required
              >
                {CURRENCY_OPTIONS.map((currency) => (
                  <option key={currency} value={currency}>
                    {currency}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Amount
              <input
                name="amount"
                value={formData.amount}
                onChange={handleInputChange}
                placeholder="100.50"
                inputMode="decimal"
                required
              />
            </label>
            <label>
              Account From
              <select
                name="accountFrom"
                value={formData.accountFrom}
                onChange={handleInputChange}
                disabled={accountsLoading || accounts.length === 0}
                required
              >
                {accounts.length === 0 && <option value="">No accounts available</option>}
                {accounts.map((account) => (
                  <option key={account.accountNumber} value={account.accountNumber}>
                    {account.accountNumber}
                  </option>
                ))}
              </select>
              <p className="field-hint">
                Available balance: {selectedSourceAccount ? formatAmount(selectedSourceAccount.balance) : '-'}
              </p>
            </label>
            <label>
              Account To
              <select
                name="accountTo"
                value={formData.accountTo}
                onChange={handleInputChange}
                disabled={accountsLoading || accounts.length === 0}
                required
              >
                {accounts.length === 0 && <option value="">No accounts available</option>}
                {accounts
                  .filter((account) => account.accountNumber !== formData.accountFrom)
                  .map((account) => (
                    <option key={account.accountNumber} value={account.accountNumber}>
                      {account.accountNumber}
                    </option>
                  ))}
              </select>
            </label>
            <label>
              Type
              <select
                name="type"
                value={formData.type}
                onChange={handleInputChange}
                required
              >
                {PAYMENT_TYPE_OPTIONS.map((type) => (
                  <option key={type} value={type}>
                    {type}
                  </option>
                ))}
              </select>
            </label>
            <div className="form-actions span-2">
              <button type="submit" disabled={submitLoading}>
                {submitLoading ? 'Creating...' : 'Create Payment'}
              </button>
              {accountsError && <p className="error-msg">{accountsError}</p>}
              {formError && <p className="error-msg">{formError}</p>}
              {formSuccess && <p className="success-msg">{formSuccess}</p>}
            </div>
          </form>
        </section>
      )}

      {activeTab === 'list' && (
        <section className="panel">
          <div className="panel-head">
            <h2>Payments List</h2>
            <div className="row-controls">
              <select
                value={listStatusFilter}
                onChange={(event) => setListStatusFilter(event.target.value)}
                aria-label="Filter by status"
              >
                <option value="ALL">All Statuses</option>
                {PAYMENT_STATUSES.filter((status) => status !== 'SENT').map((status) => (
                  <option key={status} value={status}>
                    {status}
                  </option>
                ))}
              </select>
              <button
                type="button"
                onClick={() => {
                  void loadPayments(listStatusFilter)
                }}
                disabled={listLoading}
              >
                {listLoading ? 'Loading...' : 'Apply Filter'}
              </button>
            </div>
          </div>

          {listError && <p className="error-msg">{listError}</p>}

          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>ID</th>
                  <th>From</th>
                  <th>To</th>
                  <th>Amount</th>
                  <th>Currency</th>
                  <th>Status</th>
                  <th>Type</th>
                  <th>Created</th>
                  <th>Action</th>
                  <th>History</th>
                </tr>
              </thead>
              <tbody>
                {payments.length === 0 && !listLoading && (
                  <tr>
                    <td colSpan="10" className="empty-cell">
                      No payments found for this filter.
                    </td>
                  </tr>
                )}
                {payments.map((payment) => (
                  <tr key={payment.id}>
                    <td>{payment.id}</td>
                    <td>{payment.accountFrom}</td>
                    <td>{payment.accountTo}</td>
                    <td>{formatAmount(payment.amount)}</td>
                    <td>{payment.currency}</td>
                    <td>
                      <span className={`status-pill ${payment.status?.toLowerCase() || ''}`}>
                        {payment.status}
                      </span>
                    </td>
                    <td>{payment.type}</td>
                    <td>{formatDate(payment.createdAt)}</td>
                    <td>
                      {payment.status === 'VALIDATED' ? (
                        <button
                          type="button"
                          onClick={() => {
                            void handleProcessValidatedPayment(payment.id)
                          }}
                        >
                          Process Payment
                        </button>
                      ) : (
                        '-'
                      )}
                    </td>
                    <td>
                      <button
                        type="button"
                        className="link-button"
                        onClick={() => openHistoryForPayment(payment.id)}
                      >
                        History
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}

      {activeTab === 'history' && (
        <section className="panel">
          <div className="panel-head">
            <h2>Payment History</h2>
            <form
              className="row-controls"
              onSubmit={(event) => {
                event.preventDefault()
                void loadHistory(historyPaymentId)
              }}
            >
              <input
                value={historyPaymentId}
                onChange={(event) => setHistoryPaymentId(event.target.value)}
                placeholder="Enter payment ID"
                aria-label="Payment ID"
              />
              <button type="submit" disabled={historyLoading}>
                {historyLoading ? 'Loading...' : 'Fetch History'}
              </button>
            </form>
          </div>

          {historyError && <p className="error-msg">{historyError}</p>}

          {latestHistoryEntry && (
            <div className="history-summary">
              <article className="metric-card">
                <p>Latest Status</p>
                <h3>
                  <span className={`status-pill ${latestHistoryEntry.newStatus?.toLowerCase() || ''}`}>
                    {latestHistoryEntry.newStatus}
                  </span>
                </h3>
              </article>
              <article className="metric-card">
                <p>Latest Update</p>
                <h3>{formatDate(latestHistoryEntry.changedAt)}</h3>
              </article>
              <article className="metric-card">
                <p>Payment Method</p>
                <h3>{latestHistoryEntry.type || '-'}</h3>
              </article>
            </div>
          )}

          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Source Account</th>
                  <th>Destination Account</th>
                  <th>Amount Sent</th>
                  <th>Old Status</th>
                  <th>New Status</th>
                  <th>Type</th>
                  <th>Remarks</th>
                  <th>Changed At</th>
                </tr>
              </thead>
              <tbody>
                {historyItems.length === 0 && !historyLoading && (
                  <tr>
                    <td colSpan="8" className="empty-cell">
                      No history records to display.
                    </td>
                  </tr>
                )}
                {historyItems.map((entry) => (
                  <tr key={entry.id}>
                    <td>{historyPaymentDetails?.accountFrom || '-'}</td>
                    <td>{historyPaymentDetails?.accountTo || '-'}</td>
                    <td>{historyPaymentDetails ? formatAmount(historyPaymentDetails.amount) : '-'}</td>
                    <td>
                      {entry.oldStatus ? (
                        <span className={`status-pill ${entry.oldStatus.toLowerCase()}`}>{entry.oldStatus}</span>
                      ) : (
                        '-'
                      )}
                    </td>
                    <td>
                      <span className={`status-pill ${entry.newStatus?.toLowerCase() || ''}`}>
                        {entry.newStatus || '-'}
                      </span>
                    </td>
                    <td>{entry.type || '-'}</td>
                    <td className="history-remarks">{entry.remarks || '-'}</td>
                    <td>{formatDate(entry.changedAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}

      {activeTab === 'dashboard' && (
        <AccountDashboard formatDate={formatDate} />
      )}

      {otpModal.paymentId !== null && (
        <OtpModal
          paymentId={otpModal.paymentId}
          sending={otpModal.sending}
          submitting={otpModal.submitting}
          error={otpModal.error}
          onSubmit={handleOtpSubmit}
          onCancel={handleOtpCancel}
        />
      )}

      <footer className="app-footer">
        <p className="app-footer-brand">Payment Processing Platform</p>
        <p className="app-footer-meta">
          © {new Date().getFullYear()} Team Bug Hunters.
        </p>
      </footer>
    </div>
  )
}

export default App
