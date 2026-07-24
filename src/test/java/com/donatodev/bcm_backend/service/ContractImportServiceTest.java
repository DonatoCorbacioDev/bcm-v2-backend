package com.donatodev.bcm_backend.service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import com.donatodev.bcm_backend.config.TenantContext;
import com.donatodev.bcm_backend.dto.ContractDTO;
import com.donatodev.bcm_backend.dto.ContractImportResultDTO;
import com.donatodev.bcm_backend.entity.BusinessAreas;
import com.donatodev.bcm_backend.entity.Managers;
import com.donatodev.bcm_backend.repository.BusinessAreasRepository;
import com.donatodev.bcm_backend.repository.ContractsRepository;
import com.donatodev.bcm_backend.repository.ManagersRepository;

/**
 * Unit tests for {@link ContractImportService}. Covers row-by-row validation
 * and resolution logic against in-memory .xlsx files built with Apache POI.
 */
@ExtendWith(MockitoExtension.class)
class ContractImportServiceTest {

    @Mock
    private ContractsRepository contractsRepository;

    @Mock
    private BusinessAreasRepository businessAreasRepository;

    @Mock
    private ManagersRepository managersRepository;

    @Mock
    private ContractService contractService;

    private ContractImportService importService;

    private final BusinessAreas area = BusinessAreas.builder().id(1L).name("IT").build();
    private final Managers manager = Managers.builder()
            .id(2L).firstName("Mario").lastName("Rossi").email("mario.rossi@example.com").build();

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private void setupImportService() {
        importService = new ContractImportService(
                contractsRepository, businessAreasRepository, managersRepository, contractService);
    }

    private static byte[] buildWorkbook(String[]... rows) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Contratti");
            for (int r = 0; r < rows.length; r++) {
                Row row = sheet.createRow(r);
                for (int c = 0; c < rows[r].length; c++) {
                    if (rows[r][c] != null) {
                        row.createCell(c).setCellValue(rows[r][c]);
                    }
                }
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static final String[] HEADER = {
        "Contract Number", "Customer", "Project", "Status",
        "Start Date", "End Date", "Manager", "Business Area"
    };

    private MockMultipartFile toFile(byte[] bytes) {
        return new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);
    }

    @Nested
    @DisplayName("Row validation")
    class RowValidation {

        @Test
        @DisplayName("imports valid rows and calls createContract for each")
        void importsValidRows() throws Exception {
            setupImportService();
            when(businessAreasRepository.findAll()).thenReturn(List.of(area));
            when(managersRepository.findAll()).thenReturn(List.of(manager));
            when(contractsRepository.existsByContractNumber(any())).thenReturn(false);

            byte[] bytes = buildWorkbook(HEADER,
                    new String[]{"C-1", "Cliente A", "Progetto A", "ACTIVE", "01/01/2024", "31/12/2024", "Mario Rossi", "IT"},
                    new String[]{"C-2", "Cliente B", "Progetto B", "ACTIVE", "2024-02-01", "2024-12-31", "mario.rossi@example.com", "it"});

            ContractImportResultDTO result = importService.importFromExcel(toFile(bytes));

            assertEquals(2, result.totalRows());
            assertEquals(2, result.importedCount());
            assertEquals(0, result.errorCount());
            verify(contractService, times(2)).createContract(any());
        }

        @Test
        @DisplayName("defaults status to ACTIVE when the cell is blank")
        void defaultsStatusToActive() throws Exception {
            setupImportService();
            when(businessAreasRepository.findAll()).thenReturn(List.of(area));
            when(managersRepository.findAll()).thenReturn(List.of());
            when(contractsRepository.existsByContractNumber(any())).thenReturn(false);

            byte[] bytes = buildWorkbook(HEADER,
                    new String[]{"C-1", "Cliente A", null, null, "01/01/2024", "31/12/2024", null, "IT"});

            importService.importFromExcel(toFile(bytes));

            org.mockito.ArgumentCaptor<ContractDTO> captor = org.mockito.ArgumentCaptor.forClass(ContractDTO.class);
            verify(contractService).createContract(captor.capture());
            assertEquals(com.donatodev.bcm_backend.entity.ContractStatus.ACTIVE, captor.getValue().status());
            assertEquals(LocalDate.of(2024, Month.JANUARY, 1), captor.getValue().startDate());
        }

