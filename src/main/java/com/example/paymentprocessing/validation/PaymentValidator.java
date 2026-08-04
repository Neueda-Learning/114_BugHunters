package com.example.paymentprocessing.validation;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.example.paymentprocessing.enums.PaymentStatus;
import com.example.paymentprocessing.exception.InvalidStatusTransitionException;
import com.example.paymentprocessing.model.Account;
import com.example.paymentprocessing.model.Payment;
import com.example.paymentprocessing.repository.AccountRepository;
import com.example.paymentprocessing.repository.PaymentRepository;

/**
 * Centralises all business validation rules for payments:
 * amount, currency, account, idempotency and status transition checks.
 *
 * Validation failures are reported as {@link ResponseStatusException}s whose
 * reason string is prefixed with the error code defined in the project
 * specification (e.g. INVALID_AMOUNT, INVALID_CURRENCY, ...).
 */
@Component
public class PaymentValidator {

    private static final BigDecimal MAX_AMOUNT = new BigDecimal("1000000");
    private static final int MAX_DECIMAL_PLACES = 2;

    // Example set of currencies supported by the system (ISO 4217 codes).
    private static final Set<String> SUPPORTED_CURRENCIES = Set.of("USD", "EUR", "GBP");

    // Simple alphanumeric account number format, 6-20 characters.
    private static final Pattern ACCOUNT_NUMBER_PATTERN = Pattern.compile("^[A-Za-z0-9]{6,20}$");

    private static final Map<PaymentStatus, Set<PaymentStatus>> VALID_TRANSITIONS = Map.of(
            PaymentStatus.CREATED, Set.of(PaymentStatus.VALIDATED, PaymentStatus.FAILED),
            PaymentStatus.VALIDATED, Set.of(PaymentStatus.SENT, PaymentStatus.FAILED),
            PaymentStatus.SENT, Set.of(PaymentStatus.COMPLETED, PaymentStatus.FAILED),
            PaymentStatus.COMPLETED, Set.of(),
            PaymentStatus.FAILED, Set.of());

    private final AccountRepository accountRepository;
    private final PaymentRepository paymentRepository;

    public PaymentValidator(AccountRepository accountRepository, PaymentRepository paymentRepository) {
        this.accountRepository = accountRepository;
        this.paymentRepository = paymentRepository;
    }

    /**
     * Runs every validation rule that applies to a newly submitted payment.
     */
    public void validateNewPayment(Payment payment) {
        validateIdempotencyKey(payment.getKey());
        validateCurrency(payment.getCurrency());
        validateAmount(payment.getAmount());
        Account fromAccount = validateAccounts(payment.getAccountFrom(), payment.getAccountTo());
        validateSufficientFunds(fromAccount, payment.getAmount());
    }

    public void validateAmount(double amount) {
        BigDecimal value = BigDecimal.valueOf(amount);

        if (value.compareTo(BigDecimal.ZERO) <= 0) {
            throw invalidAmount("Amount must be greater than 0");
        }
        if (value.compareTo(MAX_AMOUNT) > 0) {
            throw invalidAmount("Amount must not exceed " + MAX_AMOUNT);
        }
        if (value.stripTrailingZeros().scale() > MAX_DECIMAL_PLACES) {
            throw invalidAmount("Amount must have a maximum of " + MAX_DECIMAL_PLACES + " decimal places");
        }
    }

    public void validateCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            throw invalidCurrency("Currency is required");
        }

        String code = currency.trim().toUpperCase();
        try {
            Currency.getInstance(code);
        } catch (IllegalArgumentException ex) {
            throw invalidCurrency("Currency code '" + currency + "' is not a valid ISO 4217 code");
        }

        if (!SUPPORTED_CURRENCIES.contains(code)) {
            throw invalidCurrency("Currency '" + code + "' is not supported");
        }
    }

    /**
     * Validates the source and destination accounts and returns the source account.
     */
    public Account validateAccounts(String accountFrom, String accountTo) {
        if (accountFrom == null || accountFrom.isBlank() || accountTo == null || accountTo.isBlank()) {
            throw invalidAccount("Source and destination accounts are required");
        }
        if (accountFrom.equalsIgnoreCase(accountTo)) {
            throw invalidAccount("Source and destination accounts must be different");
        }
        if (!ACCOUNT_NUMBER_PATTERN.matcher(accountFrom).matches()) {
            throw invalidAccount("Source account number format is invalid");
        }
        if (!ACCOUNT_NUMBER_PATTERN.matcher(accountTo).matches()) {
            throw invalidAccount("Destination account number format is invalid");
        }

        Account fromAccount = accountRepository.findById(accountFrom)
                .orElseThrow(() -> invalidAccount("Source account does not exist: " + accountFrom));
        if (!accountRepository.existsById(accountTo)) {
            throw invalidAccount("Destination account does not exist: " + accountTo);
        }

        return fromAccount;
    }

    public void validateSufficientFunds(Account fromAccount, double amount) {
        if (BigDecimal.valueOf(fromAccount.getBalance()).compareTo(BigDecimal.valueOf(amount)) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "INSUFFICIENT_FUNDS: Source account has insufficient funds");
        }
    }

    public void validateIdempotencyKey(String key) {
        if (key == null || key.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "VALIDATION_FAILED: Idempotency key is required");
        }
        if (paymentRepository.findByKey(key).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "DUPLICATE_PAYMENT: Payment with idempotency key '" + key + "' already exists");
        }
    }

    public void validateStatusTransition(PaymentStatus current, PaymentStatus target) {
        if (target == null) {
            throw invalidTransition("Target status is required");
        }
        if (current == null || !VALID_TRANSITIONS.getOrDefault(current, Set.of()).contains(target)) {
            throw invalidTransition("Cannot transition payment from " + current + " to " + target);
        }
    }

    private ResponseStatusException invalidAmount(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_AMOUNT: " + message);
    }

    private ResponseStatusException invalidCurrency(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_CURRENCY: " + message);
    }

    private ResponseStatusException invalidAccount(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_ACCOUNT: " + message);
    }

    private InvalidStatusTransitionException invalidTransition(String message) {
        return new InvalidStatusTransitionException(message);
    }
}
