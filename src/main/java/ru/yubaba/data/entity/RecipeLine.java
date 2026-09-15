package ru.yubaba.data.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.math.BigDecimal;

@Embeddable
public class RecipeLine {

    @Column(nullable = false)
    public Long ingredientId;

    @Column(nullable = false)
    public String name;

    @Column(nullable = false)
    public String unit;

    @Column(nullable = false, precision = 14, scale = 3)
    public BigDecimal quantity;

    @Column(nullable = false, precision = 14, scale = 2)
    public BigDecimal unitPrice;

    public RecipeLine() {
    }

    public RecipeLine(Ingredient i, BigDecimal quantity) {
        ingredientId = i.id;
        name = i.name;
        unit = i.unit;
        this.quantity = quantity;
        unitPrice = i.price;
    }
}
