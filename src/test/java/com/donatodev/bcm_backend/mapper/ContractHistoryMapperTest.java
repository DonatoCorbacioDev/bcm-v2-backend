package com.donatodev.bcm_backend.mapper;

import static com.donatodev.bcm_backend.support.TestIds.uid;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDateTime;
import java.time.Month;

import org.junit.jupiter.api.Test;

import com.donatodev.bcm_backend.dto.ContractHistoryDTO;
import com.donatodev.bcm_backend.entity.ContractHistory;
import com.donatodev.bcm_backend.entity.ContractStatus;
import com.donatodev.bcm_backend.entity.Contracts;
import com.donatodev.bcm_backend.entity.Managers;
import com.donatodev.bcm_backend.entity.Users;

/**
 * The history only goes out through the API (the server writes it on every
 * status change), so the mapper only converts entries to DTOs.
 */
class ContractHistoryMapperTest {

    private final ContractHistoryMapper mapper = new ContractHistoryMapper();
    private final Contracts contract = Contracts.builder().id(uid(1)).build();
    private final Users user = Users.builder().id(uid(2)).build();

    private ContractHistoryDTO dtoModifiedBy(Users author) {
        return mapper.toDTO(ContractHistory.builder()
                .id(uid(100)).contract(contract).modifiedBy(author)
                .modificationDate(LocalDateTime.of(2024, Month.MAY, 5, 12, 0))
                .previousStatus(ContractStatus.ACTIVE).newStatus(ContractStatus.EXPIRED)
                .build());
    }

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
    void shouldConvertToDTOWithNullModifiedByWhenUserWasDeleted() {
        ContractHistoryDTO dto = dtoModifiedBy(null);

        assertNull(dto.modifiedById());
        assertNull(dto.modifiedByName(), "no author: the UI shows it as the system");
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
    void shouldNameTheAuthorByLastNameAlone() {
        Users author = Users.builder().id(uid(2)).username("bellini")
                .manager(Managers.builder().lastName("Bellini").build()).build();

        assertEquals("Bellini", dtoModifiedBy(author).modifiedByName());
    }
}
