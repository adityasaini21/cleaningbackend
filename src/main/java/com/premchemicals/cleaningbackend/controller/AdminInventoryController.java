package com.premchemicals.cleaningbackend.controller;

import com.premchemicals.cleaningbackend.dto.ProductResponseDTO;
import com.premchemicals.cleaningbackend.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/inventory")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminInventoryController {

    private final ProductService productService;

    // =========================================
    // 📉 GET LOW STOCK PRODUCTS
    // =========================================
    @GetMapping("/low-stock")
    public List<ProductResponseDTO> getLowStockProducts() {
        return productService.getLowStockProducts();
    }

    // =========================================
    // 🔢 GET LOW STOCK COUNT
    // =========================================
    @GetMapping("/low-stock/count")
    public long getLowStockCount() {
        return productService.getLowStockCount();
    }
}