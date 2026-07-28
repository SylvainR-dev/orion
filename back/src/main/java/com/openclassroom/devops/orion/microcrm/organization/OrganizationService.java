package com.openclassroom.devops.orion.microcrm.organization;

import java.util.List;

import org.springframework.stereotype.Service;

import com.openclassroom.devops.orion.microcrm.person.Person;
import com.openclassroom.devops.orion.microcrm.person.PersonService;

@Service
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final PersonService personService;

    public OrganizationService(OrganizationRepository organizationRepository, PersonService personService) {
        this.organizationRepository = organizationRepository;
        this.personService = personService;
    }

    public List<Organization> findAll() {
        return organizationRepository.findAll();
    }

    public Organization findById(Long id) {
        return organizationRepository.findById(id)
            .orElseThrow(() -> new OrganizationNotFoundException(id));
    }

    public Organization create(OrganizationRequest request) {
        Organization organization = new Organization(request.name());
        return organizationRepository.save(organization);
    }

    public Organization update(Long id, OrganizationRequest request) {
        Organization organization = findById(id);
        organization.setName(request.name());
        return organizationRepository.save(organization);
    }

    public void delete(Long id) {
        Organization organization = findById(id);
        organizationRepository.delete(organization);
    }

    public Organization addPerson(Long organizationId, Long personId) {
        Organization organization = findById(organizationId);
        Person person = personService.findById(personId);
        organization.addPerson(person);
        return organizationRepository.save(organization);
    }

    public Organization removePerson(Long organizationId, Long personId) {
        Organization organization = findById(organizationId);
        Person person = personService.findById(personId);
        organization.removePerson(person);
        return organizationRepository.save(organization);
    }
}