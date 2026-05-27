package com.donatodev.bcm_backend.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.donatodev.bcm_backend.dto.ManagerDTO;
import com.donatodev.bcm_backend.entity.Managers;

@SpringBootTest
@ActiveProfiles("test")
class ManagerMapperTest {

    @Autowired
    private ManagerMapper managerMapper;

    @Nested
    class ToDTOTests {

        @Test
        void shouldMapEntityToDTO() {
            Managers manager = Managers.builder()
                    .id(1L)
                    .firstName("Mario")
                    .lastName("Rossi")
                    .email("mario.rossi@example.com")
                    .phoneNumber("123456789")
                    .department("IT")
                    .build();

            ManagerDTO dto = managerMapper.toDTO(manager);

            assertEquals(1L, dto.id());
            assertEquals("Mario", dto.firstName());
            assertEquals("Rossi", dto.lastName());
            assertEquals("mario.rossi@example.com", dto.email());
            assertEquals("123456789", dto.phoneNumber());
            assertEquals("IT", dto.department());
        }

        @Test
        void shouldMapEntityToDTOWithNullOptionalFields() {
            Managers manager = Managers.builder()
                    .id(2L)
                    .firstName("Luca")
                    .lastName("Bianchi")
                    .email("luca@example.com")
                    .phoneNumber(null)
                    .department(null)
                    .build();

            ManagerDTO dto = managerMapper.toDTO(manager);

            assertEquals(2L, dto.id());
            assertEquals("Luca", dto.firstName());
            assertEquals("Bianchi", dto.lastName());
            assertEquals("luca@example.com", dto.email());
            assertNull(dto.phoneNumber());
            assertNull(dto.department());
        }

        @Test
        void shouldMapEntityToDTOWithNullId() {
            Managers manager = Managers.builder()
                    .id(null)
                    .firstName("Anna")
                    .lastName("Verdi")
                    .email("anna@example.com")
                    .phoneNumber("987654321")
                    .department("Finance")
                    .build();

            ManagerDTO dto = managerMapper.toDTO(manager);

            assertNull(dto.id());
            assertEquals("Anna", dto.firstName());
        }
    }

    @Nested
    class ToEntityTests {

        @Test
        void shouldMapDTOToEntity() {
            ManagerDTO dto = new ManagerDTO(10L, "Paolo", "Neri", "paolo@example.com", "111222333", "HR");

            Managers manager = managerMapper.toEntity(dto);

            assertEquals(10L, manager.getId());
            assertEquals("Paolo", manager.getFirstName());
            assertEquals("Neri", manager.getLastName());
            assertEquals("paolo@example.com", manager.getEmail());
            assertEquals("111222333", manager.getPhoneNumber());
            assertEquals("HR", manager.getDepartment());
        }

        @Test
        void shouldMapDTOToEntityWithNullOptionalFields() {
            ManagerDTO dto = new ManagerDTO(11L, "Giulia", "Ferri", "giulia@example.com", null, null);

            Managers manager = managerMapper.toEntity(dto);

            assertEquals(11L, manager.getId());
            assertEquals("Giulia", manager.getFirstName());
            assertEquals("Ferri", manager.getLastName());
            assertEquals("giulia@example.com", manager.getEmail());
            assertNull(manager.getPhoneNumber());
            assertNull(manager.getDepartment());
        }

        @Test
        void shouldMapDTOToEntityWithNullId() {
            ManagerDTO dto = new ManagerDTO(null, "Franco", "Mari", "franco@example.com", "555666777", "Sales");

            Managers manager = managerMapper.toEntity(dto);

            assertNull(manager.getId());
            assertEquals("Franco", manager.getFirstName());
        }
    }
}
