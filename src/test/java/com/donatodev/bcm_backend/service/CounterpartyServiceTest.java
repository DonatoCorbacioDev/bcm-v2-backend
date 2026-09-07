package com.donatodev.bcm_backend.service;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;

import com.donatodev.bcm_backend.config.TenantContext;
import com.donatodev.bcm_backend.dto.CounterpartyDTO;
import com.donatodev.bcm_backend.entity.Counterparty;
import com.donatodev.bcm_backend.entity.CounterpartyType;
import com.donatodev.bcm_backend.exception.CounterpartyNotFoundException;
import com.donatodev.bcm_backend.mapper.CounterpartyMapper;
import com.donatodev.bcm_backend.repository.CounterpartiesRepository;

/**
 * Unit tests for {@link CounterpartyService}, mirroring the coverage shape of
 * {@code BusinessAreaServiceTest} (its closest structural sibling), plus
 * dedicated tests for {@link CounterpartyService#resolveOrCreateByName} —
 * the resolve-or-create lookup used by contract Excel import.
 */
@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
class CounterpartyServiceTest {

    @Mock
    private CounterpartiesRepository repository;

    @Mock
    private CounterpartyMapper mapper;

    @InjectMocks
    private CounterpartyService service;

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("Unit Test: CounterpartyService")
    @SuppressWarnings("unused")
    class VerifyCounterpartyService {

        @Test
        @Order(1)
        @DisplayName("Get all counterparties returns list of DTOs")
        void shouldGetAllCounterparties() {
            Counterparty entity1 = Counterparty.builder().id(1L).name("Alfa Srl").type(CounterpartyType.CUSTOMER).build();
            Counterparty entity2 = Counterparty.builder().id(2L).name("Beta Srl").type(CounterpartyType.SUPPLIER).build();
            CounterpartyDTO dto1 = new CounterpartyDTO(1L, "Alfa Srl", CounterpartyType.CUSTOMER, null, null, null, null, null, null, null);
            CounterpartyDTO dto2 = new CounterpartyDTO(2L, "Beta Srl", CounterpartyType.SUPPLIER, null, null, null, null, null, null, null);

            when(repository.findAll()).thenReturn(Arrays.asList(entity1, entity2));
            when(mapper.toDTO(entity1)).thenReturn(dto1);
            when(mapper.toDTO(entity2)).thenReturn(dto2);

            List<CounterpartyDTO> result = service.getAllCounterparties();

            assertEquals(2, result.size());
            assertEquals("Alfa Srl", result.get(0).name());
            assertEquals("Beta Srl", result.get(1).name());
        }

        @Test
        @Order(2)
        @DisplayName("Get counterparty by ID returns DTO")
        void shouldGetCounterpartyById() {
            Counterparty entity = Counterparty.builder().id(1L).name("Alfa Srl").type(CounterpartyType.CUSTOMER).build();
            CounterpartyDTO dto = new CounterpartyDTO(1L, "Alfa Srl", CounterpartyType.CUSTOMER, null, null, null, null, null, null, null);

            when(repository.findById(1L)).thenReturn(Optional.of(entity));
            when(mapper.toDTO(entity)).thenReturn(dto);

            CounterpartyDTO result = service.getCounterpartyById(1L);

            assertEquals("Alfa Srl", result.name());
            assertEquals(CounterpartyType.CUSTOMER, result.type());
        }

        @Test
        @Order(3)
        @DisplayName("Get counterparty by ID throws exception if not found")
        void shouldThrowExceptionWhenCounterpartyNotFound() {
            when(repository.findById(999L)).thenReturn(Optional.empty());

            CounterpartyNotFoundException ex =
                assertThrows(CounterpartyNotFoundException.class, () -> service.getCounterpartyById(999L));
            assertEquals("ID controparte 999 non trovata", ex.getMessage());
        }

        @Test
        @Order(4)
        @DisplayName("Create counterparty returns saved DTO")
        void shouldCreateCounterparty() {
            CounterpartyDTO dto = new CounterpartyDTO(null, "New Srl", CounterpartyType.CUSTOMER, null, null, null, null, null, null, null);
            Counterparty entity = Counterparty.builder().name("New Srl").type(CounterpartyType.CUSTOMER).build();
            Counterparty savedEntity = Counterparty.builder().id(1L).name("New Srl").type(CounterpartyType.CUSTOMER).build();
            CounterpartyDTO savedDTO = new CounterpartyDTO(1L, "New Srl", CounterpartyType.CUSTOMER, null, null, null, null, null, null, null);

            when(mapper.toEntity(dto)).thenReturn(entity);
            when(repository.save(entity)).thenReturn(savedEntity);
            when(mapper.toDTO(savedEntity)).thenReturn(savedDTO);

            CounterpartyDTO result = service.createCounterparty(dto);

            assertEquals(1L, result.id());
            assertEquals("New Srl", result.name());
        }

