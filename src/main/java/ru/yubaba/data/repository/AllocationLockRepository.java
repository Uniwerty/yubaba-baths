package ru.yubaba.data.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import ru.yubaba.data.entity.AllocationLock;

public interface AllocationLockRepository extends JpaRepository<AllocationLock, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
        select l from AllocationLock l
        where l.id = 1
        """
    )
    AllocationLock acquire();
}
