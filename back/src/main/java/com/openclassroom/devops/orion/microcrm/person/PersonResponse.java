package com.openclassroom.devops.orion.microcrm.person;

import java.time.Instant;

public record PersonResponse(
    Long id,
    String firstName,
    String lastName,
    String email,
    String phone,
    String bio,
    Instant createdAt,
    Instant updatedAt
) {
    public static PersonResponse fromEntity(Person person) {
        return new PersonResponse(
            person.getId(),
            person.getFirstName(),
            person.getLastName(),
            person.getEmail(),
            person.getPhone(),
            person.getBio(),
            person.getCreatedAt(),
            person.getUpdatedAt()
        );
    }
}