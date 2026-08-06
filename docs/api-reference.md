# API Reference

Base path: /api

## Error Response Format

Most handled API errors return:

```json
{
  "errorCode": "SOME_CODE",
  "message": "Human readable message",
  "timestamp": "2026-08-06T10:00:00"
}
```

## Payment Endpoints

### Create Payment

- Method: POST
- Path: /api/payments

Request body example:

```json
{
  "amount": 250.0,
  "currency": "USD",
  "accountFrom": "ACC001",
  "accountTo": "ACC002",
  "type": "TRANSFER"
}
```

Success:

- 201 Created

### List Payments

- Method: GET
- Path: /api/payments
- Optional query: status=CREATED|VALIDATED|SENT|COMPLETED|FAILED

Success:

- 200 OK

### Get Payment by ID

- Method: GET
- Path: /api/payments/{id}

Success:

- 200 OK

### Get Payment History

- Method: GET
- Path: /api/payments/{id}/history

Success:

- 200 OK

### Update Payment Status

- Method: PATCH
- Path: /api/payments/{id}/status
- Required query: status=CREATED|VALIDATED|SENT|COMPLETED|FAILED

Success:

- 200 OK

### Validate Payment

- Method: POST
- Path: /api/payments/{id}/validate

Success:

- 200 OK

### Send OTP

- Method: POST
- Path: /api/payments/{id}/send-otp

Success:

- 200 OK

### Process Payment (OTP verification + transfer)

- Method: POST
- Path: /api/payments/{id}/process

Request body:

```json
{
  "otpCode": "123456"
}
```

Success:

- 200 OK

Typical failures:

- 401 Unauthorized with errorCode OTP_VERIFICATION_FAILED
- 400 Bad Request for invalid transitions/validation

## Account Endpoints

### List Accounts

- Method: GET
- Path: /api/accounts

Success:

- 200 OK

## Curl Examples

Create payment:

```bash
curl -X POST http://localhost:8080/api/payments \
  -H "Content-Type: application/json" \
  -d '{"amount":250.0,"currency":"USD","accountFrom":"ACC001","accountTo":"ACC002","type":"TRANSFER"}'
```

Validate:

```bash
curl -X POST http://localhost:8080/api/payments/1/validate
```

Send OTP:

```bash
curl -X POST http://localhost:8080/api/payments/1/send-otp
```

Process OTP:

```bash
curl -X POST http://localhost:8080/api/payments/1/process \
  -H "Content-Type: application/json" \
  -d '{"otpCode":"123456"}'
```
