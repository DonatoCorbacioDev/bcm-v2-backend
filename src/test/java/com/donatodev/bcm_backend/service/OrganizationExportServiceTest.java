package com.donatodev.bcm_backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.UncheckedIOException;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import com.donatodev.bcm_backend.entity.Organization;
import com.donatodev.bcm_backend.exception.OrganizationNotFoundException;
import com.donatodev.bcm_backend.repository.OrganizationRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

/**
 * The failure paths of {@link OrganizationExportService}; the happy paths
 * run end to end in {@code OrganizationExportControllerTest}.
 */
@ExtendWith(MockitoExtension.class)
class OrganizationExportServiceTest {

    @Mock private OrganizationRepository organizationRepository;
    @Mock private OrganizationExportSections sections;
    @Mock private LocalStorageService storageService;
    @Mock private AuditLogService auditLogService;
    @Mock private ObjectMapper objectMapper;
    @Mock private ObjectWriter jsonWriter;
    @Mock private PlatformTransactionManager transactionManager;

    private OrganizationExportService service;

    @BeforeEach
    void setup() {
        when(objectMapper.writerWithDefaultPrettyPrinter()).thenReturn(jsonWriter);
        service = new OrganizationExportService(organizationRepository, sections, storageService,
                auditLogService, objectMapper, transactionManager);
    }

    @Test
    @DisplayName("An organization deleted in the meantime fails before anything is written")
    void failsWhenOrganizationIsGone() {
        when(organizationRepository.findById(7L)).thenReturn(Optional.empty());
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        assertThatThrownBy(() -> service.writeExport(7L, out))
                .isInstanceOf(OrganizationNotFoundException.class);
        // Nothing reached the client: not even a ZIP header.
        assertThat(out.size()).isZero();
    }

    @Test
    @DisplayName("A record that can't be serialized fails the export with the file named")
    void reportsSerializationFailure() throws Exception {
        when(organizationRepository.findById(7L)).thenReturn(Optional.of(
                Organization.builder().id(7L).name("Acme").slug("acme").build()));
        when(jsonWriter.writeValueAsBytes(any())).thenThrow(mock(JsonProcessingException.class));

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        assertThatThrownBy(() -> service.writeExport(7L, out))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining("data/organization.json");
    }
}
