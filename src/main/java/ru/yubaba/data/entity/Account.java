package ru.yubaba.data.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import ru.yubaba.data.enums.Role;

import java.time.Instant;

@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Version
    public long version;

    @Column(nullable = false, unique = true)
    public String login;

    @JsonIgnore
    @Column(nullable = false)
    public String password;

    @Column(nullable = false)
    public String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public Role role;

    public boolean blocked;

    public Instant restUntil;

    @JsonIgnore
    public Long activeOrderId;
}
