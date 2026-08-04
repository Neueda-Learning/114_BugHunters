import { useEffect, useMemo, useState } from 'react'
import { getAccounts } from '../../../api/accounts'
import { getPayments } from '../../../api/payments'
import {
  normalizeAccountId,
  normalizeAmount,
  resolveMethodBucket,
  SPENDING_METHODS,
} from '../utils/dashboard'

function formatAmount(value) {
  const amount = normalizeAmount(value)
  return amount.toLocaleString(undefined, {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })
}

function AccountDashboard({ formatDate }) {
  const [accounts, setAccounts] = useState([])
  const [accountsLoading, setAccountsLoading] = useState(true)
  const [accountsError, setAccountsError] = useState('')

  const [selectedAccountId, setSelectedAccountId] = useState('')
  const [dashboardSelectedAccountId, setDashboardSelectedAccountId] = useState('')
  const [dashboardTransactions, setDashboardTransactions] = useState([])
  const [dashboardLoading, setDashboardLoading] = useState(false)
  const [dashboardError, setDashboardError] = useState('')

  async function loadAccountDashboard(accountId) {
    const normalizedAccountId = normalizeAccountId(accountId)
    if (!normalizedAccountId) {
      setDashboardError('Please select an account.')
      setDashboardTransactions([])
      setDashboardSelectedAccountId('')
      return
    }

    setDashboardLoading(true)
    setDashboardError('')

    try {
      const allPayments = await getPayments('ALL')
      const accountTransactions = allPayments.filter((payment) => {
        const fromAccount = normalizeAccountId(payment.accountFrom)
        const toAccount = normalizeAccountId(payment.accountTo)
        return fromAccount === normalizedAccountId || toAccount === normalizedAccountId
      })

      setDashboardTransactions(accountTransactions)
      setDashboardSelectedAccountId(normalizedAccountId)

      if (accountTransactions.length === 0) {
        setDashboardError('No transactions found for this account.')
      }
    } catch (error) {
      setDashboardTransactions([])
      setDashboardSelectedAccountId('')
      setDashboardError(error.message || 'Unable to load account dashboard')
    } finally {
      setDashboardLoading(false)
    }
  }

  function handleAccountChange(event) {
    const nextAccountId = event.target.value
    setSelectedAccountId(nextAccountId)
    void loadAccountDashboard(nextAccountId)
  }

  useEffect(() => {
    let isCancelled = false

    getAccounts()
      .then((data) => {
        if (isCancelled) {
          return
        }

        setAccounts(data)
        if (data.length > 0) {
          const initialAccountId = data[0].accountNumber
          setSelectedAccountId((current) => current || initialAccountId)
          void loadAccountDashboard(initialAccountId)
        } else {
          setDashboardTransactions([])
          setDashboardSelectedAccountId('')
          setDashboardError('No accounts available.')
        }
      })
      .catch((error) => {
        if (isCancelled) {
          return
        }

        setAccounts([])
        setAccountsError(error.message || 'Unable to load accounts')
      })
      .finally(() => {
        if (!isCancelled) {
          setAccountsLoading(false)
        }
      })

    return () => {
      isCancelled = true
    }
  }, [])

  const outgoingTransactions = useMemo(
    () =>
      dashboardTransactions.filter(
        (payment) => normalizeAccountId(payment.accountFrom) === dashboardSelectedAccountId,
      ),
    [dashboardTransactions, dashboardSelectedAccountId],
  )

  const incomingTransactions = useMemo(
    () =>
      dashboardTransactions.filter(
        (payment) => normalizeAccountId(payment.accountTo) === dashboardSelectedAccountId,
      ),
    [dashboardTransactions, dashboardSelectedAccountId],
  )

  const spendingByMethod = useMemo(
    () =>
      SPENDING_METHODS.map((method) => {
        const methodTransactions = outgoingTransactions.filter(
          (payment) => resolveMethodBucket(payment.type) === method.key,
        )
        const totalAmount = methodTransactions.reduce((sum, payment) => sum + normalizeAmount(payment.amount), 0)

        return {
          ...method,
          totalAmount,
          count: methodTransactions.length,
        }
      }),
    [outgoingTransactions],
  )

  const maxMethodSpend = useMemo(
    () => spendingByMethod.reduce((max, method) => (method.totalAmount > max ? method.totalAmount : max), 0),
    [spendingByMethod],
  )

  const totalOutgoingAmount = useMemo(
    () => outgoingTransactions.reduce((sum, payment) => sum + normalizeAmount(payment.amount), 0),
    [outgoingTransactions],
  )

  return (
    <section className="panel">
      <div className="panel-head">
        <h2>Account Dashboard</h2>
        <div className="row-controls">
          <select
            value={selectedAccountId}
            onChange={handleAccountChange}
            disabled={accountsLoading || accounts.length === 0}
            aria-label="Account"
          >
            {accounts.length === 0 && <option value="">No accounts available</option>}
            {accounts.map((account) => (
              <option key={account.accountNumber} value={account.accountNumber}>
                {account.accountNumber}
              </option>
            ))}
          </select>
        </div>
      </div>

      {accountsError && <p className="error-msg dashboard-error">{accountsError}</p>}
      {dashboardError && <p className="error-msg dashboard-error">{dashboardError}</p>}

      {dashboardSelectedAccountId && (
        <>
          <div className="dashboard-summary">
            <article className="metric-card">
              <p>Total Transactions</p>
              <h3>{dashboardTransactions.length}</h3>
            </article>
            <article className="metric-card">
              <p>Outgoing Transactions</p>
              <h3>{outgoingTransactions.length}</h3>
            </article>
            <article className="metric-card">
              <p>Incoming Transactions</p>
              <h3>{incomingTransactions.length}</h3>
            </article>
            <article className="metric-card">
              <p>Total Outgoing Spend</p>
              <h3>{formatAmount(totalOutgoingAmount)}</h3>
            </article>
          </div>

          <div className="chart-panel">
            <h3>Spending by Payment Method</h3>
            <div className="bar-chart" role="img" aria-label="Bar chart of spending grouped by payment method">
              {spendingByMethod.map((method) => {
                const width = maxMethodSpend === 0 ? 0 : Math.round((method.totalAmount / maxMethodSpend) * 100)
                return (
                  <div key={method.key} className="bar-row">
                    <div className="bar-meta">
                      <span>{method.label}</span>
                      <span>
                        {formatAmount(method.totalAmount)} ({method.count})
                      </span>
                    </div>
                    <div className="bar-track" aria-hidden="true">
                      <div className="bar-fill" style={{ width: `${width}%` }} />
                    </div>
                  </div>
                )
              })}
            </div>
          </div>

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
                  <th>Method</th>
                  <th>Created</th>
                </tr>
              </thead>
              <tbody>
                {dashboardTransactions.length === 0 && !dashboardLoading && (
                  <tr>
                    <td colSpan="8" className="empty-cell">
                      No transactions available for this account.
                    </td>
                  </tr>
                )}
                {dashboardTransactions.map((payment) => (
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
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}
    </section>
  )
}

export default AccountDashboard
