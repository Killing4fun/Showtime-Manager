package showtime.manager.view;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.*;
import java.util.List;
import javax.swing.*;
import javax.swing.tree.*;
import showtime.manager.model.DatabaseManager;
import showtime.manager.model.VideoItem;

public class FolderPanel extends JPanel {
    private JTree folderTree;
    private DefaultTreeModel treeModel;
    private DefaultMutableTreeNode rootNode;
    private JPopupMenu rightClickMenu;
    private JMenuItem editItem;
    private JMenuItem deleteItem;
    private JMenuItem deleteSeriesItem;
    private JMenuItem viewItem;
    private JMenuItem addEpisodeItem;

    // Callback interface for details panel
    private DetailsCallback detailsCallback;

    public interface DetailsCallback {
        void onItemSelected(String title, String details);
    }

    // Callback fired when a video is saved/edited (so Main can refresh the schedule table)
    private ScheduleSavedCallback scheduleSavedCallback;

    public interface ScheduleSavedCallback {
        void onScheduleNeedsRefresh();
    }

    public void setScheduleSavedCallback(ScheduleSavedCallback callback) {
        this.scheduleSavedCallback = callback;
    }

    public FolderPanel() {
        initialize();
    }

    public void setDetailsCallback(DetailsCallback callback) {
        this.detailsCallback = callback;
    }

