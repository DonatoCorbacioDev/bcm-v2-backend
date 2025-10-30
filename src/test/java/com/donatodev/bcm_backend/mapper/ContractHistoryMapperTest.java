package com.donatodev.bcm_backend.mapper;

import java.time.LocalDateTime;
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
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.repository.ContractsRepository;
import com.donatodev.bcm_backend.repository.UsersRepository;

@ExtendWith(MockitoExtension.class)
class ContractHistoryMapperTest {

    @Mock
    private ContractsRepository contractsRepository;

    @Mock
    private UsersRepository usersRepository;

    @InjectMocks
    private ContractHistoryMapper mapper;

    private Contracts contract;
    private Users user;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        contract = Contracts.builder().id(1L).build();
        user = Users.builder().id(2L).build();
    }

    @Test
    void shouldConvertToDTO() {
        ContractHistory history = ContractHistory.builder()
                .id(100L)
                .contract(contract)
                .modifiedBy(user)
                .modificationDate(LocalDateTime.of(2024, 5, 5, 12, 0))
                .previousStatus(ContractStatus.ACTIVE)
                .newStatus(ContractStatus.CANCELLED)
                .build();

        ContractHistoryDTO dto = mapper.toDTO(history);

        assertEquals(100L, dto.id());
        assertEquals(1L, dto.contractId());
        assertEquals(2L, dto.modifiedById());
        assertEquals(LocalDateTime.of(2024, 5, 5, 12, 0), dto.modificationDate());
        assertEquals(ContractStatus.ACTIVE, dto.previousStatus());
        assertEquals(ContractStatus.CANCELLED, dto.newStatus());
    }

    @Test
    void shouldConvertToEntity() {
        ContractHistoryDTO dto = new ContractHistoryDTO(
                100L, 1L, 2L,
                LocalDateTime.of(2024, 5, 5, 12, 0),
                ContractStatus.EXPIRED,
                ContractStatus.ACTIVE
        );

        when(contractsRepository.findById(1L)).thenReturn(Optional.of(contract));
        when(usersRepository.findById(2L)).thenReturn(Optional.of(user));

        ContractHistory entity = mapper.toEntity(dto);

        assertEquals(100L, entity.getId());
        assertEquals(contract, entity.getContract());
        assertEquals(user, entity.getModifiedBy());
        assertEquals(LocalDateTime.of(2024, 5, 5, 12, 0), entity.getModificationDate());
        assertEquals(ContractStatus.EXPIRED, entity.getPreviousStatus());
        assertEquals(ContractStatus.ACTIVE, entity.getNewStatus());
    }

    @Test
    void shouldUseNowIfModificationDateIsNull() {
        ContractHistoryDTO dto = new ContractHistoryDTO(
                100L, 1L, 2L,
                null,
                ContractStatus.CANCELLED,
                ContractStatus.ACTIVE
        );

        when(contractsRepository.findById(1L)).thenReturn(Optional.of(contract));
        when(usersRepository.findById(2L)).thenReturn(Optional.of(user));

        ContractHistory entity = mapper.toEntity(dto);

        assertNotNull(entity.getModificationDate());
    }

    @Test
    void shouldThrowIfContractNotFound() {
        ContractHistoryDTO dto = new ContractHistoryDTO(
                100L, 99L, 2L,
                LocalDateTime.now(),
                ContractStatus.CANCELLED,
                ContractStatus.ACTIVE
        );

        when(contractsRepository.findById(99L)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> mapper.toEntity(dto));
        assertEquals("Contract not found", ex.getMessage());
    }

    @Test
    void shouldThrowIfUserNotFound() {
        ContractHistoryDTO dto = new ContractHistoryDTO(
                100L, 1L, 99L,
                LocalDateTime.now(),
                ContractStatus.CANCELLED,
                ContractStatus.ACTIVE
        );

        when(contractsRepository.findById(1L)).thenReturn(Optional.of(contract));
        when(usersRepository.findById(99L)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> mapper.toEntity(dto));
        assertEquals("User not found", ex.getMessage());
    }
}
