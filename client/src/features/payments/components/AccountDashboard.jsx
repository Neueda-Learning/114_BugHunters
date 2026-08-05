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

const STATUS_ORDER = ['CREATED', 'VALIDATED', 'SENT', 'COMPLETED', 'FAILED']
const STATUS_COLORS = {
  CREATED: '#7ab8ff',
  VALIDATED: '#7fd39b',
  SENT: '#ffd27b',
  COMPLETED: '#44b78b',
  FAILED: '#ff8f8f',
}

function getDateKey(value) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return null
  }
  return date.toISOString().slice(0, 10)
}

function getDayLabel(value) {
  const date = new Date(value)
  return date.toLocaleDateString(undefined, { weekday: 'short' })
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

  const totalIncomingAmount = useMemo(
    () => incomingTransactions.reduce((sum, payment) => sum + normalizeAmount(payment.amount), 0),
    [incomingTransactions],
  )

  const statusBreakdown = useMemo(() => {
    const counts = STATUS_ORDER.reduce((accumulator, status) => {
      accumulator[status] = 0
      return accumulator
    }, {})

    dashboardTransactions.forEach((payment) => {
      const status = String(payment.status || '').trim().toUpperCase()
      if (Object.hasOwn(counts, status)) {
        counts[status] += 1
      }
    })

    return STATUS_ORDER.map((status) => {
      const count = counts[status]
      const percent = dashboardTransactions.length === 0 ? 0 : (count / dashboardTransactions.length) * 100
      return {
        status,
        count,
        percent,
        color: STATUS_COLORS[status],
      }
    })
  }, [dashboardTransactions])

  const statusPieBackground = useMemo(() => {
    const segments = []
    let current = 0

    statusBreakdown.forEach((item) => {
      if (item.percent <= 0) {
        return
      }

      const next = current + item.percent
      segments.push(`${item.color} ${current}% ${next}%`)
      current = next
    })

    if (segments.length === 0) {
      return '#e4ecf8'
    }

    return `conic-gradient(${segments.join(', ')})`
  }, [statusBreakdown])

  const failedCount = useMemo(
    () => statusBreakdown.find((item) => item.status === 'FAILED')?.count || 0,
    [statusBreakdown],
  )

  const completedCount = useMemo(
    () => statusBreakdown.find((item) => item.status === 'COMPLETED')?.count || 0,
    [statusBreakdown],
  )

  const completionRate = useMemo(() => {
    if (dashboardTransactions.length === 0) {
      return 0
    }
    return (completedCount / dashboardTransactions.length) * 100
  }, [completedCount, dashboardTransactions])

  const transactionTrend = useMemo(() => {
    const days = Array.from({ length: 7 }, (_, index) => {
      const date = new Date()
      date.setHours(0, 0, 0, 0)
      date.setDate(date.getDate() - (6 - index))
      const key = date.toISOString().slice(0, 10)
      return {
        key,
        label: getDayLabel(date),
        amount: 0,
      }
    })

    const dayMap = Object.fromEntries(days.map((day) => [day.key, day]))

    outgoingTransactions.forEach((payment) => {
      const key = getDateKey(payment.createdAt)
      if (!key || !dayMap[key]) {
        return
      }

      dayMap[key].amount += normalizeAmount(payment.amount)
    })

    return days
  }, [outgoingTransactions])

  const maxTrendAmount = useMemo(
    () => transactionTrend.reduce((max, day) => (day.amount > max ? day.amount : max), 0),
    [transactionTrend],
  )

  const selectedAccountBalance = useMemo(
    () => accounts.find((account) => account.accountNumber === dashboardSelectedAccountId)?.balance,
    [accounts, dashboardSelectedAccountId],
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
              <p>Current Account Balance</p>
              <h3>{selectedAccountBalance == null ? '-' : formatAmount(selectedAccountBalance)}</h3>
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
            <article className="metric-card">
              <p>Total Incoming Amount</p>
              <h3>{formatAmount(totalIncomingAmount)}</h3>
            </article>
            <article className="metric-card">
              <p>Failed Transactions</p>
              <h3>{failedCount}</h3>
            </article>
            <article className="metric-card">
              <p>Completion Rate</p>
              <h3>{completionRate.toFixed(1)}%</h3>
            </article>
            <article className="metric-card">
              <p>Net Flow (In - Out)</p>
              <h3>{formatAmount(totalIncomingAmount - totalOutgoingAmount)}</h3>
            </article>
          </div>

          <div className="dashboard-graphs">
            <div className="chart-panel">
              <h3>Status Distribution</h3>
              <div className="pie-layout">
                <div
                  className="pie-chart"
                  style={{ background: statusPieBackground }}
                  role="img"
                  aria-label="Pie chart of transaction statuses"
                />
                <ul className="pie-legend">
                  {statusBreakdown.map((item) => (
                    <li key={item.status}>
                      <span className="legend-dot" style={{ backgroundColor: item.color }} />
                      <span>{item.status}</span>
                      <span>
                        {item.count} ({item.percent.toFixed(1)}%)
                      </span>
                    </li>
                  ))}
                </ul>
              </div>
            </div>

            <div className="chart-panel">
              <h3>7-Day Outgoing Trend</h3>
              <div className="trend-chart" role="img" aria-label="Bar chart of outgoing amount over the last seven days">
                {transactionTrend.map((day) => {
                  const height = maxTrendAmount === 0 ? 0 : Math.round((day.amount / maxTrendAmount) * 100)
                  return (
                    <div key={day.key} className="trend-column" title={`${day.label}: ${formatAmount(day.amount)}`}>
                      <div className="trend-track">
                        <div className="trend-fill" style={{ height: `${height}%` }} />
                      </div>
                      <p>{day.label}</p>
                    </div>
                  )
                })}
              </div>
            </div>
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
