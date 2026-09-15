package ru.yubaba.data.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.yubaba.data.entity.Payment;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByOrderIdIn(Collection<Long> ids);

    List<Payment> findByPaidAtGreaterThanEqualAndPaidAtLessThan(Instant from, Instant to);
}