    private void initialize() {
        setLayout(new BorderLayout());

        // Create invisible root node
        rootNode = new DefaultMutableTreeNode("Root");
        treeModel = new DefaultTreeModel(rootNode);

        // Create tree
        folderTree = new JTree(treeModel);
        folderTree.setRootVisible(false);
        folderTree.setShowsRootHandles(true);
        folderTree.setRowHeight(20);

        // Create right-click menu
        createRightClickMenu();

        // Add mouse listener for right-click
        folderTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showRightClickMenu(e);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showRightClickMenu(e);
                }
            }

            private void showRightClickMenu(MouseEvent e) {
                int row = folderTree.getClosestRowForLocation(e.getX(), e.getY());
                folderTree.setSelectionRow(row);

                DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) folderTree
                        .getLastSelectedPathComponent();
                if (selectedNode != null) {
                    configureMenuForNode(selectedNode);
                    rightClickMenu.show(e.getComponent(), e.getX(), e.getY());
                }
            }
        });

        // Add selection listener for details panel
        folderTree.addTreeSelectionListener(e -> {
            DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) folderTree.getLastSelectedPathComponent();
            if (selectedNode != null) {
                updateDetailsPanel(selectedNode);
            }
        });

        // Keyboard shortcuts on the tree
        folderTree.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyPressed(java.awt.event.KeyEvent e) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) folderTree.getLastSelectedPathComponent();
                if (node == null) return;
                int code = e.getKeyCode();

                // Enter / F2 – Edit episode, or show info for series/season
                if (code == java.awt.event.KeyEvent.VK_ENTER || code == java.awt.event.KeyEvent.VK_F2) {
                    if (node instanceof VideoNode) {
                        editSelectedItem();
                    } else {
                        viewSelectedItem();
                    }
                    e.consume();
                }

                // Delete – Remove selected episode or movie
                if (code == java.awt.event.KeyEvent.VK_DELETE) {
                    if (node instanceof VideoNode) {
                        deleteSelectedEpisode();
                    } else if (node.getLevel() == 2) {
                        deleteSelectedSeries();
                    }
                    e.consume();
                }
            }
        });

        // Setup Drag and Drop from Tree
        folderTree.setDragEnabled(true);
        folderTree.setTransferHandler(new TransferHandler() {
            @Override
            public int getSourceActions(JComponent c) {
                return COPY_OR_MOVE;
            }

            @Override
            protected Transferable createTransferable(JComponent c) {
                JTree tree = (JTree) c;
                DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();

                if (selectedNode == null) {
                    return null;
                }

                // Check if it's a VideoNode (episode or movie)
                if (selectedNode instanceof VideoNode) {
                    VideoItem video = ((VideoNode) selectedNode).getVideo();

                    // Get series name properly
                    String seriesName = video.getSeriesName();
                    if (seriesName == null || seriesName.isEmpty()) {
                        // Try to get from parent nodes
                        DefaultMutableTreeNode parent = (DefaultMutableTreeNode) selectedNode.getParent();
                        if (parent != null && parent.getParent() != null) {
                            DefaultMutableTreeNode seriesNode = (DefaultMutableTreeNode) parent.getParent();
                            seriesName = seriesNode.getUserObject().toString();
                        }
                    }

                    // Create data string with ALL required fields
                    // Format: VIDEO:videoId:videoName:seriesName:seasonNumber:episodeNumber:duration
                    String data = String.format("VIDEO:%d:%s:%s:%d:%d:%d",
                            video.getId(),
                            video.getVideoName().replace(":", "\\:"),
                            (seriesName != null ? seriesName.replace(":", "\\:") : ""),
                            video.getSeasonNumber(),
                            video.getEpisodeNumber(),
                            video.getDurationSeconds());

                    System.out.println("Creating transferable for: " + data);
                    return new StringSelection(data);
                }

                return null;
            }

            @Override
            protected void exportDone(JComponent source, Transferable data, int action) {
                System.out.println("Export done with action: " + action);
            }
        });

        // Add scroll pane
        JScrollPane scrollPane = new JScrollPane(folderTree);
        add(scrollPane, BorderLayout.CENTER);

        // Load data
        refreshTree();
    }

    private void updateDetailsPanel(DefaultMutableTreeNode node) {
        if (detailsCallback == null)
            return;

        int level = node.getLevel();
        String nodeText = node.getUserObject().toString();
        String path = getSelectedPath();

        if (level == 2) { // Series
            String title = "📺 Series: " + nodeText;
            String details = buildSeriesDetails(nodeText, path);
            detailsCallback.onItemSelected(title, details);

        } else if (level == 3) { // Season
            DefaultMutableTreeNode seriesNode = (DefaultMutableTreeNode) node.getParent();
            String seriesName = seriesNode != null ? seriesNode.getUserObject().toString() : "Unknown";
            String seasonNumber = nodeText.replace("S", "");
            int episodeCount = getEpisodeCountForSeason(seriesName, seasonNumber);

            String title = "🎬 " + seriesName + " - " + nodeText;
            String details = buildSeasonDetails(seriesName, seasonNumber, episodeCount, path);
            detailsCallback.onItemSelected(title, details);

        } else if (level >= 4 && node instanceof VideoNode) { // Episode
            VideoNode videoNode = (VideoNode) node;
            VideoItem video = videoNode.getVideo();

            DefaultMutableTreeNode seasonNode = (DefaultMutableTreeNode) node.getParent();
            DefaultMutableTreeNode seriesNode = (DefaultMutableTreeNode) seasonNode.getParent();
            String seriesName = seriesNode != null ? seriesNode.getUserObject().toString() : video.getSeriesName();

            String title = "🎬 " + video.getVideoName();
            String details = buildEpisodeDetails(video, seriesName, path);
            detailsCallback.onItemSelected(title, details);

        } else if (level == 1) { // Type
            String title = "📁 Content Type: " + nodeText;
            int typeId = DatabaseManager.getTypeId(nodeText);
            List<String> seriesList = DatabaseManager.getSeriesByType(typeId);
            int seriesCount = seriesList.size();

            String details = buildTypeDetails(nodeText, seriesCount, path);
            detailsCallback.onItemSelected(title, details);
        }
    }

    private String buildSeriesDetails(String seriesName, String path) {
        return "<html>" +
                "<head><style>" +
                ".title { color: #00D1FF; font-size: 18px; font-weight: bold; margin: 0 0 5px 0; }" +
                ".hr { border: none; border-top: 1px solid #444; margin: 10px 0; }" +
                ".info-table { width: 100%; border-collapse: collapse; font-size: 12px; }" +
                ".info-table td { padding: 4px 0; }" +
                ".label { color: #B0B0B0; width: 100px; }" +
                ".value { color: #E0E0E0; }" +
                ".info-box { background-color: #252525; border: 1px solid #333; border-radius: 4px; padding: 12px; margin-top: 10px; }"
                +
                "</style></head>" +
                "<body>" +
                "<div class='title'>📺 Series: " + seriesName + "</div>" +
                "<hr class='hr'>" +
                "<table class='info-table'>" +
                "<tr><td class='label'>Type:</td><td class='value'>Series</td></tr>" +
                "<tr><td class='label'>Name:</td><td class='value'>" + seriesName + "</td></tr>" +
                "<tr><td class='label'>Path:</td><td class='value'><code style='font-size:11px; color:#00D1FF;'>" + path
                + "</code></td></tr>" +
                "<tr><td class='label'>Status:</td><td class='value'><span style='color:#4CAF50;'>● Active</span></td></tr>"
                +
                "</table>" +
                "<div class='info-box'>" +
                "<b style='color:#B0B0B0;'>📋 Series Information:</b><br>" +
                "<span style='color:#9E9E9E;'>This series contains all episodes organized by season.</span>" +
                "</div>" +
                "</body></html>";
    }

    private String buildSeasonDetails(String seriesName, String seasonNumber, int episodeCount, String path) {
        return "<html>" +
                "<head><style>" +
                ".title { color: #00D1FF; font-size: 18px; font-weight: bold; margin: 0 0 5px 0; }" +
                ".hr { border: none; border-top: 1px solid #444; margin: 10px 0; }" +
                ".info-table { width: 100%; border-collapse: collapse; font-size: 12px; }" +
                ".info-table td { padding: 4px 0; }" +
                ".label { color: #B0B0B0; width: 100px; }" +
                ".value { color: #E0E0E0; }" +
                ".info-box { background-color: #252525; border: 1px solid #333; border-radius: 4px; padding: 12px; margin-top: 10px; }"
                +
                "</style></head>" +
                "<body>" +
                "<div class='title'>🎬 " + seriesName + " - Season " + seasonNumber + "</div>" +
                "<hr class='hr'>" +
                "<table class='info-table'>" +
                "<tr><td class='label'>Series:</td><td class='value'>" + seriesName + "</td></tr>" +
                "<tr><td class='label'>Season:</td><td class='value'>" + seasonNumber + "</td></tr>" +
                "<tr><td class='label'>Episodes:</td><td class='value'>" + episodeCount + " episodes</td></tr>" +
                "<tr><td class='label'>Path:</td><td class='value'><code style='font-size:11px; color:#00D1FF;'>" + path
                + "</code></td></tr>" +
                "</table>" +
                "<div class='info-box'>" +
                "<b style='color:#B0B0B0;'>📊 Season Statistics:</b><br>" +
                "<span style='color:#9E9E9E;'>This season contains " + episodeCount
                + " episode(s). Expand to view individual episodes.</span>" +
                "</div>" +
                "</body></html>";
    }

    private String buildEpisodeDetails(VideoItem video, String seriesName, String path) {
        String durationFormatted = video.getFormattedDuration();
        String uploadDate = video.getUploadDate() != null ? video.getUploadDate() : "Not available";

        String details = "<html>" +
                "<head><style>" +
                ".title { color: #00D1FF; font-size: 18px; font-weight: bold; margin: 0 0 5px 0; }" +
                ".episode-badge { background-color: #333; color: #00D1FF; padding: 2px 8px; border-radius: 12px; border: 1px solid #444; font-size: 11px; display: inline-block; margin-bottom: 8px; }"
                +
                ".hr { border: none; border-top: 1px solid #444; margin: 10px 0; }" +
                ".info-table { width: 100%; border-collapse: collapse; font-size: 12px; }" +
                ".info-table td { padding: 4px 0; }" +
                ".label { color: #B0B0B0; width: 100px; }" +
                ".value { color: #E0E0E0; }" +
                ".info-footer { margin-top: 10px; padding: 8px; background-color: #252525; border-radius: 4px; border: 1px solid #333; font-size: 10px; color: #888; }"
                +
                "</style></head>" +
                "<body>" +
                "<div class='title'>🎬 " + video.getVideoName() + "</div>" +
                "<div class='episode-badge'>S" + video.getSeasonNumber() + " E" + video.getEpisodeNumber() + "</div>" +
                "<hr class='hr'>" +
                "<table class='info-table'>" +
                "<tr><td class='label'>Series:</td><td class='value'><strong>"
                + (seriesName != null ? seriesName : video.getSeriesName()) + "</strong></td></tr>" +
                "<tr><td class='label'>Type:</td><td class='value'>" + video.getType() + "</td></tr>" +
                "<tr><td class='label'>Duration:</td><td class='value'><span style='font-family:monospace;'>"
                + durationFormatted + "</span></td></tr>" +
                "<tr><td class='label'>Upload Date:</td><td class='value'>" + uploadDate + "</td></tr>" +
                "<tr><td class='label'>Path:</td><td class='value'><code style='font-size:10px; color:#00D1FF;'>" + path
                + "</code></td></tr>";

        if (video.getDescription() != null && !video.getDescription().isEmpty()) {
            details += "<tr><td class='label'>Description:</td><td class='value' style='font-style:italic; color:#BBB;'>"
                    + video.getDescription() + "</td></tr>";
        }

        if (video.getAudioLangs() != null && !video.getAudioLangs().isEmpty()) {
            details += "<tr><td class='label'>Audio Langs:</td><td class='value'>" + video.getAudioLangs()
                    + "</td></tr>";
        }

        if (video.getSubLangs() != null && !video.getSubLangs().isEmpty()) {
            details += "<tr><td class='label'>Subtitles:</td><td class='value'>" + video.getSubLangs() + "</td></tr>";
        }

        if (video.getRemarks() != null && !video.getRemarks().isEmpty()) {
            details += "<tr><td class='label'>Remarks:</td><td class='value' style='color:#00adb5;'>"
                    + video.getRemarks() + "</td></tr>";
        }

        details += "</table>" +
                "<div class='info-footer'>" +
                "<b>ID:</b> " + video.getId() + " | <b>Duration (seconds):</b> " + video.getDurationSeconds() +
                "</div>" +
                "</body></html>";

        return details;
    }

    private String buildTypeDetails(String typeName, int seriesCount, String path) {
        return "<html>" +
                "<head><style>" +
                ".title { color: #00D1FF; font-size: 18px; font-weight: bold; margin: 0 0 5px 0; }" +
                ".hr { border: none; border-top: 1px solid #444; margin: 10px 0; }" +
                ".info-table { width: 100%; border-collapse: collapse; font-size: 12px; }" +
                ".info-table td { padding: 4px 0; }" +
                ".label { color: #B0B0B0; width: 100px; }" +
                ".value { color: #E0E0E0; }" +
                ".info-box { background-color: #252525; border: 1px solid #333; border-radius: 4px; padding: 12px; margin-top: 10px; }"
                +
                "</style></head>" +
                "<body>" +
                "<div class='title'>📁 Content Type: " + typeName + "</div>" +
                "<hr class='hr'>" +
                "<table class='info-table'>" +
                "<tr><td class='label'>Category:</td><td class='value'><strong>" + typeName + "</strong></td></tr>" +
                "<tr><td class='label'>Series Count:</td><td class='value'>" + seriesCount + " series</td></tr>" +
                "<tr><td class='label'>Path:</td><td class='value'><code style='font-size:11px; color:#00D1FF;'>" + path
                + "</code></td></tr>" +
                "</table>" +
                "<div class='info-box'>" +
                "<b style='color:#B0B0B0;'>📁 Category Information:</b><br>" +
                "<span style='color:#9E9E9E;'>This category contains all series of type '" + typeName + "'.<br>" +
                "Total series: " + seriesCount + "<br><br>" +
                "Expand to browse available series and episodes.</span>" +
                "</div>" +
                "</body></html>";
    }

    private int getEpisodeCountForSeason(String seriesName, String seasonNumber) {
        try {
            int seasonNum = Integer.parseInt(seasonNumber);
            List<VideoItem> videos = DatabaseManager.getAllVideos();
            int count = 0;
            for (VideoItem video : videos) {
                if (video.getSeriesName() != null &&
                        video.getSeriesName().equals(seriesName) &&
                        video.getSeasonNumber() == seasonNum) {
                    count++;
                }
            }
            return count;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void createRightClickMenu() {
        rightClickMenu = new JPopupMenu();

        viewItem = new JMenuItem("View Details");
        editItem = new JMenuItem("Edit");
        deleteItem = new JMenuItem("Delete Episode");
        deleteSeriesItem = new JMenuItem("Delete Series");
        addEpisodeItem = new JMenuItem("Add Episode to Series");

        viewItem.addActionListener(e -> viewSelectedItem());
        editItem.addActionListener(e -> editSelectedItem());
        deleteItem.addActionListener(e -> deleteSelectedEpisode());
        deleteSeriesItem.addActionListener(e -> deleteSelectedSeries());
        addEpisodeItem.addActionListener(e -> addEpisodeToSeries());

        rightClickMenu.add(viewItem);
        rightClickMenu.add(editItem);
        rightClickMenu.addSeparator();
        rightClickMenu.add(deleteItem);
        rightClickMenu.add(deleteSeriesItem);
        rightClickMenu.addSeparator();
        rightClickMenu.add(addEpisodeItem);
    }

    private void configureMenuForNode(DefaultMutableTreeNode node) {
        viewItem.setVisible(false);
        editItem.setVisible(false);
        deleteItem.setVisible(false);
        deleteSeriesItem.setVisible(false);
        addEpisodeItem.setVisible(false);

        int level = node.getLevel();

        if (level == 1) { // Type node
            // No actions for type nodes
        } else if (level == 2) { // Series node
            viewItem.setVisible(true);
            deleteSeriesItem.setVisible(true);
            addEpisodeItem.setVisible(true);
            viewItem.setText("View Series Info");
        } else if (level == 3) { // Season node OR Movie node
            if (node instanceof VideoNode) { // It's a movie
                viewItem.setVisible(true);
                editItem.setVisible(true);
                deleteItem.setVisible(true);
                viewItem.setText("View Movie Details");
                editItem.setText("Edit Movie");
            } else { // It's a season
                viewItem.setVisible(true);
                viewItem.setText("View Season Info");
            }
        } else if (level >= 4) { // Episode node
            viewItem.setVisible(true);
            editItem.setVisible(true);
            deleteItem.setVisible(true);
            viewItem.setText("View Episode Details");
            editItem.setText("Edit Episode");
        }
    }

    private void viewSelectedItem() {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) folderTree.getLastSelectedPathComponent();
        if (node == null)
            return;

        String path = getSelectedPath();
        int level = node.getLevel();
        String nodeText = node.getUserObject().toString();

        if (level == 2) { // Series
            JOptionPane.showMessageDialog(this,
                    "<html><h3>Series Information</h3>" +
                            "<b>Series:</b> " + nodeText + "<br>" +
                            "<b>Path:</b> " + path + "</html>",
                    "Series Details",
                    JOptionPane.INFORMATION_MESSAGE);
        } else if (level == 3) { // Season
            JOptionPane.showMessageDialog(this,
                    "<html><h3>Season Information</h3>" +
                            "<b>Season:</b> " + nodeText + "<br>" +
                            "<b>Path:</b> " + path + "</html>",
                    "Season Details",
                    JOptionPane.INFORMATION_MESSAGE);
        } else if (level >= 4) { // Episode
            JOptionPane.showMessageDialog(this,
                    "<html><h3>Episode Information</h3>" +
                            "<b>Episode:</b> " + nodeText + "<br>" +
                            "<b>Path:</b> " + path + "</html>",
                    "Episode Details",
                    JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void editSelectedItem() {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) folderTree.getLastSelectedPathComponent();
        if (node == null || !(node instanceof VideoNode))
            return;

        VideoItem video = ((VideoNode) node).getVideo();

        // Find the parent frame
        Window window = SwingUtilities.getWindowAncestor(this);
        JFrame parentFrame = (window instanceof JFrame) ? (JFrame) window : null;

        UploadDialog dialog = new UploadDialog(parentFrame, video);
        dialog.setVisible(true);

        if (dialog.isSaved()) {
            refreshTree();
            // Notify Main so it can reload the schedule timetable
            if (scheduleSavedCallback != null) {
                scheduleSavedCallback.onScheduleNeedsRefresh();
            }
        }
    }

    private void deleteSelectedEpisode() {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) folderTree.getLastSelectedPathComponent();
        if (node == null || !(node instanceof VideoNode))
            return;

        VideoItem video = ((VideoNode) node).getVideo();

        int confirm = JOptionPane.showConfirmDialog(this,
                "<html><h3>Delete Episode?</h3>" +
                        "<p>Are you sure you want to delete:<br><b>" + video.getVideoName() + "</b>?</p>" +
                        "<p>This action cannot be undone!</p></html>",
                "Confirm Delete",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            boolean success = DatabaseManager.deleteVideo(video.getId());
            if (success) {
                JOptionPane.showMessageDialog(this, "Episode deleted successfully!", "Deleted", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this, "Failed to delete episode.", "Error", JOptionPane.ERROR_MESSAGE);
            }
            refreshTree();
        }
    }

    private void deleteSelectedSeries() {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) folderTree.getLastSelectedPathComponent();
        if (node == null || node.getLevel() != 2)
            return;

        String seriesName = node.getUserObject().toString();

        // Get the parent type node to resolve series ID
        DefaultMutableTreeNode typeNode = (DefaultMutableTreeNode) node.getParent();
        String typeName = typeNode != null ? typeNode.getUserObject().toString() : "";
        int typeId = DatabaseManager.getTypeId(typeName);
        int seriesId = DatabaseManager.getSeriesId(seriesName, typeId);

        int confirm = JOptionPane.showConfirmDialog(this,
                "<html><h3>⚠️ Delete Entire Series?</h3>" +
                        "<p>You are about to delete the series: <b>" + seriesName + "</b></p>" +
                        "<p><font color='red'>This will delete ALL episodes and schedule entries!</font></p>" +
                        "<p>This action cannot be undone!</p></html>",
                "Confirm Series Deletion",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.ERROR_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            boolean success = DatabaseManager.deleteSeries(seriesId);
            if (success) {
                JOptionPane.showMessageDialog(this, "Series '" + seriesName + "' deleted successfully!", "Deleted", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this, "Failed to delete series.", "Error", JOptionPane.ERROR_MESSAGE);
            }
            refreshTree();
        }
    }

    private void addEpisodeToSeries() {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) folderTree.getLastSelectedPathComponent();
        if (node == null || node.getLevel() != 2)
            return;

        String seriesName = node.getUserObject().toString();
        JOptionPane.showMessageDialog(this,
                "Add episode to: " + seriesName + "\nThis will open the upload dialog.",
                "Add Episode",
                JOptionPane.INFORMATION_MESSAGE);
    }

    public void refreshTree() {
        // Ensure database connection is alive
        DatabaseManager.ensureConnection();

        // Clear existing tree
        rootNode.removeAllChildren();

        if (!DatabaseManager.isConnected()) {
            System.err.println("Database not connected, attempting to reconnect...");
            DatabaseManager.initialize();

            if (!DatabaseManager.isConnected()) {
                DefaultMutableTreeNode errorNode = new DefaultMutableTreeNode(
                        "⚠️ No Database Connection - Please restart");
                rootNode.add(errorNode);
                treeModel.reload();
                return;
            }
        }

        // Load videos
        List<VideoItem> videos = DatabaseManager.getAllVideos();
        System.out.println("RefreshTree: Loaded " + videos.size() + " videos");

        if (videos.isEmpty()) {
            DefaultMutableTreeNode emptyNode = new DefaultMutableTreeNode("📂 No Videos - Click Upload to add");
            rootNode.add(emptyNode);
        } else {
            buildTreeFromVideos(videos);
        }

        treeModel.reload();
        expandAllNodes();

        // Notify that tree has been refreshed
        System.out.println("Tree refreshed successfully");
    }

    private void buildTreeFromVideos(List<VideoItem> videos) {
        for (VideoItem video : videos) {
            if (video == null || video.getType() == null)
                continue;

            String type = video.getType();
            String series = video.getSeriesName() != null ? video.getSeriesName() : "Unknown";
            int season = video.getSeasonNumber();
            int episode = video.getEpisodeNumber();
            String videoName = video.getVideoName() != null ? video.getVideoName() : "Untitled";

            DefaultMutableTreeNode typeNode = findOrCreateNode(rootNode, type);
            DefaultMutableTreeNode seriesNode = findOrCreateNode(typeNode, series);

            if (type.equals("Movie")) {
                // Show just movie name without duration
                String movieDisplay = videoName;
                VideoNode movieNode = new VideoNode(video, movieDisplay);
                seriesNode.add(movieNode);
            } else {
                String seasonName = "S" + season;
                DefaultMutableTreeNode seasonNode = findOrCreateNode(seriesNode, seasonName);

                // Show episode without duration
                String episodeDisplay = "EP" + episode + " - " + videoName;
                VideoNode episodeNode = new VideoNode(video, episodeDisplay);
                seasonNode.add(episodeNode);
            }
        }
    }

    private DefaultMutableTreeNode findOrCreateNode(DefaultMutableTreeNode parent, String name) {
        if (name == null)
            name = "Unknown";

        for (int i = 0; i < parent.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) parent.getChildAt(i);
            Object userObj = child.getUserObject();
            if (userObj != null && userObj.toString().equals(name)) {
                return child;
            }
        }

        DefaultMutableTreeNode newNode = new DefaultMutableTreeNode(name);
        parent.add(newNode);
        return newNode;
    }

    private void expandAllNodes() {
        for (int i = 0; i < folderTree.getRowCount(); i++) {
            folderTree.expandRow(i);
        }
    }

    public void searchTree(String searchText) {
        if (searchText == null || searchText.trim().isEmpty() || searchText.equals("Search...")) {
            refreshTree();
            return;
        }

        String lowerSearch = searchText.toLowerCase().trim();

        DefaultMutableTreeNode newRoot = new DefaultMutableTreeNode("Root");

        // Ensure database connection
        DatabaseManager.ensureConnection();
        List<VideoItem> videos = DatabaseManager.getAllVideos();

        boolean found = false;
        for (VideoItem video : videos) {
            if (matchesSearch(video, lowerSearch)) {
                found = true;
                String type = video.getType();
                String series = video.getSeriesName() != null ? video.getSeriesName() : "Unknown";
                int season = video.getSeasonNumber();
                int episode = video.getEpisodeNumber();
                String videoName = video.getVideoName() != null ? video.getVideoName() : "Untitled";

                DefaultMutableTreeNode typeNode = findOrCreateNode(newRoot, type);
                DefaultMutableTreeNode seriesNode = findOrCreateNode(typeNode, series);

                if (type.equals("Movie")) {
                    String movieDisplay = videoName;
                    seriesNode.add(new VideoNode(video, movieDisplay));
                } else {
                    String seasonName = "S" + season;
                    DefaultMutableTreeNode seasonNode = findOrCreateNode(seriesNode, seasonName);
                    String episodeDisplay = "EP" + episode + " - " + videoName;
                    seasonNode.add(new VideoNode(video, episodeDisplay));
                }
            }
        }

        if (!found) {
            newRoot.add(new DefaultMutableTreeNode("No results found for: " + searchText));
        }

        rootNode.removeAllChildren();
        for (int i = 0; i < newRoot.getChildCount(); i++) {
            rootNode.add((DefaultMutableTreeNode) newRoot.getChildAt(i));
        }

        treeModel.reload();
        expandAllNodes();
    }

    private boolean matchesSearch(VideoItem video, String searchText) {
        if (video == null)
            return false;

        if (video.getVideoName() != null && video.getVideoName().toLowerCase().contains(searchText))
            return true;
        if (video.getSeriesName() != null && video.getSeriesName().toLowerCase().contains(searchText))
            return true;
        if (video.getType() != null && video.getType().toLowerCase().contains(searchText))
            return true;
        if (("EP" + video.getEpisodeNumber()).toLowerCase().contains(searchText))
            return true;
        if (("S" + video.getSeasonNumber()).toLowerCase().contains(searchText))
            return true;
        if (String.valueOf(video.getDurationSeconds()).contains(searchText))
            return true;
        if (video.getFormattedDuration() != null && video.getFormattedDuration().contains(searchText))
            return true;
        return false;
    }

    public String getSelectedPath() {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) folderTree.getLastSelectedPathComponent();
        if (node == null)
            return null;

        StringBuilder path = new StringBuilder();
        Object[] pathObjects = node.getUserObjectPath();

        for (int i = 1; i < pathObjects.length; i++) {
            if (i > 1)
                path.append(" > ");
            path.append(pathObjects[i].toString());
        }

        return path.toString();
    }

    public JTree getTree() {
        return folderTree;
    }

    public DefaultMutableTreeNode getSelectedNode() {
        return (DefaultMutableTreeNode) folderTree.getLastSelectedPathComponent();
    }

    public static class VideoNode extends DefaultMutableTreeNode {
        private VideoItem video;

        public VideoNode(VideoItem video, String display) {
            super(display);
            this.video = video;
        }

        public VideoItem getVideo() {
            return video;
        }
    }

}

// FolderPanel
