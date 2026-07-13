package com.donatodev.bcm_backend.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.donatodev.bcm_backend.config.TenantContext;
import com.donatodev.bcm_backend.dto.ContractDTO;
import com.donatodev.bcm_backend.dto.ContractImportResultDTO;
import com.donatodev.bcm_backend.dto.ContractImportRowError;
import com.donatodev.bcm_backend.entity.BusinessAreas;
import com.donatodev.bcm_backend.entity.ContractStatus;
import com.donatodev.bcm_backend.entity.Managers;
import com.donatodev.bcm_backend.exception.ContractImportRowException;
import com.donatodev.bcm_backend.repository.BusinessAreasRepository;
import com.donatodev.bcm_backend.repository.ContractsRepository;
import com.donatodev.bcm_backend.repository.ManagersRepository;

/**
 * Bulk-imports contracts from an uploaded .xlsx spreadsheet. Each row is
 * validated and saved independently: a typo in one row does not block the
 * rest of the file. Column layout matches {@link ExportService}'s Excel
 * export, so a file round-tripped through export/edit/import needs no
 * reshaping.
 */
@Service
public class ContractImportService {

    private static final int MAX_DATA_ROWS = 5000;

    private static final int COL_CONTRACT_NUMBER = 0;
    private static final int COL_CUSTOMER = 1;
    private static final int COL_PROJECT = 2;
    private static final int COL_STATUS = 3;
    private static final int COL_START_DATE = 4;
    private static final int COL_END_DATE = 5;
    private static final int COL_MANAGER = 6;
    private static final int COL_AREA = 7;

    private static final String[] HEADERS = {
        "Contract Number", "Customer", "Project", "Status",
        "Start Date", "End Date", "Manager", "Business Area"
    };

    private static final DateTimeFormatter IT_DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ContractsRepository contractsRepository;
    private final BusinessAreasRepository businessAreasRepository;
    private final ManagersRepository managersRepository;
    private final ContractService contractService;

    public ContractImportService(
            ContractsRepository contractsRepository,
            BusinessAreasRepository businessAreasRepository,
            ManagersRepository managersRepository,
            ContractService contractService) {
        this.contractsRepository = contractsRepository;
        this.businessAreasRepository = businessAreasRepository;
        this.managersRepository = managersRepository;
        this.contractService = contractService;
    }

    /**
     * Imports contracts from an uploaded .xlsx file. Row 1 is treated as the
     * header and skipped; every subsequent non-blank row is validated and,
     * if valid, saved via {@link ContractService#createContract}.
     *
     * @param file the uploaded spreadsheet
     * @return a per-row report of what was imported and what was rejected
     */
    public ContractImportResultDTO importFromExcel(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new IllegalArgumentException("Sono supportati solo file .xlsx");
        }

