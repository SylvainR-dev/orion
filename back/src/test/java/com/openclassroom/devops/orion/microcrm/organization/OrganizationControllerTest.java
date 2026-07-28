package com.openclassroom.devops.orion.microcrm.organization;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.hamcrest.Matchers.hasSize;

import com.openclassroom.devops.orion.microcrm.person.Person;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrganizationController.class)
class OrganizationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrganizationService organizationService;

    @Test
    void findAll_returns200WithList() throws Exception {
        Organization organization = new Organization("Orion Incorporated");
        when(organizationService.findAll()).thenReturn(List.of(organization));

        mockMvc.perform(get("/api/organizations"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("Orion Incorporated"));
    }

    @Test
    void findById_whenExists_returns200() throws Exception {
        Organization organization = new Organization("Orion Incorporated");
        when(organizationService.findById(1L)).thenReturn(organization);

        mockMvc.perform(get("/api/organizations/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Orion Incorporated"));
    }

    @Test
    void findById_whenNotFound_returns404() throws Exception {
        when(organizationService.findById(99L)).thenThrow(new OrganizationNotFoundException(99L));

        mockMvc.perform(get("/api/organizations/99"))
            .andExpect(status().isNotFound());
    }

    @Test
    void create_withValidRequest_returns201() throws Exception {
        OrganizationRequest request = new OrganizationRequest("Orion Incorporated");
        Organization saved = new Organization("Orion Incorporated");
        when(organizationService.create(any(OrganizationRequest.class))).thenReturn(saved);

        mockMvc.perform(post("/api/organizations")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Orion Incorporated"));
    }

    @Test
    void create_withBlankName_returns400() throws Exception {
        OrganizationRequest request = new OrganizationRequest("");

        mockMvc.perform(post("/api/organizations")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());

        verify(organizationService, never()).create(any());
    }

    @Test
    void delete_whenExists_returns204() throws Exception {
        mockMvc.perform(delete("/api/organizations/1"))
            .andExpect(status().isNoContent());

        verify(organizationService).delete(1L);
    }

    @Test
    void addPerson_whenBothExist_returns200WithUpdatedOrganization() throws Exception {
        Organization organization = new Organization("Orion Incorporated");
        organization.addPerson(new Person("John", "Doe", "jdoe@example.net"));
        when(organizationService.addPerson(1L, 2L)).thenReturn(organization);

        mockMvc.perform(post("/api/organizations/1/persons/2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.personIds", hasSize(1)));
    }

    @Test
    void addPerson_whenOrganizationNotFound_returns404() throws Exception {
        when(organizationService.addPerson(99L, 2L)).thenThrow(new OrganizationNotFoundException(99L));

        mockMvc.perform(post("/api/organizations/99/persons/2"))
            .andExpect(status().isNotFound());
    }

    @Test
    void removePerson_whenBothExist_returns200() throws Exception {
        Organization organization = new Organization("Orion Incorporated");
        when(organizationService.removePerson(1L, 2L)).thenReturn(organization);

        mockMvc.perform(delete("/api/organizations/1/persons/2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.personIds", hasSize(0)));
    }
}