        @Test
        @Order(5)
        @DisplayName("Update counterparty returns updated DTO")
        void shouldUpdateCounterparty() {
            Counterparty existing = Counterparty.builder().id(1L).name("Old").type(CounterpartyType.CUSTOMER).build();
            CounterpartyDTO updatedDTO = new CounterpartyDTO(1L, "Updated", CounterpartyType.SUPPLIER,
                    "IT12345678901", "RSSMRA80A01H501U", "Via Roma 1", "Mario Rossi", "mario@updated.com", "+39123456", "Note");

            when(repository.findById(1L)).thenReturn(Optional.of(existing));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(mapper.toDTO(any())).thenReturn(updatedDTO);

            CounterpartyDTO result = service.updateCounterparty(1L, updatedDTO);

            assertEquals("Updated", result.name());
            assertEquals(CounterpartyType.SUPPLIER, result.type());
            assertEquals(CounterpartyType.SUPPLIER, existing.getType());
            assertEquals("IT12345678901", existing.getVatNumber());
            assertEquals("mario@updated.com", existing.getContactEmail());
        }

        @Test
        @Order(6)
        @DisplayName("Update counterparty throws exception if not found")
        void shouldThrowExceptionWhenUpdatingMissingCounterparty() {
            CounterpartyDTO updatedDTO = new CounterpartyDTO(1L, "Updated", CounterpartyType.CUSTOMER, null, null, null, null, null, null, null);
            when(repository.findById(1L)).thenReturn(Optional.empty());

            CounterpartyNotFoundException ex =
                assertThrows(CounterpartyNotFoundException.class, () -> service.updateCounterparty(1L, updatedDTO));
            assertEquals("ID controparte 1 non trovata", ex.getMessage());
        }

        @Test
        @Order(7)
        @DisplayName("Delete counterparty calls repository")
        void shouldDeleteCounterparty() {
            Long id = 1L;
            Counterparty counterparty = Counterparty.builder().id(id).build();
            when(repository.findById(id)).thenReturn(Optional.of(counterparty));

            service.deleteCounterparty(id);

            verify(repository, times(1)).delete(counterparty);
        }

        @Test
        @Order(8)
        @DisplayName("Delete counterparty throws if it doesn't exist")
        void shouldThrowWhenDeletingNonExistentCounterparty() {
            when(repository.findById(999L)).thenReturn(Optional.empty());

            CounterpartyNotFoundException ex = assertThrows(CounterpartyNotFoundException.class,
                    () -> service.deleteCounterparty(999L));
            assertEquals("ID controparte 999 non trovata", ex.getMessage());
            verify(repository, never()).delete(any(Counterparty.class));
        }

        @Test
        @Order(9)
        @DisplayName("getAllCounterparties with TenantContext uses org-filtered repository")
        void shouldGetAllCounterpartiesWithTenantContext() {
            Counterparty counterparty = Counterparty.builder().id(1L).name("Alfa Srl").type(CounterpartyType.CUSTOMER).build();
            CounterpartyDTO dto = new CounterpartyDTO(1L, "Alfa Srl", CounterpartyType.CUSTOMER, null, null, null, null, null, null, null);

            TenantContext.set(1L);
            try {
                when(repository.findAllByOrganizationId(1L)).thenReturn(List.of(counterparty));
                when(mapper.toDTO(counterparty)).thenReturn(dto);

                List<CounterpartyDTO> result = service.getAllCounterparties();

                assertEquals(1, result.size());
                verify(repository).findAllByOrganizationId(1L);
            } finally {
                TenantContext.clear();
            }
        }

