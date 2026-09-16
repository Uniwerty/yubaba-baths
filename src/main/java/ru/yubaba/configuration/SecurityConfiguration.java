package ru.yubaba.configuration;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import ru.yubaba.data.repository.AccountRepository;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {
    @Bean
    PasswordEncoder passwords() {
        return new BCryptPasswordEncoder();
    }

    private SecretKeySpec key(String secret) {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("JWT_SECRET must contain at least 32 bytes");
        }
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Bean
    JwtEncoder encoder(@Value("${app.jwt-secret}") String secret) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key(secret)));
    }

    @Bean
    JwtDecoder decoder(@Value("${app.jwt-secret}") String secret) {
        var decoder = NimbusJwtDecoder.withSecretKey(key(secret))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer("yubaba"));
        return decoder;
    }

    @Bean
    SecurityFilterChain security(HttpSecurity http, AccountRepository accounts) throws Exception {
        return http.csrf(c -> c.disable())
                .sessionManagement(s ->
                        s.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(a ->
                        a.requestMatchers("/api/auth/login", "/", "/index.html", "/assets/**", "/favicon.svg")
                                .permitAll()
                                .anyRequest()
                                .authenticated()
                )
                .oauth2ResourceServer(o ->
                        o.jwt(j ->
                                j.jwtAuthenticationConverter(jwt -> {
                                    var account = accounts.findByLogin(jwt.getSubject())
                                            .orElseThrow(() -> new DisabledException("Аккаунт недоступен"));
                                    if (account.blocked) {
                                        throw new DisabledException("Аккаунт заблокирован");
                                    }
                                    return new JwtAuthenticationToken(
                                            jwt,
                                            List.of(new SimpleGrantedAuthority("ROLE_" + account.role)),
                                            account.login
                                    );
                                })
                        ).authenticationEntryPoint((req, res, e) -> {
                            res.setStatus(401);
                            res.setContentType("application/json;charset=UTF-8");
                            res.getWriter().write("{\"message\":\"Войдите в систему повторно.\"}");
                        })
                )
                .exceptionHandling(e ->
                        e.accessDeniedHandler((req, res, ex) -> {
                            res.setStatus(403);
                            res.setContentType("application/json;charset=UTF-8");
                            res.getWriter().write("{\"message\":\"Недостаточно прав для этого действия.\"}");
                        })
                )
                .build();
    }
}
