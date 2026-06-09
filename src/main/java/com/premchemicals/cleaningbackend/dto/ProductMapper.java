package com.premchemicals.cleaningbackend.dto;

import com.premchemicals.cleaningbackend.model.Product;
import com.premchemicals.cleaningbackend.model.Category;

public class ProductMapper {

    public static ProductResponseDTO toDTO(Product product) {

        if (product == null) {
            return null;
        }

        ProductResponseDTO dto =
                new ProductResponseDTO();

        dto.setId(product.getId());

        dto.setName(product.getName());

        dto.setDescription(
                product.getDescription()
        );

        dto.setPrice(
                product.getPrice()
        );

        dto.setStock(
                product.getStock()
        );

        dto.setImageUrl(
                product.getImageUrl()
        );

        if (product.getCategory() != null) {

            dto.setCategoryId(
                    product.getCategory().getId()
            );

            dto.setCategoryName(
                    product.getCategory().getName()
            );
        }

        // DEFAULT VALUES
        dto.setAverageRating(0.0);
        dto.setReviewCount(0L);

        return dto;
    }

    public static Product toEntity(
            ProductRequestDTO requestDTO,
            Category category
    ) {

        if (requestDTO == null) {
            return null;
        }

        Product product =
                new Product();

        product.setName(
                requestDTO.getName()
        );

        product.setDescription(
                requestDTO.getDescription()
        );

        product.setPrice(
                requestDTO.getPrice()
        );

        product.setCostPrice(
                requestDTO.getCostPrice()
        );

        product.setStock(
                requestDTO.getStock()
        );

        product.setImageUrl(
                requestDTO.getImageUrl()
        );

        product.setCategory(
                category
        );

        product.setActive(true);

        return product;
    }
}