package com.foodie.menu.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodie.common.exception.BadRequestException;
import com.foodie.common.exception.ErrorCode;
import com.foodie.common.exception.ResourceNotFoundException;
import com.foodie.common.exception.UnprocessableEntityException;
import com.foodie.infrastructure.storage.ImageMagicBytes;
import com.foodie.infrastructure.storage.ObjectStorageClient;
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
import com.foodie.menu.entity.Category;
import com.foodie.menu.entity.MenuItem;
import com.foodie.menu.entity.Variant;
import com.foodie.menu.mapper.MenuMapper;
import com.foodie.menu.repository.CategoryRepository;
import com.foodie.menu.repository.MenuItemRepository;
import com.foodie.menu.repository.VariantRepository;
import com.foodie.menu.service.MenuCacheService;
import com.foodie.menu.service.MenuService;
import com.foodie.shared.contract.RestaurantSummaryProvider;
import com.foodie.shared.event.MenuItemPriceChangedEvent;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MenuServiceImpl implements MenuService {

    private static final Logger log = LoggerFactory.getLogger(MenuServiceImpl.class);
    private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
    private static final Duration SIGNED_URL_TTL = Duration.ofMinutes(15);

    private final CategoryRepository categoryRepository;
    private final MenuItemRepository menuItemRepository;
    private final VariantRepository variantRepository;
    private final MenuMapper menuMapper;
    private final RestaurantSummaryProvider restaurantSummaryProvider;
    private final MenuCacheService menuCacheService;
    private final ObjectStorageClient objectStorageClient;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public MenuServiceImpl(
            CategoryRepository categoryRepository,
            MenuItemRepository menuItemRepository,
            VariantRepository variantRepository,
            MenuMapper menuMapper,
            RestaurantSummaryProvider restaurantSummaryProvider,
            MenuCacheService menuCacheService,
            ObjectStorageClient objectStorageClient,
            ApplicationEventPublisher eventPublisher,
            ObjectMapper objectMapper
    ) {
        this.categoryRepository = categoryRepository;
        this.menuItemRepository = menuItemRepository;
        this.variantRepository = variantRepository;
        this.menuMapper = menuMapper;
        this.restaurantSummaryProvider = restaurantSummaryProvider;
        this.menuCacheService = menuCacheService;
        this.objectStorageClient = objectStorageClient;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public FullMenuResponseDto getFullMenu(UUID restaurantId) {
        restaurantSummaryProvider.findByRestaurantId(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found."));

        var cached = menuCacheService.get(restaurantId);
        if (cached.isPresent()) {
            try {
                return objectMapper.readValue(cached.get(), FullMenuResponseDto.class);
            } catch (JsonProcessingException ex) {
                log.warn("Failed to deserialize menu cache for restaurant {}", restaurantId, ex);
            }
        }

        List<Category> categories = categoryRepository.findByRestaurantIdOrderByDisplayOrderAscCreatedAtAsc(restaurantId);
        List<MenuItem> items = menuItemRepository.findByRestaurantIdOrderByCreatedAtAsc(restaurantId);
        Map<UUID, List<MenuItem>> itemsByCategory = items.stream()
                .collect(Collectors.groupingBy(MenuItem::getCategoryId, LinkedHashMap::new, Collectors.toList()));

        List<UUID> itemIds = items.stream().map(MenuItem::getId).toList();
        Map<UUID, List<Variant>> variantsByItem = itemIds.isEmpty()
                ? Map.of()
                : variantRepository.findByMenuItemIdInOrderByCreatedAtAsc(itemIds).stream()
                        .collect(Collectors.groupingBy(Variant::getMenuItemId, LinkedHashMap::new, Collectors.toList()));

        List<FullMenuResponseDto.MenuCategoryDto> categoryDtos = new ArrayList<>();
        for (Category category : categories) {
            List<FullMenuResponseDto.MenuItemDto> itemDtos = itemsByCategory
                    .getOrDefault(category.getId(), List.of())
                    .stream()
                    .map(item -> menuMapper.toFullMenuItem(
                            item,
                            signedOrNull(item.getImageS3Key()),
                            variantsByItem.getOrDefault(item.getId(), List.of()).stream()
                                    .map(menuMapper::toVariant)
                                    .toList()
                    ))
                    .toList();
            categoryDtos.add(menuMapper.toFullMenuCategory(category, itemDtos));
        }

        FullMenuResponseDto menu = new FullMenuResponseDto(restaurantId, categoryDtos);
        try {
            menuCacheService.put(restaurantId, objectMapper.writeValueAsString(menu));
        } catch (JsonProcessingException ex) {
            log.warn("Failed to cache menu for restaurant {}", restaurantId, ex);
        }
        return menu;
    }

    @Override
    @Transactional
    public CategoryResponseDto createCategory(UUID ownerCredentialId, CreateCategoryRequestDto request) {
        UUID restaurantId = requireOwnedRestaurantId(ownerCredentialId);
        Category category = categoryRepository.save(Category.create(
                restaurantId,
                request.name(),
                request.displayOrderOrDefault()
        ));
        menuCacheService.evict(restaurantId);
        return menuMapper.toCategory(category);
    }

    @Override
    @Transactional
    public MenuItemResponseDto createItem(UUID ownerCredentialId, CreateMenuItemRequestDto request) {
        UUID restaurantId = requireOwnedRestaurantId(ownerCredentialId);
        Category category = categoryRepository.findByIdAndRestaurantId(request.categoryId(), restaurantId)
                .orElseThrow(() -> new UnprocessableEntityException(
                        ErrorCode.CATEGORY_NOT_OWNED,
                        "Category does not belong to this restaurant."
                ));

        MenuItem item = menuItemRepository.save(MenuItem.create(
                restaurantId,
                category.getId(),
                request.name(),
                request.description(),
                request.basePrice(),
                Boolean.TRUE.equals(request.isVeg())
        ));
        publishPriceChanged(restaurantId, item.getId());
        menuCacheService.evict(restaurantId);
        return menuMapper.toMenuItem(item, null);
    }

    @Override
    @Transactional
    public AvailabilityResponseDto updateAvailability(
            UUID ownerCredentialId,
            UUID menuItemId,
            UpdateAvailabilityRequestDto request
    ) {
        UUID restaurantId = requireOwnedRestaurantId(ownerCredentialId);
        MenuItem item = menuItemRepository.findByIdAndRestaurantId(menuItemId, restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Menu item not found."));
        item.setAvailable(Boolean.TRUE.equals(request.isAvailable()));
        publishPriceChanged(restaurantId, item.getId());
        menuCacheService.evict(restaurantId);
        return menuMapper.toAvailability(item);
    }

    @Override
    @Transactional
    public VariantResponseDto addVariant(
            UUID ownerCredentialId,
            UUID menuItemId,
            CreateVariantRequestDto request
    ) {
        UUID restaurantId = requireOwnedRestaurantId(ownerCredentialId);
        MenuItem item = menuItemRepository.findByIdAndRestaurantId(menuItemId, restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Menu item not found."));

        BigDecimal unit = item.getBasePrice().add(request.priceDelta());
        if (unit.compareTo(BigDecimal.ZERO) <= 0) {
            throw new UnprocessableEntityException(
                    ErrorCode.INVALID_VARIANT_PRICE,
                    "basePrice + priceDelta must be greater than zero."
            );
        }

        Variant variant = variantRepository.save(Variant.create(item.getId(), request.name(), request.priceDelta()));
        publishPriceChanged(restaurantId, item.getId());
        menuCacheService.evict(restaurantId);
        return menuMapper.toVariant(variant);
    }

    @Override
    @Transactional
    public MenuImageUploadResponseDto uploadItemImage(
            UUID ownerCredentialId,
            UUID menuItemId,
            MultipartFile file
    ) {
        UUID restaurantId = requireOwnedRestaurantId(ownerCredentialId);
        MenuItem item = menuItemRepository.findByIdAndRestaurantId(menuItemId, restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Menu item not found."));

        if (file == null || file.isEmpty()) {
            throw new BadRequestException(ErrorCode.VALIDATION_FAILED, "file is required.");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new BadRequestException(ErrorCode.FILE_TOO_LARGE, "Menu image must be at most 5 MB.");
        }

        try {
            byte[] bytes = file.getBytes();
            if (bytes.length > MAX_IMAGE_BYTES) {
                throw new BadRequestException(ErrorCode.FILE_TOO_LARGE, "Menu image must be at most 5 MB.");
            }
            byte[] header = bytes.length <= 16 ? bytes : Arrays.copyOf(bytes, 16);
            ImageMagicBytes.DetectedImage detected = ImageMagicBytes.detect(header, file.getContentType());
            String key = "restaurants/" + restaurantId + "/menu-items/" + menuItemId
                    + "/" + UUID.randomUUID() + "." + detected.extension();
            objectStorageClient.putObject(
                    key, new ByteArrayInputStream(bytes), bytes.length, detected.contentType());
            Instant uploadedAt = Instant.now();
            item.setImageS3Key(key);
            menuCacheService.evict(restaurantId);
            return new MenuImageUploadResponseDto(key, uploadedAt);
        } catch (IOException ex) {
            throw new BadRequestException(ErrorCode.BAD_REQUEST, "Unable to read uploaded file.");
        }
    }

    private UUID requireOwnedRestaurantId(UUID ownerCredentialId) {
        return restaurantSummaryProvider.findByOwnerUserCredentialId(ownerCredentialId)
                .map(RestaurantSummaryProvider.RestaurantSummary::restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant profile not found."));
    }

    private void publishPriceChanged(UUID restaurantId, UUID menuItemId) {
        eventPublisher.publishEvent(MenuItemPriceChangedEvent.of(restaurantId, menuItemId));
    }

    private String signedOrNull(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        return objectStorageClient.createSignedGetUrl(key, SIGNED_URL_TTL);
    }
}
