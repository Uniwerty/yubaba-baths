package ru.yubaba.data.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.yubaba.data.entity.Room;

public interface RoomRepository extends JpaRepository<Room, Long> {
}
