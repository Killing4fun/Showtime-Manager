package showtime.manager.utils;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.*;
import showtime.manager.model.ScheduleItem;

import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Exports schedule data to Excel (.xlsx) format matching the standard
 * broadcast rundown layout:
 *
 * Columns (1-19):
 *  A  DATE (dd/mm/yyyy)
 *  B  TIME IN (hh:mm:ss)
 *  C  TIME OUT (hh:mm:ss)
 *  D  CODE
 *  E  DURATION
 *  F  TITLE
 *  G  DESCRIPTION
 *  H  INPUT
 *  I  VIDEO
 *  J  AUDIO
 *  K  HOST
 *  L  REMARKS
 *  M  GENRE
 *  N  SEASON
 *  O  EPISODE
 *  P  AUDIOLANGS
 *  Q  SUBLANGS
 *  R  REMARKS (2nd)
 *  S  GRAPHICS
 *
 * Each sheet is named after the day date range (e.g. "13" for 1-3 March).
 * Each day's items occupy consecutive rows starting from row 1 with the header.
 */
public class ExcelExporter {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static final String[] HEADERS = {
        "DATE (dd/mm/yyyy",
        "TIME IN (hh:mm:ss)",
        "TIME OUT (hh:mm:ss)",
        "CODE",
        "DURATION",
        "TITLE",
        "DESCRIPTION",
        "INPUT",
        "VIDEO",
        "AUDIO",
        "HOST",
        "GENRE",
        "SEASON",
        "EPISODE",
        "AUDIOLANGS",
        "SUBLANGS",
        "REMARKS",
        "GRAPHICS"
    };

