package com.openclassroom.devops.orion.microcrm.organization;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import com.openclassroom.devops.orion.microcrm.person.Person;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class OrganizationRepositoryIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Test
    void whenSaveOrganizationWithPerson_thenPersonIsPersisted() {
        Person jdoe = new Person("John", "Doe", "jdoe@example.net");
        Organization orion = new Organization("Orion Incorporated");
        orion.addPerson(jdoe);

        Organization saved = organizationRepository.save(orion);
        entityManager.flush();
        entityManager.clear();

        Optional<Organization> found = organizationRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getPersons()).hasSize(1);
        assertThat(found.get().getPersons().get(0).getEmail()).isEqualTo("jdoe@example.net");
    }

    @Test
    void whenRemovePersonFromOrganization_thenPersonNoLongerLinked() {
        Person jdoe = new Person("John", "Doe", "jdoe@example.net");
        Organization orion = new Organization("Orion Incorporated");
        orion.addPerson(jdoe);
        Organization saved = organizationRepository.save(orion);
        entityManager.flush();
        entityManager.clear();

        Organization reloaded = organizationRepository.findById(saved.getId()).orElseThrow();
        Person personToRemove = reloaded.getPersons().get(0);
        reloaded.removePerson(personToRemove);
        organizationRepository.save(reloaded);
        entityManager.flush();
        entityManager.clear();

        Organization result = organizationRepository.findById(saved.getId()).orElseThrow();

        assertThat(result.getPersons()).isEmpty();
    }

    @Test
    void whenOrganizationDeleted_thenPersonStillExists() {
        Person jdoe = new Person("John", "Doe", "jdoe@example.net");
        Organization orion = new Organization("Orion Incorporated");
        orion.addPerson(jdoe);
        Organization saved = organizationRepository.save(orion);
        entityManager.flush();
        Long personId = saved.getPersons().get(0).getId();
        entityManager.clear();

        organizationRepository.deleteById(saved.getId());
        entityManager.flush();

        Person person = entityManager.find(Person.class, personId);
        assertThat(person).isNotNull();
    }
}