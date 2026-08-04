import { useEffect, useRef, useState } from 'react'

/**
 * Modal dialog that handles the two-step OTP flow:
 *  1. While `sending` is true – shows a spinner while the OTP email is dispatched.
 *  2. Once the OTP email has been sent – shows a 6-digit code entry form.
 *
 * Props:
 *  paymentId  – ID of the payment being processed
 *  sending    – true while the send-otp API call is in flight
 *  submitting – true while the process API call is in flight
 *  error      – error string to display (or empty/null)
 *  onSubmit   – callback(otpCode: string)
 *  onCancel   – callback()
 */
export default function OtpModal({ paymentId, sending, submitting, error, onSubmit, onCancel }) {
  const [otpCode, setOtpCode] = useState('')
  const inputRef = useRef(null)

  // Focus the input once the sending phase is done
  useEffect(() => {
    if (!sending && inputRef.current) {
      inputRef.current.focus()
    }
  }, [sending])

  function handleSubmit(event) {
    event.preventDefault()
    if (otpCode.length === 6) {
      onSubmit(otpCode)
    }
  }

  function handleKeyDown(event) {
    if (event.key === 'Escape') {
      onCancel()
    }
  }

  return (
    // Backdrop
    <div className="otp-backdrop" role="dialog" aria-modal="true" aria-labelledby="otp-title" onKeyDown={handleKeyDown}>
      <div className="otp-dialog">
        <h2 id="otp-title" className="otp-title">
          Verify Payment #{paymentId}
        </h2>

        {sending ? (
          <div className="otp-sending">
            <div className="otp-spinner" aria-hidden="true" />
            <p>Sending OTP to the registered email address…</p>
          </div>
        ) : (
          <>
            <p className="otp-hint">
              A 6-digit code has been sent to the email address on the source account. Enter it below to authorise
              this payment.
            </p>

            <form onSubmit={handleSubmit}>
              <input
                ref={inputRef}
                className="otp-input"
                type="text"
                inputMode="numeric"
                pattern="[0-9]{6}"
                maxLength={6}
                placeholder="000000"
                value={otpCode}
                onChange={(e) => setOtpCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
                disabled={submitting}
                aria-label="One-time password"
                autoComplete="one-time-code"
                required
              />

              {error && <p className="error-msg otp-error">{error}</p>}

              <div className="otp-actions">
                <button type="button" className="otp-cancel-btn" onClick={onCancel} disabled={submitting}>
                  Cancel
                </button>
                <button type="submit" disabled={submitting || otpCode.length !== 6}>
                  {submitting ? 'Verifying…' : 'Confirm Payment'}
                </button>
              </div>
            </form>
          </>
        )}
      </div>
    </div>
  )
}
