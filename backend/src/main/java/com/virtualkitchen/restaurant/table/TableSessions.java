package com.virtualkitchen.restaurant.table;

import com.virtualkitchen.auth.token.ScopedTokens;
import com.virtualkitchen.common.crypto.SecureTokens;
import com.virtualkitchen.common.error.ApiException;
import io.jsonwebtoken.Claims;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * A table session is what a customer gets by scanning a table's QR code: a signed, short-lived
 * token naming exactly one table. Dine-in orders carry it instead of any table reference, so an
 * order is always for the table that was scanned. The token embeds a fingerprint of the QR token
 * it came from; regenerating the table's QR therefore invalidates every session started from the
 * old code as well as the code itself.
 */
@Component
public class TableSessions {

    static final String PURPOSE = "table-session";
    private static final String CLAIM_RESTAURANT = "rid";
    private static final String CLAIM_QR_FINGERPRINT = "qrf";

    private final ScopedTokens tokens;
    private final DiningTableRepository tables;
    private final TableSessionProperties properties;

    public TableSessions(ScopedTokens tokens, DiningTableRepository tables, TableSessionProperties properties) {
        this.tokens = tokens;
        this.tables = tables;
        this.properties = properties;
    }

    public ScopedTokens.Issued start(DiningTable table) {
        return tokens.issue(PURPOSE, table.getId().toString(), Map.of(
                CLAIM_RESTAURANT, table.getRestaurantId().toString(),
                CLAIM_QR_FINGERPRINT, fingerprint(table.getQrToken())), properties.ttl());
    }

    /** Restaurant the session belongs to (signature and expiry checked; table freshness is not). */
    public UUID restaurantOf(String sessionToken) {
        return UUID.fromString(claims(sessionToken).get(CLAIM_RESTAURANT, String.class));
    }

    /**
     * @return the session's table, provided it still exists, belongs to {@code restaurantId}, and
     *         its QR code hasn't been regenerated since the scan
     */
    @Transactional(readOnly = true)
    public DiningTable requireCurrentTable(String sessionToken, UUID restaurantId) {
        Claims claims = claims(sessionToken);
        if (!restaurantId.toString().equals(claims.get(CLAIM_RESTAURANT, String.class))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Table session belongs to a different restaurant");
        }
        DiningTable table = tables.findByIdAndRestaurantId(UUID.fromString(claims.getSubject()), restaurantId)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "This table no longer exists; please ask the staff"));
        if (!fingerprint(table.getQrToken()).equals(claims.get(CLAIM_QR_FINGERPRINT, String.class))) {
            throw new ApiException(HttpStatus.CONFLICT, "This table's QR code has been replaced; please scan the new code");
        }
        return table;
    }

    private Claims claims(String sessionToken) {
        return tokens.verify(PURPOSE, sessionToken).orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST,
                "Table session is invalid or expired; scan the table's QR code again"));
    }

    private static String fingerprint(String qrToken) {
        return SecureTokens.sha256Hex(qrToken).substring(0, 16);
    }
}
