package com.foodie.restaurant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodie.common.enums.CuisineType;
import com.foodie.common.enums.RestaurantDocType;
import com.foodie.common.enums.RestaurantStatus;
import com.foodie.common.exception.ConflictException;
import com.foodie.common.exception.ErrorCode;
import com.foodie.common.exception.ResourceNotFoundException;
import com.foodie.common.exception.UnprocessableEntityException;
import com.foodie.infrastructure.storage.ObjectStorageClient;
import com.foodie.restaurant.dto.request.CreateRestaurantRequestDto;
import com.foodie.restaurant.dto.request.RestaurantAddressRequestDto;
import com.foodie.restaurant.dto.request.UpdateRestaurantRequestDto;
import com.foodie.restaurant.dto.response.RestaurantDetailResponseDto;
import com.foodie.restaurant.entity.Restaurant;
import com.foodie.restaurant.entity.RestaurantAddress;
import com.foodie.restaurant.entity.RestaurantDocument;
import com.foodie.restaurant.mapper.RestaurantMapper;
import com.foodie.menu.repository.MenuItemRepository;
import com.foodie.order.repository.OrderRepository;
import com.foodie.restaurant.repository.RestaurantAddressRepository;
import com.foodie.restaurant.repository.RestaurantDocumentRepository;
import com.foodie.restaurant.repository.RestaurantLegalDetailRepository;
import com.foodie.restaurant.repository.RestaurantRepository;
import com.foodie.restaurant.service.RestaurantCacheService;
import com.foodie.restaurant.service.impl.RestaurantServiceImpl;
import com.foodie.shared.event.RestaurantApprovedEvent;
import com.foodie.shared.event.RestaurantCreatedEvent;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class RestaurantServiceImplTest {

    @Mock
    private RestaurantRepository restaurantRepository;
    @Mock
    private RestaurantAddressRepository restaurantAddressRepository;
    @Mock
    private RestaurantDocumentRepository restaurantDocumentRepository;
    @Mock
    private RestaurantLegalDetailRepository restaurantLegalDetailRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private MenuItemRepository menuItemRepository;
    @Mock
    private ObjectStorageClient objectStorageClient;
    @Mock
    private RestaurantCacheService restaurantCacheService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private RestaurantServiceImpl service;
    private final UUID ownerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new RestaurantServiceImpl(
                restaurantRepository,
                restaurantAddressRepository,
                restaurantDocumentRepository,
                restaurantLegalDetailRepository,
                orderRepository,
                menuItemRepository,
                new RestaurantMapper(),
                objectStorageClient,
                restaurantCacheService,
                eventPublisher,
                new ObjectMapper().findAndRegisterModules(),
                new BigDecimal("18.00")
        );
    }

    @Test
    void create_ignoresClientCommission_andPublishesCreatedEvent() {
        when(restaurantRepository.existsByOwnerUserCredentialId(ownerId)).thenReturn(false);
        when(restaurantAddressRepository.save(any())).thenAnswer(inv -> {
            RestaurantAddress a = inv.getArgument(0);
            setId(a, UUID.randomUUID());
            return a;
        });
        when(restaurantRepository.save(any())).thenAnswer(inv -> {
            Restaurant r = inv.getArgument(0);
            setId(r, UUID.randomUUID());
            return r;
        });

        CreateRestaurantRequestDto request = new CreateRestaurantRequestDto(
                "Spice Route Kitchen",
                "Authentic South Indian cuisine",
                List.of(CuisineType.SOUTH_INDIAN, CuisineType.VEGETARIAN),
                addressDto(),
                new BigDecimal("99.00")
        );

        RestaurantDetailResponseDto created = service.create(ownerId, request);

        assertThat(created.status()).isEqualTo("PENDING");
        assertThat(created.commissionPct()).isEqualByComparingTo("18.00");
        assertThat(created.ownerUserCredentialId()).isEqualTo(ownerId);
        verify(eventPublisher).publishEvent(any(RestaurantCreatedEvent.class));
        verify(restaurantCacheService).evictAllListCaches();
    }

    @Test
    void create_whenProfileExists_conflicts() {
        when(restaurantRepository.existsByOwnerUserCredentialId(ownerId)).thenReturn(true);
        assertThatThrownBy(() -> service.create(ownerId, new CreateRestaurantRequestDto(
                "X", null, List.of(CuisineType.OTHER), addressDto(), null)))
                .isInstanceOf(ConflictException.class)
                .extracting(ex -> ((ConflictException) ex).getErrorCode())
                .isEqualTo(ErrorCode.RESTAURANT_PROFILE_ALREADY_EXISTS);
    }

    @Test
    void getById_pendingHiddenFromPublic() {
        Restaurant restaurant = pendingRestaurant();
        when(restaurantRepository.findById(restaurant.getId())).thenReturn(Optional.of(restaurant));

        assertThatThrownBy(() -> service.getById(restaurant.getId(), null, false))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getById_pendingVisibleToOwner_withPrivilegedFields() {
        Restaurant restaurant = pendingRestaurant();
        when(restaurantRepository.findById(restaurant.getId())).thenReturn(Optional.of(restaurant));

        RestaurantDetailResponseDto dto = service.getById(restaurant.getId(), ownerId, false);

        assertThat(dto.status()).isEqualTo("PENDING");
        assertThat(dto.commissionPct()).isNotNull();
        assertThat(dto.ownerUserCredentialId()).isEqualTo(ownerId);
    }

    @Test
    void approve_fromPending_publishesEvent() {
        Restaurant restaurant = pendingRestaurant();
        UUID adminId = UUID.randomUUID();
        when(restaurantRepository.findById(restaurant.getId())).thenReturn(Optional.of(restaurant));

        RestaurantDetailResponseDto dto = service.approve(restaurant.getId(), adminId);

        assertThat(dto.status()).isEqualTo("APPROVED");
        assertThat(restaurant.getStatus()).isEqualTo(RestaurantStatus.APPROVED);
        verify(eventPublisher).publishEvent(any(RestaurantApprovedEvent.class));
        verify(restaurantCacheService).evictRestaurant(restaurant.getId());
    }

    @Test
    void approve_whenAlreadyApproved_illegalTransition() {
        Restaurant restaurant = pendingRestaurant();
        restaurant.approve();
        when(restaurantRepository.findById(restaurant.getId())).thenReturn(Optional.of(restaurant));

        assertThatThrownBy(() -> service.approve(restaurant.getId(), UUID.randomUUID()))
                .isInstanceOf(UnprocessableEntityException.class)
                .extracting(ex -> ((UnprocessableEntityException) ex).getErrorCode())
                .isEqualTo(ErrorCode.ILLEGAL_STATUS_TRANSITION);
    }

    @Test
    void updateMyRestaurant_doesNotChangeStatusOrCommission() {
        Restaurant restaurant = pendingRestaurant();
        when(restaurantRepository.findByOwnerUserCredentialId(ownerId)).thenReturn(Optional.of(restaurant));

        UpdateRestaurantRequestDto request = new UpdateRestaurantRequestDto(
                "New Name",
                "Updated",
                List.of(CuisineType.CHINESE),
                new RestaurantAddressRequestDto(
                        "L1", null, "Bengaluru", "560001",
                        new BigDecimal("12.970000"), new BigDecimal("77.590000")
                )
        );

        RestaurantDetailResponseDto dto = service.updateMyRestaurant(ownerId, request);

        assertThat(dto.name()).isEqualTo("New Name");
        assertThat(dto.status()).isEqualTo("PENDING");
        assertThat(dto.commissionPct()).isEqualByComparingTo("18.00");
        verify(restaurantCacheService).evictRestaurant(restaurant.getId());
    }

    @Test
    void uploadDocument_neverSelfVerifies() {
        Restaurant restaurant = pendingRestaurant();
        when(restaurantRepository.findByOwnerUserCredentialId(ownerId)).thenReturn(Optional.of(restaurant));
        when(restaurantDocumentRepository.save(any())).thenAnswer(inv -> {
            RestaurantDocument d = inv.getArgument(0);
            setId(d, UUID.randomUUID());
            return d;
        });

        byte[] pdf = "%PDF-1.4 mock content".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "fssai.pdf", "application/pdf", pdf);

        var response = service.uploadDocument(ownerId, RestaurantDocType.FSSAI, file);

        assertThat(response.docType()).isEqualTo("FSSAI");
        assertThat(response.verifiedAt()).isNull();
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(objectStorageClient).putObject(keyCaptor.capture(), any(), any(Long.class), eq("application/pdf"));
        assertThat(keyCaptor.getValue()).contains("/documents/FSSAI/");
    }

    @Test
    void search_invalidSort_throws() {
        assertThatThrownBy(() -> service.search(null, null, null, null, null, 0, 20, "price"))
                .isInstanceOf(com.foodie.common.exception.BadRequestException.class)
                .extracting(ex -> ((com.foodie.common.exception.BadRequestException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_SORT_FIELD);
        verify(restaurantRepository, never()).searchApproved(any(), any(), any(), any());
    }

    private Restaurant pendingRestaurant() {
        RestaurantAddress address = RestaurantAddress.create(
                "L1", null, "Bengaluru", "560103",
                new BigDecimal("12.935200"), new BigDecimal("77.691200")
        );
        setId(address, UUID.randomUUID());
        Restaurant restaurant = Restaurant.createPending(
                ownerId,
                "Spice Route Kitchen",
                "desc",
                new String[] {"SOUTH_INDIAN"},
                address,
                new BigDecimal("18.00")
        );
        setId(restaurant, UUID.randomUUID());
        return restaurant;
    }

    private static RestaurantAddressRequestDto addressDto() {
        return new RestaurantAddressRequestDto(
                "Flat 1", null, "Bengaluru", "560103",
                new BigDecimal("12.935200"), new BigDecimal("77.691200")
        );
    }

    private static void setId(Object entity, UUID id) {
        try {
            var field = entity.getClass().getSuperclass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
