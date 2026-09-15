package ru.yubaba.data.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.yubaba.data.entity.AuditEvent;

import java.util.List;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    List<AuditEvent> findByOrderIdOrderByOccurredAtAsc(Long orderId);
}
