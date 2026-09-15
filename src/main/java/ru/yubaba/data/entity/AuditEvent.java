package ru.yubaba.data.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "audit_events")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Version
    public long version;

    @Column(nullable = false)
    public Long orderId;

    @Column(nullable = false)
    public Long actorId;

    @Column(nullable = false)
    public String action;

    @Column(nullable = false, length = 2000)
    public String details;

    @Column(nullable = false)
    public Instant occurredAt = Instant.now();
}
