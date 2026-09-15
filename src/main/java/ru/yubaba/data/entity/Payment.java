package ru.yubaba.data.entity;

import jakarta.persistence.*;
import ru.yubaba.data.enums.PaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payments")
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Version
    public long version;

    @Column(nullable = false, unique = true)
    public Long orderId;

    @Column(nullable = false, precision = 14, scale = 2)
    public BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public PaymentMethod method;

    @Column(nullable = false)
    public Instant paidAt = Instant.now();

    @Column(nullable = false)
    public Long recordedBy;
}
