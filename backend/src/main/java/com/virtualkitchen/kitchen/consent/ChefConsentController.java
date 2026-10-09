package com.virtualkitchen.kitchen.consent;

import com.virtualkitchen.auth.security.AuthenticatedUser;
import com.virtualkitchen.kitchen.consent.dto.AcceptConsentRequest;
import com.virtualkitchen.kitchen.consent.dto.ConsentResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chef/consent")
@PreAuthorize("hasRole('CHEF')")
public class ChefConsentController {

    private final ChefConsentService consentService;

    public ChefConsentController(ChefConsentService consentService) {
        this.consentService = consentService;
    }

    @GetMapping
    public ConsentResponse current(@AuthenticationPrincipal AuthenticatedUser chef) {
        return consentService.forChefUser(chef.id());
    }

    @PostMapping
    public ConsentResponse accept(@AuthenticationPrincipal AuthenticatedUser chef,
                                  @Valid @RequestBody AcceptConsentRequest request,
                                  HttpServletRequest httpRequest) {
        // Direct peer address. Behind a proxy, enable server.forward-headers-strategy so this is the client IP.
        return consentService.accept(chef.id(), request.consentVersion(), request.consentTextHash(),
                httpRequest.getRemoteAddr());
    }

    @PostMapping("/withdraw")
    public ConsentResponse withdraw(@AuthenticationPrincipal AuthenticatedUser chef) {
        return consentService.withdraw(chef.id());
    }
}