        @Test
        @Order(10)
        @DisplayName("createCounterparty with TenantContext sets organization on entity")
        void shouldCreateCounterpartyWithTenantContext() {
            CounterpartyDTO dto = new CounterpartyDTO(null, "Gamma Srl", CounterpartyType.CUSTOMER, null, null, null, null, null, null, null);
            Counterparty entity = Counterparty.builder().name("Gamma Srl").type(CounterpartyType.CUSTOMER).build();
            Counterparty saved = Counterparty.builder().id(2L).name("Gamma Srl").type(CounterpartyType.CUSTOMER).build();
            CounterpartyDTO savedDTO = new CounterpartyDTO(2L, "Gamma Srl", CounterpartyType.CUSTOMER, null, null, null, null, null, null, null);

            TenantContext.set(5L);
            try {
                when(mapper.toEntity(dto)).thenReturn(entity);
                when(repository.save(any())).thenReturn(saved);
                when(mapper.toDTO(saved)).thenReturn(savedDTO);

                CounterpartyDTO result = service.createCounterparty(dto);

                assertEquals(2L, result.id());
                assertNotNull(entity.getOrganization());
                assertEquals(5L, entity.getOrganization().getId());
            } finally {
                TenantContext.clear();
            }
        }

        @Test
        @Order(11)
        @DisplayName("getCounterpartyById with TenantContext uses org-scoped repository")
        void shouldGetCounterpartyByIdWithTenantContext() {
            Counterparty entity = Counterparty.builder().id(1L).name("Alfa Srl").type(CounterpartyType.CUSTOMER).build();
            CounterpartyDTO dto = new CounterpartyDTO(1L, "Alfa Srl", CounterpartyType.CUSTOMER, null, null, null, null, null, null, null);

            TenantContext.set(8L);
            try {
                when(repository.findByIdAndOrganizationId(1L, 8L)).thenReturn(Optional.of(entity));
                when(mapper.toDTO(entity)).thenReturn(dto);

                CounterpartyDTO result = service.getCounterpartyById(1L);

                assertEquals("Alfa Srl", result.name());
                verify(repository).findByIdAndOrganizationId(1L, 8L);
            } finally {
                TenantContext.clear();
            }
        }
    }

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("Unit Test: CounterpartyService.resolveOrCreateByName")
    @SuppressWarnings("unused")
    class ResolveOrCreateByName {

        @Test
        @Order(1)
        @DisplayName("Returns the existing counterparty on a case-insensitive name match within the org")
        void shouldReturnExistingCounterpartyOnCaseInsensitiveMatch() {
            Counterparty existing = Counterparty.builder().id(3L).name("Alfa Srl").type(CounterpartyType.CUSTOMER).build();

            TenantContext.set(1L);
            try {
                when(repository.findByNameIgnoreCaseAndOrganizationId("alfa srl", 1L))
                        .thenReturn(Optional.of(existing));

                Counterparty result = service.resolveOrCreateByName("alfa srl");

                assertEquals(3L, result.getId());
                verify(repository, never()).save(any());
            } finally {
                TenantContext.clear();
            }
        }

        @Test
        @Order(2)
        @DisplayName("Creates a new CUSTOMER counterparty when no match exists in the org")
        void shouldCreateNewCounterpartyWhenNoMatch() {
            TenantContext.set(1L);
            try {
                when(repository.findByNameIgnoreCaseAndOrganizationId("Nuova Srl", 1L))
                        .thenReturn(Optional.empty());
                when(repository.save(any(Counterparty.class))).thenAnswer(inv -> {
                    Counterparty c = inv.getArgument(0);
                    c.setId(42L);
                    return c;
                });

                Counterparty result = service.resolveOrCreateByName("Nuova Srl");

                assertEquals(42L, result.getId());
                assertEquals("Nuova Srl", result.getName());
                assertEquals(CounterpartyType.CUSTOMER, result.getType());
                assertNotNull(result.getOrganization());
                assertEquals(1L, result.getOrganization().getId());
            } finally {
                TenantContext.clear();
            }
        }

        @Test
        @Order(3)
        @DisplayName("Without a tenant context, always creates rather than looking up by name")
        void shouldCreateWithoutTenantContext() {
            when(repository.save(any(Counterparty.class))).thenAnswer(inv -> {
                Counterparty c = inv.getArgument(0);
                c.setId(7L);
                return c;
            });

            Counterparty result = service.resolveOrCreateByName("No Org Srl");

            assertEquals(7L, result.getId());
            assertEquals(CounterpartyType.CUSTOMER, result.getType());
            verify(repository, never()).findByNameIgnoreCaseAndOrganizationId(any(), any());
        }
    }
}
