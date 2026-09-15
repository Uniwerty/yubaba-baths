package ru.yubaba.data.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "ingredients")
public class Ingredient {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Version
    public long version;

    @Column(nullable = false, unique = true)
    public String name;

    @Column(nullable = false)
    public String unit;

    @Column(nullable = false, precision = 14, scale = 3)
    public BigDecimal stock;

    @Column(nullable = false, precision = 14, scale = 3)
    public BigDecimal reserved = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 3)
    public BigDecimal threshold;

    @Column(nullable = false, precision = 14, scale = 2)
    public BigDecimal price;
}