        @Test
        @DisplayName("skips fully blank rows without counting or erroring")
        void skipsBlankRows() throws Exception {
            setupImportService();
            when(businessAreasRepository.findAll()).thenReturn(List.of(area));
            when(managersRepository.findAll()).thenReturn(List.of());
            when(contractsRepository.existsByContractNumber(any())).thenReturn(false);

            byte[] bytes = buildWorkbook(HEADER,
                    new String[]{"C-1", "Cliente A", null, null, "01/01/2024", "31/12/2024", null, "IT"},
                    new String[]{null, null, null, null, null, null, null, null});

            ContractImportResultDTO result = importService.importFromExcel(toFile(bytes));

            assertEquals(1, result.totalRows());
            assertEquals(1, result.importedCount());
        }

        @Test
        @DisplayName("reports missing contract number as a row error")
        void missingContractNumber() throws Exception {
            setupImportService();
            when(businessAreasRepository.findAll()).thenReturn(List.of(area));
            when(managersRepository.findAll()).thenReturn(List.of());

            byte[] bytes = buildWorkbook(HEADER,
                    new String[]{null, "Cliente A", null, null, "01/01/2024", "31/12/2024", null, "IT"});

            ContractImportResultDTO result = importService.importFromExcel(toFile(bytes));

            assertEquals(1, result.errorCount());
            assertTrue(result.errors().get(0).message().contains("Numero contratto"));
            verify(contractService, never()).createContract(any());
        }

        @Test
        @DisplayName("reports unknown business area")
        void unknownBusinessArea() throws Exception {
            setupImportService();
            when(businessAreasRepository.findAll()).thenReturn(List.of(area));
            when(managersRepository.findAll()).thenReturn(List.of());

            byte[] bytes = buildWorkbook(HEADER,
                    new String[]{"C-1", "Cliente A", null, null, "01/01/2024", "31/12/2024", null, "Finance"});

            ContractImportResultDTO result = importService.importFromExcel(toFile(bytes));

            assertEquals(1, result.errorCount());
            assertTrue(result.errors().get(0).message().contains("Area aziendale"));
        }

        @Test
        @DisplayName("reports unknown manager")
        void unknownManager() throws Exception {
            setupImportService();
            when(businessAreasRepository.findAll()).thenReturn(List.of(area));
            when(managersRepository.findAll()).thenReturn(List.of(manager));

            byte[] bytes = buildWorkbook(HEADER,
                    new String[]{"C-1", "Cliente A", null, null, "01/01/2024", "31/12/2024", "Luigi Verdi", "IT"});

            ContractImportResultDTO result = importService.importFromExcel(toFile(bytes));

            assertEquals(1, result.errorCount());
            assertTrue(result.errors().get(0).message().contains("Manager"));
        }

        @Test
        @DisplayName("reports invalid status value")
        void invalidStatus() throws Exception {
            setupImportService();
            when(businessAreasRepository.findAll()).thenReturn(List.of(area));
            when(managersRepository.findAll()).thenReturn(List.of());

            byte[] bytes = buildWorkbook(HEADER,
                    new String[]{"C-1", "Cliente A", null, "PENDING", "01/01/2024", "31/12/2024", null, "IT"});

            ContractImportResultDTO result = importService.importFromExcel(toFile(bytes));

            assertEquals(1, result.errorCount());
            assertTrue(result.errors().get(0).message().contains("Status"));
        }

        @Test
        @DisplayName("reports unparseable date")
        void invalidDate() throws Exception {
            setupImportService();
            when(businessAreasRepository.findAll()).thenReturn(List.of(area));
            when(managersRepository.findAll()).thenReturn(List.of());

            byte[] bytes = buildWorkbook(HEADER,
                    new String[]{"C-1", "Cliente A", null, null, "not-a-date", "31/12/2024", null, "IT"});

            ContractImportResultDTO result = importService.importFromExcel(toFile(bytes));

            assertEquals(1, result.errorCount());
            assertTrue(result.errors().get(0).message().contains("Data inizio"));
        }

