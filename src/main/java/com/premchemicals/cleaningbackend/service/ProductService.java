package com.premchemicals.cleaningbackend.service;

import com.premchemicals.cleaningbackend.dto.ProductMapper;
import com.premchemicals.cleaningbackend.dto.ProductRequestDTO;
import com.premchemicals.cleaningbackend.dto.ProductResponseDTO;

import com.premchemicals.cleaningbackend.model.Category;
import com.premchemicals.cleaningbackend.model.Product;

import com.premchemicals.cleaningbackend.repository.CategoryRepository;
import com.premchemicals.cleaningbackend.repository.ProductRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.transaction.annotation.Transactional;

import org.springframework.stereotype.Service;

import org.springframework.http.HttpStatus;

import org.springframework.web.server.ResponseStatusException;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.premchemicals.cleaningbackend.repository.ReviewRepository;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    private final CategoryRepository categoryRepository;
    private final ReviewRepository reviewRepository;

    // =========================================
    // 🔥 MAIN PRODUCT LIST
    // =========================================
    public List<ProductResponseDTO> getProducts(
            String categoryName,
            String search
    ) {

        List<Product> products;

        // SEARCH
        if (search != null &&
                !search.trim().isEmpty()) {

            products = productRepository
                    .searchProducts(search.trim());
        }

        // CATEGORY FILTER
        else if (categoryName != null &&
                !categoryName.trim().isEmpty()) {

            products = productRepository
                    .findByCategoryNameWithCategory(
                            categoryName.trim()
                    );
        }

        // ALL PRODUCTS
        else {

            products = productRepository
                    .findAllActiveWithCategory();
        }

        List<Long> productIds = products.stream().map(Product::getId).toList();
        Map<Long, RatingStats> statsMap = getRatingStatsMap(productIds);

        return products.stream()
                .map(product -> {
                    ProductResponseDTO dto = ProductMapper.toDTO(product);
                    RatingStats stats = statsMap.get(product.getId());
                    dto.setAverageRating(stats != null ? stats.averageRating : 0.0);
                    dto.setReviewCount(stats != null ? stats.reviewCount : 0L);
                    return dto;
                })
                .toList();
    }

    // =========================================
    // 📦 PAGINATED PRODUCTS
    // =========================================
    public Page<ProductResponseDTO> getAllProducts(
            Pageable pageable
    ) {

        Page<Product> page = productRepository.findActiveProductsWithCategory(pageable);
        List<Long> productIds = page.getContent().stream().map(Product::getId).toList();
        Map<Long, RatingStats> statsMap = getRatingStatsMap(productIds);

        return page.map(product -> {
            ProductResponseDTO dto = ProductMapper.toDTO(product);
            RatingStats stats = statsMap.get(product.getId());
            dto.setAverageRating(stats != null ? stats.averageRating : 0.0);
            dto.setReviewCount(stats != null ? stats.reviewCount : 0L);
            return dto;
        });
    }

    // =========================================
    // ➕ CREATE PRODUCT
    // =========================================
    public ProductResponseDTO createProduct(
            ProductRequestDTO request
    ) {

        Category category =
                categoryRepository
                        .findById(request.getCategoryId())
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Category not found"
                                )
                        );

        Product product =
                ProductMapper.toEntity(
                        request,
                        category
                );

        product.setActive(true);

        Product savedProduct =
                productRepository.save(product);

        return ProductMapper.toDTO(savedProduct);
    }

    // =========================================
    // 🔍 GET PRODUCT BY ID
    // =========================================
    public ProductResponseDTO getProductById(
            Long id
    ) {

        Product product =
                productRepository
                        .findByIdAndActiveTrue(id)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Product not found"
                                )
                        );

        return ProductMapper.toDTO(product);
    }

    // =========================================
    // ✏ UPDATE PRODUCT
    // =========================================
    @Transactional
    public ProductResponseDTO updateProduct(
            Long id,
            ProductRequestDTO request
    ) {

        Product product =
                productRepository
                        .findByIdAndActiveTrue(id)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Product not found"
                                )
                        );

        Category category =
                categoryRepository
                        .findById(request.getCategoryId())
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Category not found"
                                )
                        );

        product.setName(request.getName());

        product.setDescription(
                request.getDescription()
        );

        product.setPrice(
                request.getPrice()
        );

        product.setCostPrice(
                request.getCostPrice()
        );

        product.setStock(
                request.getStock()
        );

        product.setImageUrl(
                request.getImageUrl()
        );

        product.setCategory(category);

        Product savedProduct =
                productRepository.save(product);

        return ProductMapper.toDTO(savedProduct);
    }

    // =========================================
    // 🗑 SOFT DELETE PRODUCT
    // =========================================
    public void deleteProduct(Long id) {

        Product product =
                productRepository
                        .findByIdAndActiveTrue(id)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Product not found"
                                )
                        );

        product.setActive(false);

        productRepository.save(product);
    }

    // =========================================
    // 🔥 GET DELETED PRODUCTS
    // =========================================
    public List<ProductResponseDTO> getDeletedProducts() {

        List<Product> products = productRepository.findDeletedProducts();
        List<Long> productIds = products.stream().map(Product::getId).toList();
        Map<Long, RatingStats> statsMap = getRatingStatsMap(productIds);

        return products.stream()
                .map(product -> {
                    ProductResponseDTO dto = ProductMapper.toDTO(product);
                    RatingStats stats = statsMap.get(product.getId());
                    dto.setAverageRating(stats != null ? stats.averageRating : 0.0);
                    dto.setReviewCount(stats != null ? stats.reviewCount : 0L);
                    return dto;
                })
                .toList();
    }

    // =========================================
