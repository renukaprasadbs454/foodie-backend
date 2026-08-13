package com.foodie.menu.service;

import com.foodie.menu.dto.request.CreateCategoryRequestDto;
import com.foodie.menu.dto.request.CreateMenuItemRequestDto;
import com.foodie.menu.dto.request.CreateVariantRequestDto;
import com.foodie.menu.dto.request.UpdateAvailabilityRequestDto;
import com.foodie.menu.dto.response.AvailabilityResponseDto;
import com.foodie.menu.dto.response.CategoryResponseDto;
import com.foodie.menu.dto.response.FullMenuResponseDto;
import com.foodie.menu.dto.response.MenuImageUploadResponseDto;
import com.foodie.menu.dto.response.MenuItemResponseDto;
import com.foodie.menu.dto.response.VariantResponseDto;
import java.util.List;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

public interface MenuService {

    FullMenuResponseDto getFullMenu(UUID restaurantId);

    MenuItemResponseDto getItemById(UUID menuItemId);

    List<MenuItemResponseDto> getItemsByRestaurant(UUID restaurantId, UUID categoryId, Boolean isVeg);

    CategoryResponseDto createCategory(UUID ownerCredentialId, CreateCategoryRequestDto request);

    MenuItemResponseDto createItem(UUID ownerCredentialId, CreateMenuItemRequestDto request);

    AvailabilityResponseDto updateAvailability(
            UUID ownerCredentialId,
            UUID menuItemId,
            UpdateAvailabilityRequestDto request
    );

    VariantResponseDto addVariant(UUID ownerCredentialId, UUID menuItemId, CreateVariantRequestDto request);

    MenuImageUploadResponseDto uploadItemImage(UUID ownerCredentialId, UUID menuItemId, MultipartFile file);
}
