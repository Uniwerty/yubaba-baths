package ru.yubaba.data.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import ru.yubaba.data.entity.BathOrder;
import ru.yubaba.data.enums.OrderStatus;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface BathOrderRepository extends JpaRepository<BathOrder, Long>, JpaSpecificationExecutor<BathOrder> {

    List<BathOrder> findByStatusIn(Collection<OrderStatus> statuses);

    List<BathOrder> findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(Instant from, Instant to);
}
