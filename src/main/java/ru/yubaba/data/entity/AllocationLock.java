package ru.yubaba.data.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "allocation_lock")
public class AllocationLock {

    @Id
    public Long id;
}
