package com.virtualkitchen.realtime;

import com.virtualkitchen.auth.security.AuthenticatedUser;
import com.virtualkitchen.auth.user.Role;
import com.virtualkitchen.auth.user.UserStatus;
import com.virtualkitchen.kitchen.chef.ChefRepository;
import com.virtualkitchen.order.OrderRepository;
import com.virtualkitchen.restaurant.RestaurantRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Decides whether a connected principal may subscribe to a destination. */
@Component
public class RealtimeAccess {

    private final RestaurantRepository restaurants;
    private final ChefRepository chefs;
    private final OrderRepository orders;

    public RealtimeAccess(RestaurantRepository restaurants, ChefRepository chefs, OrderRepository orders) {
        this.restaurants = restaurants;
        this.chefs = chefs;
        this.orders = orders;
    }

    @Transactional(readOnly = true)
    public boolean canSubscribe(RealtimePrincipal principal, String destination) {
        var restaurantId = Topics.restaurantIdOf(destination);
        if (restaurantId.isPresent()) {
            return principal instanceof RealtimePrincipal.User u && canSeeRestaurant(u.user(), restaurantId.get());
        }
        var orderId = Topics.orderIdOf(destination);
        if (orderId.isPresent()) {
            return switch (principal) {
                case RealtimePrincipal.Guest g -> g.orderId().equals(orderId.get());
                case RealtimePrincipal.User u -> u.user().role() == Role.CUSTOMER
                        && orders.findByIdAndCustomerId(orderId.get(), u.user().id()).isPresent();
            };
        }
        return false;
    }

    private boolean canSeeRestaurant(AuthenticatedUser user, UUID restaurantId) {
        if (user.status() != UserStatus.ACTIVE) {
            return false;
        }
        return switch (user.role()) {
            case RESTAURANT_OWNER -> restaurants.findByIdAndOwnerId(restaurantId, user.id()).isPresent();
            case CHEF -> chefs.findByUserIdAndRemovedAtIsNull(user.id())
                    .map(chef -> chef.getRestaurantId().equals(restaurantId))
                    .orElse(false);
            default -> false;
        };
    }
}
