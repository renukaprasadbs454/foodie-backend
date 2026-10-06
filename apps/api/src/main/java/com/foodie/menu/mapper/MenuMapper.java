package com.foodie.menu.mapper;

import com.foodie.menu.dto.response.AvailabilityResponseDto;
import com.foodie.menu.dto.response.CategoryResponseDto;
import com.foodie.menu.dto.response.FullMenuResponseDto;
import com.foodie.menu.dto.response.MenuItemResponseDto;
import com.foodie.menu.dto.response.VariantResponseDto;
import com.foodie.menu.entity.Category;
import com.foodie.menu.entity.MenuItem;
import com.foodie.menu.entity.Variant;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class MenuMapper {

    public CategoryResponseDto toCategory(Category category) {
        return new CategoryResponseDto(category.getId(), category.getName(), category.getDisplayOrder());
    }

    public MenuItemResponseDto toMenuItem(MenuItem item, String imageUrl) {
        return toMenuItem(item, imageUrl, BigDecimal.ZERO, 0L);
    }

    public MenuItemResponseDto toMenuItem(MenuItem item, String imageUrl, BigDecimal avgRating, Long reviewCount) {
        return new MenuItemResponseDto(
                item.getId(),
                item.getCategoryId(),
                item.getName(),
                item.getDescription(),
                item.getBasePrice(),
                item.isVeg(),
                item.getFoodType(),
                item.isAvailable(),
                imageUrl,
                item.getPackageSize(),
                item.getPreparationTime(),
                item.getGstPct(),
                avgRating != null ? avgRating : BigDecimal.ZERO,
                reviewCount != null ? reviewCount : 0L);
    }

    public VariantResponseDto toVariant(Variant variant) {
        return new VariantResponseDto(variant.getId(), variant.getName(), variant.getPriceDelta());
    }

    public AvailabilityResponseDto toAvailability(MenuItem item) {
        return new AvailabilityResponseDto(item.getId(), item.isAvailable());
    }

    public FullMenuResponseDto.MenuItemDto toFullMenuItem(
            MenuItem item,
            String imageUrl,
            List<VariantResponseDto> variants) {
        return toFullMenuItem(item, imageUrl, variants, BigDecimal.ZERO, 0L);
    }

    public FullMenuResponseDto.MenuItemDto toFullMenuItem(
            MenuItem item,
            String imageUrl,
            List<VariantResponseDto> variants,
            BigDecimal avgRating,
            Long reviewCount) {
        return new FullMenuResponseDto.MenuItemDto(
                item.getId(),
                item.getName(),
                item.getDescription(),
                item.getBasePrice(),
                item.isVeg(),
                item.getFoodType(),
                item.isAvailable(),
                imageUrl,
                item.getPackageSize(),
                item.getPreparationTime(),
                item.getGstPct(),
                variants,
                avgRating != null ? avgRating : BigDecimal.ZERO,
                reviewCount != null ? reviewCount : 0L);
    }

    public FullMenuResponseDto.MenuCategoryDto toFullMenuCategory(
            Category category,
            List<FullMenuResponseDto.MenuItemDto> items) {
        return new FullMenuResponseDto.MenuCategoryDto(
                category.getId(),
                category.getName(),
                category.getDisplayOrder(),
                items);
    }
}

