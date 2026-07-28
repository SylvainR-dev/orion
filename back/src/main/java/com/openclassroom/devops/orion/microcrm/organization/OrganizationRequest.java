package com.openclassroom.devops.orion.microcrm.organization;

import jakarta.validation.constraints.NotBlank;

public record OrganizationRequest(
    @NotBlank String name
) {}