// 🔥 RESTORE PRODUCT
// =========================================
    @Transactional
    public ProductResponseDTO restoreProduct(
            Long id
    ) {

        Product product =
                productRepository
                        .findAnyById(id)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Product not found"
                                )
                        );

        product.setActive(true);

        Product restoredProduct =
                productRepository.save(product);

        // 🔥 FORCE CATEGORY LOAD
        restoredProduct.getCategory().getName();

        return ProductMapper.toDTO(restoredProduct);
    }

    // =========================================
    // 🔄 UPDATE PRODUCT STATUS (ACTIVATE / DEACTIVATE)
    // =========================================
    @Transactional
    public ProductResponseDTO setProductStatus(Long id, boolean active) {
        Product product = productRepository.findAnyById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Product not found"
                ));

        product.setActive(active);
        Product savedProduct = productRepository.save(product);
        if (savedProduct.getCategory() != null) {
            savedProduct.getCategory().getName();
        }
        return ProductMapper.toDTO(savedProduct);
    }

    // =========================================
    // ⚠ LOW STOCK ALERT SYSTEM
    // =========================================
    private static final int
            DEFAULT_LOW_STOCK_THRESHOLD = 10;

    public List<ProductResponseDTO>
    getLowStockProducts() {

        List<Product> products = productRepository
                .findByStockLessThanEqualAndActiveTrue(
                        DEFAULT_LOW_STOCK_THRESHOLD
                );
        List<Long> productIds = products.stream().map(Product::getId).toList();
        Map<Long, RatingStats> statsMap = getRatingStatsMap(productIds);

        return products.stream()
                .map(product -> {
                    ProductResponseDTO dto = ProductMapper.toDTO(product);
                    RatingStats stats = statsMap.get(product.getId());
                    dto.setAverageRating(stats != null ? stats.averageRating : 0.0);
                    dto.setReviewCount(stats != null ? stats.reviewCount : 0L);
                    return dto;
                })
                .toList();
    }

    public long getLowStockCount() {

        return productRepository
                .countByStockLessThanEqualAndActiveTrue(
                        DEFAULT_LOW_STOCK_THRESHOLD
                );
    }

    private Map<Long, RatingStats> getRatingStatsMap(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Object[]> rows = reviewRepository.getRatingStatsForProductIds(productIds);
        Map<Long, RatingStats> map = new HashMap<>();
        for (Object[] row : rows) {
            if (row != null && row.length >= 3 && row[0] != null) {
                Long productId = ((Number) row[0]).longValue();
                Double avgRating = row[1] != null ? ((Number) row[1]).doubleValue() : 0.0;
                Long count = row[2] != null ? ((Number) row[2]).longValue() : 0L;
                map.put(productId, new RatingStats(avgRating, count));
            }
        }
        return map;
    }

    private static class RatingStats {
        final Double averageRating;
        final Long reviewCount;

        RatingStats(Double averageRating, Long reviewCount) {
            this.averageRating = averageRating;
            this.reviewCount = reviewCount;
        }
    }
}