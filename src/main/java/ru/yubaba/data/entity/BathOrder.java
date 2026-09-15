package ru.yubaba.data.entity;

import jakarta.persistence.*;
import ru.yubaba.data.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "bath_orders")
public class BathOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Version
    public long version;

    public Long clientId;

    public Long templateId;

    @Column(nullable = false)
    public String serviceName;

    @Column(nullable = false)
    public String bathType;

    public Long preferredRoomId;

    public Long roomId;

    public int attendants;

    public int visitors;

    public int durationMinutes;

    public int preparationMinutes;

    public int breakMinutes;

    public int temperature;

    public int priority;

    @Column(nullable = false, length = 4000)
    public String steps;

    @Column(nullable = false, length = 2000)
    public String extraServices;

    @Column(nullable = false, precision = 14, scale = 2)
    public BigDecimal basePrice;

    @Column(nullable = false, precision = 14, scale = 2)
    public BigDecimal total;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public OrderStatus status = OrderStatus.CREATED;

    @Column(nullable = false)
    public Instant createdAt = Instant.now();

    public Instant launchedAt;

    public Instant waterReadyAt;

    public Instant serviceStartedAt;

    public Instant completedAt;

    public Instant closedAt;

    @Column(length = 1000)
    public String cancellationReason;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "order_lines", joinColumns = @JoinColumn(name = "order_id"))
    @OrderColumn(name = "line_no")
    public List<RecipeLine> lines = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "order_attendants", joinColumns = @JoinColumn(name = "order_id"))
    @Column(name = "account_id")
    public Set<Long> attendantIds = new LinkedHashSet<>();
}