        @Test
        @DisplayName("reports missing end date")
        void missingEndDate() throws Exception {
            setupImportService();
            when(businessAreasRepository.findAll()).thenReturn(List.of(area));
            when(managersRepository.findAll()).thenReturn(List.of());

            byte[] bytes = buildWorkbook(HEADER,
                    new String[]{"C-1", "Cliente A", null, null, "01/01/2024", null, null, "IT"});

            ContractImportResultDTO result = importService.importFromExcel(toFile(bytes));

            assertEquals(1, result.errorCount());
            assertTrue(result.errors().get(0).message().contains("Data fine"));
        }

        @Test
        @DisplayName("reports duplicate contract number within the same file")
        void duplicateWithinFile() throws Exception {
            setupImportService();
            when(businessAreasRepository.findAll()).thenReturn(List.of(area));
            when(managersRepository.findAll()).thenReturn(List.of());
            when(contractsRepository.existsByContractNumber(any())).thenReturn(false);

            byte[] bytes = buildWorkbook(HEADER,
                    new String[]{"C-1", "Cliente A", null, null, "01/01/2024", "31/12/2024", null, "IT"},
                    new String[]{"C-1", "Cliente B", null, null, "01/01/2024", "31/12/2024", null, "IT"});

            ContractImportResultDTO result = importService.importFromExcel(toFile(bytes));

            assertEquals(2, result.totalRows());
            assertEquals(1, result.importedCount());
            assertEquals(1, result.errorCount());
            assertTrue(result.errors().get(0).message().contains("gia' esistente"));
        }

        @Test
        @DisplayName("reports contract number that already exists in the database")
        void existingInDatabase() throws Exception {
            setupImportService();
            when(businessAreasRepository.findAll()).thenReturn(List.of(area));
            when(managersRepository.findAll()).thenReturn(List.of());
            when(contractsRepository.existsByContractNumber("C-1")).thenReturn(true);

            byte[] bytes = buildWorkbook(HEADER,
                    new String[]{"C-1", "Cliente A", null, null, "01/01/2024", "31/12/2024", null, "IT"});

            ContractImportResultDTO result = importService.importFromExcel(toFile(bytes));

            assertEquals(1, result.errorCount());
            verify(contractService, never()).createContract(any());
        }

        @Test
        @DisplayName("reports missing business area as a row error")
        void missingBusinessArea() throws Exception {
            setupImportService();
            when(businessAreasRepository.findAll()).thenReturn(List.of(area));
            when(managersRepository.findAll()).thenReturn(List.of());

            byte[] bytes = buildWorkbook(HEADER,
                    new String[]{"C-1", "Cliente A", null, null, "01/01/2024", "31/12/2024", null, null});

            ContractImportResultDTO result = importService.importFromExcel(toFile(bytes));

            assertEquals(1, result.errorCount());
            assertTrue(result.errors().get(0).message().contains("Area aziendale mancante"));
        }

        @Test
        @DisplayName("resolves manager by name even when an unrelated manager in the roster has no email")
        void resolvesManagerPastNullEmailEntry() throws Exception {
            setupImportService();
            Managers noEmailManager = Managers.builder().id(3L).firstName("Anna").lastName("Bianchi").email(null).build();
            when(businessAreasRepository.findAll()).thenReturn(List.of(area));
            when(managersRepository.findAll()).thenReturn(List.of(noEmailManager, manager));
            when(contractsRepository.existsByContractNumber(any())).thenReturn(false);

            byte[] bytes = buildWorkbook(HEADER,
                    new String[]{"C-1", "Cliente A", null, null, "01/01/2024", "31/12/2024", "Mario Rossi", "IT"});

            ContractImportResultDTO result = importService.importFromExcel(toFile(bytes));

            assertEquals(1, result.importedCount());
        }

        @Test
        @DisplayName("skips a row entirely absent from the sheet (gap between rows)")
        void skipsMissingRow() throws Exception {
            setupImportService();
            when(businessAreasRepository.findAll()).thenReturn(List.of(area));
            when(managersRepository.findAll()).thenReturn(List.of());
            when(contractsRepository.existsByContractNumber(any())).thenReturn(false);

            byte[] bytes;
            try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                Sheet sheet = workbook.createSheet("Contratti");
                Row header = sheet.createRow(0);
                for (int c = 0; c < HEADER.length; c++) {
                    header.createCell(c).setCellValue(HEADER[c]);
                }
                // Row 1 is intentionally never created, leaving a real gap
                // (sheet.getRow(1) returns null) rather than a blank row.
                Row dataRow = sheet.createRow(2);
                String[] values = {"C-1", "Cliente A", null, null, "01/01/2024", "31/12/2024", null, "IT"};
                for (int c = 0; c < values.length; c++) {
                    if (values[c] != null) {
                        dataRow.createCell(c).setCellValue(values[c]);
                    }
                }
                workbook.write(out);
                bytes = out.toByteArray();
            }

