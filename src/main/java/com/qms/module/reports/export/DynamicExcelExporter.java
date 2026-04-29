package com.qms.module.reports.export;

import com.qms.common.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Generates Excel files for dynamic (user-defined) reports.
 * Accepts List<Map<String,Object>> so it is module-agnostic.
 */
@Slf4j
@Component
public class DynamicExcelExporter {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MMM-yyyy");
    private static final DateTimeFormatter DT_FMT   = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm");

    @Value("${reports.export.company-name:QMS Organisation}")
    private String companyName;

    @Value("${reports.export.max-rows-excel:50000}")
    private int maxRows;

    public byte[] export(List<Map<String, Object>> rows, String reportName,
                         LocalDate dateFrom, LocalDate dateTo) {
        if (rows.size() > maxRows) {
            throw AppException.badRequest("Export exceeds maximum row limit (" + maxRows + ").");
        }
        if (rows.isEmpty()) {
            return exportEmpty(reportName);
        }

        List<String> headers = new ArrayList<>(rows.get(0).keySet());

        try (SXSSFWorkbook wb = new SXSSFWorkbook(100)) {
            Sheet sheet = wb.createSheet("Report");
            int rowNum  = 0;

            // ── Meta rows ──
            CellStyle titleStyle = wb.createCellStyle();
            Font titleFont = wb.createFont();
            titleFont.setBold(true); titleFont.setFontHeightInPoints((short) 14);
            titleFont.setColor(IndexedColors.DARK_BLUE.getIndex());
            titleStyle.setFont(titleFont);

            CellStyle metaStyle = wb.createCellStyle();
            Font metaFont = wb.createFont();
            metaFont.setItalic(true);
            metaFont.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
            metaStyle.setFont(metaFont);

            Row r0 = sheet.createRow(rowNum++);
            Cell c0 = r0.createCell(0); c0.setCellValue(companyName); c0.setCellStyle(titleStyle);

            Row r1 = sheet.createRow(rowNum++);
            Cell c1 = r1.createCell(0); c1.setCellValue(reportName); c1.setCellStyle(titleStyle);

            Row r2 = sheet.createRow(rowNum++);
            String period = (dateFrom != null ? DATE_FMT.format(dateFrom) : "—")
                          + "  to  "
                          + (dateTo != null ? DATE_FMT.format(dateTo) : "—");
            Cell c2 = r2.createCell(0);
            c2.setCellValue("Period: " + period + "    |    Generated: " + DT_FMT.format(LocalDateTime.now())
                          + "    |    Rows: " + rows.size());
            c2.setCellStyle(metaStyle);

            rowNum++; // blank

            // ── Header row ──
            CellStyle headerStyle = wb.createCellStyle();
            Font headerFont = wb.createFont();
            headerFont.setBold(true); headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setBorderBottom(BorderStyle.THIN);

            Row headerRow = sheet.createRow(rowNum++);
            for (int i = 0; i < headers.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers.get(i));
                cell.setCellStyle(headerStyle);
            }

            // ── Data row styles ──
            CellStyle evenStyle = wb.createCellStyle();
            evenStyle.setFillForegroundColor(IndexedColors.WHITE.getIndex());
            evenStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            CellStyle oddStyle = wb.createCellStyle();
            oddStyle.setFillForegroundColor((short) 0x29); // light turquoise
            oddStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // ── Data rows ──
            for (int i = 0; i < rows.size(); i++) {
                Map<String, Object> rowData = rows.get(i);
                Row dataRow = sheet.createRow(rowNum++);
                CellStyle cs = (i % 2 == 0) ? evenStyle : oddStyle;
                for (int j = 0; j < headers.size(); j++) {
                    Object val = rowData.get(headers.get(j));
                    Cell cell = dataRow.createCell(j);
                    cell.setCellStyle(cs);
                    if (val == null) {
                        cell.setCellValue("");
                    } else if (val instanceof Number n) {
                        cell.setCellValue(n.doubleValue());
                    } else {
                        cell.setCellValue(val.toString());
                    }
                }
            }

            // ── Freeze header + auto-filter ──
            sheet.createFreezePane(0, 5);
            sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(4, rowNum - 1, 0, headers.size() - 1));

            // estimated column widths
            for (int i = 0; i < headers.size(); i++) {
                sheet.setColumnWidth(i, Math.min(6000, Math.max(2500, headers.get(i).length() * 350)));
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            wb.write(baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw AppException.internalError("Excel generation failed: " + e.getMessage());
        }
    }

    private byte[] exportEmpty(String reportName) {
        try (SXSSFWorkbook wb = new SXSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Report");
            Row r = sheet.createRow(0);
            r.createCell(0).setCellValue("No data found for: " + reportName);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            wb.write(baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw AppException.internalError("Excel generation failed: " + e.getMessage());
        }
    }
}
