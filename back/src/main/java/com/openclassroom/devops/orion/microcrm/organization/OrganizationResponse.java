package com.openclassroom.devops.orion.microcrm.organization;

import java.time.Instant;
import java.util.List;

import com.openclassroom.devops.orion.microcrm.person.Person;

public record OrganizationResponse(
    Long id,
    String name,
    List<Long> personIds,
    Instant createdAt,
    Instant updatedAt
) {
    public static OrganizationResponse fromEntity(Organization organization) {
        return new OrganizationResponse(
            organization.getId(),
            organization.getName(),
            organization.getPersons().stream().map(Person::getId).toList(),
            organization.getCreatedAt(),
            organization.getUpdatedAt()
        );
    }
}