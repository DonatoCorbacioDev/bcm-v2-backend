package com.donatodev.bcm_backend.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.donatodev.bcm_backend.dto.RoleDTO;
import com.donatodev.bcm_backend.entity.Roles;

@SpringBootTest
@ActiveProfiles("test")
class RoleMapperTest {

    @Autowired
    private RoleMapper roleMapper;

    @Nested
    class ToDTOTests {

        @Test
        void shouldMapAdminEntityToDTO() {
            Roles role = Roles.builder()
                    .id(1L)
                    .role("ADMIN")
                    .build();

            RoleDTO dto = roleMapper.toDTO(role);

            assertEquals(1L, dto.id());
            assertEquals("ADMIN", dto.role());
        }

        @Test
        void shouldMapManagerEntityToDTO() {
            Roles role = Roles.builder()
                    .id(2L)
                    .role("MANAGER")
                    .build();

            RoleDTO dto = roleMapper.toDTO(role);

            assertEquals(2L, dto.id());
            assertEquals("MANAGER", dto.role());
        }

        @Test
        void shouldMapEntityToDTOWithNullId() {
            Roles role = Roles.builder()
                    .id(null)
                    .role("USER")
                    .build();

            RoleDTO dto = roleMapper.toDTO(role);

            assertNull(dto.id());
            assertEquals("USER", dto.role());
        }
    }

    @Nested
    class ToEntityTests {

        @Test
        void shouldMapDTOToEntity() {
            RoleDTO dto = new RoleDTO(10L, "ADMIN");

            Roles role = roleMapper.toEntity(dto);

            assertEquals(10L, role.getId());
            assertEquals("ADMIN", role.getRole());
        }

        @Test
        void shouldMapDTOToEntityWithNullId() {
            RoleDTO dto = new RoleDTO(null, "MANAGER");

            Roles role = roleMapper.toEntity(dto);

            assertNull(role.getId());
            assertEquals("MANAGER", role.getRole());
        }

        @Test
        void shouldMapDTOToEntityRoundTrip() {
            Roles original = Roles.builder().id(5L).role("ADMIN").build();

            RoleDTO dto = roleMapper.toDTO(original);
            Roles restored = roleMapper.toEntity(dto);

            assertEquals(original.getId(), restored.getId());
            assertEquals(original.getRole(), restored.getRole());
        }
    }
}
