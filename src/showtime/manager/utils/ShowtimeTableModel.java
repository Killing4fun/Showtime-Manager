package showtime.manager.utils;

import javax.swing.table.AbstractTableModel;
import java.time.LocalDate;
import java.time.DayOfWeek;
import java.time.format.DateTimeFormatter;
import java.util.*;
import showtime.manager.model.ScheduleItem;

public class ShowtimeTableModel extends AbstractTableModel {

    private List<LocalDate> dates;
    private Map<LocalDate, List<ScheduleItem>> dailySchedules;
    private boolean isLoading = false;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.ofPattern("EEEE");
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy");

    public ShowtimeTableModel() {
        this.dates = getWeekStartingMonday(LocalDate.now());
        this.dailySchedules = new HashMap<>();
        loadScheduleForWeek();
    }

    private List<LocalDate> getWeekStartingMonday(LocalDate currentDate) {
        List<LocalDate> weekDates = new ArrayList<>();
        LocalDate monday = currentDate;
        while (monday.getDayOfWeek() != DayOfWeek.MONDAY) {
            monday = monday.minusDays(1);
        }
        for (int i = 0; i < 7; i++) {
            weekDates.add(monday.plusDays(i));
        }
        return weekDates;
    }

    public void previousWeek() {
        if (isLoading)
            return;
        LocalDate newMonday = dates.get(0).minusWeeks(1);
        this.dates = getWeekStartingMonday(newMonday);
        loadScheduleForWeek(); // Reload data for new week
        fireTableDataChanged(); // Notify table that data changed
    }

    public void nextWeek() {
        if (isLoading)
            return;
        LocalDate newMonday = dates.get(0).plusWeeks(1);
        this.dates = getWeekStartingMonday(newMonday);
        loadScheduleForWeek();
        fireTableDataChanged();
    }

    public void currentWeek() {
        if (isLoading)
            return;
        this.dates = getWeekStartingMonday(LocalDate.now());
        loadScheduleForWeek();
        fireTableDataChanged();
    }

    public String getWeekRange() {
        if (dates.isEmpty())
            return "";
        return dates.get(0).format(MONTH_FORMATTER) + " - " + dates.get(6).format(MONTH_FORMATTER);
    }



    public LocalDate getCurrentWeekStart() {
        return dates.isEmpty() ? LocalDate.now() : dates.get(0);
    }

    public synchronized void loadScheduleForWeek() {
        if (isLoading)
            return;
        isLoading = true;

        try {
            if (dates.isEmpty())
                return;
            List<ScheduleItem> scheduleList = ScheduleManager.loadSchedule(dates.get(0), 7);
            dailySchedules.clear();
            for (LocalDate date : dates) {
                dailySchedules.put(date, new ArrayList<>());
            }
            for (ScheduleItem item : scheduleList) {
                if (item.getScheduleDate() != null) {
                    List<ScheduleItem> dailyItems = dailySchedules.get(item.getScheduleDate());
                    if (dailyItems != null) {
                        dailyItems.add(item);
                    }
                }
            }
            // Sort items per day sequentially
            for (List<ScheduleItem> list : dailySchedules.values()) {
                list.sort(Comparator.comparing(ScheduleItem::getStartTime));
            }
        } finally {
            isLoading = false;
        }

        fireTableDataChanged();
    }

    @Override
    public int getRowCount() {
        return dates.size();
    }

    @Override
    public int getColumnCount() {
        return 2; // Column 0: Day, Column 1: Timeline
    }

    @Override
    public String getColumnName(int column) {
        if (column == 0) return "Day";
        if (column == 1) return "Timeline";
        return "";
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        LocalDate date = dates.get(rowIndex);
        if (columnIndex == 0) {
            return date.format(DAY_FORMATTER) + "<br>" + date.format(DATE_FORMATTER); // Rendered as HTML by Main.java
        } else if (columnIndex == 1) {
            List<ScheduleItem> items = dailySchedules.get(date);
            return items != null ? items : new ArrayList<>(); // Return list of items for the day
        }
        return null;
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return false;
    }

    public LocalDate getDateAtRow(int rowIndex) {
        return (rowIndex >= 0 && rowIndex < dates.size()) ? dates.get(rowIndex) : null;
    }

    public List<ScheduleItem> getItemsForDate(LocalDate date) {
        List<ScheduleItem> items = dailySchedules.get(date);
        if (items == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(items);
    }

    // Add this method to fix the error
    public void debugPrintStructure() {
        System.out.println("\n=== ShowtimeTable Structure ===");
        System.out.println("Week: " + getWeekRange());
        System.out.println("Rows (days): " + getRowCount());
        System.out.println("Columns: " + getColumnCount());

        int totalItems = 0;
        for (LocalDate date : dates) {
            List<ScheduleItem> items = dailySchedules.get(date);
            int count = items != null ? items.size() : 0;
            totalItems += count;
            System.out.println("  " + date + ": " + count + " items");
            if (items != null && !items.isEmpty()) {
                for (ScheduleItem item : items) {
                    System.out.println("    - " + item.getVideoName() +
                            " | " + item.getStartTime() +
                            " to " + item.getEndTime());
                }
            }
        }
        System.out.println("Total items: " + totalItems);
        System.out.println("=============================\n");
    }
}

// ShowtimeTableModel