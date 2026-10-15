package com.virtualkitchen.kitchen.chef;

import com.virtualkitchen.kitchen.chef.dto.PublicChefResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/public/chefs")
public class PublicChefController {

    private final ChefService service;

    public PublicChefController(ChefService service) {
        this.service = service;
    }

    @GetMapping("/{chefId}")
    public PublicChefResponse get(@PathVariable UUID chefId) {
        return service.publicProfile(chefId);
    }
}
