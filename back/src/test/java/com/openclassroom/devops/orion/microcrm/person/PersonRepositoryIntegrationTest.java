package com.openclassroom.devops.orion.microcrm.person;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class PersonRepositoryIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private PersonRepository personRepository;

    @Test
    void whenFindByEmail_thenReturnPerson() {
        Person jdoe = new Person("John", "Doe", "jdoe@example.net");
        entityManager.persist(jdoe);
        entityManager.flush();

        Optional<Person> found = personRepository.findByEmail("jdoe@example.net");

        assertTrue(found.isPresent());
        assertEquals(jdoe.getEmail(), found.get().getEmail());
    }

    @Test
    void whenFindByEmail_withUnknownEmail_thenReturnEmpty() {
        Optional<Person> found = personRepository.findByEmail("inconnu@example.net");

        assertTrue(found.isEmpty());
    }
}