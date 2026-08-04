package com.example.paymentprocessing.service;

import java.util.List;
import org.springframework.stereotype.Service;
import com.example.paymentprocessing.model.Account;
import com.example.paymentprocessing.repository.AccountRepository;

@Service
public class AccountService {
    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public List<Account> getAllAccounts() {
        return accountRepository.findAll();
    }
}
