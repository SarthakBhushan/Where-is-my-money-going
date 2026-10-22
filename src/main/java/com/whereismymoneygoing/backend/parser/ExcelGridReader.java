package com.whereismymoneygoing.backend.parser;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;

/** Reads the first sheet of an .xls/.xlsx (optionally encrypted) into a grid of strings. */
@Component
public class ExcelGridReader {

    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public List<List<String>> read(byte[] bytes, String password) {
        boolean hasPassword = password != null && !password.isBlank();
        try (Workbook workbook = open(bytes, hasPassword ? password : null)) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter(Locale.ENGLISH);
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();

            List<List<String>> grid = new ArrayList<>();
            for (int r = 0; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                List<String> cells = new ArrayList<>();
                if (row != null) {
                    for (int c = 0; c < row.getLastCellNum(); c++) {
                        cells.add(cellText(row.getCell(c), formatter, evaluator));
                    }
                }
                grid.add(cells);
            }
            return grid;
        } catch (EncryptedDocumentException e) {
            if (hasPassword) {
                throw new IncorrectPasswordException("The password did not decrypt this Excel statement.");
            }
            throw new PasswordRequiredException(
                    "This Excel statement is password-protected. Enter its password to continue.");
        } catch (IOException | RuntimeException e) {
            throw new StatementParseException(
                    "Could not read this file. Only Excel files (.xls / .xlsx) are supported right now.", e);
        }
    }

    private static Workbook open(byte[] bytes, String password) throws IOException {
        ByteArrayInputStream in = new ByteArrayInputStream(bytes);
        return password == null ? WorkbookFactory.create(in) : WorkbookFactory.create(in, password);
    }

    private static String cellText(Cell cell, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                    ? cell.getLocalDateTimeCellValue().toLocalDate().format(DMY)
                    : BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> formatter.formatCellValue(cell, evaluator);
            default -> "";
        };
    }
}