package com.openclassroom.devops.orion.microcrm.organization;

public class OrganizationNotFoundException extends RuntimeException {
    public OrganizationNotFoundException(Long id) {
        super("Organization not found with id " + id);
    }
}