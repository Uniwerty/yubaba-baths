package ru.yubaba.data.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "rooms")
public class Room {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Version
    public long version;

    @Column(nullable = false, unique = true)
    public String name;

    @Column(nullable = false)
    public String bathType;

    public int capacity;
}
