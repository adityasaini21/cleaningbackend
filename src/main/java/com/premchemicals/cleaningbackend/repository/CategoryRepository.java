package com.premchemicals.cleaningbackend.repository;

import com.premchemicals.cleaningbackend.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {
}
