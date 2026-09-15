package ru.yubaba.data.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.yubaba.data.entity.Client;

public interface ClientRepository extends JpaRepository<Client, Long> {
}
