package com.example.paymentprocessing.repository;

import java.util.Optional;

import com.example.paymentprocessing.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

@Repository
public interface AccountRepository extends JpaRepository<Account, String> {

    /**
     * Locks the account row for the duration of the current transaction so that
     * concurrent payment processing cannot read/update stale balances (prevents
     * lost-update / double-spend races between simultaneous transfers).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.accountNumber = :accountNumber")
    Optional<Account> findByAccountNumberForUpdate(@Param("accountNumber") String accountNumber);
}
