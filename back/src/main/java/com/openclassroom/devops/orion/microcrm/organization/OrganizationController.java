package com.openclassroom.devops.orion.microcrm.organization;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/organizations")
public class OrganizationController {

    private final OrganizationService organizationService;

    public OrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @GetMapping
    public List<OrganizationResponse> findAll() {
        return organizationService.findAll().stream().map(OrganizationResponse::fromEntity).toList();
    }

    @GetMapping("/{id}")
    public OrganizationResponse findById(@PathVariable Long id) {
        return OrganizationResponse.fromEntity(organizationService.findById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrganizationResponse create(@Valid @RequestBody OrganizationRequest request) {
        return OrganizationResponse.fromEntity(organizationService.create(request));
    }

    @PutMapping("/{id}")
    public OrganizationResponse update(@PathVariable Long id, @Valid @RequestBody OrganizationRequest request) {
        return OrganizationResponse.fromEntity(organizationService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        organizationService.delete(id);
    }

    @PostMapping("/{organizationId}/persons/{personId}")
    public OrganizationResponse addPerson(@PathVariable Long organizationId, @PathVariable Long personId) {
        return OrganizationResponse.fromEntity(organizationService.addPerson(organizationId, personId));
    }

    @DeleteMapping("/{organizationId}/persons/{personId}")
    public OrganizationResponse removePerson(@PathVariable Long organizationId, @PathVariable Long personId) {
        return OrganizationResponse.fromEntity(organizationService.removePerson(organizationId, personId));
    }
}