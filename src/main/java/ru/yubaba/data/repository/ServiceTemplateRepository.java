package ru.yubaba.data.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.yubaba.data.entity.ServiceTemplate;

public interface ServiceTemplateRepository extends JpaRepository<ServiceTemplate, Long> {
}
