package com.virtualkitchen.restaurant.table;

import com.virtualkitchen.common.config.WebProperties;
import org.springframework.stereotype.Component;

/** The URL printed in a table's QR code. */
@Component
public class TableLinks {

    private final WebProperties web;

    public TableLinks(WebProperties web) {
        this.web = web;
    }

    public String qrUrl(String qrToken) {
        return web.base() + "/t/" + qrToken;
    }
}
