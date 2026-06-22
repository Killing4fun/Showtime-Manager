package showtime.manager.model;

public class VideoItem {
    private int id;
    private String type;
    private String seriesName;
    private int seasonNumber;
    private int episodeNumber;
    private String videoName;
    private int durationSeconds;
    private String description;
    private String uploadDate;
    private String audioLangs;
    private String subLangs;
    private String remarks;
    private boolean hasSeasons = true; // Default to true for existing data

    public VideoItem() {
    }

    public VideoItem(String type, String seriesName, int seasonNumber,
            int episodeNumber, String videoName, int durationSeconds, String description,
            String audioLangs, String subLangs, String remarks, boolean hasSeasons) {
        this.type = type;
        this.seriesName = seriesName;
        this.seasonNumber = seasonNumber;
        this.episodeNumber = episodeNumber;
        this.videoName = videoName;
        this.durationSeconds = durationSeconds;
        this.description = description;
        this.audioLangs = audioLangs;
        this.subLangs = subLangs;
        this.remarks = remarks;
        this.hasSeasons = hasSeasons;
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
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

    public String getVideoName() {
        return videoName;
    }

    public void setVideoName(String videoName) {
        this.videoName = videoName;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(int durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public String getUploadDate() {
        return uploadDate;
    }

    public void setUploadDate(String uploadDate) {
        this.uploadDate = uploadDate;
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

    public boolean hasSeasons() {
        return hasSeasons;
    }

    public void setHasSeasons(boolean hasSeasons) {
        this.hasSeasons = hasSeasons;
    }

    public int getDuration() {
        return durationSeconds;
    }

    public void setDuration(int duration) {
        this.durationSeconds = duration;
    }

    public String getFormattedDuration() {
        int hours = durationSeconds / 3600;
        int minutes = (durationSeconds % 3600) / 60;
        int seconds = durationSeconds % 60;

        if (hours > 0) {
            return String.format("%d hour %d minit %d second", hours, minutes, seconds);
        } else {
            return String.format("%d minit %d second", minutes, seconds);
        }
    }

    @Override
    public String toString() {
        if (type.equals("Movie")) {
            return videoName + " (" + getFormattedDuration() + ")";
        } else {
            return seriesName + " S" + seasonNumber + "E" + episodeNumber +
                    " - " + videoName + " (" + getFormattedDuration() + ")";
        }
    }
}

// VideoItem