package com.virtualkitchen.realtime;

import com.virtualkitchen.auth.security.AuthenticatedUser;

import java.security.Principal;
import java.util.UUID;

/** Who is on the other end of a STOMP connection, fixed at CONNECT time. */
public sealed interface RealtimePrincipal extends Principal {

    /** A logged-in user (customer, owner or chef) who connected with their access token. */
    record User(AuthenticatedUser user) implements RealtimePrincipal {
        @Override
        public String getName() {
            return user.id().toString();
        }
    }

    /** A paired station recording phone (X-Device-Token). */
    record Station(UUID stationId, UUID restaurantId) implements RealtimePrincipal {
        @Override
        public String getName() {
            return "station:" + stationId;
        }
    }

    /** A guest who connected with the order-scoped token from guest checkout. */
    record Guest(UUID orderId, UUID customerId) implements RealtimePrincipal {
        @Override
        public String getName() {
            return "guest:" + orderId;
        }
    }
}
