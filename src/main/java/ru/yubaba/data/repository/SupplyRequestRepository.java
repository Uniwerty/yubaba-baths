package ru.yubaba.data.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.yubaba.data.entity.SupplyRequest;

public interface SupplyRequestRepository extends JpaRepository<SupplyRequest, Long> {
}
