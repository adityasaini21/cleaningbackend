package com.premchemicals.cleaningbackend.controller;

import com.premchemicals.cleaningbackend.dto.ProductRequestDTO;
import com.premchemicals.cleaningbackend.dto.ProductResponseDTO;
import com.premchemicals.cleaningbackend.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ProductController {

    private final ProductService productService;

    // =========================================
    // ✅ CREATE PRODUCT
    // =========================================
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponseDTO createProduct(
            @Valid @RequestBody ProductRequestDTO request) {

        return productService.createProduct(request);
    }

    // =========================================
    // ✅ GET PRODUCT BY ID
    // =========================================
    @GetMapping("/{id}")
    public ProductResponseDTO getProductById(@PathVariable Long id) {
        return productService.getProductById(id);
    }

    // =========================================
    // ✅ UPDATE PRODUCT
    // =========================================
    @PutMapping("/{id}")
    public ProductResponseDTO updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody ProductRequestDTO request) {

        return productService.updateProduct(id, request);
    }

    // =========================================
    // ✅ DELETE PRODUCT (SOFT DELETE)
    // =========================================
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
    }

    // =========================================
    // 🔥 MAIN PRODUCT LIST (FILTER + SEARCH + PAGINATION)
    // =========================================
    @GetMapping
    public List<ProductResponseDTO> getProducts(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String search) {

        return productService.getProducts(category, search);
    }

    // =========================================
    // 🔍 SEARCH (Optional legacy support)
    // =========================================
    @GetMapping("/search")
    public List<ProductResponseDTO> searchProducts(
            @RequestParam String keyword) {

        return productService.getProducts(null, keyword.trim());
    }

    // =========================================
// 🔥 GET DELETED PRODUCTS
// =========================================
    @GetMapping("/deleted")
    public List<ProductResponseDTO> getDeletedProducts() {

        return productService.getDeletedProducts();
    }

    // =========================================
// 🔥 RESTORE PRODUCT
// =========================================
    @PutMapping("/{id}/restore")
    public ProductResponseDTO restoreProduct(
            @PathVariable Long id) {

        return productService.restoreProduct(id);
    }

    // =========================================
    // 📦 PAGINATION ONLY (Admin style)
    // =========================================
    @GetMapping("/paged")
    public Page<ProductResponseDTO> getAllProductsPaged(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String direction) {

        if (size > 50) {
            size = 50;
        }

        Sort sort = direction.equalsIgnoreCase("desc")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();

        Pageable pageable = PageRequest.of(page, size, sort);

        return productService.getAllProducts(pageable);
    }
}