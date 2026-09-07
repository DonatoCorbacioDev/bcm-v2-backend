package com.donatodev.bcm_backend.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.donatodev.bcm_backend.dto.CounterpartyDTO;
import com.donatodev.bcm_backend.entity.Counterparty;
import com.donatodev.bcm_backend.entity.CounterpartyType;

@SpringBootTest
@ActiveProfiles("test")
class CounterpartyMapperTest {

    @Autowired
    private CounterpartyMapper counterpartyMapper;

    @Nested
    class ToDTOTests {

        @Test
        void shouldMapEntityToDTO() {
            Counterparty counterparty = Counterparty.builder()
                    .id(1L)
                    .name("Alfa Srl")
                    .type(CounterpartyType.CUSTOMER)
                    .vatNumber("IT123")
                    .taxCode("RSSMRA80A01H501U")
                    .address("Via Roma 1")
                    .contactName("Mario Rossi")
                    .contactEmail("mario@alfa.it")
                    .contactPhone("+39123456")
                    .notes("VIP client")
                    .build();

            CounterpartyDTO dto = counterpartyMapper.toDTO(counterparty);

            assertEquals(1L, dto.id());
            assertEquals("Alfa Srl", dto.name());
            assertEquals(CounterpartyType.CUSTOMER, dto.type());
            assertEquals("IT123", dto.vatNumber());
            assertEquals("RSSMRA80A01H501U", dto.taxCode());
            assertEquals("Via Roma 1", dto.address());
            assertEquals("Mario Rossi", dto.contactName());
            assertEquals("mario@alfa.it", dto.contactEmail());
            assertEquals("+39123456", dto.contactPhone());
            assertEquals("VIP client", dto.notes());
        }

        @Test
        void shouldMapEntityToDTOWithOnlyRequiredFields() {
            Counterparty counterparty = Counterparty.builder()
                    .id(2L)
                    .name("Beta Srl")
                    .type(CounterpartyType.SUPPLIER)
                    .build();

            CounterpartyDTO dto = counterpartyMapper.toDTO(counterparty);

            assertEquals(2L, dto.id());
            assertEquals("Beta Srl", dto.name());
            assertEquals(CounterpartyType.SUPPLIER, dto.type());
            assertNull(dto.vatNumber());
            assertNull(dto.contactEmail());
        }
    }

    @Nested
    class ToEntityTests {

        @Test
        void shouldMapDTOToEntity() {
            CounterpartyDTO dto = new CounterpartyDTO(10L, "Gamma Srl", CounterpartyType.BOTH,
                    "IT456", "TAXCODE1", "Via Napoli 2", "Luigi Verdi", "luigi@gamma.it", "+39987654", "Notes here");

            Counterparty counterparty = counterpartyMapper.toEntity(dto);

            assertEquals(10L, counterparty.getId());
            assertEquals("Gamma Srl", counterparty.getName());
            assertEquals(CounterpartyType.BOTH, counterparty.getType());
            assertEquals("IT456", counterparty.getVatNumber());
            assertEquals("TAXCODE1", counterparty.getTaxCode());
            assertEquals("Via Napoli 2", counterparty.getAddress());
            assertEquals("Luigi Verdi", counterparty.getContactName());
            assertEquals("luigi@gamma.it", counterparty.getContactEmail());
            assertEquals("+39987654", counterparty.getContactPhone());
            assertEquals("Notes here", counterparty.getNotes());
        }

        @Test
        void shouldMapDTOToEntityWithNullId() {
            CounterpartyDTO dto = new CounterpartyDTO(null, "Delta Srl", CounterpartyType.CUSTOMER,
                    null, null, null, null, null, null, null);

            Counterparty counterparty = counterpartyMapper.toEntity(dto);

            assertNull(counterparty.getId());
            assertEquals("Delta Srl", counterparty.getName());
            assertEquals(CounterpartyType.CUSTOMER, counterparty.getType());
        }
    }
}
