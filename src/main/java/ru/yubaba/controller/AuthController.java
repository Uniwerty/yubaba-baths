package ru.yubaba.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.bind.annotation.*;
import ru.yubaba.data.repository.AccountRepository;
import ru.yubaba.service.BusinessException;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AccountRepository accounts;
    private final PasswordEncoder passwords;
    private final JwtEncoder encoder;

    public AuthController(AccountRepository accounts, PasswordEncoder passwords, JwtEncoder encoder) {
        this.accounts = accounts;
        this.passwords = passwords;
        this.encoder = encoder;
    }

    @PostMapping("/login")
    public Object login(@Valid @RequestBody Requests.Login input) {
        var a = accounts.findByLogin(input.login())
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "Неверный логин или пароль."));
        if (a.blocked || !passwords.matches(input.password(), a.password)) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Неверный логин или пароль.");
        }
        var claims = JwtClaimsSet.builder()
                .issuer("yubaba")
                .subject(a.login)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plus(Duration.ofHours(12)))
                .build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return Map.of("token", token, "user", a);
    }

    @GetMapping("/me")
    public Object me(Authentication auth) {
        return accounts.findByLogin(auth.getName()).orElseThrow();
    }
}
