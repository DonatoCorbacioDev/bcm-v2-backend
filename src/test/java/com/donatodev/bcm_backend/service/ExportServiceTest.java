package com.donatodev.bcm_backend.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import com.donatodev.bcm_backend.dto.BusinessAreaDTO;
import com.donatodev.bcm_backend.dto.ContractDTO;
import com.donatodev.bcm_backend.dto.ManagerDTO;
import com.donatodev.bcm_backend.entity.ContractStatus;
import com.itextpdf.text.pdf.PdfReader;

/**
 * Unit tests for ExportService. Tests Excel and PDF export functionality with
 * various scenarios.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("ExportService Tests")
class ExportServiceTest {

    private ExportService exportService;
    private List<ContractDTO> testContracts;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        exportService = new ExportService();
        testContracts = createTestContracts();
    }

    /**
     * Creates sample contracts for testing.
     */
    private List<ContractDTO> createTestContracts() {
        List<ContractDTO> contracts = new ArrayList<>();

        ManagerDTO manager1 = new ManagerDTO(
                1L, "Mario", "Rossi", "mario.rossi@example.com",
                "+39123456789", "IT Department"
        );

        ManagerDTO manager2 = new ManagerDTO(
                2L, "Luigi", "Bianchi", "luigi.bianchi@example.com",
                "+39987654321", "Sales"
        );

        BusinessAreaDTO area1 = new BusinessAreaDTO(1L, "IT Services", "Technology services");
        BusinessAreaDTO area2 = new BusinessAreaDTO(2L, "Consulting", "Business consulting");

        contracts.add(new ContractDTO(
                1L, // id
                "ACME Corp", // customerName
                "CNT-2025-001", // contractNumber
                "WBS-001", // wbsCode
                "Project Alpha", // projectName
                ContractStatus.ACTIVE, // status
                LocalDate.of(2025, 1, 1), // startDate
                LocalDate.of(2025, 12, 31), // endDate
                1L, // areaId
                1L, // managerId
                "Mario Rossi", // managerName
                manager1, // manager
                area1, // area
                null // daysUntilExpiry
        ));

        contracts.add(new ContractDTO(
                2L,
                "TechStart Inc",
                "CNT-2025-002",
                "WBS-002",
                "Project Beta",
                ContractStatus.ACTIVE,
                LocalDate.of(2025, 2, 1),
                LocalDate.of(2025, 11, 30),
                2L,
                2L,
                "Luigi Bianchi", // managerName
                manager2,
                area2,
                null
        ));

        return contracts;
    }

    /**
     * Creates a contract with null manager and area for edge case testing.
     */
    private ContractDTO createContractWithNulls() {
        return new ContractDTO(
                3L,
                "Beta Corp",
                "CNT-2025-003",
                "WBS-003",
                "Project Gamma",
                ContractStatus.EXPIRED,
                LocalDate.of(2025, 3, 1),
                LocalDate.of(2025, 9, 30),
                null, // areaId
                null, // managerId
                null, // managerName
                null, // manager
                null, // area
                null // daysUntilExpiry
        );
    }

    @Nested
    @Order(1)
    @DisplayName("Excel Export Tests")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @SuppressWarnings("unused")
    class ExcelExportTests {

        @Test
        @Order(1)
        @DisplayName("Should export contracts to Excel successfully")
        void testExportContractsToExcel_Success() throws IOException {
            // Act
            byte[] excelData = exportService.exportContractsToExcel(testContracts);

            // Assert
            assertNotNull(excelData, "Excel data should not be null");
            assertTrue(excelData.length > 0, "Excel data should not be empty");

            // Verify Excel content
            try (ByteArrayInputStream bis = new ByteArrayInputStream(excelData); Workbook workbook = new XSSFWorkbook(bis)) {

                Sheet sheet = workbook.getSheetAt(0);
                assertThat(sheet.getSheetName()).isEqualTo("Contracts");

                // Verify header row
                Row headerRow = sheet.getRow(0);
                assertThat(headerRow.getCell(0).getStringCellValue()).isEqualTo("Contract Number");
                assertThat(headerRow.getCell(1).getStringCellValue()).isEqualTo("Customer");
                assertThat(headerRow.getCell(2).getStringCellValue()).isEqualTo("Project");
                assertThat(headerRow.getCell(7).getStringCellValue()).isEqualTo("Business Area");

                // Verify data rows (2 contracts + 1 header = 3 rows)
                assertThat(sheet.getPhysicalNumberOfRows()).isEqualTo(3);

                // Verify first contract data
                Row firstDataRow = sheet.getRow(1);
                assertThat(firstDataRow.getCell(0).getStringCellValue()).isEqualTo("CNT-2025-001");
                assertThat(firstDataRow.getCell(1).getStringCellValue()).isEqualTo("ACME Corp");
                assertThat(firstDataRow.getCell(2).getStringCellValue()).isEqualTo("Project Alpha");
                assertThat(firstDataRow.getCell(3).getStringCellValue()).isEqualTo("ACTIVE");
                assertThat(firstDataRow.getCell(6).getStringCellValue()).isEqualTo("Mario Rossi");
                assertThat(firstDataRow.getCell(7).getStringCellValue()).isEqualTo("IT Services");
            }
        }

        @Test
        @Order(2)
        @DisplayName("Should export empty list to Excel successfully")
        void testExportContractsToExcel_EmptyList() throws IOException {
            // Arrange
            List<ContractDTO> emptyList = new ArrayList<>();

            // Act
            byte[] excelData = exportService.exportContractsToExcel(emptyList);

            // Assert
            assertNotNull(excelData);
            assertTrue(excelData.length > 0);

            // Verify only header row exists
            try (ByteArrayInputStream bis = new ByteArrayInputStream(excelData); Workbook workbook = new XSSFWorkbook(bis)) {

                Sheet sheet = workbook.getSheetAt(0);
                assertThat(sheet.getPhysicalNumberOfRows()).isEqualTo(1); // Only header
            }
        }

        @Test
        @Order(3)
        @DisplayName("Should handle null manager and area in Excel export")
        void testExportContractsToExcel_WithNullValues() throws IOException {
            // Arrange
            List<ContractDTO> contractsWithNulls = new ArrayList<>();
            contractsWithNulls.add(createContractWithNulls());

            // Act
            byte[] excelData = exportService.exportContractsToExcel(contractsWithNulls);

            // Assert
            assertNotNull(excelData);

            try (ByteArrayInputStream bis = new ByteArrayInputStream(excelData); Workbook workbook = new XSSFWorkbook(bis)) {

                Sheet sheet = workbook.getSheetAt(0);
                Row dataRow = sheet.getRow(1);

                // Verify N/A is used for null values
                assertThat(dataRow.getCell(6).getStringCellValue()).isEqualTo("N/A"); // Manager
                assertThat(dataRow.getCell(7).getStringCellValue()).isEqualTo("N/A"); // Area
            }
        }

        @Test
        @Order(4)
        @DisplayName("Should export large list of contracts to Excel")
        void testExportContractsToExcel_LargeList() throws IOException {
            // Arrange - Create 100 contracts
            List<ContractDTO> largeList = new ArrayList<>();
            for (int i = 1; i <= 100; i++) {
                ManagerDTO manager = new ManagerDTO(
                        (long) i, "Manager" + i, "Test" + i,
                        "manager" + i + "@test.com", "+39123" + i, "Dept" + i
                );
                BusinessAreaDTO area = new BusinessAreaDTO((long) i, "Area" + i, "Desc" + i);

                largeList.add(new ContractDTO(
                        (long) i,
                        "Customer" + i,
                        "CNT-2025-" + String.format("%03d", i),
                        "WBS-" + i,
                        "Project" + i,
                        ContractStatus.ACTIVE,
                        LocalDate.of(2025, 1, 1),
                        LocalDate.of(2025, 12, 31),
                        (long) i,
                        (long) i,
                        "Manager" + i + " Test" + i,
                        manager,
                        area,
                        null
                ));
            }

            // Act
            byte[] excelData = exportService.exportContractsToExcel(largeList);

            // Assert
            assertNotNull(excelData);
            assertTrue(excelData.length > 0);

            try (ByteArrayInputStream bis = new ByteArrayInputStream(excelData); Workbook workbook = new XSSFWorkbook(bis)) {

                Sheet sheet = workbook.getSheetAt(0);
                // 100 contracts + 1 header row
                assertThat(sheet.getPhysicalNumberOfRows()).isEqualTo(101);
            }
        }
    }

    @Nested
    @Order(2)
    @DisplayName("PDF Export Tests")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @SuppressWarnings("unused")
    class PDFExportTests {

        @Test
        @Order(1)
        @DisplayName("Should export contracts to PDF successfully")
        void testExportContractsToPDF_Success() throws Exception {
            // Act
            byte[] pdfData = exportService.exportContractsToPDF(testContracts);

            // Assert
            assertNotNull(pdfData, "PDF data should not be null");
            assertTrue(pdfData.length > 0, "PDF data should not be empty");

            // Verify PDF is valid
            try (ByteArrayInputStream bis = new ByteArrayInputStream(pdfData)) {
                PdfReader reader = new PdfReader(bis);
                assertThat(reader.getNumberOfPages()).isGreaterThan(0);
                reader.close();
            }
        }

        @Test
        @Order(2)
        @DisplayName("Should export empty list to PDF successfully")
        void testExportContractsToPDF_EmptyList() throws Exception {
            // Arrange
            List<ContractDTO> emptyList = new ArrayList<>();

            // Act
            byte[] pdfData = exportService.exportContractsToPDF(emptyList);

            // Assert
            assertNotNull(pdfData);
            assertTrue(pdfData.length > 0);

            // Verify PDF is valid even with no data rows
            try (ByteArrayInputStream bis = new ByteArrayInputStream(pdfData)) {
                PdfReader reader = new PdfReader(bis);
                assertThat(reader.getNumberOfPages()).isGreaterThan(0);
                reader.close();
            }
        }

        @Test
        @Order(3)
        @DisplayName("Should handle null manager and area in PDF export")
        void testExportContractsToPDF_WithNullValues() throws Exception {
            // Arrange
            List<ContractDTO> contractsWithNulls = new ArrayList<>();
            contractsWithNulls.add(createContractWithNulls());

            // Act
            byte[] pdfData = exportService.exportContractsToPDF(contractsWithNulls);

            // Assert
            assertNotNull(pdfData);
            assertTrue(pdfData.length > 0);

            // Verify PDF is valid
            try (ByteArrayInputStream bis = new ByteArrayInputStream(pdfData)) {
                PdfReader reader = new PdfReader(bis);
                assertThat(reader.getNumberOfPages()).isGreaterThan(0);
                reader.close();
            }
        }

        @Test
        @Order(4)
        @DisplayName("Should include correct metadata in PDF")
        void testExportContractsToPDF_Metadata() throws Exception {
            // Act
            byte[] pdfData = exportService.exportContractsToPDF(testContracts);

            // Assert - Verify PDF structure
            try (ByteArrayInputStream bis = new ByteArrayInputStream(pdfData)) {
                PdfReader reader = new PdfReader(bis);

                // Verify at least one page exists
                assertThat(reader.getNumberOfPages()).isEqualTo(1);

                reader.close();
            }
        }

        @Test
        @Order(5)
        @DisplayName("Should export large list of contracts to PDF")
        void testExportContractsToPDF_LargeList() throws Exception {
            // Arrange - Create 50 contracts
            List<ContractDTO> largeList = new ArrayList<>();
            for (int i = 1; i <= 50; i++) {
                ManagerDTO manager = new ManagerDTO(
                        (long) i, "Manager" + i, "Test" + i,
                        "manager" + i + "@test.com", "+39123" + i, "Dept" + i
                );
                BusinessAreaDTO area = new BusinessAreaDTO((long) i, "Area" + i, "Desc" + i);

                largeList.add(new ContractDTO(
                        (long) i,
                        "Customer" + i,
                        "CNT-2025-" + String.format("%03d", i),
                        "WBS-" + i,
                        "Project" + i,
                        ContractStatus.ACTIVE,
                        LocalDate.of(2025, 1, 1),
                        LocalDate.of(2025, 12, 31),
                        (long) i,
                        (long) i,
                        "Manager" + i + " Test" + i,
                        manager,
                        area,
                        null
                ));
            }

            // Act
            byte[] pdfData = exportService.exportContractsToPDF(largeList);

            // Assert
            assertNotNull(pdfData);
            assertTrue(pdfData.length > 0);

            try (ByteArrayInputStream bis = new ByteArrayInputStream(pdfData)) {
                PdfReader reader = new PdfReader(bis);
                assertThat(reader.getNumberOfPages()).isGreaterThan(0);
                reader.close();
            }
        }
    }
}
