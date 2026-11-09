package com.donatodev.bcm_backend.mapper;

import static com.donatodev.bcm_backend.support.TestIds.uid;
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
                    .id(uid(1))
                    .role("ADMIN")
                    .build();

            RoleDTO dto = roleMapper.toDTO(role);

            assertEquals(uid(1), dto.id());
            assertEquals("ADMIN", dto.role());
        }

        @Test
        void shouldMapManagerEntityToDTO() {
            Roles role = Roles.builder()
                    .id(uid(2))
                    .role("MANAGER")
                    .build();

            RoleDTO dto = roleMapper.toDTO(role);

            assertEquals(uid(2), dto.id());
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
            RoleDTO dto = new RoleDTO(uid(10), "ADMIN");

            Roles role = roleMapper.toEntity(dto);

            assertEquals(uid(10), role.getId());
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
            Roles original = Roles.builder().id(uid(5)).role("ADMIN").build();

            RoleDTO dto = roleMapper.toDTO(original);
            Roles restored = roleMapper.toEntity(dto);

            assertEquals(original.getId(), restored.getId());
            assertEquals(original.getRole(), restored.getRole());
        }
    }
}
