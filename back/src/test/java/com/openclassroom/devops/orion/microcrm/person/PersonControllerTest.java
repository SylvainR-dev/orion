package com.openclassroom.devops.orion.microcrm.person;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PersonController.class)
class PersonControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PersonService personService;

    @Test
    void findAll_returns200WithList() throws Exception {
        Person person = new Person("John", "Doe", "jdoe@example.net");
        when(personService.findAll()).thenReturn(List.of(person));

        mockMvc.perform(get("/api/persons"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].email").value("jdoe@example.net"));
    }

    @Test
    void findById_whenExists_returns200() throws Exception {
        Person person = new Person("John", "Doe", "jdoe@example.net");
        when(personService.findById(1L)).thenReturn(person);

        mockMvc.perform(get("/api/persons/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("jdoe@example.net"));
    }

    @Test
    void findById_whenNotFound_returns404() throws Exception {
        when(personService.findById(99L)).thenThrow(new PersonNotFoundException(99L));

        mockMvc.perform(get("/api/persons/99"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Person not found with id 99"));
    }

    @Test
    void create_withValidRequest_returns201() throws Exception {
        PersonRequest request = new PersonRequest("John", "Doe", "jdoe@example.net", "0600000000", null);
        Person saved = new Person("John", "Doe", "jdoe@example.net");
        when(personService.create(any(PersonRequest.class))).thenReturn(saved);

        mockMvc.perform(post("/api/persons")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.email").value("jdoe@example.net"));
    }

    @Test
    void create_withBlankFirstName_returns400() throws Exception {
        PersonRequest request = new PersonRequest("", "Doe", "jdoe@example.net", null, null);

        mockMvc.perform(post("/api/persons")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());

        verify(personService, never()).create(any());
    }

    @Test
    void create_withInvalidEmail_returns400() throws Exception {
        PersonRequest request = new PersonRequest("John", "Doe", "not-an-email", null, null);

        mockMvc.perform(post("/api/persons")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());

        verify(personService, never()).create(any());
    }

    @Test
    void create_withDuplicateEmail_returns409() throws Exception {
        PersonRequest request = new PersonRequest("John", "Doe", "jdoe@example.net", null, null);
        when(personService.create(any(PersonRequest.class))).thenThrow(new EmailAlreadyUsedException("jdoe@example.net"));

        mockMvc.perform(post("/api/persons")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict());
    }

    @Test
    void delete_whenExists_returns204() throws Exception {
        mockMvc.perform(delete("/api/persons/1"))
            .andExpect(status().isNoContent());

        verify(personService).delete(1L);
    }

    @Test
    void delete_whenNotFound_returns404() throws Exception {
        doThrow(new PersonNotFoundException(99L)).when(personService).delete(99L);

        mockMvc.perform(delete("/api/persons/99"))
            .andExpect(status().isNotFound());
    }
}