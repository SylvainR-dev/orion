package com.openclassroom.devops.orion.microcrm.person;

public class PersonNotFoundException extends RuntimeException {
    public PersonNotFoundException(Long id) {
        super("Person not found with id " + id);
    }
}