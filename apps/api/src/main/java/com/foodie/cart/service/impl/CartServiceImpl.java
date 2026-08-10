package com.foodie.cart.service.impl;

import com.foodie.cart.dto.request.AddCartItemRequestDto;
import com.foodie.cart.dto.response.CartConflictHintDto;
import com.foodie.cart.dto.response.CartResponseDto;
import com.foodie.cart.entity.Cart;
import com.foodie.cart.entity.CartItem;
import com.foodie.cart.mapper.CartMapper;
import com.foodie.cart.repository.CartItemRepository;
import com.foodie.cart.repository.CartRepository;
import com.foodie.cart.service.CartService;
import com.foodie.common.exception.BadRequestException;
import com.foodie.common.exception.ConflictException;
import com.foodie.common.exception.ErrorCode;
import com.foodie.common.exception.ResourceNotFoundException;
import com.foodie.common.exception.UnprocessableEntityException;
import com.foodie.shared.contract.CartCheckoutPort;
import com.foodie.shared.contract.CustomerSummaryProvider;
import com.foodie.shared.contract.MenuItemPriceProvider;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CartServiceImpl implements CartService, CartCheckoutPort {

    private static final int MAX_QUANTITY = 20;

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final CartMapper cartMapper;
    private final CustomerSummaryProvider customerSummaryProvider;
    private final MenuItemPriceProvider menuItemPriceProvider;

    public CartServiceImpl(
            CartRepository cartRepository,
            CartItemRepository cartItemRepository,
            CartMapper cartMapper,
            CustomerSummaryProvider customerSummaryProvider,
            MenuItemPriceProvider menuItemPriceProvider
    ) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.cartMapper = cartMapper;
        this.customerSummaryProvider = customerSummaryProvider;
        this.menuItemPriceProvider = menuItemPriceProvider;
    }

    @Override
    @Transactional
    public CartResponseDto getOrCreate(UUID userCredentialId) {
        Cart cart = getOrCreateCart(resolveCustomerId(userCredentialId));
        return toView(cart);
    }

    @Override
    @Transactional
    public CartResponseDto addItem(UUID userCredentialId, AddCartItemRequestDto request) {
        UUID customerId = resolveCustomerId(userCredentialId);
        Cart cart = getOrCreateCart(customerId);

        MenuItemPriceProvider.MenuItemPriceSnapshot snapshot = menuItemPriceProvider
                .getPriceSnapshot(request.menuItemId(), request.variantId())
                .orElseThrow(() -> new ResourceNotFoundException("Menu item not found."));

        if (!snapshot.available()) {
            throw new UnprocessableEntityException(
                    ErrorCode.ITEM_UNAVAILABLE, "Menu item is currently unavailable.");
        }

        if (cart.getRestaurantId() != null && !cart.getRestaurantId().equals(snapshot.restaurantId())) {
            throw new ConflictException(
                    ErrorCode.CART_RESTAURANT_CONFLICT,
                    "Cart already contains items from a different restaurant.",
                    CartConflictHintDto.clearCart()
            );
        }

        Optional<CartItem> existing = findLine(cart.getId(), request.menuItemId(), request.variantId());
        if (existing.isPresent()) {
            CartItem line = existing.get();
            int newQty = line.getQuantity() + request.quantity();
            if (newQty > MAX_QUANTITY) {
                throw new BadRequestException(
                        ErrorCode.VALIDATION_FAILED,
                        "Quantity cannot exceed " + MAX_QUANTITY + " for a single line."
                );
            }
            line.setQuantity(newQty);
            if (request.notes() != null) {
                line.setNotes(request.notes());
            }
        } else {
            cartItemRepository.save(CartItem.create(
                    cart,
                    request.menuItemId(),
                    request.variantId(),
                    request.quantity(),
                    request.notes()
            ));
            if (cart.getRestaurantId() == null) {
                cart.setRestaurantId(snapshot.restaurantId());
            }
        }

        return toView(cart);
    }

    @Override
    @Transactional
    public CartResponseDto removeItem(UUID userCredentialId, UUID cartItemId) {
        Cart cart = getOrCreateCart(resolveCustomerId(userCredentialId));
        CartItem item = cartItemRepository.findByIdAndCartId(cartItemId, cart.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found."));
        cartItemRepository.delete(item);

        if (cartItemRepository.findByCartIdOrderByCreatedAtAsc(cart.getId()).isEmpty()) {
            cart.clearRestaurant();
        }
        return toView(cart);
    }

    @Override
    @Transactional
    public void clear(UUID userCredentialId) {
        Cart cart = getOrCreateCart(resolveCustomerId(userCredentialId));
        cartItemRepository.deleteAllByCartId(cart.getId());
        cart.clearRestaurant();
    }

    @Override
    @Transactional
    public CartCheckoutSnapshot getCheckoutSnapshot(UUID userCredentialId) {
        CartResponseDto view = getOrCreate(userCredentialId);
        List<Line> lines = view.items().stream()
                .map(item -> new Line(
                        item.menuItemId(),
                        item.variantId(),
                        item.quantity(),
                        item.unitPrice(),
                        item.lineTotal()
                ))
                .toList();
        return new CartCheckoutSnapshot(view.cartId(), view.restaurantId(), lines, view.subtotal());
    }

    @Override
    @Transactional
    public void clearCart(UUID userCredentialId) {
        clear(userCredentialId);
    }

    private Cart getOrCreateCart(UUID customerId) {
        return cartRepository.findByCustomerId(customerId)
                .orElseGet(() -> cartRepository.save(Cart.createEmpty(customerId)));
    }

    private UUID resolveCustomerId(UUID userCredentialId) {
        return customerSummaryProvider.findByUserCredentialId(userCredentialId)
                .map(CustomerSummaryProvider.CustomerSummary::customerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Customer profile not found. Complete profile setup first."));
    }

    private Optional<CartItem> findLine(UUID cartId, UUID menuItemId, UUID variantId) {
        if (variantId == null) {
            return cartItemRepository.findByCartIdAndMenuItemIdAndVariantIdIsNull(cartId, menuItemId);
        }
        return cartItemRepository.findByCartIdAndMenuItemIdAndVariantId(cartId, menuItemId, variantId);
    }

    private CartResponseDto toView(Cart cart) {
        List<CartItem> items = cartItemRepository.findByCartIdOrderByCreatedAtAsc(cart.getId());
        List<CartMapper.PricedLine> priced = new ArrayList<>();
        for (CartItem item : items) {
            BigDecimal unitPrice = menuItemPriceProvider
                    .getPriceSnapshot(item.getMenuItemId(), item.getVariantId())
                    .map(MenuItemPriceProvider.MenuItemPriceSnapshot::unitPrice)
                    .orElse(BigDecimal.ZERO);
            priced.add(new CartMapper.PricedLine(cartMapper.toItem(item, unitPrice)));
        }
        return cartMapper.toCart(cart, priced);
    }
}
