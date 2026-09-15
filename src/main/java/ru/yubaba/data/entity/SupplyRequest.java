package ru.yubaba.data.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "supply_requests")
public class SupplyRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Version
    public long version;

    @Column(nullable = false)
    public Long ingredientId;

    @Column(nullable = false)
    public String ingredientName;

    @Column(nullable = false)
    public String unit;

    @Column(nullable = false, precision = 14, scale = 3)
    public BigDecimal quantity;

    @Column(nullable = false)
    public String status = "OPEN";

    @Column(nullable = false)
    public Instant createdAt = Instant.now();

    public Instant receivedAt;
}