            ContractImportResultDTO result = importService.importFromExcel(toFile(bytes));

            assertEquals(1, result.totalRows());
            assertEquals(1, result.importedCount());
        }

        @Test
        @DisplayName("parses a real Excel date-formatted cell for the start date")
        void parsesRealExcelDateCell() throws Exception {
            setupImportService();
            when(businessAreasRepository.findAll()).thenReturn(List.of(area));
            when(managersRepository.findAll()).thenReturn(List.of());
            when(contractsRepository.existsByContractNumber(any())).thenReturn(false);

            byte[] bytes;
            try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                Sheet sheet = workbook.createSheet("Contratti");
                Row header = sheet.createRow(0);
                for (int c = 0; c < HEADER.length; c++) {
                    header.createCell(c).setCellValue(HEADER[c]);
                }
                Row dataRow = sheet.createRow(1);
                dataRow.createCell(0).setCellValue("C-1");
                dataRow.createCell(1).setCellValue("Cliente A");
                org.apache.poi.ss.usermodel.CellStyle dateStyle = workbook.createCellStyle();
                dateStyle.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("m/d/yy"));
                org.apache.poi.ss.usermodel.Cell startCell = dataRow.createCell(4);
                startCell.setCellValue(LocalDate.of(2024, Month.JANUARY, 1));
                startCell.setCellStyle(dateStyle);
                dataRow.createCell(5).setCellValue("31/12/2024");
                dataRow.createCell(7).setCellValue("IT");
                workbook.write(out);
                bytes = out.toByteArray();
            }

            importService.importFromExcel(toFile(bytes));

            org.mockito.ArgumentCaptor<ContractDTO> captor = org.mockito.ArgumentCaptor.forClass(ContractDTO.class);
            verify(contractService).createContract(captor.capture());
            assertEquals(LocalDate.of(2024, Month.JANUARY, 1), captor.getValue().startDate());
        }

        @Test
        @DisplayName("stringifies non-string cell types (numeric, boolean, formula) for optional text fields")
        void stringifiesMixedCellTypes() throws Exception {
            setupImportService();
            when(businessAreasRepository.findAll()).thenReturn(List.of(area));
            when(managersRepository.findAll()).thenReturn(List.of());
            when(contractsRepository.existsByContractNumber(any())).thenReturn(false);

            byte[] bytes;
            try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                Sheet sheet = workbook.createSheet("Contratti");
                Row header = sheet.createRow(0);
                for (int c = 0; c < HEADER.length; c++) {
                    header.createCell(c).setCellValue(HEADER[c]);
                }
                Row dataRow = sheet.createRow(1);
                dataRow.createCell(0).setCellValue("C-1");
                dataRow.createCell(1).setCellValue("Cliente A");
                dataRow.createCell(2).setCellValue(12345.0);
                dataRow.createCell(4).setCellValue("01/01/2024");
                dataRow.createCell(5).setCellValue("31/12/2024");
                dataRow.createCell(7).setCellValue("IT");
                workbook.write(out);
                bytes = out.toByteArray();
            }

            importService.importFromExcel(toFile(bytes));

            org.mockito.ArgumentCaptor<ContractDTO> captor = org.mockito.ArgumentCaptor.forClass(ContractDTO.class);
            verify(contractService).createContract(captor.capture());
            assertEquals("12345.0", captor.getValue().projectName());
        }

        @Test
        @DisplayName("stringifies a formula cell for an optional text field")
        void stringifiesFormulaCell() throws Exception {
            setupImportService();
            when(businessAreasRepository.findAll()).thenReturn(List.of(area));
            when(managersRepository.findAll()).thenReturn(List.of());
            when(contractsRepository.existsByContractNumber(any())).thenReturn(false);

            byte[] bytes;
            try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                Sheet sheet = workbook.createSheet("Contratti");
                Row header = sheet.createRow(0);
                for (int c = 0; c < HEADER.length; c++) {
                    header.createCell(c).setCellValue(HEADER[c]);
                }
                Row dataRow = sheet.createRow(1);
                dataRow.createCell(0).setCellValue("C-1");
                dataRow.createCell(1).setCellValue("Cliente A");
                dataRow.createCell(2).setCellFormula("1+1");
                dataRow.createCell(4).setCellValue("01/01/2024");
                dataRow.createCell(5).setCellValue("31/12/2024");
                dataRow.createCell(7).setCellValue("IT");
                workbook.write(out);
                bytes = out.toByteArray();
            }

            importService.importFromExcel(toFile(bytes));

            org.mockito.ArgumentCaptor<ContractDTO> captor = org.mockito.ArgumentCaptor.forClass(ContractDTO.class);
            verify(contractService).createContract(captor.capture());
            assertEquals("1+1", captor.getValue().projectName());
        }

        @Test
        @DisplayName("stringifies a boolean cell for an optional text field")
        void stringifiesBooleanCell() throws Exception {
            setupImportService();
            when(businessAreasRepository.findAll()).thenReturn(List.of(area));
            when(managersRepository.findAll()).thenReturn(List.of());
            when(contractsRepository.existsByContractNumber(any())).thenReturn(false);

            byte[] bytes;
            try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                Sheet sheet = workbook.createSheet("Contratti");
                Row header = sheet.createRow(0);
                for (int c = 0; c < HEADER.length; c++) {
                    header.createCell(c).setCellValue(HEADER[c]);
                }
                Row dataRow = sheet.createRow(1);
                dataRow.createCell(0).setCellValue("C-1");
                dataRow.createCell(1).setCellValue("Cliente A");
                dataRow.createCell(2).setCellValue(true);
                dataRow.createCell(4).setCellValue("01/01/2024");
                dataRow.createCell(5).setCellValue("31/12/2024");
                dataRow.createCell(7).setCellValue("IT");
                workbook.write(out);
                bytes = out.toByteArray();
            }

            importService.importFromExcel(toFile(bytes));

            org.mockito.ArgumentCaptor<ContractDTO> captor = org.mockito.ArgumentCaptor.forClass(ContractDTO.class);
            verify(contractService).createContract(captor.capture());
            assertEquals("true", captor.getValue().projectName());
        }

        @Test
        @DisplayName("stringifies a date-formatted numeric cell used in a non-date optional field")
        void stringifiesDateFormattedCellInNonDateField() throws Exception {
            setupImportService();
            when(businessAreasRepository.findAll()).thenReturn(List.of(area));
            when(managersRepository.findAll()).thenReturn(List.of());
            when(contractsRepository.existsByContractNumber(any())).thenReturn(false);

            byte[] bytes;
            try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                Sheet sheet = workbook.createSheet("Contratti");
                Row header = sheet.createRow(0);
                for (int c = 0; c < HEADER.length; c++) {
                    header.createCell(c).setCellValue(HEADER[c]);
                }
                Row dataRow = sheet.createRow(1);
                dataRow.createCell(0).setCellValue("C-1");
                dataRow.createCell(1).setCellValue("Cliente A");
                org.apache.poi.ss.usermodel.CellStyle dateStyle = workbook.createCellStyle();
                dateStyle.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("m/d/yy"));
                org.apache.poi.ss.usermodel.Cell projectCell = dataRow.createCell(2);
                projectCell.setCellValue(LocalDate.of(2024, Month.MARCH, 1));
                projectCell.setCellStyle(dateStyle);
                dataRow.createCell(4).setCellValue("01/01/2024");
                dataRow.createCell(5).setCellValue("31/12/2024");
                dataRow.createCell(7).setCellValue("IT");
                workbook.write(out);
                bytes = out.toByteArray();
            }

            importService.importFromExcel(toFile(bytes));

            org.mockito.ArgumentCaptor<ContractDTO> captor = org.mockito.ArgumentCaptor.forClass(ContractDTO.class);
            verify(contractService).createContract(captor.capture());
            assertEquals("2024-03-01", captor.getValue().projectName());
        }

        @Test
        @DisplayName("stringifies an error cell as blank for optional fields")
        void treatsErrorCellAsBlankForOptionalField() throws Exception {
            setupImportService();
            when(businessAreasRepository.findAll()).thenReturn(List.of(area));
            when(managersRepository.findAll()).thenReturn(List.of());
            when(contractsRepository.existsByContractNumber(any())).thenReturn(false);

            byte[] bytes;
            try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                Sheet sheet = workbook.createSheet("Contratti");
                Row header = sheet.createRow(0);
                for (int c = 0; c < HEADER.length; c++) {
                    header.createCell(c).setCellValue(HEADER[c]);
                }
                Row dataRow = sheet.createRow(1);
                dataRow.createCell(0).setCellValue("C-1");
                dataRow.createCell(1).setCellValue("Cliente A");
                dataRow.createCell(2).setCellErrorValue(org.apache.poi.ss.usermodel.FormulaError.DIV0.getCode());
                dataRow.createCell(4).setCellValue("01/01/2024");
                dataRow.createCell(5).setCellValue("31/12/2024");
                dataRow.createCell(7).setCellValue("IT");
                workbook.write(out);
                bytes = out.toByteArray();
            }

            importService.importFromExcel(toFile(bytes));

            org.mockito.ArgumentCaptor<ContractDTO> captor = org.mockito.ArgumentCaptor.forClass(ContractDTO.class);
            verify(contractService).createContract(captor.capture());
            assertEquals(null, captor.getValue().projectName());
        }

        @Test
        @DisplayName("treats a whitespace-only cell as blank for optional fields")
        void treatsWhitespaceOnlyCellAsBlankForOptionalField() throws Exception {
            setupImportService();
            when(businessAreasRepository.findAll()).thenReturn(List.of(area));
            when(managersRepository.findAll()).thenReturn(List.of());
            when(contractsRepository.existsByContractNumber(any())).thenReturn(false);

            byte[] bytes = buildWorkbook(HEADER,
                    new String[]{"C-1", "Cliente A", "   ", null, "01/01/2024", "31/12/2024", null, "IT"});

            importService.importFromExcel(toFile(bytes));

            org.mockito.ArgumentCaptor<ContractDTO> captor = org.mockito.ArgumentCaptor.forClass(ContractDTO.class);
            verify(contractService).createContract(captor.capture());
            assertEquals(null, captor.getValue().projectName());
        }

        @Test
        @DisplayName("treats an explicitly created but empty cell as blank")
        void treatsExplicitBlankCellAsBlank() throws Exception {
            setupImportService();
            when(businessAreasRepository.findAll()).thenReturn(List.of(area));
            when(managersRepository.findAll()).thenReturn(List.of());
            when(contractsRepository.existsByContractNumber(any())).thenReturn(false);

            byte[] bytes;
            try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                Sheet sheet = workbook.createSheet("Contratti");
                Row header = sheet.createRow(0);
                for (int c = 0; c < HEADER.length; c++) {
                    header.createCell(c).setCellValue(HEADER[c]);
                }
                Row dataRow = sheet.createRow(1);
                dataRow.createCell(0).setCellValue("C-1");
                dataRow.createCell(1).setCellValue("Cliente A");
                dataRow.createCell(2); // explicitly created, no value: CellType.BLANK
                dataRow.createCell(4).setCellValue("01/01/2024");
                dataRow.createCell(5).setCellValue("31/12/2024");
                dataRow.createCell(7).setCellValue("IT");
                workbook.write(out);
                bytes = out.toByteArray();
            }

            importService.importFromExcel(toFile(bytes));

            org.mockito.ArgumentCaptor<ContractDTO> captor = org.mockito.ArgumentCaptor.forClass(ContractDTO.class);
            verify(contractService).createContract(captor.capture());
            assertEquals(null, captor.getValue().projectName());
        }

        @Test
        @DisplayName("treats a whitespace-only cell as blank for required fields")
        void treatsWhitespaceOnlyCellAsBlank() throws Exception {
            setupImportService();
            when(businessAreasRepository.findAll()).thenReturn(List.of(area));
            when(managersRepository.findAll()).thenReturn(List.of());

            byte[] bytes = buildWorkbook(HEADER,
                    new String[]{"   ", "Cliente A", null, null, "01/01/2024", "31/12/2024", null, "IT"});

            ContractImportResultDTO result = importService.importFromExcel(toFile(bytes));

            assertEquals(1, result.errors().size());
            assertTrue(result.errors().get(0).message().contains("Numero contratto mancante"));
        }

        @Test
        @DisplayName("skips rows where every cell is whitespace-only, same as a fully blank row")
        void skipsRowsWithOnlyWhitespaceCells() throws Exception {
            setupImportService();

            byte[] bytes = buildWorkbook(HEADER,
                    new String[]{" ", " ", " ", " ", " ", " ", " ", " "});

            ContractImportResultDTO result = importService.importFromExcel(toFile(bytes));

            assertEquals(0, result.totalRows());
            verify(contractService, never()).createContract(any());
        }

        @Test
        @DisplayName("rejects a date column holding a plain (non-date-formatted) number")
        void rejectsNonDateFormattedNumericDateCell() throws Exception {
            setupImportService();
            when(businessAreasRepository.findAll()).thenReturn(List.of(area));
            when(managersRepository.findAll()).thenReturn(List.of());

            byte[] bytes;
            try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                Sheet sheet = workbook.createSheet("Contratti");
                Row header = sheet.createRow(0);
                for (int c = 0; c < HEADER.length; c++) {
                    header.createCell(c).setCellValue(HEADER[c]);
                }
                Row dataRow = sheet.createRow(1);
                dataRow.createCell(0).setCellValue("C-1");
                dataRow.createCell(1).setCellValue("Cliente A");
                dataRow.createCell(4).setCellValue(45000.0);
                dataRow.createCell(5).setCellValue("31/12/2024");
                dataRow.createCell(7).setCellValue("IT");
                workbook.write(out);
                bytes = out.toByteArray();
            }

            ContractImportResultDTO result = importService.importFromExcel(toFile(bytes));

            assertEquals(1, result.errors().size());
            assertTrue(result.errors().get(0).message().contains("Data inizio non valida"));
        }

        @Test
        @DisplayName("rejects a date column that is a whitespace-only string")
        void rejectsWhitespaceOnlyDateCell() throws Exception {
            setupImportService();
            when(businessAreasRepository.findAll()).thenReturn(List.of(area));
            when(managersRepository.findAll()).thenReturn(List.of());

            byte[] bytes = buildWorkbook(HEADER,
                    new String[]{"C-1", "Cliente A", null, null, "   ", "31/12/2024", null, "IT"});

            ContractImportResultDTO result = importService.importFromExcel(toFile(bytes));

            assertEquals(1, result.errors().size());
            assertTrue(result.errors().get(0).message().contains("Data inizio mancante"));
        }

        @Test
        @DisplayName("rejects a date column that is an explicitly created but empty cell")
        void rejectsExplicitBlankDateCell() throws Exception {
            setupImportService();
            when(businessAreasRepository.findAll()).thenReturn(List.of(area));
            when(managersRepository.findAll()).thenReturn(List.of());

            byte[] bytes;
            try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                Sheet sheet = workbook.createSheet("Contratti");
                Row header = sheet.createRow(0);
                for (int c = 0; c < HEADER.length; c++) {
                    header.createCell(c).setCellValue(HEADER[c]);
                }
                Row dataRow = sheet.createRow(1);
                dataRow.createCell(0).setCellValue("C-1");
                dataRow.createCell(1).setCellValue("Cliente A");
                dataRow.createCell(4); // explicitly created, no value: CellType.BLANK
                dataRow.createCell(5).setCellValue("31/12/2024");
                dataRow.createCell(7).setCellValue("IT");
                workbook.write(out);
                bytes = out.toByteArray();
            }

            ContractImportResultDTO result = importService.importFromExcel(toFile(bytes));

            assertEquals(1, result.errors().size());
            assertTrue(result.errors().get(0).message().contains("Data inizio mancante"));
        }

        @Test
        @DisplayName("skips business areas with a null name while resolving a match")
        void skipsAreasWithNullNameWhenResolving() throws Exception {
            setupImportService();
            BusinessAreas unnamed = BusinessAreas.builder().id(99L).name(null).build();
            when(businessAreasRepository.findAll()).thenReturn(List.of(unnamed, area));
            when(managersRepository.findAll()).thenReturn(List.of());
            when(contractsRepository.existsByContractNumber(any())).thenReturn(false);

            byte[] bytes = buildWorkbook(HEADER,
                    new String[]{"C-1", "Cliente A", null, null, "01/01/2024", "31/12/2024", null, "IT"});

            importService.importFromExcel(toFile(bytes));

            org.mockito.ArgumentCaptor<ContractDTO> captor = org.mockito.ArgumentCaptor.forClass(ContractDTO.class);
            verify(contractService).createContract(captor.capture());
            assertEquals(area.getId(), captor.getValue().areaId());
        }
    }

    @Nested
    @DisplayName("File-level validation")
    class FileValidation {

        @Test
        @DisplayName("rejects files that are not .xlsx")
        void rejectsNonXlsx() {
            setupImportService();
            MockMultipartFile file = new MockMultipartFile("file", "import.csv", "text/csv", "a,b,c".getBytes());

            assertThrows(IllegalArgumentException.class, () -> importService.importFromExcel(file));
        }

        @Test
        @DisplayName("rejects files with no original filename")
        void rejectsMissingFilename() {
            setupImportService();
            MockMultipartFile file = new MockMultipartFile("file", null, "text/csv", "a,b,c".getBytes());

            assertThrows(IllegalArgumentException.class, () -> importService.importFromExcel(file));
        }

        @Test
        @DisplayName("rejects a workbook with no sheets")
        void rejectsWorkbookWithNoSheets() throws Exception {
            setupImportService();
            when(businessAreasRepository.findAll()).thenReturn(List.of());
            when(managersRepository.findAll()).thenReturn(List.of());

            byte[] bytes;
            try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                workbook.write(out);
                bytes = out.toByteArray();
            }
            MockMultipartFile file = toFile(bytes);

            assertThrows(IllegalArgumentException.class, () -> importService.importFromExcel(file));
        }

        @Test
        @DisplayName("rejects files exceeding the maximum row count")
        void rejectsTooManyRows() throws Exception {
            setupImportService();
            byte[] bytes;
            try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                Sheet sheet = workbook.createSheet("Contratti");
                Row header = sheet.createRow(0);
                for (int c = 0; c < HEADER.length; c++) {
                    header.createCell(c).setCellValue(HEADER[c]);
                }
                // Create a sparse row far beyond the limit; POI doesn't need
                // the intervening rows populated for getLastRowNum() to reflect it.
                sheet.createRow(5002).createCell(0).setCellValue("overflow");
                workbook.write(out);
                bytes = out.toByteArray();
            }
            MockMultipartFile file = toFile(bytes);

            assertThrows(IllegalArgumentException.class, () -> importService.importFromExcel(file));
        }

        @Test
        @DisplayName("scopes area/manager lookups to the current tenant when set")
        void scopesToTenant() throws Exception {
            setupImportService();
            TenantContext.set(9L);
            when(businessAreasRepository.findAllByOrganizationId(9L)).thenReturn(List.of(area));
            when(managersRepository.findAllByOrganizationId(9L)).thenReturn(List.of(manager));
            when(contractsRepository.existsByContractNumber(any())).thenReturn(false);

            byte[] bytes = buildWorkbook(HEADER,
                    new String[]{"C-1", "Cliente A", null, null, "01/01/2024", "31/12/2024", null, "IT"});

            importService.importFromExcel(toFile(bytes));

            verify(businessAreasRepository).findAllByOrganizationId(9L);
            verify(managersRepository).findAllByOrganizationId(9L);
            verify(businessAreasRepository, never()).findAll();
        }
    }

    @Nested
    @DisplayName("Template generation")
    class TemplateGeneration {

        @Test
        @DisplayName("generates a workbook with header and example row")
        void generatesTemplate() throws Exception {
            setupImportService();
            byte[] bytes = importService.generateTemplate();

            try (Workbook workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(bytes))) {
                Sheet sheet = workbook.getSheet("Contratti");
                assertEquals("Contract Number", sheet.getRow(0).getCell(0).getStringCellValue());
                assertEquals("2024-001", sheet.getRow(1).getCell(0).getStringCellValue());
                assertEquals(2, workbook.getNumberOfSheets());
            }
        }
    }
}
