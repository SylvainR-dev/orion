package com.openclassroom.devops.orion.microcrm.person;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PersonServiceTest {

    @Mock
    private PersonRepository personRepository;

    @InjectMocks
    private PersonService personService;

    @Test
    void findAll_returnsAllPersons() {
        Person p1 = new Person("John", "Doe", "jdoe@example.net");
        Person p2 = new Person("Jane", "Smith", "jsmith@example.net");
        when(personRepository.findAll()).thenReturn(List.of(p1, p2));

        List<Person> result = personService.findAll();

        assertThat(result).hasSize(2).containsExactly(p1, p2);
    }

    @Test
    void findById_whenExists_returnsPerson() {
        Person person = new Person("John", "Doe", "jdoe@example.net");
        when(personRepository.findById(1L)).thenReturn(Optional.of(person));

        Person result = personService.findById(1L);

        assertThat(result).isEqualTo(person);
    }

    @Test
    void findById_whenNotFound_throwsPersonNotFoundException() {
        when(personRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(PersonNotFoundException.class, () -> personService.findById(99L));
    }

    @Test
    void create_withNewEmail_savesPerson() {
        PersonRequest request = new PersonRequest("John", "Doe", "jdoe@example.net", "0600000000", "bio");
        when(personRepository.findByEmail("jdoe@example.net")).thenReturn(Optional.empty());
        when(personRepository.save(any(Person.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Person result = personService.create(request);

        assertThat(result.getFirstName()).isEqualTo("John");
        assertThat(result.getEmail()).isEqualTo("jdoe@example.net");
        assertThat(result.getPhone()).isEqualTo("0600000000");
        verify(personRepository).save(any(Person.class));
    }

    @Test
    void create_withExistingEmail_throwsEmailAlreadyUsedException() {
        PersonRequest request = new PersonRequest("John", "Doe", "jdoe@example.net", null, null);
        Person existing = new Person("Someone", "Else", "jdoe@example.net");
        when(personRepository.findByEmail("jdoe@example.net")).thenReturn(Optional.of(existing));

        assertThrows(EmailAlreadyUsedException.class, () -> personService.create(request));
        verify(personRepository, never()).save(any());
    }

    @Test
    void update_withSameEmailAsOwnRecord_doesNotThrow() {
        Person person = new Person("John", "Doe", "jdoe@example.net");
        setId(person, 1L);
        PersonRequest request = new PersonRequest("John", "Updated", "jdoe@example.net", null, null);

        when(personRepository.findById(1L)).thenReturn(Optional.of(person));
        when(personRepository.findByEmail("jdoe@example.net")).thenReturn(Optional.of(person));
        when(personRepository.save(any(Person.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Person result = personService.update(1L, request);

        assertThat(result.getLastName()).isEqualTo("Updated");
    }

    @Test
    void update_withEmailUsedByAnotherPerson_throwsEmailAlreadyUsedException() {
        Person person = new Person("John", "Doe", "jdoe@example.net");
        setId(person, 1L);
        Person other = new Person("Jane", "Smith", "jsmith@example.net");
        setId(other, 2L);
        PersonRequest request = new PersonRequest("John", "Doe", "jsmith@example.net", null, null);

        when(personRepository.findById(1L)).thenReturn(Optional.of(person));
        when(personRepository.findByEmail("jsmith@example.net")).thenReturn(Optional.of(other));

        assertThrows(EmailAlreadyUsedException.class, () -> personService.update(1L, request));
        verify(personRepository, never()).save(any());
    }

    @Test
    void delete_whenExists_deletesPerson() {
        Person person = new Person("John", "Doe", "jdoe@example.net");
        when(personRepository.findById(1L)).thenReturn(Optional.of(person));

        personService.delete(1L);

        verify(personRepository).delete(person);
    }

    @Test
    void delete_whenNotFound_throwsPersonNotFoundException() {
        when(personRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(PersonNotFoundException.class, () -> personService.delete(99L));
        verify(personRepository, never()).delete(any());
    }

    private void setId(Person person, Long id) {
        try {
            var field = Person.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(person, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}