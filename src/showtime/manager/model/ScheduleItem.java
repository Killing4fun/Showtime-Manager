package showtime.manager.model;

import java.time.LocalTime;
import java.time.LocalDate;

public class ScheduleItem {
    private int id;
    private int videoId;
    private String videoName;
    private String seriesName;
    private int seasonNumber;
    private int episodeNumber;
    private LocalTime startTime;
    private LocalTime endTime;
    private LocalDate scheduleDate;
    private String dayOfWeek;
    private int durationSeconds;
    private String status;
    private String videoType;
    private String description;
    private String audioLangs;
    private String subLangs;
    private String remarks;
    private boolean isLoop;
    private boolean hasSeasons = true; // Default to true

    public ScheduleItem() {
    }

    public ScheduleItem(int videoId, String videoName, String seriesName,
            int seasonNumber, int episodeNumber,
            LocalTime startTime, int durationSeconds) {
        this.videoId = videoId;
        this.videoName = videoName;
        this.seriesName = seriesName;
        this.seasonNumber = seasonNumber;
        this.episodeNumber = episodeNumber;
        this.startTime = startTime;
        this.durationSeconds = durationSeconds;
        this.endTime = startTime.plusSeconds(durationSeconds);
        this.status = "Scheduled";
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getVideoId() {
        return videoId;
    }

    public void setVideoId(int videoId) {
        this.videoId = videoId;
    }

    public String getVideoName() {
        return videoName;
    }

    public void setVideoName(String videoName) {
        this.videoName = videoName;
    }

    public String getSeriesName() {
        return seriesName;
    }

    public void setSeriesName(String seriesName) {
        this.seriesName = seriesName;
    }

    public int getSeasonNumber() {
        return seasonNumber;
    }

    public void setSeasonNumber(int seasonNumber) {
        this.seasonNumber = seasonNumber;
    }

    public int getEpisodeNumber() {
        return episodeNumber;
    }

    public void setEpisodeNumber(int episodeNumber) {
        this.episodeNumber = episodeNumber;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
        if (durationSeconds > 0) {
            this.endTime = startTime.plusSeconds(durationSeconds);
        }
    }

    public LocalTime getEndTime() {
        if (endTime == null && startTime != null && durationSeconds > 0) {
            endTime = startTime.plusSeconds(durationSeconds);
        }
        return endTime;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(int durationSeconds) {
        this.durationSeconds = durationSeconds;
        if (startTime != null) {
            this.endTime = startTime.plusSeconds(durationSeconds);
        }
    }

    public LocalDate getScheduleDate() {
        return scheduleDate;
    }

    public void setScheduleDate(LocalDate scheduleDate) {
        this.scheduleDate = scheduleDate;
    }

    public String getDayOfWeek() {
        return dayOfWeek;
    }

    public void setDayOfWeek(String dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getVideoType() {
        return videoType;
    }

    public void setVideoType(String videoType) {
        this.videoType = videoType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getAudioLangs() {
        return audioLangs;
    }

    public void setAudioLangs(String audioLangs) {
        this.audioLangs = audioLangs;
    }

    public String getSubLangs() {
        return subLangs;
    }

    public void setSubLangs(String subLangs) {
        this.subLangs = subLangs;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    public boolean isLoop() {
        return isLoop;
    }

    public void setLoop(boolean isLoop) {
        this.isLoop = isLoop;
    }

    public boolean hasSeasons() {
        return hasSeasons;
    }

    public void setHasSeasons(boolean hasSeasons) {
        this.hasSeasons = hasSeasons;
    }

    public String getDisplayName() {
        if (hasSeasons && seasonNumber > 0 && episodeNumber > 0) {
            return String.format("%s S%dE%d - %s", seriesName, seasonNumber, episodeNumber, videoName);
        } else {
            return videoName;
        }
    }

    public String getFormattedTimeRange() {
        if (startTime == null || endTime == null)
            return "";
        return String.format("%02d:%02d - %02d:%02d",
                startTime.getHour(), startTime.getMinute(),
                endTime.getHour(), endTime.getMinute());
    }

    @Override
    public String toString() {
        return String.format("%s - %s (%s)",
                scheduleDate, getDisplayName(), getFormattedTimeRange());
    }
}

// ScedualItem