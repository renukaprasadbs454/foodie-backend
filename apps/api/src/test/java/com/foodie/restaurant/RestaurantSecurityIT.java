package com.foodie.restaurant;

import com.foodie.common.exception.ResourceNotFoundException;
import com.foodie.menu.dto.request.CreateCategoryRequestDto;
import com.foodie.menu.service.MenuService;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
class RestaurantSecurityIT {

    @Autowired
    private MenuService menuService;

    @Test
    @DisplayName("Should throw ResourceNotFoundException when non-existent owner credential accesses menu")
    void testCrossRestaurantSecurityDenial() {
        UUID fakeOwnerCredentialId = UUID.randomUUID();

        assertThrows(ResourceNotFoundException.class, () -> {
            menuService.getCategories(fakeOwnerCredentialId);
        });

        assertThrows(ResourceNotFoundException.class, () -> {
            menuService.createCategory(fakeOwnerCredentialId, new CreateCategoryRequestDto("Test Cat", 1));
        });
    }
}
