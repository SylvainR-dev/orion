package com.openclassroom.devops.orion.microcrm.person;

import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class PersonService {

    private final PersonRepository personRepository;

    public PersonService(PersonRepository personRepository) {
        this.personRepository = personRepository;
    }

    public List<Person> findAll() {
        return personRepository.findAll();
    }

    public Person findById(Long id) {
        return personRepository.findById(id)
            .orElseThrow(() -> new PersonNotFoundException(id));
    }

    public Person create(PersonRequest request) {
        personRepository.findByEmail(request.email()).ifPresent(existing -> {
            throw new EmailAlreadyUsedException(request.email());
        });

        Person person = new Person(request.firstName(), request.lastName(), request.email());
        person.setPhone(request.phone());
        person.setBio(request.bio());
        return personRepository.save(person);
    }

    public Person update(Long id, PersonRequest request) {
        Person person = findById(id);

        personRepository.findByEmail(request.email())
            .filter(existing -> !existing.getId().equals(id))
            .ifPresent(existing -> {
                throw new EmailAlreadyUsedException(request.email());
            });

        person.setFirstName(request.firstName());
        person.setLastName(request.lastName());
        person.setEmail(request.email());
        person.setPhone(request.phone());
        person.setBio(request.bio());
        return personRepository.save(person);
    }

    public void delete(Long id) {
        Person person = findById(id);
        personRepository.delete(person);
    }
}