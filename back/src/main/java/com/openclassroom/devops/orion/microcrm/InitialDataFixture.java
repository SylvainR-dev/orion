package com.openclassroom.devops.orion.microcrm;

import java.util.Arrays;

import org.springframework.stereotype.Component;

import com.openclassroom.devops.orion.microcrm.organization.Organization;
import com.openclassroom.devops.orion.microcrm.organization.OrganizationRepository;
import com.openclassroom.devops.orion.microcrm.person.Person;
import com.openclassroom.devops.orion.microcrm.person.PersonRepository;

import ro.polak.springboot.datafixtures.DataFixture;
import ro.polak.springboot.datafixtures.DataFixtureSet;

@Component
public class InitialDataFixture implements DataFixture {

    private final PersonRepository personRepository;
    private final OrganizationRepository organizationRepository;

    public InitialDataFixture(PersonRepository personRepository, OrganizationRepository organizationRepository) {
        this.personRepository = personRepository;
        this.organizationRepository = organizationRepository;
    }

    @Override
    public boolean canBeLoaded() {
        return personRepository.count() == 0;
    }

    @Override
    public DataFixtureSet getSet() {
        return DataFixtureSet.DICTIONARY;
    }

    @Override
    public void load() {
        Person jdoe = new Person("John", "Doe", "jdoe@example.net");

        Organization orionInc = new Organization("Orion Incorporated");
        orionInc.addPerson(jdoe);

        organizationRepository.saveAll(Arrays.asList(orionInc));
    }
}