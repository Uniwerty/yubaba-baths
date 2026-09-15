package ru.yubaba.data.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.yubaba.data.entity.Ingredient;

public interface IngredientRepository extends JpaRepository<Ingredient, Long> {
}
