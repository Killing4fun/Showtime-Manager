package showtime.manager.view;

import java.awt.*;
import java.util.List;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import showtime.manager.model.DatabaseManager;
import showtime.manager.model.VideoItem;

public class UploadDialog extends JDialog {
    private JTextField episodeNameField;
    private JTextField durationField;
    private JComboBox<String> typeCombo;
    private JComboBox<String> seriesCombo;
    private JTextField newSeriesField;
    private JCheckBox hasSeasonCheck;
    private JSpinner seasonSpinner;
    private JSpinner episodeSpinner;
    private JButton saveButton;
    private JButton cancelButton;
    private JButton newTypeButton;
    private JRadioButton existingSeriesRadio;
    private JRadioButton newSeriesRadio;
    private ButtonGroup seriesGroup;
    private JLabel durationHintLabel;
    private JLabel durationPreviewLabel;
    private JLabel seasonStatusLabel;
    private JLabel episodeStatusLabel;
    private JTextArea descriptionArea;
    private JTextField remarksArea;
    private JTextField audioLangsField;
    private JTextField subLangsField;

    private boolean saved = false;
    private boolean isEditMode = false;
    private VideoItem savedVideo = null;
    private VideoItem originalVideo = null;
    private int videoId = -1;
    private int currentTypeId = -1;
    private int currentSeriesId = -1;

    public UploadDialog(JFrame parent) {
        this(parent, null);
    }

