package showtime.manager.utils;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import showtime.manager.model.DatabaseManager;
import showtime.manager.model.ScheduleItem;

public class ScheduleManager {

    // Broadcast runs from 06:00 to 06:00 next day (24 hours)
    public static final LocalTime BROADCAST_START = LocalTime.of(6, 0);
    public static final LocalTime BROADCAST_END = LocalTime.of(6, 0); // End time is same as start time (next day)

    public static final int TOTAL_SECONDS_IN_DAY = 24 * 3600; // 86400 seconds
    public static final int MIDNIGHT_SECONDS = 18 * 3600; // 18 hours from 06:00 = 24:00 (midnight)

    // Convert a time to seconds since broadcast start (handles wrap-around)
    public static int getSecondsSinceBroadcastStart(LocalTime time) {
        int startSeconds = BROADCAST_START.toSecondOfDay();
        int timeSeconds = time.toSecondOfDay();

        if (timeSeconds >= startSeconds) {
            return timeSeconds - startSeconds;
        } else {
            // Time is past midnight, add 24 hours
            return (timeSeconds + TOTAL_SECONDS_IN_DAY) - startSeconds;
        }
    }

    // Convert seconds since broadcast start back to LocalTime
    public static LocalTime getTimeFromSeconds(int secondsSinceStart) {
        int totalSeconds = BROADCAST_START.toSecondOfDay() + secondsSinceStart;
        if (totalSeconds >= TOTAL_SECONDS_IN_DAY) {
            totalSeconds -= TOTAL_SECONDS_IN_DAY;
        }
        return LocalTime.ofSecondOfDay(totalSeconds);
    }

    // Check if a time slot fits within the broadcast day
    public static boolean canFit(LocalTime startTime, int durationSeconds) {
        int startSeconds = getSecondsSinceBroadcastStart(startTime);
        int endSeconds = startSeconds + durationSeconds;
        return endSeconds <= TOTAL_SECONDS_IN_DAY;
    }

    // Calculate end time with midnight crossing support
    public static LocalTime calculateEndTime(LocalTime startTime, int durationSeconds) {
        int startSeconds = getSecondsSinceBroadcastStart(startTime);
        int endSeconds = startSeconds + durationSeconds;

        if (endSeconds > TOTAL_SECONDS_IN_DAY) {
            endSeconds = TOTAL_SECONDS_IN_DAY;
        }

        return getTimeFromSeconds(endSeconds);
    }

    // Check if a schedule item crosses midnight
    public static boolean crossesMidnight(LocalTime startTime, int durationSeconds) {
        int startSeconds = getSecondsSinceBroadcastStart(startTime);
        int endSeconds = startSeconds + durationSeconds;
        return endSeconds > MIDNIGHT_SECONDS;
    }

    public static List<LocalTime> generateTimeSlots(int intervalMinutes) {
        List<LocalTime> slots = new ArrayList<>();
        int totalSlots = TOTAL_SECONDS_IN_DAY / (intervalMinutes * 60);

        for (int i = 0; i < totalSlots; i++) {
            slots.add(getTimeFromSeconds(i * intervalMinutes * 60));
        }

        return slots;
    }

    public static List<LocalTime> getDefaultTimeSlots() {
        return generateTimeSlots(30);
    }

    public static boolean saveScheduleItem(ScheduleItem item) {
        if (!DatabaseManager.tableExists("schedule")) {
            return false;
        }

        LocalTime endTime = calculateEndTime(item.getStartTime(), item.getDurationSeconds());

        String sql = "INSERT INTO schedule (video_id, start_time, end_time, schedule_date, day_of_week, status, duration, is_loop) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try {
            Connection conn = DatabaseManager.getConnection();
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, item.getVideoId());
                pstmt.setString(2, item.getStartTime().toString());
                pstmt.setString(3, endTime.toString());
                pstmt.setString(4, item.getScheduleDate().toString());
                pstmt.setString(5, item.getDayOfWeek());
                pstmt.setString(6, item.getStatus());
                pstmt.setInt(7, item.getDurationSeconds());
                pstmt.setInt(8, item.isLoop() ? 1 : 0);

