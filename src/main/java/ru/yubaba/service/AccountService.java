package ru.yubaba.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yubaba.controller.dto.AccountInput;
import ru.yubaba.data.entity.Account;
import ru.yubaba.data.repository.AccountRepository;

import java.util.List;

import static ru.yubaba.service.ServiceChecks.*;

@Service
@Transactional(readOnly = true)
public class AccountService {
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final CurrentAccountService currentAccountService;

    public AccountService(
            AccountRepository accountRepository,
            PasswordEncoder passwordEncoder,
            CurrentAccountService currentAccountService
    ) {
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.currentAccountService = currentAccountService;
    }

    public List<Account> getAccounts() {
        return accountRepository.findAll();
    }

    @Transactional
    public Account saveAccount(AccountInput input) {
        var account = new Account();
        account.login = input.login();
        account.name = input.name().trim();
        account.role = input.role();
        account.password = passwordEncoder.encode(input.password());
        return accountRepository.saveAndFlush(account);
    }

    @Transactional
    public Account blockAccount(Long id, boolean blocked) {
        var account = accountRepository.findById(id).orElseThrow(ServiceChecks::createNotFoundException);
        check(!account.id.equals(currentAccountService.getCurrent().id), "Нельзя заблокировать собственную учётную запись.");
        if (blocked) {
            check(
                    account.activeOrderId == null,
                    "У банщика есть активный заказ. Сначала завершите или отмените его."
            );
        }
        account.blocked = blocked;
        return accountRepository.saveAndFlush(account);
    }
}
