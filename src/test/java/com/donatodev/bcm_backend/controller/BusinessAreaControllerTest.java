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

import com.donatodev.bcm_backend.dto.BusinessAreaDTO;
import com.donatodev.bcm_backend.entity.BusinessAreas;
import com.donatodev.bcm_backend.repository.BusinessAreasRepository;
import com.donatodev.bcm_backend.security.SecurityConfig;
import com.donatodev.bcm_backend.util.TestDataCleaner;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Integration tests for {@link BusinessAreaController}.
 * <p>
 * Tests CRUD operations on the /business-areas endpoint with
 * mock authentication and verifies responses and status codes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SecurityConfig.class)
class BusinessAreaControllerTest {
	
	@Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BusinessAreasRepository repository;
    
    @Autowired
    private TestDataCleaner testDataCleaner;

    /**
     * Cleans database before each test to ensure isolated test environment.
     */
    @BeforeEach
    @SuppressWarnings("unused")
    void cleanDb() {
        testDataCleaner.clean();
    }

    /**
     * Nested class containing tests for the business area API.
     */
    @Nested
    @TestMethodOrder(OrderAnnotation.class)
    @DisplayName("API Verification on Business Area")
    @SuppressWarnings("unused")
    class VerificationApiBusinessArea {

        /**
         * Test creating a new business area returns HTTP 201 Created.
         */
        @Test
        @Order(1)
        @DisplayName("The new area has been created")
        @WithMockUser(roles = "ADMIN")
        void shouldCreateBusinessArea() throws Exception {
            BusinessAreaDTO dto = new BusinessAreaDTO(null, "Logistics", "Handles shipments");

            mockMvc.perform(post("/business-areas")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isCreated());
        }

        /**
         * Test retrieving all business areas returns the saved entries.
         */
        @Test
        @Order(2)
        @DisplayName("All areas have been recovered successfully")
        @WithMockUser(roles = "ADMIN")
        void shouldGetAllBusinessAreas() throws Exception {
            BusinessAreas area1 = BusinessAreas.builder()
                    .name("Finance")
                    .description("Handles budgets")
                    .build();

            BusinessAreas area2 = BusinessAreas.builder()
                    .name("IT")
                    .description("Manages infrastructure")
                    .build();

            repository.save(area1);
            repository.save(area2);

            mockMvc.perform(get("/business-areas"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[?(@.name=='Finance')]").exists())
                    .andExpect(jsonPath("$[?(@.name=='IT')]").exists());
        }

        /**
         * Test retrieving a business area by valid ID returns correct data.
         */
       @Test
       @Order(3)
       @DisplayName("The ID area has been retrieved successfully")
       @WithMockUser(roles = "ADMIN")
       void getBusinessAreaById() throws Exception {
           BusinessAreas savedEntity = repository.save(
                    BusinessAreas.builder()
                   .name("Marketing")
                   .description("Area marketing")
                   .build()
         );

           mockMvc.perform(get("/business-areas/{id}", savedEntity.getId()))
                   .andExpect(status().isOk())
                   .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                   .andExpect(jsonPath("$.id").value(savedEntity.getId()))
                   .andExpect(jsonPath("$.name").value("Marketing"))
                   .andExpect(jsonPath("$.description").value("Area marketing"));
        }

        /**
         * Test updating an existing business area reflects changes.
         */
        @Test
        @Order(4)
        @DisplayName("The area has been updated successfully")
        @WithMockUser(roles = "ADMIN")
        void shouldUpdateBusinessArea() throws Exception {
            BusinessAreas original = repository.save(BusinessAreas.builder()
                    .name("OldName")
                    .description("Old description")
                    .build());

            BusinessAreaDTO updatedDTO = new BusinessAreaDTO(original.getId(), "NewName", "Updated description");

            mockMvc.perform(put("/business-areas/{id}", original.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(updatedDTO)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("NewName"))
                    .andExpect(jsonPath("$.description").value("Updated description"));
        }

        /**
         * Test deleting a business area by ID returns HTTP 204 No Content.
         */
        @Test
        @Order(5)
        @DisplayName("The area has been deleted successfully")
        @WithMockUser(roles = "ADMIN")
        void shouldDeleteBusinessArea() throws Exception {
            BusinessAreas areaToDelete = repository.save(BusinessAreas.builder()
                    .name("ToBeDeleted")
                    .description("Temporary")
                    .build());

            mockMvc.perform(delete("/business-areas/{id}", areaToDelete.getId()))
                    .andExpect(status().isNoContent());
        }

        /**
         * Test retrieving a business area with invalid ID returns HTTP 404 Not Found.
         */
        @Test
        @Order(6)
        @DisplayName("Area not found when searching by invalid ID")
        @WithMockUser(roles = "ADMIN")
        void shouldReturnNotFoundForInvalidId() throws Exception {
            mockMvc.perform(get("/business-areas/{id}", 9999L))
                    .andExpect(status().isNotFound());
        }
    }
    
        @Test
        @Order(7)
        @DisplayName("Access denied for non-admin user")
        @WithMockUser(roles = "MANAGER") 
        void shouldReturnForbiddenForManagerRole() throws Exception {
            mockMvc.perform(get("/business-areas"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Forbidden")));
    }
}