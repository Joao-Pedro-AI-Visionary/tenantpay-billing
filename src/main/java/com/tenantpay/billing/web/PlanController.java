package com.tenantpay.billing.web;

import com.tenantpay.billing.application.CreatePlan;
import com.tenantpay.billing.application.DeactivatePlan;
import com.tenantpay.billing.application.GetPlan;
import com.tenantpay.billing.application.ListPlans;
import com.tenantpay.billing.application.UpdatePlan;
import com.tenantpay.billing.web.dto.CatalogDtos.PlanRequest;
import com.tenantpay.billing.web.dto.CatalogDtos.PlanResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/plans")
@Tag(name = "Plans")
public class PlanController {
    private final CreatePlan createPlan;
    private final UpdatePlan updatePlan;
    private final DeactivatePlan deactivatePlan;
    private final ListPlans listPlans;
    private final GetPlan getPlan;

    public PlanController(CreatePlan createPlan, UpdatePlan updatePlan, DeactivatePlan deactivatePlan,
                          ListPlans listPlans, GetPlan getPlan) {
        this.createPlan = createPlan;
        this.updatePlan = updatePlan;
        this.deactivatePlan = deactivatePlan;
        this.listPlans = listPlans;
        this.getPlan = getPlan;
    }

    @PostMapping
    public ResponseEntity<PlanResponse> create(@AuthenticationPrincipal Jwt jwt,
                                               @Valid @RequestBody PlanRequest request) {
        PlanResponse plan = PlanResponse.of(createPlan.execute(new CreatePlan.Command(CurrentUser.of(jwt).tenantId(),
                request.name(), request.description(), request.amountCents(), request.interval())));
        return ResponseEntity.created(URI.create("/api/v1/plans/" + plan.id())).body(plan);
    }

    @GetMapping
    public List<PlanResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return listPlans.execute(CurrentUser.of(jwt).tenantId()).stream().map(PlanResponse::of).toList();
    }

    @GetMapping("/{id}")
    public PlanResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return PlanResponse.of(getPlan.execute(CurrentUser.of(jwt).tenantId(), id));
    }

    @PutMapping("/{id}")
    public PlanResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                               @Valid @RequestBody PlanRequest request) {
        return PlanResponse.of(updatePlan.execute(new UpdatePlan.Command(CurrentUser.of(jwt).tenantId(), id,
                request.name(), request.description(), request.amountCents(), request.interval())));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        deactivatePlan.execute(CurrentUser.of(jwt).tenantId(), id);
        return ResponseEntity.noContent().build();
    }
}
