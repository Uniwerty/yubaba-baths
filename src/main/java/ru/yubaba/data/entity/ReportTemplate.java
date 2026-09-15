package ru.yubaba.data.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "report_templates")
public class ReportTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Version
    public long version;

    @Column(nullable = false)
    public Long ownerId;

    @Column(nullable = false)
    public String name;

    @Column(nullable = false, length = 2000)
    public String parameters;
}
