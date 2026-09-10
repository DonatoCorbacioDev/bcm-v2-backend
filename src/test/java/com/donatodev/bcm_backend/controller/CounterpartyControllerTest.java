package com.donatodev.bcm_backend.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.donatodev.bcm_backend.dto.CounterpartyDTO;
import com.donatodev.bcm_backend.entity.Counterparty;
import com.donatodev.bcm_backend.entity.CounterpartyType;
import com.donatodev.bcm_backend.repository.CounterpartiesRepository;
import com.donatodev.bcm_backend.security.SecurityConfig;
import com.donatodev.bcm_backend.util.TestDataCleaner;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Integration tests for {@link CounterpartyController}, mirroring the
 * structure and coverage of {@code BusinessAreaControllerTest} (its closest
 * structural sibling): CRUD happy paths, not-found, and the ADMIN-writes /
 * MANAGER-reads-only role split.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SecurityConfig.class)
class CounterpartyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CounterpartiesRepository repository;

    @Autowired
    private TestDataCleaner testDataCleaner;

    @BeforeEach
    @SuppressWarnings("unused")
    void cleanDb() {
        testDataCleaner.clean();
        // TestDataCleaner predates the counterparties table.
        repository.deleteAll();
    }

    @Nested
    @TestMethodOrder(OrderAnnotation.class)
    @DisplayName("API Verification on Counterparty")
    @SuppressWarnings("unused")
    class VerificationApiCounterparty {

        @Test
        @Order(1)
        @DisplayName("The new counterparty has been created")
        @WithMockUser(roles = "ADMIN")
        void shouldCreateCounterparty() throws Exception {
            CounterpartyDTO dto = new CounterpartyDTO(null, "Logistics Srl", CounterpartyType.SUPPLIER,
                    "IT00000000001", null, "Via Milano 1", "Anna Bianchi", "anna@logistics.it", "+39000000", null);

            mockMvc.perform(post("/counterparties")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("Logistics Srl"))
                    .andExpect(jsonPath("$.type").value("SUPPLIER"));
        }

        @Test
        @Order(2)
        @DisplayName("Creating a counterparty without a name is rejected")
        @WithMockUser(roles = "ADMIN")
        void shouldRejectCounterpartyWithoutName() throws Exception {
            CounterpartyDTO dto = new CounterpartyDTO(null, "", CounterpartyType.CUSTOMER, null, null, null, null, null, null, null);

            mockMvc.perform(post("/counterparties")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @Order(3)
        @DisplayName("All counterparties have been recovered successfully")
        @WithMockUser(roles = "ADMIN")
        void shouldGetAllCounterparties() throws Exception {
            repository.save(Counterparty.builder().name("Alfa Srl").type(CounterpartyType.CUSTOMER).build());
            repository.save(Counterparty.builder().name("Beta Srl").type(CounterpartyType.SUPPLIER).build());

            mockMvc.perform(get("/counterparties"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[?(@.name=='Alfa Srl')]").exists())
                    .andExpect(jsonPath("$[?(@.name=='Beta Srl')]").exists());
        }

        @Test
        @Order(4)
        @DisplayName("The counterparty has been retrieved successfully by ID")
        @WithMockUser(roles = "ADMIN")
        void getCounterpartyById() throws Exception {
            Counterparty saved = repository.save(Counterparty.builder()
                    .name("Gamma Srl").type(CounterpartyType.BOTH).vatNumber("IT123").build());

            mockMvc.perform(get("/counterparties/{id}", saved.getId()))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id").value(saved.getId()))
                    .andExpect(jsonPath("$.name").value("Gamma Srl"))
                    .andExpect(jsonPath("$.type").value("BOTH"))
                    .andExpect(jsonPath("$.vatNumber").value("IT123"));
        }

        @Test
        @Order(5)
        @DisplayName("The counterparty has been updated successfully")
        @WithMockUser(roles = "ADMIN")
        void shouldUpdateCounterparty() throws Exception {
            Counterparty original = repository.save(Counterparty.builder()
                    .name("OldName").type(CounterpartyType.CUSTOMER).build());

            CounterpartyDTO updatedDTO = new CounterpartyDTO(original.getId(), "NewName", CounterpartyType.SUPPLIER,
                    null, null, null, null, null, null, "Updated notes");

            mockMvc.perform(put("/counterparties/{id}", original.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(updatedDTO)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("NewName"))
                    .andExpect(jsonPath("$.type").value("SUPPLIER"))
                    .andExpect(jsonPath("$.notes").value("Updated notes"));
        }

        @Test
        @Order(6)
        @DisplayName("The counterparty has been deleted successfully")
        @WithMockUser(roles = "ADMIN")
        void shouldDeleteCounterparty() throws Exception {
            Counterparty toDelete = repository.save(Counterparty.builder()
                    .name("ToBeDeleted").type(CounterpartyType.CUSTOMER).build());

            mockMvc.perform(delete("/counterparties/{id}", toDelete.getId()))
                    .andExpect(status().isNoContent());
        }

        @Test
        @Order(7)
        @DisplayName("Counterparty not found when searching by invalid ID")
        @WithMockUser(roles = "ADMIN")
        void shouldReturnNotFoundForInvalidId() throws Exception {
            mockMvc.perform(get("/counterparties/{id}", 9999L))
                    .andExpect(status().isNotFound());
        }

        @Test
        @Order(8)
        @DisplayName("MANAGER can read counterparties (GET allowed)")
        @WithMockUser(roles = "MANAGER")
        void shouldAllowManagerToReadCounterparties() throws Exception {
            mockMvc.perform(get("/counterparties"))
                    .andExpect(status().isOk());
        }

        @Test
        @Order(9)
        @DisplayName("MANAGER cannot create counterparties (POST forbidden)")
        @WithMockUser(roles = "MANAGER")
        void shouldReturnForbiddenForManagerOnPost() throws Exception {
            CounterpartyDTO dto = new CounterpartyDTO(null, "NewCounterparty", CounterpartyType.CUSTOMER, null, null, null, null, null, null, null);
            mockMvc.perform(post("/counterparties")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @Order(10)
        @DisplayName("MANAGER cannot delete counterparties (DELETE forbidden)")
        @WithMockUser(roles = "MANAGER")
        void shouldReturnForbiddenForManagerOnDelete() throws Exception {
            Counterparty counterparty = repository.save(Counterparty.builder()
                    .name("ManagerCannotDelete").type(CounterpartyType.CUSTOMER).build());
            mockMvc.perform(delete("/counterparties/{id}", counterparty.getId()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @Order(11)
        @DisplayName("Invoicing summary is retrieved successfully by ID")
        @WithMockUser(roles = "ADMIN")
        void shouldGetInvoicingSummary() throws Exception {
            Counterparty saved = repository.save(Counterparty.builder()
                    .name("Delta Srl").type(CounterpartyType.CUSTOMER).build());

            mockMvc.perform(get("/counterparties/{id}/invoicing-summary", saved.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.counterpartyId").value(saved.getId()))
                    .andExpect(jsonPath("$.counterpartyName").value("Delta Srl"))
                    .andExpect(jsonPath("$.activeContracts").value(0))
                    .andExpect(jsonPath("$.invoiceCount").value(0));
        }

        @Test
        @Order(12)
        @DisplayName("MANAGER can read the invoicing summary (GET allowed)")
        @WithMockUser(roles = "MANAGER")
        void shouldAllowManagerToReadInvoicingSummary() throws Exception {
            Counterparty saved = repository.save(Counterparty.builder()
                    .name("Epsilon Srl").type(CounterpartyType.CUSTOMER).build());

            mockMvc.perform(get("/counterparties/{id}/invoicing-summary", saved.getId()))
                    .andExpect(status().isOk());
        }
    }
}
