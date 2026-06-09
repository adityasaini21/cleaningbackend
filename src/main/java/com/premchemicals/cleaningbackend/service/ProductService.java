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


import java.util.List;

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

        return products.stream()
                .map(product -> {

                    ProductResponseDTO dto =
                            ProductMapper.toDTO(product);

                    dto.setAverageRating(

                            reviewRepository
                                    .getAverageRating(
                                            product.getId()
                                    ) == null

                                    ? 0.0

                                    : reviewRepository
                                    .getAverageRating(
                                            product.getId()
                                    )
                    );

                    dto.setReviewCount(

                            reviewRepository
                                    .getReviewCount(
                                            product.getId()
                                    )
                    );

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

        return productRepository
                .findActiveProductsWithCategory(pageable)
                .map(product -> {

                    ProductResponseDTO dto =
                            ProductMapper.toDTO(product);

                    dto.setAverageRating(

                            reviewRepository
                                    .getAverageRating(
                                            product.getId()
                                    ) == null

                                    ? 0.0

                                    : reviewRepository
                                    .getAverageRating(
                                            product.getId()
                                    )
                    );

                    dto.setReviewCount(

                            reviewRepository
                                    .getReviewCount(
                                            product.getId()
                                    )
                    );

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

        return productRepository
                .findDeletedProducts()
                .stream()
                .map(product -> {

                    ProductResponseDTO dto =
                            ProductMapper.toDTO(product);

                    dto.setAverageRating(

                            reviewRepository
                                    .getAverageRating(
                                            product.getId()
                                    ) == null

                                    ? 0.0

                                    : reviewRepository
                                    .getAverageRating(
                                            product.getId()
                                    )
                    );

                    dto.setReviewCount(

                            reviewRepository
                                    .getReviewCount(
                                            product.getId()
                                    )
                    );

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
    // ⚠ LOW STOCK ALERT SYSTEM
    // =========================================
    private static final int
            DEFAULT_LOW_STOCK_THRESHOLD = 10;

    public List<ProductResponseDTO>
    getLowStockProducts() {

        return productRepository
                .findByStockLessThanEqualAndActiveTrue(
                        DEFAULT_LOW_STOCK_THRESHOLD
                )
                .stream()
                .map(product -> {

                    ProductResponseDTO dto =
                            ProductMapper.toDTO(product);

                    dto.setAverageRating(

                            reviewRepository
                                    .getAverageRating(
                                            product.getId()
                                    ) == null

                                    ? 0.0

                                    : reviewRepository
                                    .getAverageRating(
                                            product.getId()
                                    )
                    );

                    dto.setReviewCount(

                            reviewRepository
                                    .getReviewCount(
                                            product.getId()
                                    )
                    );

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
}