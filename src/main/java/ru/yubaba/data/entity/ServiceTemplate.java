package ru.yubaba.data.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "service_templates")
public class ServiceTemplate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Version
    public long version;

    @Column(nullable = false)
    public String name;

    @Column(nullable = false)
    public String bathType;

    public Long preferredRoomId;

    public int attendants;

    public int durationMinutes;

    public int preparationMinutes;

    public int breakMinutes;

    public int temperature;

    @Column(nullable = false, length = 4000)
    public String steps;

    @Column(nullable = false, length = 2000)
    public String extraServices;

    @Column(nullable = false, precision = 14, scale = 2)
    public BigDecimal basePrice;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "template_lines", joinColumns = @JoinColumn(name = "template_id"))
    @OrderColumn(name = "line_no")
    public List<RecipeLine> lines = new ArrayList<>();
}