        Long orgId = TenantContext.get();
        List<BusinessAreas> areas = (orgId != null)
                ? businessAreasRepository.findAllByOrganizationId(orgId)
                : businessAreasRepository.findAll();
        List<Managers> managers = (orgId != null)
                ? managersRepository.findAllByOrganizationId(orgId)
                : managersRepository.findAll();

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new IllegalArgumentException("Il file non contiene nessun foglio");
            }
            Sheet sheet = workbook.getSheetAt(0);
            int lastRowNum = sheet.getLastRowNum();
            if (lastRowNum > MAX_DATA_ROWS) {
                throw new IllegalArgumentException(
                        "Il file supera il limite massimo di " + MAX_DATA_ROWS + " righe");
            }

            Set<String> seenContractNumbers = new HashSet<>();
            List<ContractImportRowError> errors = new ArrayList<>();
            int totalRows = 0;
            int imported = 0;

            for (int rowIdx = 1; rowIdx <= lastRowNum; rowIdx++) {
                Row row = sheet.getRow(rowIdx);
                if (isRowBlank(row)) {
                    continue;
                }
                totalRows++;
                int excelRowNumber = rowIdx + 1;
                try {
                    ContractDTO dto = parseRow(row, areas, managers, seenContractNumbers);
                    contractService.createContract(dto);
                    imported++;
                } catch (ContractImportRowException e) {
                    errors.add(new ContractImportRowError(excelRowNumber, e.getMessage()));
                }
            }

            return new ContractImportResultDTO(totalRows, imported, errors.size(), errors);
        }
    }

    /**
     * Generates a downloadable .xlsx template matching the expected import
     * layout, with one filled example row and an instructions sheet.
     */
    public byte[] generateTemplate() throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Contratti");

            CellStyle headerStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN);

            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }

            Row example = sheet.createRow(1);
            String[] exampleValues = {
                "2024-001", "Azienda Rossi SRL", "Fornitura hardware", "ACTIVE",
                "01/01/2024", "31/12/2024", "Mario Rossi", "IT"
            };
            for (int i = 0; i < exampleValues.length; i++) {
                example.createCell(i).setCellValue(exampleValues[i]);
            }

            for (int i = 0; i < HEADERS.length; i++) {
                sheet.autoSizeColumn(i);
            }

            Sheet instructions = workbook.createSheet("Istruzioni");
            String[] notes = {
                "Non modificare l'ordine delle colonne nel foglio \"Contratti\".",
                "La prima riga e' l'intestazione e viene sempre ignorata durante l'import.",
                "Numero contratto e Cliente sono obbligatori.",
                "Data inizio e Data fine sono obbligatorie, formato gg/mm/aaaa o aaaa-mm-gg.",
                "Status: ACTIVE, EXPIRED, CANCELLED o DRAFT. Se vuoto, viene impostato ACTIVE.",
                "Area aziendale e' obbligatoria e deve corrispondere esattamente a un'area gia' esistente.",
                "Manager e' opzionale: se indicato deve corrispondere a un manager gia' esistente (nome e cognome, o email).",
                "Le righe con errori vengono segnalate singolarmente: le altre righe valide vengono comunque importate."
            };
            for (int i = 0; i < notes.length; i++) {
                instructions.createRow(i).createCell(0).setCellValue(notes[i]);
            }
            instructions.autoSizeColumn(0);

            workbook.write(out);
            return out.toByteArray();
        }
    }

    private ContractDTO parseRow(Row row, List<BusinessAreas> areas, List<Managers> managers,
            Set<String> seenContractNumbers) {
        String contractNumber = getRequiredString(row, COL_CONTRACT_NUMBER, "Numero contratto");
        String customerName = getRequiredString(row, COL_CUSTOMER, "Cliente");
        String projectName = getOptionalString(row, COL_PROJECT);
        ContractStatus status = parseStatus(getOptionalString(row, COL_STATUS));
        LocalDate startDate = parseDateCell(getCell(row, COL_START_DATE), "Data inizio");
        LocalDate endDate = parseDateCell(getCell(row, COL_END_DATE), "Data fine");
        Long areaId = resolveArea(getOptionalString(row, COL_AREA), areas);
        Long managerId = resolveManager(getOptionalString(row, COL_MANAGER), managers);

        if (!seenContractNumbers.add(contractNumber) || contractsRepository.existsByContractNumber(contractNumber)) {
            throw new ContractImportRowException("Numero contratto gia' esistente: " + contractNumber);
        }

        return new ContractDTO(null, customerName, contractNumber, null, projectName, status,
                startDate, endDate, areaId, managerId, null, null, null, null);
    }

    private boolean isRowBlank(Row row) {
        if (row == null) {
            return true;
        }
        for (int i = 0; i < HEADERS.length; i++) {
            String value = cellToString(getCell(row, i));
            if (value != null && !value.isBlank()) {
                return false;
            }
        }
        return true;
    }

    private Cell getCell(Row row, int idx) {
        return row == null ? null : row.getCell(idx);
    }

    private String getRequiredString(Row row, int idx, String fieldName) {
        String value = cellToString(getCell(row, idx));
        if (value == null || value.isBlank()) {
            throw new ContractImportRowException(fieldName + " mancante");
        }
        return value;
    }

    private String getOptionalString(Row row, int idx) {
        String value = cellToString(getCell(row, idx));
        return (value == null || value.isBlank()) ? null : value;
    }

    private String cellToString(Cell cell) {
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return null;
        }
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                    ? cell.getLocalDateTimeCellValue().toLocalDate().toString()
                    : String.valueOf(cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            default -> null;
        };
    }

    private ContractStatus parseStatus(String raw) {
        if (raw == null) {
            return ContractStatus.ACTIVE;
        }
        try {
            return ContractStatus.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ContractImportRowException("Status non valido: \"" + raw
                    + "\" (valori ammessi: ACTIVE, EXPIRED, CANCELLED, DRAFT)");
        }
    }

    private LocalDate parseDateCell(Cell cell, String fieldName) {
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            throw new ContractImportRowException(fieldName + " mancante");
        }
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate();
        }
        String raw = cellToString(cell);
        if (raw == null || raw.isBlank()) {
            throw new ContractImportRowException(fieldName + " mancante");
        }
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException isoFailed) {
            try {
                return LocalDate.parse(raw, IT_DATE_FORMAT);
            } catch (DateTimeParseException itFailed) {
                throw new ContractImportRowException(fieldName + " non valida: \"" + raw
                        + "\" (formati supportati: gg/mm/aaaa o aaaa-mm-gg)");
            }
        }
    }

    private Long resolveArea(String name, List<BusinessAreas> areas) {
        if (name == null) {
            throw new ContractImportRowException("Area aziendale mancante");
        }
        String needle = foldCase(name);
        for (BusinessAreas a : areas) {
            if (a.getName() != null && foldCase(a.getName()).equals(needle)) {
                return a.getId();
            }
        }
        throw new ContractImportRowException("Area aziendale non trovata: \"" + name + "\"");
    }

    private Long resolveManager(String value, List<Managers> managers) {
        if (value == null) {
            return null;
        }
        String needle = foldCase(value);
        for (Managers m : managers) {
            if (matchesManager(m, needle)) {
                return m.getId();
            }
        }
        throw new ContractImportRowException("Manager non trovato: \"" + value + "\"");
    }

    private boolean matchesManager(Managers m, String needle) {
        String fullName = foldCase(m.getFirstName() + " " + m.getLastName());
        if (fullName.equals(needle)) {
            return true;
        }
        return m.getEmail() != null && foldCase(m.getEmail()).equals(needle);
    }

    /**
     * Case-insensitive comparison key: Unicode-normalizes before
     * upper-casing with a fixed locale, avoiding the ambiguity of
     * {@code equalsIgnoreCase}/locale-default case folding.
     */
    private String foldCase(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFC).toUpperCase(Locale.ROOT);
    }
}
