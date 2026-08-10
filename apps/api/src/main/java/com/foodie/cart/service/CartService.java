package com.foodie.cart.service;

import com.foodie.cart.dto.request.AddCartItemRequestDto;
import com.foodie.cart.dto.response.CartResponseDto;
import java.util.UUID;

public interface CartService {

    CartResponseDto getOrCreate(UUID userCredentialId);

    CartResponseDto addItem(UUID userCredentialId, AddCartItemRequestDto request);

    CartResponseDto removeItem(UUID userCredentialId, UUID cartItemId);

    void clear(UUID userCredentialId);
}
