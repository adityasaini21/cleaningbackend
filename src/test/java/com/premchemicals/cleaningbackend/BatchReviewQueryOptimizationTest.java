package com.premchemicals.cleaningbackend;

import com.premchemicals.cleaningbackend.dto.ProductResponseDTO;
import com.premchemicals.cleaningbackend.model.Category;
import com.premchemicals.cleaningbackend.model.Product;
import com.premchemicals.cleaningbackend.repository.CategoryRepository;
import com.premchemicals.cleaningbackend.repository.ProductRepository;
import com.premchemicals.cleaningbackend.repository.ReviewRepository;
import com.premchemicals.cleaningbackend.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

public class BatchReviewQueryOptimizationTest {

    private ProductRepository productRepository;
    private CategoryRepository categoryRepository;
    private ReviewRepository reviewRepository;
    private ProductService productService;

    private Category testCategory;
    private Product product1;
    private Product product2;
    private Product product3;

    @BeforeEach
    void setUp() {
        productRepository = mock(ProductRepository.class);
        categoryRepository = mock(CategoryRepository.class);
        reviewRepository = mock(ReviewRepository.class);
        productService = new ProductService(productRepository, categoryRepository, reviewRepository);

        testCategory = new Category(1L, "Cleaners");
        product1 = Product.builder().id(101L).name("Floor Cleaner").price(199.0).active(true).category(testCategory).build();
        product2 = Product.builder().id(102L).name("Glass Cleaner").price(149.0).active(true).category(testCategory).build();
        product3 = Product.builder().id(103L).name("Toilet Cleaner").price(99.0).active(true).category(testCategory).build();
    }

    @Test
    @DisplayName("getProducts: executes a single batch review query instead of per-product scalar queries")
    void getProducts_usesSingleBatchReviewQuery() {
        when(productRepository.findAllActiveWithCategory()).thenReturn(List.of(product1, product2, product3));

        // Mock batch return: product 101 has 4.5 avg / 2 reviews; product 102 has 3.0 avg / 1 review; product 103 has 0 reviews
        List<Object[]> batchStats = List.<Object[]>of(
                new Object[]{101L, 4.5, 2L},
                new Object[]{102L, 3.0, 1L}
        );
        when(reviewRepository.getRatingStatsForProductIds(List.of(101L, 102L, 103L))).thenReturn(batchStats);

        List<ProductResponseDTO> result = productService.getProducts(null, null);

        assertEquals(3, result.size());

        // Verify Product 101
        ProductResponseDTO dto1 = result.stream().filter(p -> p.getId().equals(101L)).findFirst().orElseThrow();
        assertEquals(4.5, dto1.getAverageRating());
        assertEquals(2L, dto1.getReviewCount());

        // Verify Product 102
        ProductResponseDTO dto2 = result.stream().filter(p -> p.getId().equals(102L)).findFirst().orElseThrow();
        assertEquals(3.0, dto2.getAverageRating());
        assertEquals(1L, dto2.getReviewCount());

        // Verify Product 103 (no reviews, defaults to 0.0 and 0L)
        ProductResponseDTO dto3 = result.stream().filter(p -> p.getId().equals(103L)).findFirst().orElseThrow();
        assertEquals(0.0, dto3.getAverageRating());
        assertEquals(0L, dto3.getReviewCount());

        // Verify only ONE batch query was called and scalar queries were NEVER called
        verify(reviewRepository, times(1)).getRatingStatsForProductIds(List.of(101L, 102L, 103L));
        verify(reviewRepository, never()).getAverageRating(any());
        verify(reviewRepository, never()).getReviewCount(any());
    }

    @Test
    @DisplayName("getProducts: handles empty catalog without querying reviewRepository")
    void getProducts_emptyCatalog_doesNotQueryReviews() {
        when(productRepository.findAllActiveWithCategory()).thenReturn(Collections.emptyList());

        List<ProductResponseDTO> result = productService.getProducts(null, null);

        assertTrue(result.isEmpty());
        verify(reviewRepository, never()).getRatingStatsForProductIds(anyList());
    }

    @Test
    @DisplayName("getAllProducts (Pageable): executes single batch query for page contents")
    void getAllProducts_paged_usesBatchReviewQuery() {
        Page<Product> page = new PageImpl<>(List.of(product1, product2), PageRequest.of(0, 10), 2);
        when(productRepository.findActiveProductsWithCategory(any())).thenReturn(page);

        List<Object[]> batchStats = List.<Object[]>of(
                new Object[]{101L, 5.0, 10L}
        );
        when(reviewRepository.getRatingStatsForProductIds(List.of(101L, 102L))).thenReturn(batchStats);

        Page<ProductResponseDTO> result = productService.getAllProducts(PageRequest.of(0, 10));

        assertEquals(2, result.getContent().size());
        assertEquals(5.0, result.getContent().get(0).getAverageRating());
        assertEquals(10L, result.getContent().get(0).getReviewCount());
        assertEquals(0.0, result.getContent().get(1).getAverageRating());
        assertEquals(0L, result.getContent().get(1).getReviewCount());

        verify(reviewRepository, times(1)).getRatingStatsForProductIds(List.of(101L, 102L));
        verify(reviewRepository, never()).getAverageRating(any());
        verify(reviewRepository, never()).getReviewCount(any());
    }

    @Test
    @DisplayName("getDeletedProducts: executes single batch query for deleted products")
    void getDeletedProducts_usesBatchReviewQuery() {
        when(productRepository.findDeletedProducts()).thenReturn(List.of(product1));
        List<Object[]> batchStats = List.<Object[]>of(
                new Object[]{101L, 4.0, 3L}
        );
        when(reviewRepository.getRatingStatsForProductIds(List.of(101L))).thenReturn(batchStats);

        List<ProductResponseDTO> result = productService.getDeletedProducts();

        assertEquals(1, result.size());
        assertEquals(4.0, result.get(0).getAverageRating());
        assertEquals(3L, result.get(0).getReviewCount());

        verify(reviewRepository, times(1)).getRatingStatsForProductIds(List.of(101L));
        verify(reviewRepository, never()).getAverageRating(any());
        verify(reviewRepository, never()).getReviewCount(any());
    }
}
