package com.premchemicals.cleaningbackend.repository;

import com.premchemicals.cleaningbackend.model.Product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ProductRepository
        extends JpaRepository<Product, Long> {

    // =========================================
    // ALL ACTIVE PRODUCTS WITH CATEGORY
    // =========================================
    @Query("""
        SELECT p FROM Product p
        JOIN FETCH p.category
        WHERE p.active = true
    """)
    List<Product> findAllActiveWithCategory();

    // =========================================
    // FIND PRODUCT BY ID
    // =========================================
    Optional<Product> findByIdAndActiveTrue(Long id);

    // =========================================
    // 🔥 FIND ANY PRODUCT (ACTIVE/DELETED)
    // =========================================
    @Query("""
        SELECT p FROM Product p
        JOIN FETCH p.category
        WHERE p.id = :id
    """)
    Optional<Product> findAnyById(Long id);

    // =========================================
    // 🔥 GET ALL DELETED PRODUCTS
    // =========================================
    @Query("""
        SELECT p FROM Product p
        JOIN FETCH p.category
        WHERE p.active = false
    """)
    List<Product> findDeletedProducts();

    // =========================================
    // SEARCH PRODUCTS
    // =========================================
    @Query("""
        SELECT p FROM Product p
        JOIN FETCH p.category
        WHERE p.active = true
        AND LOWER(p.name)
        LIKE LOWER(CONCAT('%', :keyword, '%'))
    """)
    List<Product> searchProducts(String keyword);

    // =========================================
    // CATEGORY FILTER
    // =========================================
    @Query("""
        SELECT p FROM Product p
        JOIN FETCH p.category
        WHERE LOWER(p.category.name)
        = LOWER(:categoryName)
        AND p.active = true
    """)
    List<Product> findByCategoryNameWithCategory(
            String categoryName);

    // =========================================
    // PAGINATION WITH CATEGORY FIX
    // =========================================
    @Query(
            value = """
            SELECT p FROM Product p
            JOIN FETCH p.category
            WHERE p.active = true
        """,

            countQuery = """
            SELECT COUNT(p) FROM Product p
            WHERE p.active = true
        """
    )
    Page<Product> findActiveProductsWithCategory(
            Pageable pageable);

    // =========================================
    // LOW STOCK ALERT SYSTEM
    // =========================================
    List<Product>
    findByStockLessThanEqualAndActiveTrue(
            Integer threshold);

    long countByStockLessThanEqualAndActiveTrue(
            Integer threshold);
}