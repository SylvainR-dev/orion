package com.openclassroom.devops.orion.microcrm.organization;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.openclassroom.devops.orion.microcrm.person.Person;
import com.openclassroom.devops.orion.microcrm.person.PersonService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrganizationServiceTest {

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private PersonService personService;

    @InjectMocks
    private OrganizationService organizationService;

    @Test
    void findAll_returnsAllOrganizations() {
        Organization org1 = new Organization("Orion Incorporated");
        Organization org2 = new Organization("Acme Corp");
        when(organizationRepository.findAll()).thenReturn(List.of(org1, org2));

        List<Organization> result = organizationService.findAll();

        assertThat(result).hasSize(2).containsExactly(org1, org2);
    }

    @Test
    void findById_whenExists_returnsOrganization() {
        Organization organization = new Organization("Orion Incorporated");
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization));

        Organization result = organizationService.findById(1L);

        assertThat(result).isEqualTo(organization);
    }

    @Test
    void findById_whenNotFound_throwsOrganizationNotFoundException() {
        when(organizationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(OrganizationNotFoundException.class, () -> organizationService.findById(99L));
    }

    @Test
    void create_savesOrganization() {
        OrganizationRequest request = new OrganizationRequest("Orion Incorporated");
        when(organizationRepository.save(any(Organization.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Organization result = organizationService.create(request);

        assertThat(result.getName()).isEqualTo("Orion Incorporated");
        verify(organizationRepository).save(any(Organization.class));
    }

    @Test
    void update_whenExists_updatesName() {
        Organization organization = new Organization("Old Name");
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization));
        when(organizationRepository.save(any(Organization.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Organization result = organizationService.update(1L, new OrganizationRequest("New Name"));

        assertThat(result.getName()).isEqualTo("New Name");
    }

    @Test
    void update_whenNotFound_throwsOrganizationNotFoundException() {
        when(organizationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(OrganizationNotFoundException.class,
            () -> organizationService.update(99L, new OrganizationRequest("New Name")));
        verify(organizationRepository, never()).save(any());
    }

    @Test
    void delete_whenExists_deletesOrganization() {
        Organization organization = new Organization("Orion Incorporated");
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization));

        organizationService.delete(1L);

        verify(organizationRepository).delete(organization);
    }

    @Test
    void delete_whenNotFound_throwsOrganizationNotFoundException() {
        when(organizationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(OrganizationNotFoundException.class, () -> organizationService.delete(99L));
        verify(organizationRepository, never()).delete(any());
    }

    @Test
    void addPerson_whenBothExist_addsPersonToOrganization() {
        Organization organization = new Organization("Orion Incorporated");
        Person person = new Person("John", "Doe", "jdoe@example.net");

        when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization));
        when(personService.findById(2L)).thenReturn(person);
        when(organizationRepository.save(any(Organization.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Organization result = organizationService.addPerson(1L, 2L);

        assertThat(result.getPersons()).contains(person);
    }

    @Test
    void addPerson_whenOrganizationNotFound_throwsOrganizationNotFoundException() {
        when(organizationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(OrganizationNotFoundException.class, () -> organizationService.addPerson(99L, 2L));
        verify(personService, never()).findById(any());
        verify(organizationRepository, never()).save(any());
    }

    @Test
    void removePerson_whenBothExist_removesPersonFromOrganization() {
        Organization organization = new Organization("Orion Incorporated");
        Person person = new Person("John", "Doe", "jdoe@example.net");
        organization.addPerson(person);

        when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization));
        when(personService.findById(2L)).thenReturn(person);
        when(organizationRepository.save(any(Organization.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Organization result = organizationService.removePerson(1L, 2L);

        assertThat(result.getPersons()).doesNotContain(person);
    }
}