package ru.yubaba.data.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "clients")
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Version
    public long version;

    @Column(nullable = false)
    public String name;

    @Column(nullable = false, unique = true)
    public String contact;

    @Column(nullable = false)
    public String notes = "";
}