    /**
     * Exports a week's schedule (startDate to startDate+days-1) to a file.
     *
     * @param filePath   absolute path to the output .xlsx file
     * @param startDate  first date of the export range
     * @param days       number of days to include
     * @throws IOException on file write errors
     */
    public static void exportWeek(String filePath, LocalDate startDate, int days) throws IOException {
        // Load all schedule items for the range
        List<ScheduleItem> allItems = ScheduleManager.loadSchedule(startDate, days);

        // Group items by date
        Map<LocalDate, List<ScheduleItem>> byDate = new LinkedHashMap<>();
        for (int i = 0; i < days; i++) {
            byDate.put(startDate.plusDays(i), new ArrayList<>());
        }
        for (ScheduleItem item : allItems) {
            LocalDate d = item.getScheduleDate();
            if (byDate.containsKey(d)) {
                byDate.get(d).add(item);
            }
        }

        try (XSSFWorkbook wb = new XSSFWorkbook()) {

            // Build shared styles once
            XSSFCellStyle headerStyle   = buildHeaderStyle(wb);
            XSSFCellStyle dataDateStyle = buildDataStyle(wb, false);
            XSSFCellStyle dataStyle     = buildDataStyle(wb, false);

            for (Map.Entry<LocalDate, List<ScheduleItem>> entry : byDate.entrySet()) {
                LocalDate date  = entry.getKey();
                List<ScheduleItem> items = entry.getValue();

                // Sheet name: day+month digits only, e.g. 1-3 March → "13"
                String sheetName = buildSheetName(date);
                XSSFSheet sheet = wb.createSheet(sheetName);

                // ---- Row 0: header ----
                XSSFRow headerRow = sheet.createRow(0);
                for (int c = 0; c < HEADERS.length; c++) {
                    XSSFCell cell = headerRow.createCell(c);
                    cell.setCellValue(HEADERS[c]);
                    cell.setCellStyle(headerStyle);
                }

                // ---- Data rows ----
                int rowIdx = 1;
                for (ScheduleItem item : items) {
                    XSSFRow row = sheet.createRow(rowIdx++);
                    fillRow(row, item, dataStyle);
                }

                // ---- Auto-size key columns ----
                for (int c = 0; c < HEADERS.length; c++) {
                    sheet.autoSizeColumn(c);
                    // Enforce reasonable min/max widths (POI units = 1/256 of a char)
                    int width = sheet.getColumnWidth(c);
                    if (width < 2000)  sheet.setColumnWidth(c, 2000);
                    if (width > 12000) sheet.setColumnWidth(c, 12000);
                }
            }

            // Write to file
            try (FileOutputStream fos = new FileOutputStream(filePath)) {
                wb.write(fos);
            }
        }
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /** Sheet name = day/month (e.g. 01-03). Note: '/' is illegal in sheet names, using '-' instead. */
    private static String buildSheetName(LocalDate date) {
        return date.format(DateTimeFormatter.ofPattern("dd-MM"));
    }

    /** Populate one data row from a ScheduleItem. */
    private static void fillRow(XSSFRow row, ScheduleItem item, XSSFCellStyle style) {

        LocalDate  date      = item.getScheduleDate();
        LocalTime  startTime = item.getStartTime();
        LocalTime  endTime   = item.getEndTime();

        // Col A – DATE
        setCell(row, 0, date != null ? date.format(DATE_FMT) : "", style);

        // Col B – TIME IN
        setCell(row, 1, startTime != null ? startTime.format(TIME_FMT) : "", style);

        // Col C – TIME OUT
        setCell(row, 2, endTime != null ? endTime.format(TIME_FMT) : "", style);

        // Col D – CODE  (empty, not tracked in DB)
        setCell(row, 3, "", style);

        // Col E – DURATION  hh:mm:ss
        setCell(row, 4, formatDuration(item.getDurationSeconds()), style);

        // Col F – TITLE (Series Name - Episode Name)
        String sName = nullSafe(item.getSeriesName());
        String vName = nullSafe(item.getVideoName());
        String fullTitle = vName;
        setCell(row, 5, fullTitle, style);

        // Col G – DESCRIPTION
        setCell(row, 6, nullSafe(item.getDescription()), style);

        // Col H – INPUT  (empty)
        setCell(row, 7, "", style);

        // Col I – VIDEO  (empty)
        setCell(row, 8, "", style);

        // Col J – AUDIO  (empty)
        setCell(row, 9, "", style);

        // Col K – HOST  (empty)
        setCell(row, 10, "", style);

        // Col L – GENRE  (videoType)
        setCell(row, 11, nullSafe(item.getVideoType()), style);

        // Col M – SEASON
        int season = item.getSeasonNumber();
        setCell(row, 12, (item.hasSeasons() && season > 0) ? String.valueOf(season) : "", style);

        // Col N – EPISODE
        int episode = item.getEpisodeNumber();
        setCell(row, 13, (item.hasSeasons() && episode > 0) ? String.valueOf(episode) : "", style);

        // Col O – AUDIOLANGS
        setCell(row, 14, nullSafe(item.getAudioLangs()), style);

        // Col P – SUBLANGS
        setCell(row, 15, nullSafe(item.getSubLangs()), style);

        // Col Q – REMARKS (Original database remarks)
        setCell(row, 16, nullSafe(item.getRemarks()), style);

        // Col R – GRAPHICS  (empty)
        setCell(row, 17, "", style);
    }

    private static void setCell(XSSFRow row, int col, String value, XSSFCellStyle style) {
        XSSFCell cell = row.createCell(col);
        cell.setCellValue(value != null ? value : "");
        cell.setCellStyle(style);
    }

    private static String nullSafe(String s) {
        return (s == null || s.equalsIgnoreCase("null")) ? "" : s;
    }

    private static String formatDuration(int totalSeconds) {
        int h = totalSeconds / 3600;
        int m = (totalSeconds % 3600) / 60;
        int s = totalSeconds % 60;
        return String.format("%02d:%02d:%02d", h, m, s);
    }

    // -----------------------------------------------------------------------
    // Style builders
    // -----------------------------------------------------------------------

    private static XSSFCellStyle buildHeaderStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = wb.createCellStyle();
        XSSFFont font = wb.createFont();
        font.setBold(true);
        font.setFontName("Arial");
        font.setFontHeightInPoints((short) 10);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);

        // Black background
        style.setFillForegroundColor(IndexedColors.BLACK.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        // Thin all-around border
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private static XSSFCellStyle buildDataStyle(XSSFWorkbook wb, boolean dateCol) {
        XSSFCellStyle style = wb.createCellStyle();
        XSSFFont font = wb.createFont();
        font.setFontName("Arial");
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(false);
        // Thin border matching reference file
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }
}
