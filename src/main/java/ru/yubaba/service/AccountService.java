package ru.yubaba.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yubaba.controller.dto.AccountInput;
import ru.yubaba.data.entity.Account;
import ru.yubaba.data.repository.AccountRepository;
import ru.yubaba.data.repository.AllocationLockRepository;

import java.util.List;

import static ru.yubaba.service.ServiceChecks.*;

@Service
@Transactional(readOnly = true)
public class AccountService {
    private final AccountRepository accountRepository;
    private final AllocationLockRepository allocationLockRepository;
    private final PasswordEncoder passwordEncoder;
    private final CurrentAccountService currentAccountService;
    private final ResourceAvailabilityService resourceAvailabilityService;

    public AccountService(
            AccountRepository accountRepository,
            AllocationLockRepository allocationLockRepository,
            PasswordEncoder passwordEncoder,
            CurrentAccountService currentAccountService,
            ResourceAvailabilityService resourceAvailabilityService
    ) {
        this.accountRepository = accountRepository;
        this.allocationLockRepository = allocationLockRepository;
        this.passwordEncoder = passwordEncoder;
        this.currentAccountService = currentAccountService;
        this.resourceAvailabilityService = resourceAvailabilityService;
    }

    public List<Account> getAccounts() {
        return accountRepository.findAll();
    }

    @Transactional
    public Account saveAccount(AccountInput input) {
        allocationLockRepository.acquire();
        var account = new Account();
        account.login = input.login();
        account.name = input.name().trim();
        account.role = input.role();
        account.password = passwordEncoder.encode(input.password());
        return accountRepository.saveAndFlush(account);
    }

    @Transactional
    public Account blockAccount(Long id, boolean blocked) {
        allocationLockRepository.acquire();
        var account = accountRepository.findById(id).orElseThrow(ServiceChecks::createNotFoundException);
        check(!account.id.equals(currentAccountService.getCurrent().id), "Нельзя заблокировать собственную учётную запись.");
        if (blocked) {
            check(
                    resourceAvailabilityService.getActiveOrders().stream().noneMatch(o -> o.attendantIds.contains(account.id)),
                    "У банщика есть активный заказ. Сначала завершите или отмените его."
            );
        }
        account.blocked = blocked;
        return accountRepository.saveAndFlush(account);
    }
}
