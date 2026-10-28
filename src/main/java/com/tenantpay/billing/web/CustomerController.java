package com.tenantpay.billing.web;

import com.tenantpay.billing.application.CreateCustomer;
import com.tenantpay.billing.application.GetCustomer;
import com.tenantpay.billing.application.ListCustomers;
import com.tenantpay.billing.web.dto.CatalogDtos.CustomerRequest;
import com.tenantpay.billing.web.dto.CatalogDtos.CustomerResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customers")
@Tag(name = "Customers")
public class CustomerController {
    private final CreateCustomer createCustomer;
    private final ListCustomers listCustomers;
    private final GetCustomer getCustomer;

    public CustomerController(CreateCustomer createCustomer, ListCustomers listCustomers, GetCustomer getCustomer) {
        this.createCustomer = createCustomer;
        this.listCustomers = listCustomers;
        this.getCustomer = getCustomer;
    }

    @PostMapping
    public ResponseEntity<CustomerResponse> create(@AuthenticationPrincipal Jwt jwt,
                                                   @Valid @RequestBody CustomerRequest request) {
        CustomerResponse customer = CustomerResponse.of(createCustomer.execute(new CreateCustomer.Command(
                CurrentUser.of(jwt).tenantId(), request.name(), request.email(), request.document())));
        return ResponseEntity.created(URI.create("/api/v1/customers/" + customer.id())).body(customer);
    }

    @GetMapping
    public List<CustomerResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return listCustomers.execute(CurrentUser.of(jwt).tenantId()).stream().map(CustomerResponse::of).toList();
    }

    @GetMapping("/{id}")
    public CustomerResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return CustomerResponse.of(getCustomer.execute(CurrentUser.of(jwt).tenantId(), id));
    }
}