    public UploadDialog(JFrame parent, VideoItem videoToEdit) {
        super(parent, videoToEdit == null ? "Upload New Video" : "Edit Video Info", true);
        this.isEditMode = videoToEdit != null;
        this.originalVideo = videoToEdit;

        initComponents();
        loadTypes();

        if (isEditMode) {
            this.videoId = videoToEdit.getId();
            populateFields(videoToEdit);
            saveButton.setText("Update Episode");
        }

        pack();
        setLocationRelativeTo(parent);
        setSize(700, 850);
        setMinimumSize(new java.awt.Dimension(600, 750));
        setResizable(true);

        // Keyboard shortcuts: Escape = Cancel, Enter = Save (skip if description area has focus)
        javax.swing.InputMap im = getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        javax.swing.ActionMap am = getRootPane().getActionMap();

        im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0), "dlg-cancel");
        am.put("dlg-cancel", new javax.swing.AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent evt) { dispose(); }
        });

        im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, 0), "dlg-save");
        am.put("dlg-save", new javax.swing.AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent evt) {
                // Let the description textarea keep its own Enter for newlines
                java.awt.Component focused = getFocusOwner();
                if (focused == descriptionArea) return;
                saveVideo();
            }
        });
    }

    private void populateFields(VideoItem video) {
        System.out.println("📝 Populating fields for video: " + video.getVideoName());
        
        episodeNameField.setText(video.getVideoName());
        durationField.setText(formatSecondsToHMS(video.getDurationSeconds()));

        // Match Type
        System.out.println("  Type: " + video.getType());
        for (int i = 0; i < typeCombo.getItemCount(); i++) {
            if (typeCombo.getItemAt(i).equals(video.getType())) {
                System.out.println("  ✓ Type matched at index " + i);
                typeCombo.setSelectedIndex(i);
                break;
            }
        }

        // Match Series (this triggers onTypeSelected which loads series)
        int typeId = DatabaseManager.getTypeId(video.getType());
        System.out.println("  Type ID: " + typeId);
        loadSeriesForType(typeId);
        
        System.out.println("  Series: " + video.getSeriesName());
        for (int i = 0; i < seriesCombo.getItemCount(); i++) {
            if (seriesCombo.getItemAt(i).equals(video.getSeriesName())) {
                System.out.println("  ✓ Series matched at index " + i);
                seriesCombo.setSelectedIndex(i);
                break;
            }
        }
        existingSeriesRadio.setSelected(true);
        updateSeriesFields();

        // Set season and episode
        hasSeasonCheck.setSelected(video.hasSeasons());
        updateSeasonFields();
        seasonSpinner.setValue(video.getSeasonNumber());
        episodeSpinner.setValue(video.getEpisodeNumber());
        descriptionArea.setText(video.getDescription() != null ? video.getDescription() : "");
        remarksArea.setText(video.getRemarks() != null ? video.getRemarks() : "");
        audioLangsField.setText(video.getAudioLangs() != null ? video.getAudioLangs() : "");
        subLangsField.setText(video.getSubLangs() != null ? video.getSubLangs() : "");

        updateDurationPreview();
        System.out.println("  ✓ Fields populated successfully");
    }

    private void initComponents() {
        setLayout(new BorderLayout());

        // Main horizontal container
        JPanel mainContent = new JPanel(new GridBagLayout());
        mainContent.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        GridBagConstraints mainGbc = new GridBagConstraints();
        mainGbc.fill = GridBagConstraints.BOTH;
        mainGbc.weighty = 1.0;

        // --- LEFT COLUMN: Metadata & Notes ---
        JPanel leftPanel = new JPanel(new GridBagLayout());
        leftPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Metadata & Notes"),
                BorderFactory.createEmptyBorder(5, 5, 5, 5)));
        GridBagConstraints gbcL = new GridBagConstraints();
        gbcL.fill = GridBagConstraints.HORIZONTAL;
        gbcL.insets = new Insets(4, 4, 4, 4);
        gbcL.weightx = 1.0;

        int rowL = 0;

        // Row 0: Episode Name
        gbcL.gridx = 0;
        gbcL.gridy = rowL;
        gbcL.gridwidth = 1;
        JLabel episodeNameLabel = new JLabel("Episode Name:");
        episodeNameLabel.setFont(new Font("Arial", Font.BOLD, 12));
        leftPanel.add(episodeNameLabel, gbcL);

        rowL++;
        gbcL.gridy = rowL;
        episodeNameField = new JTextField(25);
        leftPanel.add(episodeNameField, gbcL);

        rowL++;

        // Row 1: Duration
        gbcL.gridy = rowL;
        JLabel durationLabel = new JLabel("Duration:");
        durationLabel.setFont(new Font("Arial", Font.BOLD, 12));
        leftPanel.add(durationLabel, gbcL);

        rowL++;
        gbcL.gridy = rowL;
        JPanel durationPanel = new JPanel(new BorderLayout(5, 0));
        durationField = new JTextField(10);
        durationField.getDocument().addDocumentListener(new DurationListener());
        durationPanel.add(durationField, BorderLayout.CENTER);
        durationPreviewLabel = new JLabel("--:--:--");
        durationPreviewLabel.setFont(new Font("Monospaced", Font.BOLD, 12));
        durationPreviewLabel.setForeground(new Color(0, 173, 181));
        durationPanel.add(durationPreviewLabel, BorderLayout.EAST);
        leftPanel.add(durationPanel, gbcL);

        rowL++;

        // Languages
        gbcL.gridy = rowL;
        leftPanel.add(new JLabel("Audio Languages:"), gbcL);
        rowL++;
        gbcL.gridy = rowL;
        audioLangsField = new JTextField(25);
        leftPanel.add(audioLangsField, gbcL);

        rowL++;
        gbcL.gridy = rowL;
        leftPanel.add(new JLabel("Subtitle Languages:"), gbcL);
        rowL++;
        gbcL.gridy = rowL;
        subLangsField = new JTextField(25);
        leftPanel.add(subLangsField, gbcL);

        rowL++;

        rowL++;
        leftPanel.add(Box.createVerticalGlue(), gbcL);

        // --- RIGHT COLUMN: Content & Episode Info ---
        JPanel rightPanel = new JPanel(new GridBagLayout());
        rightPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Content Placement"),
                BorderFactory.createEmptyBorder(5, 5, 5, 5)));
        GridBagConstraints gbcR = new GridBagConstraints();
        gbcR.fill = GridBagConstraints.HORIZONTAL;
        gbcR.insets = new Insets(4, 4, 4, 4);
        gbcR.weightx = 1.0;
        rightPanel.setMinimumSize(new java.awt.Dimension(330, 0)); // ← Lock column width
        rightPanel.setPreferredSize(new java.awt.Dimension(330, 300));

        int rowR = 0;

        // Content Type
        gbcR.gridx = 0;
        gbcR.gridy = rowR;
        rightPanel.add(new JLabel("Content Type:"), gbcR);
        rowR++;
        gbcR.gridy = rowR;
        JPanel typePanel = new JPanel(new BorderLayout(5, 0));
        typeCombo = new JComboBox<>();
        typeCombo.addActionListener(e -> onTypeSelected());
        typePanel.add(typeCombo, BorderLayout.CENTER);
        newTypeButton = new JButton("New");
        newTypeButton.addActionListener(e -> showNewTypeDialog());
        typePanel.add(newTypeButton, BorderLayout.EAST);
        rightPanel.add(typePanel, gbcR);

        rowR++;

        // Series selection
        gbcR.gridy = rowR;
        rightPanel.add(new JLabel("Series:"), gbcR);
        rowR++;
        gbcR.gridy = rowR;
        existingSeriesRadio = new JRadioButton("Existing:", true);
        existingSeriesRadio.addActionListener(e -> updateSeriesFields());
        seriesCombo = new JComboBox<>();
        seriesCombo.addActionListener(e -> onSeriesSelected());
        seriesCombo.setMinimumSize(new java.awt.Dimension(150, 25)); // ← Fix: stable min width
        seriesCombo.setPreferredSize(new java.awt.Dimension(200, 25));
        JPanel existingPanel = new JPanel(new BorderLayout(5, 0));
        existingPanel.add(existingSeriesRadio, BorderLayout.WEST);
        existingPanel.add(seriesCombo, BorderLayout.CENTER);
        rightPanel.add(existingPanel, gbcR);

        rowR++;
        gbcR.gridy = rowR;
        newSeriesRadio = new JRadioButton("New Series:", false);
        newSeriesRadio.addActionListener(e -> updateSeriesFields());
        newSeriesField = new JTextField(15);
        newSeriesField.setEnabled(false);
        newSeriesField.setMinimumSize(new java.awt.Dimension(150, 25)); // ← Fix: stable min width
        newSeriesField.setPreferredSize(new java.awt.Dimension(200, 25));
        JPanel newSerPanel = new JPanel(new BorderLayout(5, 0));
        newSerPanel.add(newSeriesRadio, BorderLayout.WEST);
        newSerPanel.add(newSeriesField, BorderLayout.CENTER);
        rightPanel.add(newSerPanel, gbcR);

        seriesGroup = new ButtonGroup();
        seriesGroup.add(existingSeriesRadio);
        seriesGroup.add(newSeriesRadio);

        rowR++;
        rightPanel.add(new JSeparator(), gbcR);
        rowR++;

        // Season/Episode
        gbcR.gridy = rowR;
        hasSeasonCheck = new JCheckBox("Has Seasons?", true);
        hasSeasonCheck.addActionListener(e -> updateSeasonFields());
        rightPanel.add(hasSeasonCheck, gbcR);

        rowR++;
        gbcR.gridy = rowR;
        JPanel seasonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        seasonPanel.add(new JLabel("Season:"));
        seasonSpinner = new JSpinner(new SpinnerNumberModel(1, 0, 100, 1));
        seasonSpinner.addChangeListener(e -> onSeasonChanged());
        seasonPanel.add(seasonSpinner);
        seasonStatusLabel = new JLabel(" ");
        seasonPanel.add(seasonStatusLabel);
        rightPanel.add(seasonPanel, gbcR);

        rowR++;
        gbcR.gridy = rowR;
        JPanel episodePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        episodePanel.add(new JLabel("Episode:"));
        episodeSpinner = new JSpinner(new SpinnerNumberModel(1, 0, 1000, 1));
        episodeSpinner.addChangeListener(e -> onEpisodeChanged());
        episodePanel.add(episodeSpinner);
        episodeStatusLabel = new JLabel(" ");
        episodePanel.add(episodeStatusLabel);
        rightPanel.add(episodePanel, gbcR);

        rowR++;
        rightPanel.add(Box.createVerticalGlue(), gbcR);

        // Add columns to main container
        mainGbc.gridy = 0;
        mainGbc.gridx = 0;
        mainGbc.weightx = 0.6;
        mainGbc.gridwidth = 1;
        mainContent.add(leftPanel, mainGbc);
        mainGbc.gridx = 1;
        mainGbc.weightx = 0.4;
        mainContent.add(rightPanel, mainGbc);

        // --- BOTTOM SECTION: Full-width Notes & Description ---
        JPanel notesPanel = new JPanel(new GridBagLayout());
        notesPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Notes & Description"),
                BorderFactory.createEmptyBorder(5, 5, 5, 5)));
        GridBagConstraints gbcN = new GridBagConstraints();
        gbcN.fill = GridBagConstraints.BOTH;
        gbcN.insets = new Insets(4, 4, 4, 4);
        gbcN.weightx = 1.0;

        int rowN = 0;
        gbcN.gridy = rowN;
        gbcN.weighty = 0;
        notesPanel.add(new JLabel("Description:"), gbcN);
        rowN++;
        gbcN.gridy = rowN;
        gbcN.weighty = 1.0; // ← KEY: let this row grow
        descriptionArea = new JTextArea(20, 50);
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        notesPanel.add(new JScrollPane(descriptionArea), gbcN);

        rowN++; // ← Fix: increment so Remarks appears BELOW, not beside
        gbcN.gridy = rowN;
        gbcN.weighty = 0;
        notesPanel.add(new JLabel("Remarks:"), gbcN);
        rowN++;
        gbcN.gridy = rowN;
        remarksArea = new JTextField(50);
        notesPanel.add(remarksArea, gbcN);

        mainGbc.gridy = 1;
        mainGbc.gridx = 0;
        mainGbc.gridwidth = 2;
        mainGbc.weightx = 1.0;
        mainGbc.weighty = 1.0; // ← KEY: let notesPanel grow vertically
        mainGbc.fill = GridBagConstraints.BOTH;
        mainContent.add(notesPanel, mainGbc);

        add(mainContent, BorderLayout.CENTER);

        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        saveButton = new JButton("Save Episode");
        saveButton.setFont(new Font("Arial", Font.BOLD, 12));
        saveButton.setBackground(new Color(46, 125, 50));
        saveButton.setForeground(Color.WHITE);
        saveButton.setFocusPainted(false);
        saveButton.setPreferredSize(new Dimension(120, 35));
        saveButton.addActionListener(e -> saveVideo());

        cancelButton = new JButton("Cancel");
        cancelButton.setFont(new Font("Arial", Font.PLAIN, 12));
        cancelButton.setPreferredSize(new Dimension(100, 35));
        cancelButton.addActionListener(e -> dispose());

        buttonPanel.add(saveButton);
        buttonPanel.add(cancelButton);

        add(buttonPanel, BorderLayout.SOUTH);

        // Add window listener
        addWindowListener(new UploadWindowListener());

        updateSeasonFields();
        updateSeriesFields();
    }

    private void onSeriesSelected() {
        if (existingSeriesRadio.isSelected() && seriesCombo.getSelectedItem() != null) {
            String seriesName = (String) seriesCombo.getSelectedItem();
            if (!seriesName.equals("No series yet")) {
                String typeName = (String) typeCombo.getSelectedItem();
                int typeId = DatabaseManager.getTypeId(typeName);
                currentSeriesId = DatabaseManager.getSeriesId(seriesName, typeId);
                updateSeasonEpisodeStatus();
            }
        }
    }

    private void onSeasonChanged() {
        updateSeasonEpisodeStatus();
    }

    private void onEpisodeChanged() {
        updateSeasonEpisodeStatus();
    }

    private void updateSeasonEpisodeStatus() {
        if (currentSeriesId == -1 || !hasSeasonCheck.isSelected()) {
            seasonStatusLabel.setText(" ");
            episodeStatusLabel.setText(" ");
            return;
        }

        int seasonNum = (Integer) seasonSpinner.getValue();
        int episodeNum = (Integer) episodeSpinner.getValue();

        // Check if season exists
        boolean seasonExists = DatabaseManager.seasonExists(currentSeriesId, seasonNum);
        if (seasonExists) {
            seasonStatusLabel.setText("✓ Season exists");
            seasonStatusLabel.setForeground(new Color(76, 175, 80));
        } else {
            seasonStatusLabel.setText("🆕 New season will be created");
            seasonStatusLabel.setForeground(new Color(255, 152, 0));
        }

        // Check if episode exists
        boolean episodeExists = DatabaseManager.episodeExists(currentSeriesId, seasonNum, episodeNum);
        if (episodeExists) {
            episodeStatusLabel.setText("⚠️ Episode already exists!");
            episodeStatusLabel.setForeground(new Color(239, 83, 80));
        } else {
            // Check if this would be the next episode
            int nextEpisode = DatabaseManager.getNextEpisodeNumber(currentSeriesId);
            int nextSeason = DatabaseManager.getCurrentSeason(currentSeriesId);

            if (seasonNum == nextSeason && episodeNum == nextEpisode) {
                episodeStatusLabel.setText("✓ Next available episode");
                episodeStatusLabel.setForeground(new Color(76, 175, 80));
            } else if (seasonNum > nextSeason || (seasonNum == nextSeason && episodeNum > nextEpisode)) {
                episodeStatusLabel.setText("⏭️ Future episode (gap detected)");
                episodeStatusLabel.setForeground(new Color(255, 152, 0));
            } else {
                episodeStatusLabel.setText("📝 New episode");
                episodeStatusLabel.setForeground(new Color(0, 173, 181));
            }
        }
    }

    private void updateDurationPreview() {
        String text = durationField.getText().trim();
        if (text.isEmpty()) {
            durationPreviewLabel.setText("--:--:--");
            return;
        }

        try {
            int totalSeconds = parseDurationToSeconds(text);
            String formatted = formatSecondsToHMS(totalSeconds);
            durationPreviewLabel.setText(formatted);
            durationPreviewLabel.setForeground(new Color(0, 173, 181));
        } catch (NumberFormatException e) {
            durationPreviewLabel.setText("Invalid format");
            durationPreviewLabel.setForeground(new Color(239, 83, 80));
        }
    }

    private int parseDurationToSeconds(String durationText) throws NumberFormatException {
        if (durationText == null || durationText.trim().isEmpty()) {
            throw new NumberFormatException("Empty duration");
        }

        String text = durationText.trim().toLowerCase();

        // Format 1: HH:MM:SS - "1:30:00"
        if (text.matches("^\\d+:[0-5]?\\d:[0-5]?\\d$")) {
            String[] parts = text.split(":");
            int hours = Integer.parseInt(parts[0]);
            int minutes = Integer.parseInt(parts[1]);
            int seconds = Integer.parseInt(parts[2]);
            return (hours * 3600) + (minutes * 60) + seconds;
        }

        // Format 2: MM:SS - "90:00"
        if (text.matches("^\\d+:[0-5]?\\d$")) {
            String[] parts = text.split(":");
            int minutes = Integer.parseInt(parts[0]);
            int seconds = Integer.parseInt(parts[1]);
            return (minutes * 60) + seconds;
        }

        // Format 3: Simple seconds - "3600"
        if (text.matches("^\\d+$")) {
            return Integer.parseInt(text);
        }

        throw new NumberFormatException("Invalid duration format");
    }

    private String formatSecondsToHMS(int totalSeconds) {
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;

        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%d:%02d", minutes, seconds);
        }
    }

    private void loadTypes() {
        typeCombo.removeAllItems();
        List<String> types = DatabaseManager.getContentTypes();
        for (String type : types) {
            typeCombo.addItem(type);
        }

        if (typeCombo.getItemCount() > 0) {
            typeCombo.setSelectedIndex(0);
        }
    }

    private void onTypeSelected() {
        String selectedType = (String) typeCombo.getSelectedItem();
        if (selectedType != null) {
            currentTypeId = DatabaseManager.getTypeId(selectedType);
            loadSeriesForType(currentTypeId);
            currentSeriesId = -1;

            if (seriesCombo.getItemCount() > 0 && !seriesCombo.getItemAt(0).equals("No series yet")) {
                existingSeriesRadio.setSelected(true);
                updateSeriesFields();
                seriesCombo.setSelectedIndex(0);
                onSeriesSelected();
            } else {
                newSeriesRadio.setSelected(true);
                updateSeriesFields();
            }
        }
    }

    private void loadSeriesForType(int typeId) {
        seriesCombo.removeAllItems();

        if (typeId == -1)
            return;

        List<String> series = DatabaseManager.getSeriesByType(typeId);
        if (series.isEmpty()) {
            seriesCombo.addItem("No series yet");
        } else {
            for (String seriesName : series) {
                seriesCombo.addItem(seriesName);
            }
        }
    }

    private void updateSeriesFields() {
        if (existingSeriesRadio.isSelected()) {
            seriesCombo.setEnabled(true);
            newSeriesField.setEnabled(false);
            newSeriesField.setText("");

            if (seriesCombo.getItemCount() == 1 && seriesCombo.getItemAt(0).equals("No series yet")) {
                newSeriesRadio.setSelected(true);
                updateSeriesFields();
            } else {
                // Trigger series selection to update status
                onSeriesSelected();
            }
        } else {
            seriesCombo.setEnabled(false);
            newSeriesField.setEnabled(true);
            newSeriesField.requestFocus();
            currentSeriesId = -1;
            seasonStatusLabel.setText(" ");
            episodeStatusLabel.setText(" ");
        }
    }

    private void showNewTypeDialog() {
        String newType = JOptionPane.showInputDialog(this,
                "Enter new content type:",
                "New Content Type",
                JOptionPane.PLAIN_MESSAGE);

        if (newType != null && !newType.trim().isEmpty()) {
            String trimmedType = newType.trim();

            // Check if already exists in current combo box
            boolean alreadyExists = false;
            for (int i = 0; i < typeCombo.getItemCount(); i++) {
                if (typeCombo.getItemAt(i).equals(trimmedType)) {
                    alreadyExists = true;
                    break;
                }
            }

            if (alreadyExists) {
                JOptionPane.showMessageDialog(this,
                        "Content type '" + trimmedType + "' already exists!",
                        "Duplicate Type",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }

            // Add to database
            boolean success = DatabaseManager.addContentType(trimmedType);
            if (success) {
                loadTypes();
                typeCombo.setSelectedItem(trimmedType);
                JOptionPane.showMessageDialog(this,
                        "Content type '" + trimmedType + "' added successfully!",
                        "Success",
                        JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this,
                        "<html>Error adding content type!<br>" +
                                "Please check if database is connected.</html>",
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void updateSeasonFields() {
        boolean hasSeasons = hasSeasonCheck.isSelected();
        seasonSpinner.setEnabled(hasSeasons);
        episodeSpinner.setEnabled(hasSeasons);
        seasonStatusLabel.setVisible(hasSeasons);
        episodeStatusLabel.setVisible(hasSeasons);

        if (!hasSeasons) {
            seasonSpinner.setValue(0);
            episodeSpinner.setValue(0);
            seasonStatusLabel.setText(" ");
            episodeStatusLabel.setText(" ");
        } else if (currentSeriesId != -1) {
            updateSeasonEpisodeStatus();
        }
    }

    private void saveVideo() {
        if (!validateFields()) {
            return;
        }

        String episodeName = episodeNameField.getText().trim();
        String typeName = (String) typeCombo.getSelectedItem();
        boolean hasSeasons = hasSeasonCheck.isSelected();
        int seasonNum = hasSeasons ? (Integer) seasonSpinner.getValue() : 0;
        int episodeNum = hasSeasons ? (Integer) episodeSpinner.getValue() : 0;

        // Parse duration to seconds
        int durationSeconds;
        try {
            durationSeconds = parseDurationToSeconds(durationField.getText().trim());
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this,
                    "<html>Invalid duration format!<br>" +
                            "Please use HH:MM:SS format (e.g., 1:30:00)</html>",
                    "Duration Error",
                    JOptionPane.ERROR_MESSAGE);
            durationField.requestFocus();
            return;
        }

        // Get series name
        String seriesName;
        int seriesId;

        try {
            if (existingSeriesRadio.isSelected()) {
                seriesName = (String) seriesCombo.getSelectedItem();
                if (seriesName.equals("No series yet")) {
                    JOptionPane.showMessageDialog(this,
                            "Please create a new series first.",
                            "No Series Available",
                            JOptionPane.WARNING_MESSAGE);
                    newSeriesRadio.setSelected(true);
                    updateSeriesFields();
                    return;
                }

                int typeId = DatabaseManager.getTypeId(typeName);
                seriesId = DatabaseManager.getSeriesId(seriesName, typeId);
            } else {
                seriesName = newSeriesField.getText().trim();
                int typeId = DatabaseManager.getTypeId(typeName);
                seriesId = DatabaseManager.createSeries(seriesName, typeId);
            }

            if (seriesId == -1) {
                JOptionPane.showMessageDialog(this,
                        "Error creating/retrieving series!",
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            // Check for duplicate episode with warning
            if (hasSeasons) {
                boolean exists = false;
                if (isEditMode) {
                    exists = DatabaseManager.episodeExistsExcluding(seriesId, seasonNum, episodeNum, videoId);
                } else {
                    exists = DatabaseManager.episodeExists(seriesId, seasonNum, episodeNum);
                }

                if (exists) {
                    String message = "<html><h3>⚠️ Episode Already Exists!</h3>" +
                            "<p>The episode <b>" + seriesName + " S" + seasonNum + "E" + episodeNum
                            + "</b> already exists.</p>" +
                            "<p>What would you like to do?</p></html>";

                    String[] options = { "Go to Next Episode", "Overwrite", "Cancel" };
                    int choice = JOptionPane.showOptionDialog(this,
                            message,
                            "Duplicate Episode",
                            JOptionPane.YES_NO_CANCEL_OPTION,
                            JOptionPane.WARNING_MESSAGE,
                            null,
                            options,
                            options[0]);

                    if (choice == 0) { // Go to Next Episode
                        int nextEpisode = DatabaseManager.getNextEpisodeNumber(seriesId);
                        int nextSeason = DatabaseManager.getCurrentSeason(seriesId);
                        seasonSpinner.setValue(nextSeason);
                        episodeSpinner.setValue(nextEpisode);
                        updateDurationPreview(); // Refresh preview
                        return;
                    } else if (choice == 1) { // Overwrite
                        // If we are in edit mode, we shouldn't delete the row we're editing!
                        // But wait, if someone ELSE has that episode number, we should delete THEM.
                        // DatabaseManager.deleteEpisode deletes by (seasonId, episodeNum).
                        int targetSeasonId = DatabaseManager.getOrCreateSeason(seriesId, seasonNum);
                        DatabaseManager.deleteEpisode(targetSeasonId, episodeNum);
                        // Now the slot is free.
                    } else { // Cancel
                        return;
                    }
                }
            }

            // Get or create season
            int seasonId = DatabaseManager.getOrCreateSeason(seriesId, seasonNum);
            if (seasonId == -1) {
                JOptionPane.showMessageDialog(this,
                        "Error creating/retrieving season!",
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            // Insert or Update video
            boolean success = false;
            String description = descriptionArea.getText().trim();
            String remarks = remarksArea.getText().trim();
            String audioLangs = audioLangsField.getText().trim();
            String subLangs = subLangsField.getText().trim();

            if (isEditMode) {
                success = DatabaseManager.updateVideoMetadata(videoId, seasonId, episodeNum, episodeName,
                        durationSeconds, description, audioLangs, subLangs, remarks, hasSeasons);
                if (success) {
                    // Propagate new duration to all scheduled rows for this video
                    DatabaseManager.syncScheduleDurationsForVideo(videoId);
                }
            } else {
                success = DatabaseManager.insertVideo(seasonId, episodeNum, episodeName, durationSeconds,
                        description, audioLangs, subLangs, remarks, hasSeasons);
            }

            if (success) {
                saved = true;
                // Create the video item for the caller to use if needed
                savedVideo = new VideoItem(typeName, seriesName, seasonNum, episodeNum, episodeName,
                        durationSeconds, description, audioLangs, subLangs, remarks, hasSeasons);
                if (isEditMode) {
                    savedVideo.setId(videoId);
                }

                String actionLabel = isEditMode ? "Updated" : "Saved";
                String seriesSource = existingSeriesRadio.isSelected() ? "Existing" : "New";
                String formattedDuration = formatSecondsToHMS(durationSeconds);

                JOptionPane.showMessageDialog(this,
                        "<html><h3>✅ Episode " + actionLabel + " Successfully!</h3>" +
                                "<p><b>Type:</b> " + typeName + "<br>" +
                                "<b>Series:</b> " + seriesName + " (" + seriesSource + ")<br>" +
                                "<b>Season:</b> " + (hasSeasons ? seasonNum : "N/A") + "<br>" +
                                "<b>Episode:</b> " + (hasSeasons ? episodeNum : "N/A") + "<br>" +
                                "<b>Episode Name:</b> " + episodeName + "<br>" +
                                "<b>Duration:</b> " + formattedDuration + "</p></html>",
                        "Success",
                        JOptionPane.INFORMATION_MESSAGE);

                dispose();
            } else {
                JOptionPane.showMessageDialog(this,
                        "Error saving episode to database!",
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
            }

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                    "Error: " + e.getMessage(),
                    "Exception Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private boolean validateFields() {
        if (episodeNameField.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter episode name");
            episodeNameField.requestFocus();
            return false;
        }

        if (durationField.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter duration");
            durationField.requestFocus();
            return false;
        }

        if (typeCombo.getSelectedItem() == null) {
            JOptionPane.showMessageDialog(this, "Please select content type");
            return false;
        }

        if (existingSeriesRadio.isSelected()) {
            if (seriesCombo.getSelectedItem() == null ||
                    seriesCombo.getSelectedItem().toString().equals("No series yet")) {
                JOptionPane.showMessageDialog(this,
                        "No series available. Please create a new series.");
                newSeriesRadio.setSelected(true);
                updateSeriesFields();
                return false;
            }
        } else {
            if (newSeriesField.getText().trim().isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please enter new series name");
                newSeriesField.requestFocus();
                return false;
            }
        }

        return true;
    }

    public boolean isSaved() {
        return saved;
    }

    public VideoItem getSavedVideo() {
        return savedVideo;
    }

    public void resetForm() {
        episodeNameField.setText("");
        durationField.setText("");
        hasSeasonCheck.setSelected(true);
        seasonSpinner.setValue(1);
        episodeSpinner.setValue(1);
        existingSeriesRadio.setSelected(true);
        newSeriesField.setText("");
        updateSeriesFields();
        updateSeasonFields();
        durationPreviewLabel.setText("--:--:--");
        seasonStatusLabel.setText(" ");
        episodeStatusLabel.setText(" ");
        saved = false;
        savedVideo = null;
        currentSeriesId = -1;

        onTypeSelected();
    }

    private class DurationListener implements DocumentListener {
        @Override
        public void insertUpdate(DocumentEvent e) {
            updateDurationPreview();
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
            updateDurationPreview();
        }

        @Override
        public void changedUpdate(DocumentEvent e) {
            updateDurationPreview();
        }
    }

    private class UploadWindowListener extends java.awt.event.WindowAdapter {
        @Override
        public void windowClosing(java.awt.event.WindowEvent e) {
            dispose();
        }
    }
}

// UploadDialog