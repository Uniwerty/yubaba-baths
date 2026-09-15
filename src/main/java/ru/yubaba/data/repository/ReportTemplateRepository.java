package ru.yubaba.data.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.yubaba.data.entity.ReportTemplate;

public interface ReportTemplateRepository extends JpaRepository<ReportTemplate, Long> {
}
