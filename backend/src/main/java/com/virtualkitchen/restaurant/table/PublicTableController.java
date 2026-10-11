package com.virtualkitchen.restaurant.table;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Target of the QR code's web page: resolves the scanned token and starts a table session. */
@RestController
public class PublicTableController {

    private final TableLookupService lookupService;

    public PublicTableController(TableLookupService lookupService) {
        this.lookupService = lookupService;
    }

    @GetMapping("/api/tables/{qrToken}")
    public TableLookupResponse lookup(@PathVariable String qrToken) {
        return lookupService.lookup(qrToken);
    }
}
