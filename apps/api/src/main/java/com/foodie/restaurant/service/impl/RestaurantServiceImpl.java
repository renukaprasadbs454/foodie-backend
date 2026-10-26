package com.foodie.restaurant.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import com.foodie.shared.contract.ReviewRatingQuery;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodie.common.dto.PaginationMeta;
import com.foodie.common.enums.CuisineType;
import com.foodie.common.enums.RestaurantDocType;
import com.foodie.common.enums.RestaurantImageType;
import com.foodie.common.enums.RestaurantStatus;
import com.foodie.common.exception.BadRequestException;
import com.foodie.common.exception.ConflictException;
import com.foodie.common.exception.ErrorCode;
import com.foodie.common.exception.ResourceNotFoundException;
import com.foodie.common.exception.UnprocessableEntityException;
import com.foodie.infrastructure.storage.DocumentMagicBytes;
import com.foodie.infrastructure.storage.ImageMagicBytes;
import com.foodie.infrastructure.storage.ObjectStorageClient;
import com.foodie.restaurant.dto.request.CreateRestaurantRequestDto;
import com.foodie.restaurant.dto.request.RestaurantAddressRequestDto;
import com.foodie.restaurant.dto.request.RestaurantLegalDetailRequestDto;
import com.foodie.restaurant.dto.request.RestaurantUpiRequestDto;
import com.foodie.restaurant.dto.request.UpdateRestaurantBankDetailsRequestDto;
import com.foodie.restaurant.dto.request.UpdateRestaurantLocationRequestDto;
import com.foodie.restaurant.dto.request.UpdateRestaurantRequestDto;
import com.foodie.restaurant.dto.request.VerifyUpiRequestDto;
import com.foodie.common.enums.OrderStatus;
import com.foodie.menu.repository.MenuItemRepository;
import com.foodie.order.entity.Order;
import com.foodie.order.repository.OrderRepository;
import com.foodie.restaurant.dto.response.RestaurantAddressResponseDto;
import com.foodie.restaurant.dto.response.RestaurantBankDetailsResponseDto;
import com.foodie.restaurant.dto.response.RestaurantDashboardSummaryResponseDto;
import com.foodie.restaurant.dto.response.RestaurantDetailResponseDto;
import com.foodie.restaurant.dto.response.RestaurantDocumentResponseDto;
import com.foodie.restaurant.dto.response.RestaurantImageUploadResponseDto;
import com.foodie.restaurant.dto.response.RestaurantLegalDetailResponseDto;
import com.foodie.restaurant.dto.response.RestaurantLocationResponseDto;
import com.foodie.restaurant.dto.response.RestaurantSummaryResponseDto;
import com.foodie.restaurant.dto.response.RestaurantUpiResponseDto;
import com.foodie.restaurant.dto.response.VerificationResultResponseDto;
import com.foodie.restaurant.entity.Restaurant;
import com.foodie.restaurant.entity.RestaurantAddress;
import com.foodie.restaurant.entity.RestaurantBankDetails;
import com.foodie.restaurant.entity.RestaurantDocument;
import com.foodie.restaurant.entity.RestaurantLegalDetail;
import com.foodie.restaurant.mapper.RestaurantMapper;
import com.foodie.restaurant.repository.RestaurantAddressRepository;
import com.foodie.restaurant.repository.RestaurantBankDetailsRepository;
import com.foodie.restaurant.repository.RestaurantDocumentRepository;
import com.foodie.restaurant.repository.RestaurantLegalDetailRepository;
import com.foodie.restaurant.repository.RestaurantRepository;
import com.foodie.admin.entity.LocationZone;
import com.foodie.admin.repository.LocationZoneRepository;
import com.foodie.auth.repository.UserCredentialRepository;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import com.foodie.restaurant.service.RestaurantCacheService;
import com.foodie.restaurant.service.RestaurantService;
import com.foodie.shared.event.RestaurantApprovedEvent;
import com.foodie.shared.event.RestaurantCreatedEvent;
import com.foodie.shared.event.RestaurantSuspendedEvent;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class RestaurantServiceImpl implements RestaurantService {

    private static final Logger log = LoggerFactory.getLogger(RestaurantServiceImpl.class);
    private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
    private static final long MAX_DOCUMENT_BYTES = 10L * 1024 * 1024;
    private static final Duration SIGNED_URL_TTL = Duration.ofMinutes(15);

    private final RestaurantRepository restaurantRepository;
    private final RestaurantAddressRepository restaurantAddressRepository;
    private final RestaurantDocumentRepository restaurantDocumentRepository;
    private final RestaurantLegalDetailRepository restaurantLegalDetailRepository;
    private final OrderRepository orderRepository;
    private final MenuItemRepository menuItemRepository;
    private final RestaurantBankDetailsRepository restaurantBankDetailsRepository;
    private final RestaurantMapper restaurantMapper;
    private final ObjectStorageClient objectStorageClient;
    private final RestaurantCacheService restaurantCacheService;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final LocationZoneRepository locationZoneRepository;
    private final UserCredentialRepository userCredentialRepository;
    private final BigDecimal defaultCommissionPct;
    private final ReviewRatingQuery reviewRatingQuery;

    @Autowired(required = false)
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Autowired
    public RestaurantServiceImpl(
            RestaurantRepository restaurantRepository,
            RestaurantAddressRepository restaurantAddressRepository,
            RestaurantDocumentRepository restaurantDocumentRepository,
            RestaurantLegalDetailRepository restaurantLegalDetailRepository,
            OrderRepository orderRepository,
            MenuItemRepository menuItemRepository,
            RestaurantBankDetailsRepository restaurantBankDetailsRepository,
            RestaurantMapper restaurantMapper,
            ObjectStorageClient objectStorageClient,
            RestaurantCacheService restaurantCacheService,
            ApplicationEventPublisher eventPublisher,
            ObjectMapper objectMapper,
            LocationZoneRepository locationZoneRepository,
            UserCredentialRepository userCredentialRepository,
            @Value("${foodie.restaurant.default-commission-pct:18.00}") BigDecimal defaultCommissionPct,
            @Autowired(required = false) ReviewRatingQuery reviewRatingQuery) {
        this.restaurantRepository = restaurantRepository;
        this.restaurantAddressRepository = restaurantAddressRepository;
        this.restaurantDocumentRepository = restaurantDocumentRepository;
        this.restaurantLegalDetailRepository = restaurantLegalDetailRepository;
        this.orderRepository = orderRepository;
        this.menuItemRepository = menuItemRepository;
        this.restaurantBankDetailsRepository = restaurantBankDetailsRepository;
        this.restaurantMapper = restaurantMapper;
        this.objectStorageClient = objectStorageClient;
        this.restaurantCacheService = restaurantCacheService;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
        this.locationZoneRepository = locationZoneRepository;
        this.userCredentialRepository = userCredentialRepository;
        this.defaultCommissionPct = defaultCommissionPct;
        this.reviewRatingQuery = reviewRatingQuery;
    }

    public RestaurantServiceImpl(
            RestaurantRepository restaurantRepository,
            RestaurantAddressRepository restaurantAddressRepository,
            RestaurantDocumentRepository restaurantDocumentRepository,
            RestaurantLegalDetailRepository restaurantLegalDetailRepository,
            OrderRepository orderRepository,
            MenuItemRepository menuItemRepository,
            RestaurantBankDetailsRepository restaurantBankDetailsRepository,
            RestaurantMapper restaurantMapper,
            ObjectStorageClient objectStorageClient,
            RestaurantCacheService restaurantCacheService,
            ApplicationEventPublisher eventPublisher,
            ObjectMapper objectMapper,
            LocationZoneRepository locationZoneRepository,
            UserCredentialRepository userCredentialRepository,
            BigDecimal defaultCommissionPct) {
        this(
                restaurantRepository, restaurantAddressRepository, restaurantDocumentRepository,
                restaurantLegalDetailRepository, orderRepository, menuItemRepository,
                restaurantBankDetailsRepository, restaurantMapper, objectStorageClient,
                restaurantCacheService, eventPublisher, objectMapper, locationZoneRepository,
                userCredentialRepository, defaultCommissionPct, null);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<RestaurantSummaryResponseDto> search(
            String search,
            String cuisineType,
            Double minRating,
            Double lat,
            Double lng,
            int page,
            int size,
            String sort) {
        return search(search, cuisineType, minRating, lat, lng, null, page, size, sort);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<RestaurantSummaryResponseDto> search(
            String search,
            String cuisineType,
            Double minRating,
            Double lat,
            Double lng,
            String zoneId,
            int page,
            int size,
            String sort) {
        validateCuisineFilter(cuisineType);
        if (sort != null && !sort.isBlank() && !"nearby".equalsIgnoreCase(sort)) {
            resolveSort(sort);
        }
        BigDecimal minRatingDecimal = minRating != null ? BigDecimal.valueOf(minRating) : null;

        // Resolve active admin operating zone
        LocationZone targetZone = null;
        if (zoneId != null && !zoneId.isBlank()) {
            targetZone = locationZoneRepository.findById(zoneId.trim()).orElse(null);
        } else if (lat != null && lng != null) {
            targetZone = findActiveZoneForCoordinates(lat, lng);
            if (targetZone == null) {
                // Customer coordinates fall outside all active admin operating zones!
                log.info("Customer location ({}, {}) falls outside all active admin operating zones. Returning empty restaurants feed.", lat, lng);
                PaginationMeta emptyPagination = new PaginationMeta(page, size, 0, 0);
                return new PageResult<>(java.util.Collections.emptyList(), emptyPagination);
            }
        }

        String zoneKey = targetZone != null ? targetZone.getId() : "all";
        String cacheKey = zoneKey + "|" + RestaurantCacheService.geoBucket(lat, lng)
                + "|" + nullToEmpty(search)
                + "|" + nullToEmpty(cuisineType)
                + "|" + (minRating != null ? minRating : "")
                + "|" + page + "|" + size + "|" + nullToEmpty(sort);
        var cached = restaurantCacheService.getListJson(cacheKey);
        if (cached.isPresent()) {
            try {
                CachedPage cachedPage = objectMapper.readValue(cached.get(), CachedPage.class);
                return new PageResult<>(cachedPage.items(), cachedPage.pagination());
            } catch (JsonProcessingException ex) {
                log.warn("Failed to deserialize restaurant list cache", ex);
            }
        }

        boolean onlyTop = "topPosition".equalsIgnoreCase(sort);

        // Fetch all approved restaurants and filter strictly by active admin operating zone
        List<Restaurant> approvedList = restaurantRepository.findAllByStatus(com.foodie.common.enums.RestaurantStatus.APPROVED);

        final LocationZone finalTargetZone = targetZone;
        java.util.stream.Stream<Restaurant> stream = approvedList.stream();

        // 1. Zone filter - strictly isolate to the matched admin operating zone
        if (finalTargetZone != null) {
            List<Restaurant> inZone = stream.filter(r -> isRestaurantInZone(r, finalTargetZone)).toList();
            if (!inZone.isEmpty()) {
                stream = inZone.stream();
            } else {
                // Expansion or approved zone fallback: serve nearby restaurants from the city or within 50km
                String targetCity = finalTargetZone.getCityName() != null ? finalTargetZone.getCityName().toLowerCase() : "";
                List<Restaurant> nearby = approvedList.stream().filter(r -> {
                    RestaurantAddress addr = r.getAddress();
                    if (addr != null && addr.getCity() != null) {
                        String rCity = addr.getCity().toLowerCase();
                        if (rCity.equals(targetCity)) return true;
                        if ((targetCity.contains("bangalore") || targetCity.contains("bengaluru")) &&
                            (rCity.contains("bangalore") || rCity.contains("bengaluru"))) return true;
                        if ((targetCity.contains("tumkur") || targetCity.contains("tumakuru")) &&
                            (rCity.contains("tumkur") || rCity.contains("tumakuru"))) return true;
                    }
                    if (lat != null && lng != null && addr != null && addr.getLatitude() != null && addr.getLongitude() != null) {
                        double dist = calculateDistance(lat, lng, addr.getLatitude().doubleValue(), addr.getLongitude().doubleValue());
                        return dist <= 50.0;
                    }
                    return false;
                }).toList();
                if (nearby.isEmpty()) {
                    nearby = approvedList;
                }
                stream = nearby.stream();
            }
        }

        // 2. Search filter
        if (search != null && !search.isBlank()) {
            String lowerSearch = search.trim().toLowerCase();
            stream = stream.filter(r -> (r.getName() != null && r.getName().toLowerCase().contains(lowerSearch))
                    || (r.getDescription() != null && r.getDescription().toLowerCase().contains(lowerSearch)));
        }

        // 3. Cuisine filter
        if (cuisineType != null && !cuisineType.isBlank()) {
            String lowerCuisine = cuisineType.trim().toLowerCase();
            stream = stream.filter(r -> {
                if (r.getCuisineTypes() == null) return false;
                for (String c : r.getCuisineTypes()) {
                    if (c != null && c.toLowerCase().contains(lowerCuisine)) return true;
                }
                return false;
            });
        }

        // 4. Rating filter
        if (minRatingDecimal != null) {
            stream = stream.filter(r -> r.getAvgRating() != null && r.getAvgRating().compareTo(minRatingDecimal) >= 0);
        }

        // 5. Top position filter
        if (onlyTop) {
            stream = stream.filter(r -> r.getTopPosition() != null);
        }

        List<Restaurant> filtered = new java.util.ArrayList<>(stream.toList());

        // Sort results
        if ("avgRating".equalsIgnoreCase(sort)) {
            filtered.sort(java.util.Comparator.comparing(Restaurant::getAvgRating, java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())));
        } else if ("topPosition".equalsIgnoreCase(sort)) {
            filtered.sort(java.util.Comparator.comparing(Restaurant::getTopPosition, java.util.Comparator.nullsLast(Integer::compareTo)));
        } else if (lat != null && lng != null) {
            // Sort by proximity to customer coordinates
            double cLat = lat;
            double cLng = lng;
            filtered.sort(java.util.Comparator.comparingDouble(r -> {
                if (r.getLatitude() != null && r.getLongitude() != null) {
                    return calculateDistance(cLat, cLng, r.getLatitude().doubleValue(), r.getLongitude().doubleValue());
                }
                return 999999.0;
            }));
        } else {
            // Default: topPosition first, then avgRating descending
            filtered.sort(java.util.Comparator
                    .comparing(Restaurant::getTopPosition, java.util.Comparator.nullsLast(Integer::compareTo))
                    .thenComparing(Restaurant::getAvgRating, java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())));
        }

        int totalElements = filtered.size();
        int pageSize = clampSize(size);
        int pageIndex = Math.max(page, 0);
        int totalPages = pageSize > 0 ? (int) Math.ceil((double) totalElements / pageSize) : 0;

        int fromIndex = Math.min(pageIndex * pageSize, totalElements);
        int toIndex = Math.min(fromIndex + pageSize, totalElements);
        List<Restaurant> paged = filtered.subList(fromIndex, toIndex);

        List<RestaurantSummaryResponseDto> items = paged.stream()
                .map(r -> restaurantMapper.toSummary(r, signedOrNull(r.getLogoImageKey())))
                .toList();
        PaginationMeta pagination = new PaginationMeta(pageIndex, pageSize, totalElements, totalPages);
        PageResult<RestaurantSummaryResponseDto> pageResult = new PageResult<>(items, pagination);
        try {
            restaurantCacheService.putListJson(
                    cacheKey,
                    objectMapper.writeValueAsString(new CachedPage(items, pagination)));
        } catch (JsonProcessingException ex) {
            log.warn("Failed to cache restaurant list", ex);
        }
        return pageResult;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<RestaurantDetailResponseDto> listForAdmin(String status, int page, int size, String sort) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size), resolveSort(sort));
        com.foodie.common.enums.RestaurantStatus statusEnum = null;
        if (status != null && !status.isBlank()) {
            try {
                statusEnum = com.foodie.common.enums.RestaurantStatus.valueOf(status);
            } catch (IllegalArgumentException ignored) {
            }
        }
        Page<Restaurant> result;
        if (statusEnum != null) {
            result = restaurantRepository.findByStatus(statusEnum, pageable);
        } else {
            result = restaurantRepository.findByStatusNot(com.foodie.common.enums.RestaurantStatus.ONBOARDING, pageable);
        }
        List<RestaurantDetailResponseDto> items = result.getContent().stream()
                .map(r -> buildDetail(r, true))
                .toList();
        PaginationMeta pagination = new PaginationMeta(
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
        return new PageResult<>(items, pagination);
    }

    @Override
    @Transactional(readOnly = true)
    public RestaurantDetailResponseDto getById(UUID restaurantId, UUID callerCredentialId, boolean callerIsAdmin) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseGet(() -> {
                    List<Restaurant> approved = restaurantRepository.findAllByStatus(RestaurantStatus.APPROVED);
                    if (!approved.isEmpty()) {
                        return approved.get(0);
                    }
                    throw new ResourceNotFoundException("Restaurant not found.");
                });

        boolean owner = callerCredentialId != null
                && callerCredentialId.equals(restaurant.getOwnerUserCredentialId());
        boolean privileged = owner || callerIsAdmin;

        if (restaurant.getStatus() != RestaurantStatus.APPROVED && !privileged) {
            throw new ResourceNotFoundException("Restaurant not found.");
        }

        if (restaurant.getStatus() == RestaurantStatus.APPROVED && !privileged) {
            var cached = restaurantCacheService.getDetailJson(restaurantId);
            if (cached.isPresent()) {
                try {
                    return objectMapper.readValue(cached.get(), RestaurantDetailResponseDto.class);
                } catch (JsonProcessingException ex) {
                    log.warn("Failed to deserialize restaurant detail cache", ex);
                }
            }
        }

        RestaurantDetailResponseDto dto = buildDetail(restaurant, privileged);
        if (restaurant.getStatus() == RestaurantStatus.APPROVED && !privileged) {
            try {
                restaurantCacheService.putDetailJson(restaurantId, objectMapper.writeValueAsString(dto));
            } catch (JsonProcessingException ex) {
                log.warn("Failed to cache restaurant detail", ex);
            }
        }
        return dto;
    }

    public RestaurantDetailResponseDto getMyProfile(UUID ownerCredentialId) {
        return getMyRestaurant(ownerCredentialId);
    }

    @Override
    @Transactional(readOnly = true)
    public RestaurantDetailResponseDto getMyRestaurant(UUID ownerCredentialId) {
        Restaurant restaurant = requireOwned(ownerCredentialId);
        return buildDetail(restaurant, true);
    }

    @Override
    @Transactional
    public RestaurantDetailResponseDto toggleStatus(UUID ownerCredentialId, boolean isOpen) {
        Restaurant restaurant = requireOwned(ownerCredentialId);
        restaurant.setIsOpen(isOpen);
        restaurant = restaurantRepository.save(restaurant);
        restaurantCacheService.evictRestaurant(restaurant.getId());
        restaurantCacheService.evictAllListCaches();
        return buildDetail(restaurant, true);
    }

    @Override
    @Transactional
    public RestaurantDetailResponseDto create(UUID ownerCredentialId, CreateRestaurantRequestDto request) {
        if (restaurantRepository.existsByOwnerUserCredentialId(ownerCredentialId)) {
            throw new ConflictException(
                    ErrorCode.RESTAURANT_PROFILE_ALREADY_EXISTS,
                    "A restaurant profile already exists for this account.");
        }

        if (request.address() == null || request.address().city() == null) {
            throw new BadRequestException(ErrorCode.VALIDATION_FAILED, "Address cannot be null.");
        }
        if (!isLocationWithinActiveZone(request.address().latitude(), request.address().longitude())) {
            throw new BadRequestException(ErrorCode.VALIDATION_FAILED,
                    "The provided coordinates are outside of our active service operating areas limit boundaries.");
        }

        RestaurantAddress address = restaurantAddressRepository.save(toAddress(request.address()));
        // commissionPct from client is ignored — platform default only (API Contracts
        // §3.3).
        Restaurant restaurant = Restaurant.createPending(
                ownerCredentialId,
                request.name(),
                request.description(),
                toCuisineArray(request.cuisineTypes()),
                request.restaurantType(),
                address,
                defaultCommissionPct);

        String zoneId = request.zoneId();
        String zoneName = request.zoneName();
        if (zoneId != null && !zoneId.isBlank()) {
            LocationZone z = locationZoneRepository.findById(zoneId.trim()).orElse(null);
            if (z != null) {
                zoneId = z.getId();
                zoneName = z.getZoneName();
            }
        }
        if (zoneId == null || zoneId.isBlank()) {
            LocationZone matched = findActiveZoneForCoordinates(
                    request.address().latitude().doubleValue(),
                    request.address().longitude().doubleValue());
            if (matched != null) {
                zoneId = matched.getId();
                zoneName = matched.getZoneName();
            } else {
                RestaurantAddressResponseDto addrDto = new RestaurantAddressResponseDto(
                        address.getLine1(), address.getLine2(), address.getLandmark(), address.getCity(),
                        address.getState(), address.getCountry(), address.getPincode(), address.getFormattedAddress(),
                        address.getLatitude(), address.getLongitude());
                zoneId = RestaurantDetailResponseDto.deriveZoneId(addrDto, address.getLatitude(), address.getLongitude());
                zoneName = RestaurantDetailResponseDto.deriveZoneName(addrDto, address.getLatitude(), address.getLongitude());
            }
        }
        restaurant.setZoneId(zoneId);
        restaurant.setZoneName(zoneName);
        restaurant = restaurantRepository.save(restaurant);

        eventPublisher.publishEvent(RestaurantCreatedEvent.of(
                restaurant.getId(), ownerCredentialId, restaurant.getName()));
        restaurantCacheService.evictAllListCaches();
        return buildDetail(restaurant, true);
    }

    @Override
    @Transactional
    public RestaurantDetailResponseDto updateMyRestaurant(UUID ownerCredentialId, UpdateRestaurantRequestDto request) {
        Restaurant restaurant = requireOwned(ownerCredentialId);
        restaurant.updateProfile(request.name(), request.description(), toCuisineArray(request.cuisineTypes()));
        restaurant.getAddress().replace(
                request.address().line1(),
                request.address().line2(),
                request.address().landmark(),
                request.address().city(),
                request.address().state(),
                request.address().country(),
                request.address().pincode(),
                request.address().formattedAddress(),
                request.address().latitude(),
                request.address().longitude());
        restaurant.syncGeoFromAddress();
        restaurantCacheService.evictRestaurant(restaurant.getId());
        return buildDetail(restaurant, true);
    }

    @Override
    @Transactional(readOnly = true)
    public RestaurantLocationResponseDto getLocation(UUID ownerCredentialId) {
        Restaurant restaurant = requireOwned(ownerCredentialId);
        return restaurantMapper.toLocation(restaurant);
    }

    @Override
    @Transactional
    public RestaurantLocationResponseDto updateLocation(UUID ownerCredentialId,
            UpdateRestaurantLocationRequestDto request) {
        Restaurant restaurant = requireOwned(ownerCredentialId);

        if (request.city() == null) {
            throw new BadRequestException(ErrorCode.VALIDATION_FAILED, "Location city cannot be null.");
        }
        if (!isLocationWithinActiveZone(request.latitude(), request.longitude())) {
            throw new BadRequestException(ErrorCode.VALIDATION_FAILED,
                    "The provided coordinates are outside of our active service operating areas limit boundaries.");
        }

        restaurant.getAddress().replace(
                request.addressLine1(),
                request.addressLine2(),
                request.landmark(),
                request.city(),
                request.state(),
                request.country(),
                request.pincode(),
                request.formattedAddress(),
                request.latitude(),
                request.longitude());
        restaurant.syncGeoFromAddress();
        LocationZone matchedZone = findActiveZoneForCoordinates(request.latitude().doubleValue(), request.longitude().doubleValue());
        if (matchedZone != null) {
            restaurant.setZoneId(matchedZone.getId());
            restaurant.setZoneName(matchedZone.getZoneName());
        }
        restaurantRepository.save(restaurant);
        restaurantCacheService.evictRestaurant(restaurant.getId());
        restaurantCacheService.evictAllListCaches();
        return restaurantMapper.toLocation(restaurant);
    }

    @Override
    @Transactional(readOnly = true)
    public RestaurantBankDetailsResponseDto getBankDetails(UUID ownerCredentialId) {
        Restaurant restaurant = requireOwned(ownerCredentialId);
        RestaurantBankDetails bankDetails = restaurantBankDetailsRepository.findByRestaurantId(restaurant.getId())
                .orElse(null);
        return restaurantMapper.toBankDetails(bankDetails);
    }

    @Override
    @Transactional
    public RestaurantBankDetailsResponseDto updateBankDetails(UUID ownerCredentialId,
            UpdateRestaurantBankDetailsRequestDto request) {
        Restaurant restaurant = requireOwned(ownerCredentialId);
        RestaurantBankDetails bankDetails = restaurantBankDetailsRepository.findByRestaurantId(restaurant.getId())
                .orElseGet(() -> RestaurantBankDetails.createDefault(restaurant.getId()));
        bankDetails.updateDetails(
                request.accountHolderName(),
                request.bankName(),
                request.accountNumber(),
                request.ifscCode(),
                request.accountType(),
                request.branchName(),
                request.upiId());
        bankDetails = restaurantBankDetailsRepository.save(bankDetails);
        return restaurantMapper.toBankDetails(bankDetails);
    }

    @Override
    @Transactional
    public VerificationResultResponseDto verifyBankDetails(UUID ownerCredentialId) {
        Restaurant restaurant = requireOwned(ownerCredentialId);
        RestaurantBankDetails bankDetails = restaurantBankDetailsRepository.findByRestaurantId(restaurant.getId())
                .orElseThrow(() -> new BadRequestException(
                        ErrorCode.VALIDATION_FAILED, "Bank details not found. Please add bank details first."));
        if (bankDetails.getAccountNumber() == null || bankDetails.getAccountNumber().isBlank()
                || bankDetails.getIfscCode() == null || bankDetails.getIfscCode().isBlank()) {
            throw new BadRequestException(
                    ErrorCode.VALIDATION_FAILED, "Bank account number and IFSC code are required for verification.");
        }
        bankDetails.verifyBankAccount();
        restaurantBankDetailsRepository.save(bankDetails);
        return new VerificationResultResponseDto("VERIFIED", "Bank account verified successfully.");
    }

    @Override
    @Transactional
    public VerificationResultResponseDto verifyUpi(UUID ownerCredentialId, VerifyUpiRequestDto request) {
        Restaurant restaurant = requireOwned(ownerCredentialId);
        RestaurantBankDetails bankDetails = restaurantBankDetailsRepository.findByRestaurantId(restaurant.getId())
                .orElseGet(() -> restaurantBankDetailsRepository
                        .save(RestaurantBankDetails.createDefault(restaurant.getId())));
        bankDetails.verifyUpi(request.upiId());
        restaurantBankDetailsRepository.save(bankDetails);
        return new VerificationResultResponseDto("VERIFIED", "UPI ID verified successfully.");
    }

    @Override
    @Transactional
    public RestaurantDocumentResponseDto uploadDocument(
            UUID ownerCredentialId,
            RestaurantDocType docType,
            MultipartFile file) {
        Restaurant restaurant = requireOwned(ownerCredentialId);
        byte[] bytes = readBytes(file, MAX_DOCUMENT_BYTES, "Document must be at most 10 MB.");
        DocumentMagicBytes.DetectedDocument detected = DocumentMagicBytes.detect(
                header(bytes), file.getContentType());
        String key = "restaurants/" + restaurant.getId() + "/documents/" + docType.name()
                + "/" + UUID.randomUUID() + "." + detected.extension();
        objectStorageClient.putObject(key, new ByteArrayInputStream(bytes), bytes.length, detected.contentType());
        RestaurantDocument document = restaurantDocumentRepository.save(
                RestaurantDocument.create(restaurant, docType, key));
        String docUrl = objectStorageClient.createSignedGetUrl(document.getS3Key(), java.time.Duration.ofHours(1));
        return restaurantMapper.toDocument(document, docUrl);
    }

    @Override
    @Transactional
    public RestaurantImageUploadResponseDto uploadImage(
            UUID ownerCredentialId,
            RestaurantImageType imageType,
            MultipartFile file) {
        Restaurant restaurant = requireOwned(ownerCredentialId);
        byte[] bytes = readBytes(file, MAX_IMAGE_BYTES, "Image must be at most 5 MB.");
        ImageMagicBytes.DetectedImage detected = ImageMagicBytes.detect(header(bytes), file.getContentType());
        String key = "restaurants/" + restaurant.getId() + "/images/"
                + UUID.randomUUID() + "." + detected.extension();
        objectStorageClient.putObject(key, new ByteArrayInputStream(bytes), bytes.length, detected.contentType());
        Instant uploadedAt = Instant.now();
        if (imageType == RestaurantImageType.LOGO) {
            restaurant.setLogoImageKey(key);
        } else {
            restaurant.setCoverImageKey(key);
        }
        restaurantCacheService.evictRestaurant(restaurant.getId());
        return new RestaurantImageUploadResponseDto(key, imageType.name(), uploadedAt);
    }

    @Override
    @Transactional(readOnly = true)
    public RestaurantUpiResponseDto getUpiDetails(UUID ownerCredentialId) {
        Restaurant restaurant = requireOwned(ownerCredentialId);
        return restaurantMapper.toUpiResponse(restaurant);
    }

    @Override
    @Transactional
    public RestaurantUpiResponseDto updateUpiDetails(UUID ownerCredentialId, RestaurantUpiRequestDto request) {
        Restaurant restaurant = requireOwned(ownerCredentialId);
        restaurant.updateUpi(request.upiId(), request.upiName());
        restaurantCacheService.evictRestaurant(restaurant.getId());
        return restaurantMapper.toUpiResponse(restaurant);
    }

    @Override
    @Transactional
    public RestaurantUpiResponseDto verifyUpiDetails(UUID ownerCredentialId) {
        Restaurant restaurant = requireOwned(ownerCredentialId);
        if (restaurant.getUpiId() == null || restaurant.getUpiId().isBlank()) {
            throw new BadRequestException(ErrorCode.VALIDATION_FAILED,
                    "UPI ID must be configured before verification.");
        }
        restaurant.verifyUpi();
        restaurantCacheService.evictRestaurant(restaurant.getId());
        return restaurantMapper.toUpiResponse(restaurant);
    }

    @Override
    @Transactional
    public RestaurantLegalDetailResponseDto createLegalDetails(UUID ownerCredentialId,
            RestaurantLegalDetailRequestDto request) {
        Restaurant restaurant = requireOwned(ownerCredentialId);
        RestaurantLegalDetail detail = restaurantLegalDetailRepository.findByRestaurantId(restaurant.getId())
                .orElse(null);
        if (detail != null) {
            detail.update(
                    request.gstin(),
                    request.pan(),
                    request.fssaiLicenseNumber(),
                    request.legalName(),
                    request.businessType(),
                    request.contactEmail(),
                    request.contactPhone());
        } else {
            detail = restaurantLegalDetailRepository.save(RestaurantLegalDetail.create(
                    restaurant,
                    request.gstin(),
                    request.pan(),
                    request.fssaiLicenseNumber(),
                    request.legalName(),
                    request.businessType(),
                    request.contactEmail(),
                    request.contactPhone()));
        }
        return restaurantMapper.toLegalDetailResponse(detail);
    }

    @Override
    @Transactional(readOnly = true)
    public RestaurantLegalDetailResponseDto getLegalDetails(UUID ownerCredentialId) {
        Restaurant restaurant = requireOwned(ownerCredentialId);
        return restaurantLegalDetailRepository.findByRestaurantId(restaurant.getId())
                .map(restaurantMapper::toLegalDetailResponse)
                .orElse(null);
    }

    @Override
    @Transactional
    public RestaurantLegalDetailResponseDto updateLegalDetails(UUID ownerCredentialId,
            RestaurantLegalDetailRequestDto request) {
        return createLegalDetails(ownerCredentialId, request);
    }

    @Override
    @Transactional(readOnly = true)
    public RestaurantDashboardSummaryResponseDto getDashboardSummary(UUID ownerCredentialId, LocalDate dateFrom,
            LocalDate dateTo) {
        Restaurant restaurant = requireOwned(ownerCredentialId);

        List<Order> orders;
        if (dateFrom != null && dateTo != null) {
            orders = orderRepository.findByRestaurantIdAndCreatedAtBetween(
                    restaurant.getId(),
                    dateFrom.atStartOfDay(ZoneOffset.UTC).toInstant(),
                    dateTo.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant());
        } else {
            orders = orderRepository.findByRestaurantId(restaurant.getId());
        }

        long totalOrders = orders.size();
        long completedOrders = 0;
        long cancelledOrders = 0;
        long pendingOrders = 0;

        BigDecimal grossSales = BigDecimal.ZERO;
        BigDecimal totalEarning = BigDecimal.ZERO;

        for (Order order : orders) {
            OrderStatus status = order.getStatus();
            if (status == OrderStatus.DELIVERED || status == OrderStatus.PICKED_UP
                    || status == OrderStatus.OUT_FOR_DELIVERY) {
                completedOrders++;
                if (order.getTotalAmount() != null) {
                    grossSales = grossSales.add(order.getTotalAmount());
                    BigDecimal deliveryFee = order.getDeliveryFee() != null ? order.getDeliveryFee() : BigDecimal.ZERO;
                    totalEarning = totalEarning.add(order.getTotalAmount().subtract(deliveryFee));
                }
            } else if (status == OrderStatus.CANCELLED || status == OrderStatus.REJECTED) {
                cancelledOrders++;
            } else if (status == OrderStatus.ACCEPTED || status == OrderStatus.PREPARING
                    || status == OrderStatus.READY_FOR_PICKUP || status == OrderStatus.ASSIGNED) {
                pendingOrders++;
            }
            // Note: PLACED/CONFIRMED are treated as new orders not pending for kitchen
            // action
        }

        BigDecimal commissionPct = restaurant.getCommissionPct() != null ? restaurant.getCommissionPct()
                : BigDecimal.ZERO;
        BigDecimal commissionDeducted = totalEarning.multiply(commissionPct)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal netEarnings = totalEarning.subtract(commissionDeducted);

        BigDecimal avgOrderValue = completedOrders > 0
                ? totalEarning.divide(BigDecimal.valueOf(completedOrders), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        long activeMenuItemsCount = menuItemRepository.findByRestaurantIdAndAvailableTrue(restaurant.getId()).size();

        return new RestaurantDashboardSummaryResponseDto(
                totalOrders,
                completedOrders,
                cancelledOrders,
                pendingOrders,
                grossSales.setScale(2, RoundingMode.HALF_UP),
                totalEarning.setScale(2, RoundingMode.HALF_UP),
                commissionDeducted.setScale(2, RoundingMode.HALF_UP),
                netEarnings.setScale(2, RoundingMode.HALF_UP),
                avgOrderValue,
                restaurant.getAvgRating(),
                activeMenuItemsCount);
    }

    @Override
    @Transactional
    public RestaurantDetailResponseDto updateTimings(UUID ownerCredentialId, com.foodie.restaurant.dto.request.UpdateTimingsRequestDto request) {
        Restaurant restaurant = requireOwned(ownerCredentialId);
        restaurant.updateTimings(request.openTime(), request.closeTime(), request.openDays().toArray(new String[0]));
        restaurantCacheService.evictRestaurant(restaurant.getId());
        restaurantCacheService.evictAllListCaches();
        return buildDetail(restaurant, true);
    }

    @Override
    @Transactional
    public RestaurantDetailResponseDto submitRegistration(UUID ownerCredentialId) {
        Restaurant restaurant = requireOwned(ownerCredentialId);
        restaurant.submitRegistration();
        restaurantCacheService.evictRestaurant(restaurant.getId());
        restaurantCacheService.evictAllListCaches();
        return buildDetail(restaurant, true);
    }

    @Override
    @Transactional
    public RestaurantDetailResponseDto approve(UUID restaurantId, UUID adminId) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found."));
        if (restaurant.getStatus() == RestaurantStatus.APPROVED) {
            throw new UnprocessableEntityException(
                    ErrorCode.ILLEGAL_STATUS_TRANSITION,
                    "Restaurant is already approved.");
        }
        restaurant.approve();
        eventPublisher.publishEvent(RestaurantApprovedEvent.of(restaurantId, adminId));
        restaurantCacheService.evictRestaurant(restaurantId);
        restaurantCacheService.evictAllListCaches();
        return buildDetail(restaurant, true);
    }

    @Override
    @Transactional
    public RestaurantDetailResponseDto suspend(UUID restaurantId, UUID adminId, String reason) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found."));
        if (restaurant.getStatus() == RestaurantStatus.SUSPENDED) {
            throw new UnprocessableEntityException(
                    ErrorCode.ILLEGAL_STATUS_TRANSITION,
                    "Restaurant is already suspended.");
        }
        restaurant.suspend();
        eventPublisher.publishEvent(RestaurantSuspendedEvent.of(restaurantId, adminId, reason));
        restaurantCacheService.evictRestaurant(restaurantId);
        restaurantCacheService.evictAllListCaches();
        log.info("Restaurant {} suspended by admin {}: {}", restaurantId, adminId, reason);
        return buildDetail(restaurant, true);
    }

    @Override
    @Transactional
    public RestaurantDetailResponseDto reject(UUID restaurantId, UUID adminId, String reason) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found."));
        restaurant.reject(reason);
        restaurantCacheService.evictRestaurant(restaurantId);
        restaurantCacheService.evictAllListCaches();
        log.info("Restaurant {} rejected by admin {}: {}", restaurantId, adminId, reason);
        return buildDetail(restaurant, true);
    }

    @Override
    @Transactional
    public void delete(UUID restaurantId, UUID adminId) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found."));
        if (restaurant.getStatus() != RestaurantStatus.SUSPENDED) {
            throw new UnprocessableEntityException(
                    ErrorCode.ILLEGAL_STATUS_TRANSITION,
                    "Only SUSPENDED restaurants can be deleted.");
        }

        if (jdbcTemplate != null) {
            try {
                // 1. Decouple support conversations from this restaurant (preserve audit/messages)
                jdbcTemplate.update("UPDATE support_conversation SET restaurant_id = NULL WHERE restaurant_id = ?", restaurantId);
            } catch (Exception e) {
                log.warn("Error unlinking support conversations for restaurant {}: {}", restaurantId, e.getMessage());
            }

            try {
                // 2. Clear customer carts referencing this restaurant
                jdbcTemplate.update("UPDATE cart SET restaurant_id = NULL WHERE restaurant_id = ?", restaurantId);
            } catch (Exception e) {
                log.warn("Error unlinking carts for restaurant {}: {}", restaurantId, e.getMessage());
            }

            try {
                // 3. Remove reviews associated with this restaurant
                jdbcTemplate.update("DELETE FROM review WHERE restaurant_id = ?", restaurantId);
            } catch (Exception e) {
                log.warn("Error deleting reviews for restaurant {}: {}", restaurantId, e.getMessage());
            }

            try {
                // 4. Remove restaurant-specific coupons
                jdbcTemplate.update("DELETE FROM coupon WHERE restaurant_id = ?", restaurantId);
            } catch (Exception e) {
                log.warn("Error deleting coupons for restaurant {}: {}", restaurantId, e.getMessage());
            }

            try {
                // 5. Remove settlement records if any exist
                jdbcTemplate.update("DELETE FROM restaurant_settlement WHERE restaurant_id = ?", restaurantId);
            } catch (Exception e) {
                log.warn("Error deleting settlements for restaurant {}: {}", restaurantId, e.getMessage());
            }
        }

        UUID addressId = restaurant.getAddress() != null ? restaurant.getAddress().getId() : null;
        restaurantRepository.delete(restaurant);
        if (addressId != null) {
            try {
                restaurantAddressRepository.deleteById(addressId);
            } catch (Exception e) {
                log.warn("Error deleting orphaned restaurant address {}: {}", addressId, e.getMessage());
            }
        }

        restaurantCacheService.evictRestaurant(restaurantId);
        restaurantCacheService.evictAllListCaches();
        log.info("Restaurant {} permanently deleted by admin {}", restaurantId, adminId);
    }

    @Override
    @Transactional
    public RestaurantDetailResponseDto resubmit(UUID ownerCredentialId) {
        Restaurant restaurant = requireOwned(ownerCredentialId);
        restaurant.resubmit();
        restaurantCacheService.evictRestaurant(restaurant.getId());
        return buildDetail(restaurant, true);
    }

    @Override
    @Transactional
    public void updateTopPositions(List<com.foodie.admin.dto.request.UpdateRestaurantPositionRequestDto> positions,
            UUID adminId) {
        restaurantRepository.findByTopPositionIsNotNull().forEach(restaurant -> {
            restaurant.setTopPosition(null);
            restaurantRepository.save(restaurant);
        });
        for (var pos : positions) {
            restaurantRepository.findById(pos.restaurantId()).ifPresent(restaurant -> {
                restaurant.setTopPosition(pos.position());
                restaurantRepository.save(restaurant);
            });
        }
        restaurantCacheService.evictAllListCaches();
        log.info("Restaurant top positions updated by admin {}", adminId);
    }

    @Override
    @Transactional
    public RestaurantDocumentResponseDto verifyDocument(UUID restaurantId, UUID documentId, UUID adminId) {
        RestaurantDocument document = restaurantDocumentRepository.findByIdAndRestaurantId(documentId, restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found."));
        document.markVerified();
        log.info("Restaurant document {} verified by admin {}", documentId, adminId);
        String docUrl = objectStorageClient.createSignedGetUrl(document.getS3Key(), java.time.Duration.ofHours(1));
        return restaurantMapper.toDocument(document, docUrl);
    }

    private Restaurant requireOwned(UUID ownerCredentialId) {
        return restaurantRepository.findByOwnerUserCredentialId(ownerCredentialId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant profile not found."));
    }

    private RestaurantDetailResponseDto buildDetail(Restaurant restaurant, boolean privileged) {
        RestaurantLegalDetailResponseDto legalDetails = null;
        List<RestaurantDocumentResponseDto> documents = null;
        String contactPhone = null;

        if (privileged) {
            var legalOpt = restaurantLegalDetailRepository.findByRestaurantId(restaurant.getId());
            if (legalOpt.isPresent()) {
                legalDetails = restaurantMapper.toLegalDetailResponse(legalOpt.get());
                if (legalDetails.contactPhone() != null && !legalDetails.contactPhone().isBlank()) {
                    contactPhone = legalDetails.contactPhone();
                }
            }
            documents = restaurantDocumentRepository.findByRestaurantId(restaurant.getId()).stream()
                    .map(doc -> {
                        String docUrl = objectStorageClient.createSignedGetUrl(doc.getS3Key(), java.time.Duration.ofHours(1));
                        return restaurantMapper.toDocument(doc, docUrl);
                    }).toList();
        }

        if ((contactPhone == null || contactPhone.isBlank()) && restaurant.getOwnerUserCredentialId() != null) {
            contactPhone = userCredentialRepository.findById(restaurant.getOwnerUserCredentialId())
                    .map(com.foodie.auth.entity.UserCredential::getPhoneNumber)
                    .orElse(null);
        }

        BigDecimal dynamicRating = restaurant.getAvgRating();
        long reviewCount = 0L;
        if (reviewRatingQuery != null) {
            ReviewRatingQuery.RestaurantRating ratingInfo = reviewRatingQuery.getRestaurantRating(restaurant.getId());
            if (ratingInfo != null) {
                dynamicRating = ratingInfo.avgRating();
                reviewCount = ratingInfo.reviewCount();
            }
        }

        return restaurantMapper.toDetail(
                restaurant,
                signedOrNull(restaurant.getLogoImageKey()),
                signedOrNull(restaurant.getCoverImageKey()),
                privileged,
                legalDetails,
                documents,
                contactPhone,
                dynamicRating,
                reviewCount);
    }

    private RestaurantAddress toAddress(RestaurantAddressRequestDto dto) {
        return RestaurantAddress.create(
                dto.line1(), dto.line2(), dto.landmark(), dto.city(), dto.state(), dto.country(),
                dto.pincode(), dto.formattedAddress(), dto.latitude(), dto.longitude());
    }

    private static String[] toCuisineArray(List<CuisineType> cuisineTypes) {
        return cuisineTypes.stream().map(Enum::name).toArray(String[]::new);
    }

    private void validateCuisineFilter(String cuisineType) {
        // Do not throw 400 Bad Request for free-text search filters
    }

    private Sort resolveSort(String sort) {
        String field = sort == null || sort.isBlank() ? "createdAt" : sort;
        return switch (field) {
            case "name" -> Sort.by(Sort.Direction.ASC, "name");
            case "avgRating" -> Sort.by(Sort.Direction.DESC, "avgRating");
            case "createdAt" -> Sort.by(Sort.Direction.DESC, "createdAt");
            case "topPosition" -> Sort.by(Sort.Order.asc("topPosition").nullsLast());
            default -> throw new BadRequestException(
                    ErrorCode.INVALID_SORT_FIELD, "Allowed sort fields: name, avgRating, createdAt, topPosition.");
        };
    }

    private String signedOrNull(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        return objectStorageClient.createSignedGetUrl(key, SIGNED_URL_TTL);
    }

    private static byte[] readBytes(MultipartFile file, long maxBytes, String tooLargeMessage) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException(ErrorCode.VALIDATION_FAILED, "file is required.");
        }
        if (file.getSize() > maxBytes) {
            throw new BadRequestException(ErrorCode.FILE_TOO_LARGE, tooLargeMessage);
        }
        try {
            byte[] bytes = file.getBytes();
            if (bytes.length > maxBytes) {
                throw new BadRequestException(ErrorCode.FILE_TOO_LARGE, tooLargeMessage);
            }
            return bytes;
        } catch (IOException ex) {
            throw new BadRequestException(ErrorCode.BAD_REQUEST, "Unable to read uploaded file.");
        }
    }

    private static byte[] header(byte[] bytes) {
        return bytes.length <= 16 ? bytes : Arrays.copyOf(bytes, 16);
    }

    private static int clampSize(int size) {
        if (size <= 0) {
            return 20;
        }
        return Math.min(size, 100);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private record CachedPage(List<RestaurantSummaryResponseDto> items, PaginationMeta pagination) {
    }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        int r = 6371; // Earth radius km
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * r * Math.asin(Math.sqrt(a));
    }

    private boolean isLocationWithinActiveZone(java.math.BigDecimal lat, java.math.BigDecimal lng) {
        if (lat == null || lng == null)
            return false;

        double latDouble = lat.doubleValue();
        double lngDouble = lng.doubleValue();

        List<LocationZone> zones = locationZoneRepository.findAll();
        for (LocationZone zone : zones) {
            if (!zone.isRestaurantEnabled())
                continue;

            boolean isMatch = false;
            if (zone.getLatitude() != null && zone.getLongitude() != null && zone.getRadiusKm() != null) {
                double dist = calculateDistance(latDouble, lngDouble, zone.getLatitude(), zone.getLongitude());
                if (dist <= zone.getRadiusKm()) {
                    isMatch = true;
                }
            }
            if (!isMatch && zone.getPolygonCoordinates() != null && !zone.getPolygonCoordinates().isBlank()) {
                if (isPointInPolygon(latDouble, lngDouble, zone.getPolygonCoordinates())) {
                    isMatch = true;
                }
            }
            if (isMatch)
                return true;
        }
        return false;
    }

    @Override
    @Transactional(readOnly = true)
    public com.foodie.restaurant.dto.response.CustomerZoneDetectResponseDto detectCustomerZone(Double lat, Double lng) {
        List<LocationZone> allZones = getOrInitActiveZones();
        List<String> availableZoneNames = allZones.stream()
                .filter(z -> "ACTIVE".equalsIgnoreCase(z.getStatus()))
                .map(LocationZone::getZoneName)
                .toList();

        if (lat == null || lng == null) {
            return new com.foodie.restaurant.dto.response.CustomerZoneDetectResponseDto(
                    false, null, null, null, null, null, 0, availableZoneNames,
                    "Customer coordinates required for zone detection.");
        }

        LocationZone matchedZone = findActiveZoneForCoordinates(lat, lng);
        if (matchedZone == null) {
            return new com.foodie.restaurant.dto.response.CustomerZoneDetectResponseDto(
                    false,
                    null,
                    null,
                    null,
                    lat,
                    lng,
                    0,
                    availableZoneNames,
                    "You are currently outside our active operating zones. We deliver in: " + String.join(", ", availableZoneNames)
            );
        }

        List<Restaurant> approved = restaurantRepository.findAllByStatus(com.foodie.common.enums.RestaurantStatus.APPROVED);
        long count = approved.stream().filter(r -> isRestaurantInZone(r, matchedZone)).count();
        if (count == 0) {
            String targetCity = matchedZone.getCityName() != null ? matchedZone.getCityName().toLowerCase() : "";
            count = approved.stream().filter(r -> {
                RestaurantAddress addr = r.getAddress();
                if (addr != null && addr.getCity() != null) {
                    String rCity = addr.getCity().toLowerCase();
                    if (rCity.equals(targetCity)) return true;
                    if ((targetCity.contains("bangalore") || targetCity.contains("bengaluru")) &&
                        (rCity.contains("bangalore") || rCity.contains("bengaluru"))) return true;
                    if ((targetCity.contains("tumkur") || targetCity.contains("tumakuru")) &&
                        (rCity.contains("tumkur") || rCity.contains("tumakuru"))) return true;
                }
                if (lat != null && lng != null && addr != null && addr.getLatitude() != null && addr.getLongitude() != null) {
                    double dist = calculateDistance(lat, lng, addr.getLatitude().doubleValue(), addr.getLongitude().doubleValue());
                    return dist <= 50.0;
                }
                return false;
            }).count();
            if (count == 0) {
                count = approved.size();
            }
        }

        return new com.foodie.restaurant.dto.response.CustomerZoneDetectResponseDto(
                true,
                matchedZone.getId(),
                matchedZone.getZoneName(),
                matchedZone.getCityName(),
                lat,
                lng,
                count,
                availableZoneNames,
                "Operating Zone Identified: " + matchedZone.getZoneName()
        );
    }

    private List<LocationZone> getOrInitActiveZones() {
        return locationZoneRepository.findAll().stream()
                .filter(z -> !"DELETED".equalsIgnoreCase(z.getStatus()))
                .filter(z -> "ACTIVE".equalsIgnoreCase(z.getStatus()))
                .collect(java.util.stream.Collectors.toList());
    }

    private LocationZone findActiveZoneForCoordinates(Double lat, Double lng) {
        if (lat == null || lng == null) return null;
        List<LocationZone> allZones = getOrInitActiveZones();
        LocationZone bestZone = null;
        double minDistance = Double.MAX_VALUE;

        for (LocationZone zone : allZones) {
            if (zone.getStatus() != null && !"ACTIVE".equalsIgnoreCase(zone.getStatus().trim())) {
                continue;
            }
            if (!zone.isCustomerOrderingEnabled()) {
                continue;
            }
            if (isPointInsideZone(lat, lng, zone)) {
                if (zone.getLatitude() != null && zone.getLongitude() != null) {
                    double d = calculateDistance(lat, lng, zone.getLatitude(), zone.getLongitude());
                    if (d < minDistance) {
                        minDistance = d;
                        bestZone = zone;
                    }
                } else if (bestZone == null) {
                    bestZone = zone;
                }
            }
        }
        return bestZone;
    }

    private boolean isPointInsideZone(double lat, double lng, LocationZone zone) {
        if (zone == null) return false;
        if (zone.getStatus() != null && !"ACTIVE".equalsIgnoreCase(zone.getStatus().trim())) return false;
        if (!zone.isCustomerOrderingEnabled() && !zone.isRestaurantEnabled()) return false;

        // Radius check
        if (zone.getLatitude() != null && zone.getLongitude() != null && zone.getRadiusKm() != null && zone.getRadiusKm() > 0) {
            double dist = calculateDistance(lat, lng, zone.getLatitude(), zone.getLongitude());
            if (dist <= zone.getRadiusKm()) {
                return true;
            }
        }
        // Polygon check
        if (zone.getPolygonCoordinates() != null && !zone.getPolygonCoordinates().isBlank()) {
            if (isPointInPolygon(lat, lng, zone.getPolygonCoordinates())) {
                return true;
            }
        }
        return false;
    }

    private boolean isRestaurantInZone(Restaurant r, LocationZone zone) {
        if (r == null || zone == null) return false;

        // 1. By restaurant coordinates
        if (r.getLatitude() != null && r.getLongitude() != null) {
            double lat = r.getLatitude().doubleValue();
            double lng = r.getLongitude().doubleValue();
            if (isPointInsideZone(lat, lng, zone)) {
                return true;
            }
        }

        // 2. By restaurant address coordinates
        if (r.getAddress() != null && r.getAddress().getLatitude() != null && r.getAddress().getLongitude() != null) {
            double lat = r.getAddress().getLatitude().doubleValue();
            double lng = r.getAddress().getLongitude().doubleValue();
            if (isPointInsideZone(lat, lng, zone)) {
                return true;
            }
        }

        // 3. Fallback by city match
        if (zone.getCityName() != null && !zone.getCityName().isBlank()) {
            String zoneCity = zone.getCityName().trim().toLowerCase();
            if (r.getAddress() != null && r.getAddress().getCity() != null) {
                String restCity = r.getAddress().getCity().trim().toLowerCase();
                if (restCity.contains(zoneCity) || zoneCity.contains(restCity)) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean isPointInPolygon(double lat, double lng, String polygonStr) {
        try {
            String[] boundaries = polygonStr.split("\\|");
            if (boundaries.length < 3)
                return false;
            double[][] poly = new double[boundaries.length][2];
            for (int i = 0; i < boundaries.length; i++) {
                String[] cords = boundaries[i].trim().split(",");
                poly[i][0] = Double.parseDouble(cords[0]); // lat
                poly[i][1] = Double.parseDouble(cords[1]); // lng
            }
            boolean inside = false;
            for (int i = 0, j = poly.length - 1; i < poly.length; j = i++) {
                double xi = poly[i][1], yi = poly[i][0];
                double xj = poly[j][1], yj = poly[j][0];
                boolean intersect = ((yi > lat) != (yj > lat)) && (lng < (xj - xi) * (lat - yi) / (yj - yi) + xi);
                if (intersect)
                    inside = !inside;
            }
            return inside;
        } catch (Exception e) {
            return false;
        }
    }
}
