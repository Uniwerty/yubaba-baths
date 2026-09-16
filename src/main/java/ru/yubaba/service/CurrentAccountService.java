package ru.yubaba.service;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yubaba.data.entity.Account;
import ru.yubaba.data.repository.AccountRepository;

@Service
@Transactional(readOnly = true)
public class CurrentAccountService {
    private final AccountRepository accountRepository;

    public CurrentAccountService(
            AccountRepository accountRepository
    ) {
        this.accountRepository = accountRepository;
    }

    public Account getCurrent() {
        return accountRepository.findByLogin(SecurityContextHolder.getContext().getAuthentication().getName())
                .orElseThrow();
    }
}
