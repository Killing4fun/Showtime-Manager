package showtime.manager.utils;

import javax.swing.*;
import java.awt.datatransfer.*;
import java.io.IOException;
import java.time.LocalTime;
import java.time.LocalDate;
import java.util.*;
import showtime.manager.model.ScheduleItem;
import showtime.manager.model.VideoItem;
import showtime.manager.model.DatabaseManager;

public class ScheduleTransferHandler extends TransferHandler {

    private ShowtimeTableModel model;
    private ScheduleItem draggedItem;

    public ScheduleTransferHandler(ShowtimeTableModel model) {
        this.model = model;
    }

    public void setDraggedItem(ScheduleItem item) {
        this.draggedItem = item;
    }

    @Override
    protected Transferable createTransferable(JComponent c) {
        if (draggedItem != null) {
            String data = "SCHEDULE_MOV:" + draggedItem.getId() + ":" + draggedItem.getDurationSeconds();
            return new StringSelection(data);
        }
        return null;
    }

    @Override
    public int getSourceActions(JComponent c) {
        return MOVE;
    }

    @Override
    protected void exportDone(JComponent source, Transferable data, int action) {
        super.exportDone(source, data, action);
        draggedItem = null;
        model.loadScheduleForWeek();
    }

    // Only allow drops from outside (e.g., folder tree)
    @Override
    public boolean canImport(TransferSupport support) {
        if (!support.isDrop())
            return false;
        if (!support.isDataFlavorSupported(DataFlavor.stringFlavor))
            return false;
        JTable.DropLocation dl = (JTable.DropLocation) support.getDropLocation();
        return dl.getRow() >= 0 && dl.getColumn() == 0; // Timeline is now column 0
    }

    @Override
    public boolean importData(TransferSupport support) {
        if (!canImport(support))
            return false;
        try {
            JTable.DropLocation dl = (JTable.DropLocation) support.getDropLocation();
            int targetRow = dl.getRow();
            int clickX = dl.getDropPoint().x;
            String data = (String) support.getTransferable().getTransferData(DataFlavor.stringFlavor);
            System.out.println("Importing: " + data);

            if (data.startsWith("SCHEDULE_MOV:")) {
                String[] parts = data.split(":");
                if (parts.length >= 3) {
                    int scheduleId = Integer.parseInt(parts[1]);

                    LocalDate newDate = model.getDateAtRow(targetRow);
                    if (newDate == null)
                        return false;

                    int insertIndex = getInsertIndex(newDate, clickX, scheduleId);

                    boolean success = ScheduleManager.insertScheduleItemAtIndex(scheduleId, newDate, insertIndex);
                    if (success) {
                        model.loadScheduleForWeek();
                        return true;
                    }
                }
            } else if (data.startsWith("VIDEO:")) {
                String[] parts = data.split(":");
                if (parts.length >= 7) {
                    int videoId = Integer.parseInt(parts[1]);
                    String videoName = parts[2].replace("\\:", ":");
                    String seriesName = parts[3].replace("\\:", ":");
                    int seasonNumber = Integer.parseInt(parts[4]);
                    int episodeNumber = Integer.parseInt(parts[5]);
                    int durationSeconds = Integer.parseInt(parts[6]);

                    LocalDate scheduleDate = model.getDateAtRow(targetRow);
                    if (scheduleDate == null)
                        return false;

                    int insertIndex = getInsertIndex(scheduleDate, clickX, -1);

                    ScheduleItem item = createScheduleItem(videoId, videoName, seriesName,
                            seasonNumber, episodeNumber, scheduleDate, LocalTime.of(0,0), durationSeconds);
                            
                    boolean success = ScheduleManager.insertNewScheduleItemAtIndex(item, scheduleDate, insertIndex);
                    if (success) {
                        model.loadScheduleForWeek();
                        JOptionPane.showMessageDialog(null,
                                "Successfully scheduled the video.",
                                "Success",
                                JOptionPane.INFORMATION_MESSAGE);
                        return true;
                    }
                }
            }
        } catch (UnsupportedFlavorException | IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    // -------------------- Helper Methods --------------------

    private int getInsertIndex(LocalDate date, int clickX, int ignoreId) {
        List<ScheduleItem> items = new ArrayList<>(model.getItemsForDate(date));
        if (ignoreId > 0) {
            items.removeIf(it -> it.getId() == ignoreId);
        }
        items.sort(Comparator.comparingInt(it -> ScheduleManager.getSecondsSinceBroadcastStart(it.getStartTime())));

        int currentX = 5;
        double pixelsPerSecond = 200.0 / 3600.0;
        int insertIndex = items.size();

        for (int i = 0; i < items.size(); i++) {
            ScheduleItem item = items.get(i);
            int blockWidth = (int) (item.getDurationSeconds() * pixelsPerSecond);
            if (blockWidth < 40)
                blockWidth = 40;

            int midPoint = currentX + (blockWidth / 2);
            if (clickX < midPoint) {
                insertIndex = i;
                break;
            }
            currentX += blockWidth + 4;
        }

        return insertIndex;
    }

    private ScheduleItem createScheduleItem(int videoId, String videoName, String seriesName,
            int seasonNumber, int episodeNumber,
            LocalDate date, LocalTime startTime, int durationSeconds) {
        ScheduleItem item = new ScheduleItem();
        item.setVideoId(videoId);
        item.setVideoName(videoName);
        item.setSeriesName(seriesName);
        item.setSeasonNumber(seasonNumber);
        item.setEpisodeNumber(episodeNumber);
        item.setStartTime(startTime);
        item.setDurationSeconds(durationSeconds);
        item.setScheduleDate(date);
        item.setDayOfWeek(date.getDayOfWeek().toString());
        item.setStatus("Scheduled");

        // Copy metadata
        List<VideoItem> videos = DatabaseManager.getAllVideos();
        for (VideoItem v : videos) {
            if (v.getId() == videoId) {
                item.setVideoType(v.getType());
                item.setDescription(v.getDescription());
                item.setAudioLangs(v.getAudioLangs());
                item.setSubLangs(v.getSubLangs());
                item.setRemarks(v.getRemarks());
                break;
            }
        }
        return item;
    }
}

// ScheduleTransferHandler