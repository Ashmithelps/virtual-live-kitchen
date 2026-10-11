package com.virtualkitchen.payment.razorpay;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Expected values computed independently (Python hmac/hashlib). */
class RazorpaySignaturesTest {

    @Test
    void checkoutSignatureIsHmacOfOrderPipePayment() {
        String expected = "05a90d99a226250bdd07dcbec806d936d0ac974af71513b19a36466e7f5eb3a3";
        assertThat(RazorpaySignatures.checkoutValid("order_9A33XWu170gUtm", "pay_29QQoUBi66xm2f", expected, "test_key_secret")).isTrue();
        assertThat(RazorpaySignatures.checkoutValid("order_9A33XWu170gUtm", "pay_29QQoUBi66xm2f", expected.toUpperCase(), "test_key_secret")).isTrue();
        assertThat(RazorpaySignatures.checkoutValid("order_OTHER", "pay_29QQoUBi66xm2f", expected, "test_key_secret")).isFalse();
        assertThat(RazorpaySignatures.checkoutValid("order_9A33XWu170gUtm", "pay_29QQoUBi66xm2f", expected, "wrong_secret")).isFalse();
        assertThat(RazorpaySignatures.checkoutValid("order_9A33XWu170gUtm", "pay_29QQoUBi66xm2f", null, "test_key_secret")).isFalse();
    }

    @Test
    void webhookSignatureIsHmacOfTheRawBody() {
        String body = "{\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":{\"id\":\"pay_1\",\"order_id\":\"order_1\",\"amount\":62685}}}}";
        String expected = "7835c940ec199feb0fef64b19290454af7e09dccdafec44ddb92dbf9d2e3eeb9";
        assertThat(RazorpaySignatures.webhookValid(body, expected, "test_webhook_secret")).isTrue();
        // One changed byte (e.g. a tampered amount) breaks it.
        assertThat(RazorpaySignatures.webhookValid(body.replace("62685", "62686"), expected, "test_webhook_secret")).isFalse();
        assertThat(RazorpaySignatures.webhookValid(body, expected, "other_secret")).isFalse();
    }
}
