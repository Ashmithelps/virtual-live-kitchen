package com.virtualkitchen.payment;

import com.virtualkitchen.common.error.ApiException;
import org.springframework.http.HttpStatus;

/** The gateway couldn't be reached or refused a call; the customer can simply try again. */
public class PaymentProviderException extends ApiException {

    public PaymentProviderException(String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, message);
    }
}
