package showtime.manager.utils;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import showtime.manager.model.DatabaseManager;
import showtime.manager.model.ScheduleItem;
import showtime.manager.model.VideoItem;

import javax.swing.*;
import java.io.File;
import java.io.FileInputStream;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class ExcelImporter {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    public static void importExcelFile(JFrame parentFrame, File file, boolean contentOnly) {
        Map<LocalDate, List<ScheduleItem>> importedSchedules = new TreeMap<>();
        List<VideoRecord> videoRecords = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(file);
             Workbook wb = new XSSFWorkbook(fis)) {

            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                Sheet sheet = wb.getSheetAt(i);
                for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                    Row row = sheet.getRow(r);
                    if (row == null) continue;

                    String dateStr = getCellString(row.getCell(0));
                    String timeInStr = getCellString(row.getCell(1));
                    String durationStr = getCellString(row.getCell(4));
                    String title = getCellString(row.getCell(5));

                    // Skip empty rows
                    if (title.isEmpty() && dateStr.isEmpty() && timeInStr.isEmpty()) {
                        continue;
                    }

                    if (title.isEmpty()) {
                        System.err.println("Row " + r + " missing title. Skipping.");
                        continue;
                    }

                    if (!contentOnly && (dateStr.isEmpty() || durationStr.isEmpty())) {
                        System.err.println("Row " + r + " missing critical schedule data. Skipping.");
                        continue;
                    }

                    LocalDate date = null;
                    LocalTime timeIn = null;
                    if (!contentOnly) {
                        try {
                            if (dateStr.contains("T")) {
                                date = java.time.LocalDateTime.parse(dateStr).toLocalDate();
                            } else {
                                if (dateStr.contains("/")) dateStr = dateStr.replace("/", "-");
                                date = LocalDate.parse(dateStr, DATE_FMT);
                            }

                            if (!timeInStr.isEmpty()) {
                                if (timeInStr.contains("T")) {
                                    timeIn = java.time.LocalDateTime.parse(timeInStr).toLocalTime();
                                } else {
                                    timeIn = LocalTime.parse(timeInStr, TIME_FMT);
                                }
                            } else {
                                timeIn = LocalTime.of(0, 0, 0);
                            }
                        } catch (Exception e) {
                            System.err.println("Error parsing date/time at row " + r + ": " + e.getMessage() + " (Date: " + dateStr + ", Time: " + timeInStr + ")");
                            continue;
                        }
                    }

                    int durationSecs = parseDuration(durationStr);

                    String desc = getCellString(row.getCell(6));
                    String genre = getCellString(row.getCell(11));
                    String seasonStr = getCellString(row.getCell(12));
                    String episodeStr = getCellString(row.getCell(13));
                    String audio = getCellString(row.getCell(14));
                    String sub = getCellString(row.getCell(15));
                    String remarks = getCellString(row.getCell(16));

                    if (genre.isEmpty()) genre = "Other";

                    int season = 0;
                    try { if (!seasonStr.isEmpty()) season = Integer.parseInt(seasonStr); } catch (Exception ignored) {}
                    int episode = 0;
                    try { if (!episodeStr.isEmpty()) episode = Integer.parseInt(episodeStr); } catch (Exception ignored) {}

                    VideoRecord vr = new VideoRecord(title, desc, genre, season, episode, durationSecs, audio, sub, remarks);
                    
                    if (!contentOnly) {
                        ScheduleItem item = new ScheduleItem();
                        item.setScheduleDate(date);
                        item.setStartTime(timeIn);
                        item.setDurationSeconds(durationSecs);
                        item.setRemarks(remarks);
                        item.setDayOfWeek(date.getDayOfWeek().toString());
                        item.setStatus("Scheduled");
                        
                        vr.scheduleItem = item;
                        
                        importedSchedules.computeIfAbsent(date, k -> new ArrayList<>()).add(item);
                    }
                    videoRecords.add(vr);
                }
            }

            if (!contentOnly && importedSchedules.isEmpty()) {
                JOptionPane.showMessageDialog(parentFrame, "No valid schedule data found in the Excel file.", "Import Empty", JOptionPane.WARNING_MESSAGE);
                return;
            }
            
            if (contentOnly && videoRecords.isEmpty()) {
                JOptionPane.showMessageDialog(parentFrame, "No valid content data found in the Excel file.", "Import Empty", JOptionPane.WARNING_MESSAGE);
                return;
            }

            boolean overwrite = false;
            if (!contentOnly) {
                // Ask User about overwriting
                int overwriteChoice = JOptionPane.showOptionDialog(parentFrame,
                        "Excel data parsed successfully.\n" +
                        "For dates that already have schedule items, what would you like to do?",
                        "Import Configuration",
                        JOptionPane.YES_NO_CANCEL_OPTION,
                        JOptionPane.QUESTION_MESSAGE,
                        null,
                        new String[]{"Overwrite Existing", "Append (Merge)", "Cancel"},
                        "Append (Merge)");

                if (overwriteChoice == 2 || overwriteChoice == JOptionPane.CLOSED_OPTION) { // Cancel
                    return;
                }
                overwrite = (overwriteChoice == 0);
            }

            // Process Database entries
            for (VideoRecord vr : videoRecords) {
                // Ensure Genre
                int typeId = DatabaseManager.getTypeId(vr.genre);
                if (typeId == -1) {
                    DatabaseManager.addContentType(vr.genre);
                    typeId = DatabaseManager.getTypeId(vr.genre);
                }

                // Ensure Video
                VideoItem vItem = DatabaseManager.getVideoByName(vr.title);
                if (vItem == null) {
                    // Create Series, Season, and Video
                    int seriesId = DatabaseManager.getSeriesId(vr.title, typeId);
                    if (seriesId == -1) {
                        seriesId = DatabaseManager.createSeries(vr.title, typeId);
                    }
                    int seasonNumber = vr.season > 0 ? vr.season : 1;
                    int seasonId = DatabaseManager.getOrCreateSeason(seriesId, seasonNumber);
                    
                    int epNum = vr.episode > 0 ? vr.episode : 1;
                    boolean hasSeasons = vr.season > 0 || vr.episode > 0;
                    
                    DatabaseManager.insertVideo(seasonId, epNum, vr.title, vr.duration, vr.desc, vr.audio, vr.sub, vr.remarks, hasSeasons);
                    vItem = DatabaseManager.getVideoByName(vr.title);
                }
                
                if (vItem != null) {
                    if (vr.scheduleItem != null) {
                        vr.scheduleItem.setVideoId(vItem.getId());
                    }
                } else {
                    System.err.println("Failed to resolve video id for title: " + vr.title);
                }
            }

            if (!contentOnly) {
                // Save to schedule
                int totalInserted = 0;
                for (Map.Entry<LocalDate, List<ScheduleItem>> entry : importedSchedules.entrySet()) {
                    LocalDate date = entry.getKey();
                    List<ScheduleItem> items = entry.getValue();

                    if (overwrite) {
                        ScheduleManager.clearDaySchedule(date);
                    }

                    for (ScheduleItem item : items) {
                        if (item.getVideoId() > 0) {
                            ScheduleManager.saveScheduleItem(item);
                            totalInserted++;
                        }
                    }
                    
                    ScheduleManager.reflowDay(date); // Reflow to ensure no overlaps and correct ordering
                }

                JOptionPane.showMessageDialog(parentFrame, "Schedule Import Completed successfully.\n" + totalInserted + " items inserted.", "Import Success", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(parentFrame, "Content Import Completed successfully.\n" + videoRecords.size() + " videos processed.", "Import Success", JOptionPane.INFORMATION_MESSAGE);
            }

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(parentFrame, "Error importing Excel file:\n" + e.getMessage(), "Import Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static String getCellString(Cell cell) {
        if (cell == null) return "";
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                // Check if it's a date or time
                if (DateUtil.isCellDateFormatted(cell)) {
                    // It's tricky to parse date vs time automatically, but POI provides it.
                    // However, we rely on String formats. If it's pure double, just return string.
                    return cell.getLocalDateTimeCellValue().toString();
                }
                double val = cell.getNumericCellValue();
                if (val == (int) val) {
                    return String.valueOf((int) val);
                }
                return String.valueOf(val);
            default:
                return "";
        }
    }

    private static int parseDuration(String durStr) {
        if (durStr == null || durStr.isEmpty()) return 0;
        try {
            if (durStr.contains("T")) {
                java.time.LocalTime t = java.time.LocalDateTime.parse(durStr).toLocalTime();
                return t.getHour() * 3600 + t.getMinute() * 60 + t.getSecond();
            }
            String[] parts = durStr.split(":");
            if (parts.length == 3) {
                int h = Integer.parseInt(parts[0]);
                int m = Integer.parseInt(parts[1]);
                int s = Integer.parseInt(parts[2]);
                return h * 3600 + m * 60 + s;
            } else if (parts.length == 2) {
                // mm:ss format fallback
                int m = Integer.parseInt(parts[0]);
                int s = Integer.parseInt(parts[1]);
                return m * 60 + s;
            } else if (parts.length == 1) {
                // assume seconds
                return Integer.parseInt(parts[0]);
            }
        } catch (Exception ignored) {}
        return 0;
    }

    private static class VideoRecord {
        String title, desc, genre, audio, sub, remarks;
        int season, episode, duration;
        ScheduleItem scheduleItem;

        public VideoRecord(String title, String desc, String genre, int season, int episode, int duration, String audio, String sub, String remarks) {
            this.title = title;
            this.desc = desc;
            this.genre = genre;
            this.season = season;
            this.episode = episode;
            this.duration = duration;
            this.audio = audio;
            this.sub = sub;
            this.remarks = remarks;
        }
    }
}