                int result = pstmt.executeUpdate();
                return result > 0;
            }
        } catch (SQLException e) {
            System.err.println("Error saving schedule: " + e.getMessage());
            return false;
        }
    }

    public static boolean updateScheduleItemDateAndTime(int scheduleId, LocalDate newDate, LocalTime newStartTime) {
        if (!DatabaseManager.tableExists("schedule"))
            return false;

        Connection conn = null;
        try {
            conn = DatabaseManager.getConnection();
            conn.setAutoCommit(false);
            
            // Get the item's duration, loop status, and remarks
            String sql = "SELECT CASE WHEN s.duration > 0 THEN s.duration ELSE v.duration END as effective_duration, " +
                         "s.is_loop, s.remarks " +
                         "FROM schedule s JOIN video v ON s.video_id = v.id WHERE s.id = ?";
            int durationSeconds = 0;
            int isLoop = 0;
            String remarks = null;
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, scheduleId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        durationSeconds = rs.getInt("effective_duration");
                        isLoop = rs.getInt("is_loop");
                        remarks = rs.getString("remarks");
                    }
                }
            }

            LocalTime newEndTime = calculateEndTime(newStartTime, durationSeconds);
            
            // Move item to new date with a temporary date to avoid constraints
            String updateSql = "UPDATE schedule SET schedule_date = '2099-12-31', start_time = ?, end_time = ?, day_of_week = ?, duration = ?, is_loop = ?, remarks = ? WHERE id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(updateSql)) {
                pstmt.setString(1, newStartTime.toString());
                pstmt.setString(2, newEndTime.toString());
                pstmt.setString(3, newDate.getDayOfWeek().toString());
                pstmt.setInt(4, durationSeconds);
                pstmt.setInt(5, isLoop);
                pstmt.setString(6, remarks);
                pstmt.setInt(7, scheduleId);
                pstmt.executeUpdate();
            }
            
            // Restore proper date
            String restoreSql = "UPDATE schedule SET schedule_date = ? WHERE schedule_date = '2099-12-31'";
            try (PreparedStatement pstmt = conn.prepareStatement(restoreSql)) {
                pstmt.setString(1, newDate.toString());
                pstmt.executeUpdate();
            }

            conn.commit();
        } catch (SQLException e) {
            try { if (conn != null) conn.rollback(); } catch(SQLException ex) {}
            e.printStackTrace();
            return false;
        } finally {
            try { if (conn != null) conn.setAutoCommit(true); } catch(SQLException ex) {}
        }

        reflowDay(newDate);
        return true;
    }

    public static boolean updateScheduleStartAndEnd(int scheduleId, LocalDate date, LocalTime startTime, LocalTime endTime) {
        if (!DatabaseManager.tableExists("schedule"))
            return false;

        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);
            try {
                String sql = "UPDATE schedule SET schedule_date = ?, start_time = ?, end_time = ?, day_of_week = ? WHERE id = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, date.toString());
                    pstmt.setString(2, startTime.toString());
                    pstmt.setString(3, endTime.toString());
                    pstmt.setString(4, date.getDayOfWeek().toString());
                    pstmt.setInt(5, scheduleId);
                    pstmt.executeUpdate();
                }
                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                e.printStackTrace();
                return false;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean updateScheduleStatus(int scheduleId, String status) {
        if (!DatabaseManager.tableExists("schedule"))
            return false;

        String sql = "UPDATE schedule SET status = ? WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, status);
            pstmt.setInt(2, scheduleId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean updateScheduleItemTime(int scheduleId, LocalTime newStartTime) {
        if (!DatabaseManager.tableExists("schedule"))
            return false;

        String sql = "UPDATE schedule SET start_time = ?, end_time = ? WHERE id = ?";
        try {
            Connection conn = DatabaseManager.getConnection();
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {

                String durationSql = "SELECT CASE WHEN s.duration > 0 THEN s.duration ELSE v.duration END as effective_duration " +
                                     "FROM schedule s JOIN video v ON s.video_id = v.id WHERE s.id = ?";
                int durationSeconds = 0;
                try (PreparedStatement durStmt = conn.prepareStatement(durationSql)) {
                    durStmt.setInt(1, scheduleId);
                    try (ResultSet rs = durStmt.executeQuery()) {
                        if (rs.next()) {
                            durationSeconds = rs.getInt("effective_duration");
                        }
                    }
                }

                LocalTime endTime = calculateEndTime(newStartTime, durationSeconds);

                pstmt.setString(1, newStartTime.toString());
                pstmt.setString(2, endTime.toString());
                pstmt.setInt(3, scheduleId);

                return pstmt.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean deleteScheduleItem(int scheduleId) {
        if (!DatabaseManager.tableExists("schedule"))
            return false;
        String sql = "DELETE FROM schedule WHERE id = ?";
        try {
            Connection conn = DatabaseManager.getConnection();
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, scheduleId);
                return pstmt.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean insertScheduleItemAtIndex(int scheduleId, LocalDate newDate, int insertIndex) {
        if (!DatabaseManager.tableExists("schedule"))
            return false;

        // Perform read queries BEFORE opening the transaction to prevent singleton connection closure
        ScheduleItem draggedItem = getScheduleItemById(scheduleId);
        if (draggedItem == null) return false;
        
        LocalDate oldDate = draggedItem.getScheduleDate();
        List<ScheduleItem> items = loadSchedule(newDate, 1);
        
        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Set the new date on the dragged item
                draggedItem.setScheduleDate(newDate);
                
                items.removeIf(it -> it.getId() == scheduleId);
                items.sort(Comparator.comparingInt(it -> getSecondsSinceBroadcastStart(it.getStartTime())));
                
                // Ensure insertIndex is within bounds
                if (insertIndex < 0) insertIndex = 0;
                if (insertIndex > items.size()) insertIndex = items.size();
                
                // Insert the new item into the ordered list
                items.add(insertIndex, draggedItem);
                
                // Recalculate schedule times sequentially starting from BROADCAST_START
                int currentSeconds = 0;
                for (ScheduleItem item : items) {
                    LocalTime newStart = getTimeFromSeconds(currentSeconds);
                    LocalTime newEnd = calculateEndTime(newStart, item.getDurationSeconds());
                    item.setStartTime(newStart);
                    
                    // Temp update to avoid UNIQUE constraints
                    String tempSql = "UPDATE schedule SET start_time = ?, end_time = ?, schedule_date = '2099-12-31', day_of_week = ?, status = ?, duration = ?, is_loop = ?, remarks = ? WHERE id = ?";
                    try (PreparedStatement ts = conn.prepareStatement(tempSql)) {
                        ts.setString(1, newStart.toString());
                        ts.setString(2, newEnd.toString());
                        ts.setString(3, newDate.getDayOfWeek().toString());
                        ts.setString(4, item.getStatus());
                        ts.setInt(5, item.getDurationSeconds());
                        ts.setInt(6, item.isLoop() ? 1 : 0);
                        ts.setString(7, item.getRemarks());
                        ts.setInt(8, item.getId());
                        ts.executeUpdate();
                    }
                    
                    currentSeconds += item.getDurationSeconds();
                }
                
                // Finalize restoration to newDate
                String restoreSql = "UPDATE schedule SET schedule_date = ? WHERE schedule_date = '2099-12-31'";
                try (PreparedStatement rs = conn.prepareStatement(restoreSql)) {
                    rs.setString(1, newDate.toString());
                    rs.executeUpdate();
                }

                conn.commit();

            } catch (SQLException e) {
                conn.rollback();
                e.printStackTrace();
                return false;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }

        // After successful transaction on newDate, if we moved the item across days, reflow the old date's gaps!
        if (oldDate != null && !oldDate.equals(newDate)) {
            reflowDay(oldDate);
        }

        return true;
    }


    public static List<ScheduleItem> loadSchedule(LocalDate startDate, int days) {
        List<ScheduleItem> schedule = new ArrayList<>();
        if (!DatabaseManager.tableExists("schedule"))
            return schedule;

        String sql = """
                    SELECT
                        s.id, s.video_id, v.name as video_name, s.start_time, s.end_time, s.schedule_date,
                        s.day_of_week, s.status, s.is_loop,
                        CASE WHEN s.duration > 0 THEN s.duration ELSE v.duration END as effective_duration,
                        v.description,
                        v.episode_number, se.season_number,
                        ser.name as series_name, ct.name as type_name,
                        v.audio_langs, v.sub_langs, v.has_seasons,
                        COALESCE(s.remarks, v.remarks) as effective_remarks
                    FROM schedule s
                    LEFT JOIN video v ON s.video_id = v.id
                    LEFT JOIN season se ON v.season_id = se.id
                    LEFT JOIN series ser ON se.series_id = ser.id
                    LEFT JOIN content_type ct ON ser.type_id = ct.id
                    WHERE s.schedule_date >= ? AND s.schedule_date < ?
                    ORDER BY s.schedule_date, s.start_time
                """;

        try {
            Connection conn = DatabaseManager.getConnection();
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, startDate.toString());
                pstmt.setString(2, startDate.plusDays(days).toString());
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        ScheduleItem item = new ScheduleItem();
                        item.setId(rs.getInt("id"));
                        item.setVideoId(rs.getInt("video_id"));
                        item.setVideoName(rs.getString("video_name"));
                        item.setSeriesName(rs.getString("series_name"));
                        item.setSeasonNumber(rs.getInt("season_number"));
                        item.setEpisodeNumber(rs.getInt("episode_number"));
                        item.setStartTime(LocalTime.parse(rs.getString("start_time")));
                        item.setDurationSeconds(rs.getInt("effective_duration"));
                        item.setScheduleDate(LocalDate.parse(rs.getString("schedule_date")));
                        item.setDayOfWeek(rs.getString("day_of_week"));
                        item.setStatus(rs.getString("status"));
                        item.setLoop(rs.getInt("is_loop") == 1);
                        item.setVideoType(rs.getString("type_name"));
                        item.setDescription(rs.getString("description"));
                        item.setAudioLangs(rs.getString("audio_langs"));
                        item.setSubLangs(rs.getString("sub_langs"));
                        item.setHasSeasons(rs.getInt("has_seasons") == 1);
                        item.setRemarks(rs.getString("effective_remarks"));
                        schedule.add(item);
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error loading schedule: " + e.getMessage());
        }
        return schedule;
    }

    public static List<ScheduleItem> getSchedulesForDay(LocalDate date) {
        return loadSchedule(date, 1);
    }

    public static String formatDuration(int seconds) {
        int hours = seconds / 3600;
        int minutes = (seconds % 3600) / 60;
        int secs = seconds % 60;

        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, secs);
        } else {
            return String.format("%d:%02d", minutes, secs);
        }
    }

    // Clear all schedule items for a specific day
    public static boolean clearDaySchedule(LocalDate date) {
        if (!DatabaseManager.tableExists("schedule"))
            return false;
        String sql = "DELETE FROM schedule WHERE schedule_date = ?";
        try {
            Connection conn = DatabaseManager.getConnection();
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, date.toString());
                int deleted = pstmt.executeUpdate();
                System.out.println("Cleared " + deleted + " items from " + date);
                return true;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static void reflowDay(LocalDate date) {
        java.util.List<ScheduleItem> items = loadSchedule(date, 1);
        if (items != null && !items.isEmpty()) {
            items.sort(java.util.Comparator.comparingInt(item -> getSecondsSinceBroadcastStart(item.getStartTime())));
            
            try {
                Connection conn = DatabaseManager.getConnection();
                conn.setAutoCommit(false);
                try {
                    int currentSeconds = 0;
                    for (ScheduleItem item : items) {
                        LocalTime newStart = getTimeFromSeconds(currentSeconds);
                        LocalTime endTime = calculateEndTime(newStart, item.getDurationSeconds());
                        
                        // Use dummy date to avoid UNIQUE constraint conflicts during bulk update
                        String sql = "UPDATE schedule SET start_time = ?, end_time = ?, schedule_date = '2099-12-31', duration = ?, is_loop = ?, remarks = ? WHERE id = ?";
                        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                            pstmt.setString(1, newStart.toString());
                            pstmt.setString(2, endTime.toString());
                            pstmt.setInt(3, item.getDurationSeconds());
                            pstmt.setInt(4, item.isLoop() ? 1 : 0);
                            pstmt.setString(5, item.getRemarks());
                            pstmt.setInt(6, item.getId());
                            pstmt.executeUpdate();
                        }
                        currentSeconds += item.getDurationSeconds();
                    }
                    
                    String restoreSql = "UPDATE schedule SET schedule_date = ? WHERE schedule_date = '2099-12-31'";
                    try (PreparedStatement restoreStmt = conn.prepareStatement(restoreSql)) {
                        restoreStmt.setString(1, date.toString());
                        restoreStmt.executeUpdate();
                    }

                    conn.commit();
                } catch (SQLException e) {
                    if (conn != null) conn.rollback();
                    e.printStackTrace();
                } finally {
                    if (conn != null) conn.setAutoCommit(true);
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    public static boolean insertNewScheduleItemAtIndex(ScheduleItem newItem, LocalDate newDate, int insertIndex) {
        if (!DatabaseManager.tableExists("schedule"))
            return false;

        // Perform read queries BEFORE opening the transaction to prevent singleton connection closure
        List<ScheduleItem> items = loadSchedule(newDate, 1);

        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // save it with dummy date to avoid conflict
                String sql = "INSERT INTO schedule (video_id, start_time, end_time, schedule_date, day_of_week, status) VALUES (?, '00:00:00', '00:00:00', '2099-12-30', 'Monday', ?)";
                int newId = -1;
                try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    pstmt.setInt(1, newItem.getVideoId());
                    pstmt.setString(2, newItem.getStatus());
                    pstmt.executeUpdate();
                    ResultSet rs = pstmt.getGeneratedKeys();
                    if (rs.next()) {
                        newId = rs.getInt(1);
                    }
                }
                
                if (newId == -1) {
                    conn.rollback();
                    return false;
                }
                newItem.setId(newId);
                newItem.setScheduleDate(newDate);
                
                items.sort(Comparator.comparingInt(it -> getSecondsSinceBroadcastStart(it.getStartTime())));
                
                if (insertIndex < 0) insertIndex = 0;
                if (insertIndex > items.size()) insertIndex = items.size();
                items.add(insertIndex, newItem);
                
                int currentSeconds = 0;
                for (ScheduleItem item : items) {
                    LocalTime newStart = getTimeFromSeconds(currentSeconds);
                    LocalTime newEnd = calculateEndTime(newStart, item.getDurationSeconds());
                    
                    String tempSql = "UPDATE schedule SET start_time = ?, end_time = ?, schedule_date = '2099-12-31', day_of_week = ?, duration = ?, is_loop = ? WHERE id = ?";
                    try (PreparedStatement ts = conn.prepareStatement(tempSql)) {
                        ts.setString(1, newStart.toString());
                        ts.setString(2, newEnd.toString());
                        ts.setString(3, newDate.getDayOfWeek().toString());
                        ts.setInt(4, item.getDurationSeconds());
                        ts.setInt(5, item.isLoop() ? 1 : 0);
                        ts.setInt(6, item.getId());
                        ts.executeUpdate();
                    }
                    currentSeconds += item.getDurationSeconds();
                }
                
                String restoreSql = "UPDATE schedule SET schedule_date = ? WHERE schedule_date = '2099-12-31'";
                try (PreparedStatement rs = conn.prepareStatement(restoreSql)) {
                    rs.setString(1, newDate.toString());
                    rs.executeUpdate();
                }

                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                e.printStackTrace();
                return false;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static ScheduleItem getScheduleItemById(int scheduleId) {
        if (!DatabaseManager.tableExists("schedule")) return null;
        String sql = """
                    SELECT
                        s.id, s.video_id, s.start_time, s.end_time, s.schedule_date,
                        s.day_of_week, s.status, s.is_loop,
                        CASE WHEN s.duration > 0 THEN s.duration ELSE v.duration END as effective_duration,
                        v.name as video_name, v.description,
                        v.episode_number, se.season_number,
                        ser.name as series_name, ct.name as type_name,
                        v.audio_langs, v.sub_langs, v.has_seasons,
                        COALESCE(s.remarks, v.remarks) as effective_remarks
                    FROM schedule s
                    LEFT JOIN video v ON s.video_id = v.id
                    LEFT JOIN season se ON v.season_id = se.id
                    LEFT JOIN series ser ON se.series_id = ser.id
                    LEFT JOIN content_type ct ON ser.type_id = ct.id
                    WHERE s.id = ?
                """;
        try {
            Connection conn = DatabaseManager.getConnection();
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, scheduleId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        ScheduleItem item = new ScheduleItem();
                        item.setId(rs.getInt("id"));
                        item.setVideoId(rs.getInt("video_id"));
                        item.setVideoName(rs.getString("video_name"));
                        item.setSeriesName(rs.getString("series_name"));
                        item.setSeasonNumber(rs.getInt("season_number"));
                        item.setEpisodeNumber(rs.getInt("episode_number"));
                        item.setStartTime(LocalTime.parse(rs.getString("start_time")));
                        item.setDurationSeconds(rs.getInt("effective_duration"));
                        item.setScheduleDate(LocalDate.parse(rs.getString("schedule_date")));
                        item.setDayOfWeek(rs.getString("day_of_week"));
                        item.setStatus(rs.getString("status"));
                        item.setLoop(rs.getInt("is_loop") == 1);
                        item.setVideoType(rs.getString("type_name"));
                        item.setDescription(rs.getString("description"));
                        item.setAudioLangs(rs.getString("audio_langs"));
                        item.setSubLangs(rs.getString("sub_langs"));
                        item.setHasSeasons(rs.getInt("has_seasons") == 1);
                        item.setRemarks(rs.getString("effective_remarks"));
                        return item;
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static boolean loopScheduleItem(int scheduleId, LocalTime targetEndTime) {
        if (!DatabaseManager.tableExists("schedule")) return false;

        ScheduleItem originalItem = getScheduleItemById(scheduleId);
        if (originalItem == null) return false;

        int targetSec = getSecondsSinceBroadcastStart(targetEndTime);
        int startSec = getSecondsSinceBroadcastStart(originalItem.getStartTime());
        int newDuration = targetSec - startSec;

        if (newDuration <= originalItem.getDurationSeconds()) return true; // Already reaches or passes target

        // One Big Box approach: Simply update the duration and set is_loop = 1, remarks = 'ON LOOP'
        String sql = "UPDATE schedule SET duration = ?, end_time = ?, is_loop = 1, remarks = 'ON LOOP' WHERE id = ?";
        try {
            Connection conn = DatabaseManager.getConnection();
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, newDuration);
                pstmt.setString(2, targetEndTime.toString());
                pstmt.setInt(3, scheduleId);
                
                if (pstmt.executeUpdate() > 0) {
                    reflowDay(originalItem.getScheduleDate());
                    return true;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }
}

// ScheduleManager