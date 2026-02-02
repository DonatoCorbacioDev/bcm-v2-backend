package com.donatodev.bcm_backend.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import com.donatodev.bcm_backend.dto.ContractDTO;
import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;

/**
 * Service for exporting contracts to Excel and PDF formats. Provides methods to
 * generate professional reports in multiple formats.
 */
@Service
public class ExportService {

    /**
     * Exports contracts to Excel format (.xlsx). Includes header styling,
     * auto-sizing, and null-safe data handling.
     *
     * @param contracts list of contracts to export
     * @return byte array of the Excel file
     * @throws IOException if export fails
     */
    public byte[] exportContractsToExcel(List<ContractDTO> contracts) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Contracts");

            // Create header row with styling
            Row headerRow = sheet.createRow(0);
            String[] headers = {
                "Contract Number", "Customer", "Project", "Status",
                "Start Date", "End Date", "Manager", "Business Area"
            };

            CellStyle headerStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setFontHeightInPoints((short) 12);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN);

            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Fill data rows
            int rowNum = 1;
            for (ContractDTO contract : contracts) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(contract.contractNumber());
                row.createCell(1).setCellValue(contract.customerName());
                row.createCell(2).setCellValue(contract.projectName());
                row.createCell(3).setCellValue(contract.status().toString());
                row.createCell(4).setCellValue(contract.startDate().toString());
                row.createCell(5).setCellValue(contract.endDate().toString());
                row.createCell(6).setCellValue(
                        contract.manager() != null
                        ? contract.manager().firstName() + " " + contract.manager().lastName()
                        : "N/A"
                );
                row.createCell(7).setCellValue(
                        contract.area() != null ? contract.area().name() : "N/A"
                );
            }

            // Auto-size all columns
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        }
    }

    /**
     * Exports contracts to PDF format in landscape orientation. Includes title,
     * generation date, styled table, and statistics.
     *
     * @param contracts list of contracts to export
     * @return byte array of the PDF file
     * @throws DocumentException if PDF generation fails
     */
    public byte[] exportContractsToPDF(List<ContractDTO> contracts) throws DocumentException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4.rotate()); // Landscape
        PdfWriter.getInstance(document, out);

        document.open();

        // Title
        Font titleFont = new Font(Font.FontFamily.HELVETICA, 20, Font.BOLD, BaseColor.DARK_GRAY);
        Paragraph title = new Paragraph("Business Contracts Manager", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        document.add(title);

        Paragraph subtitle = new Paragraph("Contracts Export Report",
                new Font(Font.FontFamily.HELVETICA, 14, Font.NORMAL, BaseColor.GRAY));
        subtitle.setAlignment(Element.ALIGN_CENTER);
        subtitle.setSpacingAfter(10);
        document.add(subtitle);

        // Generation date
        String dateStr = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        Paragraph date = new Paragraph("Generated: " + dateStr,
                new Font(Font.FontFamily.HELVETICA, 9, Font.ITALIC, BaseColor.GRAY));
        date.setAlignment(Element.ALIGN_CENTER);
        date.setSpacingAfter(20);
        document.add(date);

        // Table
        PdfPTable table = new PdfPTable(8);
        table.setWidthPercentage(100);
        table.setSpacingBefore(10f);

        // Header
        String[] headers = {
            "Contract Number", "Customer", "Project", "Status",
            "Start Date", "End Date", "Manager", "Business Area"
        };
        Font headerFont = new Font(Font.FontFamily.HELVETICA, 9, Font.BOLD, BaseColor.WHITE);

        for (String header : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(header, headerFont));
            cell.setBackgroundColor(new BaseColor(52, 73, 94)); // Dark blue
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            cell.setPadding(6);
            table.addCell(cell);
        }

        // Data rows with alternating colors
        Font dataFont = new Font(Font.FontFamily.HELVETICA, 8);
        BaseColor evenRowColor = new BaseColor(240, 240, 240);

        int rowIndex = 0;
        for (ContractDTO contract : contracts) {
            BaseColor rowColor = (rowIndex % 2 == 0) ? BaseColor.WHITE : evenRowColor;

            addCellToTable(table, contract.contractNumber(), dataFont, rowColor);
            addCellToTable(table, contract.customerName(), dataFont, rowColor);
            addCellToTable(table, contract.projectName(), dataFont, rowColor);
            addCellToTable(table, contract.status().toString(), dataFont, rowColor);
            addCellToTable(table, contract.startDate().toString(), dataFont, rowColor);
            addCellToTable(table, contract.endDate().toString(), dataFont, rowColor);
            addCellToTable(table,
                    contract.manager() != null
                    ? contract.manager().firstName() + " " + contract.manager().lastName()
                    : "N/A",
                    dataFont, rowColor
            );
            addCellToTable(table,
                    contract.area() != null ? contract.area().name() : "N/A",
                    dataFont, rowColor
            );

            rowIndex++;
        }

        document.add(table);

        // Footer with statistics
        Paragraph footer = new Paragraph(
                String.format("%nTotal Contracts: %d", contracts.size()),
                new Font(Font.FontFamily.HELVETICA, 11, Font.BOLD, BaseColor.DARK_GRAY)
        );
        footer.setSpacingBefore(15);
        document.add(footer);

        document.close();
        return out.toByteArray();
    }

    /**
     * Helper method to add a cell to the PDF table with consistent styling.
     */
    private void addCellToTable(PdfPTable table, String content, Font font, BaseColor bgColor) {
        PdfPCell cell = new PdfPCell(new Phrase(content, font));
        cell.setBackgroundColor(bgColor);
        cell.setPadding(4);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        table.addCell(cell);
    }
}
