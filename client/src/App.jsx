import { useEffect, useMemo, useState } from 'react'
import {
  PAYMENT_STATUSES,
  createPayment,
  getPaymentHistory,
  getPayments,
  processPayment,
  sendOtp,
  validatePayment,
} from './api/payments'
import AccountDashboard from './features/payments/components/AccountDashboard'
import OtpModal from './features/payments/components/OtpModal'
import './App.css'

function App() {
  const [activeTab, setActiveTab] = useState('create')
  const [payments, setPayments] = useState([])
  const [listStatusFilter, setListStatusFilter] = useState('ALL')
  const [listLoading, setListLoading] = useState(false)
  const [listError, setListError] = useState('')

  const [historyItems, setHistoryItems] = useState([])
  const [historyPaymentId, setHistoryPaymentId] = useState('')
  const [historyLoading, setHistoryLoading] = useState(false)
  const [historyError, setHistoryError] = useState('')

  const [otpModal, setOtpModal] = useState({ paymentId: null, sending: false, submitting: false, error: '' })
  const [validatingId, setValidatingId] = useState(null)

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
  }, [])

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
      return
    }

    setHistoryLoading(true)
    setHistoryError('')
    try {
      const data = await getPaymentHistory(paymentId)
      const sortedHistory = [...data].sort((a, b) => {
        const dateA = new Date(a.changedAt).getTime()
        const dateB = new Date(b.changedAt).getTime()

        if (Number.isFinite(dateA) && Number.isFinite(dateB) && dateA !== dateB) {
          return dateB - dateA
        }

        return (b.id ?? 0) - (a.id ?? 0)
      })
      setHistoryItems(sortedHistory)
    } catch (error) {
      setHistoryItems([])
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
      setFormSuccess(`Payment #${created.id} created successfully.`)
      setFormData({
        currency: formData.currency,
        amount: '',
        accountFrom: '',
        accountTo: '',
        type: formData.type,
      })
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

  async function handleValidateClick(paymentId) {
    setListError('')
    setValidatingId(paymentId)
    try {
      await validatePayment(paymentId)
      await loadPayments(listStatusFilter)
    } catch (error) {
      setListError(error.message || 'Payment validation failed')
      await loadPayments(listStatusFilter)
    } finally {
      setValidatingId(null)
    }
  }

  async function handleSendOtpClick(paymentId) {
    setOtpModal({ paymentId, sending: true, submitting: false, error: '' })
    try {
      await sendOtp(paymentId)
      setOtpModal((prev) => ({ ...prev, sending: false }))
    } catch (error) {
      setOtpModal((prev) => ({ ...prev, sending: false, error: error.message || 'Failed to send OTP' }))
    }
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

  return (
    <div className="app-shell">
      <header className="app-header">
        <p className="eyebrow">Payment Processing Console</p>
        <h1>Payments Frontend</h1>
        <p className="subtitle">
          Create payments, list them with filters, and inspect payment history connected to your backend API.
        </p>
      </header>

      <nav className="tabbar" aria-label="Payment pages">
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
        <button
          type="button"
          className={activeTab === 'dashboard' ? 'tab active' : 'tab'}
          onClick={() => setActiveTab('dashboard')}
        >
          Account Dashboard
        </button>
      </nav>

      {activeTab === 'create' && (
        <section className="panel">
          <h2>Create Payment</h2>
          <form className="grid-form" onSubmit={handleCreatePayment}>
            <label>
              Currency
              <input
                name="currency"
                value={formData.currency}
                onChange={handleInputChange}
                placeholder="USD"
                maxLength={3}
                required
              />
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
              <input
                name="accountFrom"
                value={formData.accountFrom}
                onChange={handleInputChange}
                placeholder="ACC-1001"
                required
              />
            </label>
            <label>
              Account To
              <input
                name="accountTo"
                value={formData.accountTo}
                onChange={handleInputChange}
                placeholder="ACC-2004"
                required
              />
            </label>
            <label>
              Type
              <input
                name="type"
                value={formData.type}
                onChange={handleInputChange}
                placeholder="TRANSFER"
                required
              />
            </label>
            <div className="form-actions span-2">
              <button type="submit" disabled={submitLoading}>
                {submitLoading ? 'Creating...' : 'Create Payment'}
              </button>
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
                {PAYMENT_STATUSES.map((status) => (
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
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {payments.length === 0 && !listLoading && (
                  <tr>
                    <td colSpan="9" className="empty-cell">
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
                    <td className="actions-cell">
                      <button
                        type="button"
                        className="link-button"
                        onClick={() => openHistoryForPayment(payment.id)}
                      >
                        History
                      </button>
                      {payment.status === 'CREATED' && (
                        <button
                          type="button"
                          className="link-button validate-btn"
                          onClick={() => handleValidateClick(payment.id)}
                          disabled={validatingId === payment.id}
                        >
                          {validatingId === payment.id ? 'Validating...' : 'Validate'}
                        </button>
                      )}
                      {payment.status === 'VALIDATED' && (
                        <button
                          type="button"
                          className="link-button process-btn"
                          onClick={() => handleSendOtpClick(payment.id)}
                        >
                          Send OTP
                        </button>
                      )}
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
                  <th>History ID</th>
                  <th>Payment ID</th>
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
                    <td colSpan="7" className="empty-cell">
                      No history records to display.
                    </td>
                  </tr>
                )}
                {historyItems.map((entry) => (
                  <tr key={entry.id}>
                    <td>{entry.id}</td>
                    <td>{entry.paymentId}</td>
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
    </div>
  )
}

export default App
