package com.donatodev.bcm_backend.mapper;

import static com.donatodev.bcm_backend.support.TestIds.uid;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import com.donatodev.bcm_backend.config.TenantContext;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.donatodev.bcm_backend.dto.ContractHistoryDTO;
import com.donatodev.bcm_backend.entity.ContractHistory;
import com.donatodev.bcm_backend.entity.ContractStatus;
import com.donatodev.bcm_backend.entity.Contracts;
import com.donatodev.bcm_backend.entity.Managers;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.repository.ContractsRepository;
import com.donatodev.bcm_backend.repository.UsersRepository;

@ExtendWith(MockitoExtension.class)
class ContractHistoryMapperTest {

    /** The organization every request carries (its token's); a test that needs another sets its own. */
    private static final UUID TENANT = uid(1);

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        TenantContext.set(TENANT);
        contract = Contracts.builder().id(uid(1)).build();
        user = Users.builder().id(uid(2)).build();
    }

    @AfterEach
    void leaveTenant() {
        TenantContext.clear();
    }

    @Mock
    private ContractsRepository contractsRepository;

    @Mock
    private UsersRepository usersRepository;

    @InjectMocks
    private ContractHistoryMapper mapper;

    private Contracts contract;
    private Users user;

    @Test
    void shouldConvertToDTO() {
        ContractHistory history = ContractHistory.builder()
                .id(uid(100))
                .contract(contract)
                .modifiedBy(user)
                .modificationDate(LocalDateTime.of(2024, Month.MAY, 5, 12, 0))
                .previousStatus(ContractStatus.ACTIVE)
                .newStatus(ContractStatus.CANCELLED)
                .build();

        ContractHistoryDTO dto = mapper.toDTO(history);

        assertEquals(uid(100), dto.id());
        assertEquals(uid(1), dto.contractId());
        assertEquals(uid(2), dto.modifiedById());
        assertEquals(LocalDateTime.of(2024, Month.MAY, 5, 12, 0), dto.modificationDate());
        assertEquals(ContractStatus.ACTIVE, dto.previousStatus());
        assertEquals(ContractStatus.CANCELLED, dto.newStatus());
    }

    @Test
    void shouldConvertToEntity() {
        ContractHistoryDTO dto = new ContractHistoryDTO(
                uid(100), uid(1), uid(2),
                LocalDateTime.of(2024, Month.MAY, 5, 12, 0),
                ContractStatus.EXPIRED,
                ContractStatus.ACTIVE
        );

        when(contractsRepository.findByIdAndOrganization_Id(uid(1), TENANT)).thenReturn(Optional.of(contract));
        when(usersRepository.findByIdAndOrganizationId(uid(2), TENANT)).thenReturn(Optional.of(user));

        ContractHistory entity = mapper.toEntity(dto);

        assertEquals(uid(100), entity.getId());
        assertEquals(contract, entity.getContract());
        assertEquals(user, entity.getModifiedBy());
        assertEquals(LocalDateTime.of(2024, Month.MAY, 5, 12, 0), entity.getModificationDate());
        assertEquals(ContractStatus.EXPIRED, entity.getPreviousStatus());
        assertEquals(ContractStatus.ACTIVE, entity.getNewStatus());
    }

    @Test
    void shouldUseNowIfModificationDateIsNull() {
        ContractHistoryDTO dto = new ContractHistoryDTO(
                uid(100), uid(1), uid(2),
                null,
                ContractStatus.CANCELLED,
                ContractStatus.ACTIVE
        );

        when(contractsRepository.findByIdAndOrganization_Id(uid(1), TENANT)).thenReturn(Optional.of(contract));
        when(usersRepository.findByIdAndOrganizationId(uid(2), TENANT)).thenReturn(Optional.of(user));

        ContractHistory entity = mapper.toEntity(dto);

        assertNotNull(entity.getModificationDate());
    }

    @Test
    void shouldConvertToDTOWithNullModifiedByWhenUserWasDeleted() {
        ContractHistory history = ContractHistory.builder()
                .id(uid(100))
                .contract(contract)
                .modifiedBy(null)
                .modificationDate(LocalDateTime.of(2024, Month.MAY, 5, 12, 0))
                .previousStatus(ContractStatus.ACTIVE)
                .newStatus(ContractStatus.CANCELLED)
                .build();

        ContractHistoryDTO dto = mapper.toDTO(history);

        assertEquals(null, dto.modifiedById());
        assertEquals(null, dto.modifiedByName(), "no author: the UI shows it as the system");
    }

    private ContractHistoryDTO dtoModifiedBy(Users author) {
        return mapper.toDTO(ContractHistory.builder()
                .id(uid(100)).contract(contract).modifiedBy(author)
                .modificationDate(LocalDateTime.of(2024, Month.MAY, 5, 12, 0))
                .previousStatus(ContractStatus.ACTIVE).newStatus(ContractStatus.EXPIRED)
                .build());
    }

    @Test
    void shouldNameTheAuthorByTheirManagerProfile() {
        Users author = Users.builder().id(uid(2)).username("m.bellini")
                .manager(Managers.builder().firstName("Marco").lastName("Bellini").build()).build();

        assertEquals("Marco Bellini", dtoModifiedBy(author).modifiedByName());
    }

    @Test
    void shouldFallBackToTheUsernameWithoutAManagerName() {
        Users noProfile = Users.builder().id(uid(2)).username("admin").build();
        Users blankProfile = Users.builder().id(uid(3)).username("ops")
                .manager(Managers.builder().firstName(" ").build()).build();

        assertEquals("admin", dtoModifiedBy(noProfile).modifiedByName());
        assertEquals("ops", dtoModifiedBy(blankProfile).modifiedByName());
    }

    @Test
    void shouldConvertToEntityWithNullModifiedByWhenIdIsNull() {
        ContractHistoryDTO dto = new ContractHistoryDTO(
                uid(100), uid(1), null,
                LocalDateTime.of(2024, Month.MAY, 5, 12, 0),
                ContractStatus.EXPIRED,
                ContractStatus.ACTIVE
        );

        when(contractsRepository.findByIdAndOrganization_Id(uid(1), TENANT)).thenReturn(Optional.of(contract));

        ContractHistory entity = mapper.toEntity(dto);

        assertEquals(null, entity.getModifiedBy());
    }

    @Test
    void shouldThrowIfContractNotFound() {
        ContractHistoryDTO dto = new ContractHistoryDTO(
                uid(100), uid(99), uid(2),
                LocalDateTime.of(2027, Month.JUNE, 15, 12, 0),
                ContractStatus.CANCELLED,
                ContractStatus.ACTIVE
        );

        when(contractsRepository.findByIdAndOrganization_Id(uid(99), TENANT)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> mapper.toEntity(dto));
        assertEquals("Contract not found", ex.getMessage());
    }

    @Test
    void shouldThrowIfUserNotFound() {
        ContractHistoryDTO dto = new ContractHistoryDTO(
                uid(100), uid(1), uid(99),
                LocalDateTime.of(2027, Month.JUNE, 15, 12, 0),
                ContractStatus.CANCELLED,
                ContractStatus.ACTIVE
        );

        when(contractsRepository.findByIdAndOrganization_Id(uid(1), TENANT)).thenReturn(Optional.of(contract));
        when(usersRepository.findByIdAndOrganizationId(uid(99), TENANT)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> mapper.toEntity(dto));
        assertEquals("User not found", ex.getMessage());
    }

    @Test
    void shouldNameTheAuthorByLastNameAlone() {
        Users author = Users.builder().id(uid(2)).username("bellini")
                .manager(Managers.builder().lastName("Bellini").build()).build();

        assertEquals("Bellini", dtoModifiedBy(author).modifiedByName());
    }

    @Test
    void shouldLookUpContractAndAuthorInTheCurrentOrganizationOnly() {
        com.donatodev.bcm_backend.config.TenantContext.set(uid(7));
        try {
            when(contractsRepository.findByIdAndOrganization_Id(uid(1), uid(7))).thenReturn(Optional.of(contract));
            when(usersRepository.findByIdAndOrganizationId(uid(2), uid(7))).thenReturn(Optional.of(user));

            ContractHistory entity = mapper.toEntity(new ContractHistoryDTO(
                    uid(100), uid(1), uid(2), LocalDateTime.of(2024, Month.MAY, 5, 12, 0), ContractStatus.EXPIRED, ContractStatus.ACTIVE));

            assertEquals(contract, entity.getContract());
            assertEquals(user, entity.getModifiedBy());
        } finally {
            com.donatodev.bcm_backend.config.TenantContext.clear();
        }
    }
}
