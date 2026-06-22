/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/GUIForms/JFrame.java to edit this template
 */
package showtime.manager;

import javax.swing.JLabel;
import javax.swing.UIManager;
import javax.swing.DropMode;
import javax.swing.TransferHandler;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.ListSelectionModel;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Color;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.Timer;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JComboBox;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.awt.FlowLayout;
import java.sql.Connection;
import java.sql.PreparedStatement;
import showtime.manager.view.FolderPanel;
import showtime.manager.model.DatabaseManager;
import showtime.manager.view.UploadDialog;
import showtime.manager.utils.ShowtimeTableModel;
import showtime.manager.utils.ScheduleManager;
import showtime.manager.utils.ScheduleTransferHandler;
import showtime.manager.utils.ExcelExporter;
import showtime.manager.utils.ExcelImporter;
import showtime.manager.renderer.ShowtimeTableRenderer;
import showtime.manager.model.ScheduleItem;
import java.time.LocalTime;
import java.time.LocalDate;
import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.File;
import java.awt.KeyboardFocusManager;
import java.awt.KeyEventDispatcher;
import java.awt.event.KeyEvent;

/**
 *
 * @author Killing4fun
 */
public class Main extends javax.swing.JFrame {

    private static final java.util.logging.Logger logger = java.util.logging.Logger.getLogger(Main.class.getName());

    // Custom fields
    private FolderPanel folderPanel;
    private Timer searchTimer;
    private ShowtimeTableModel showtimeModel;
    private ScheduleItem selectedScheduleItem = null;
    private ScheduleItem copiedScheduleItem = null;
    private int selectedScheduleRow = -1;
    private int selectedSchedulePosition = -1;
    private boolean isInitializing = true;

    // Navigation panel components
    private javax.swing.JPanel NavigationPanel;
    private javax.swing.JButton prevWeekButton;
    private javax.swing.JButton nextWeekButton;
    private javax.swing.JButton currentWeekButton;
    private javax.swing.JLabel weekRangeLabel;

    // Table components for fixed column
    private JTable fixedColumnTable;
    private JTable scrollableTable;
    private JScrollPane scrollPane;

    /**
     * Creates new form Main
     */
    public Main() {
        setupDarkTheme();
        initComponents();
    }

    private void setupDarkTheme() {
        try {
            // Base background and text
            UIManager.put("control", new Color(18, 18, 18));
            UIManager.put("info", new Color(25, 25, 25));
            UIManager.put("nimbusBase", new Color(18, 18, 18));
            UIManager.put("nimbusFocus", new Color(0, 173, 181));
            UIManager.put("nimbusLightBackground", new Color(30, 30, 30));
            UIManager.put("nimbusSelectionBackground", new Color(0, 173, 181));
            UIManager.put("nimbusSelectedText", Color.WHITE);
            UIManager.put("text", new Color(220, 220, 220));
            UIManager.put("nimbusDisabledText", new Color(100, 100, 100));

            // Component specifics
            UIManager.put("Panel.background", new Color(18, 18, 18));
            UIManager.put("Label.foreground", new Color(220, 220, 220));
            UIManager.put("Table.background", new Color(25, 25, 25));
            UIManager.put("Table.foreground", new Color(220, 220, 220));
            UIManager.put("Table.gridColor", new Color(45, 45, 45));
            UIManager.put("Table.selectionBackground", new Color(0, 173, 181, 100));

            UIManager.put("TableHeader.background", new Color(35, 35, 35));
            UIManager.put("TableHeader.foreground", Color.WHITE);

            UIManager.put("Tree.background", new Color(22, 22, 22));
            UIManager.put("Tree.foreground", new Color(210, 210, 210));
            UIManager.put("Tree.textForeground", new Color(210, 210, 210));
            UIManager.put("Tree.selectionBackground", new Color(0, 173, 181, 120));

            UIManager.put("TextField.background", new Color(35, 35, 35));
            UIManager.put("TextField.foreground", Color.WHITE);
            UIManager.put("TextField.caretForeground", Color.WHITE);

            UIManager.put("ScrollPane.background", new Color(18, 18, 18));
            UIManager.put("Viewport.background", new Color(18, 18, 18));

            UIManager.put("Button.background", new Color(45, 45, 45));
            UIManager.put("Button.foreground", Color.WHITE);

        } catch (Exception e) {
            System.err.println("Error setting dark theme: " + e.getMessage());
        }
    }

    /**
     * This method is called from within the constructor to initialize the form.
     */
    private void initComponents() {

        FolderPanel = new javax.swing.JPanel();
        SearchTextField = new javax.swing.JTextField();
        ListFolderPanel = new javax.swing.JPanel();
        ShowtimePanel = new javax.swing.JPanel();
        DetailsPanel = new javax.swing.JPanel();
        UploadPanel = new javax.swing.JPanel();
        ButtonPanel = new javax.swing.JPanel();
        refreshButton = new javax.swing.JButton();
        exportButton = new javax.swing.JButton();
        importButton = new javax.swing.JButton();
        deleteDbButton = new javax.swing.JButton();

        // Navigation Panel components
        NavigationPanel = new javax.swing.JPanel();
        prevWeekButton = new javax.swing.JButton();
        nextWeekButton = new javax.swing.JButton();
        currentWeekButton = new javax.swing.JButton();
        weekRangeLabel = new javax.swing.JLabel();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("Showtime Manager - Content Scheduling System");

        // ==================== FOLDER PANEL ====================
        FolderPanel.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));
        FolderPanel.setPreferredSize(new Dimension(280, 600));

        SearchTextField.setText("Search...");
        SearchTextField.setForeground(new java.awt.Color(153, 153, 153));

        // Add focus listener for placeholder text
        SearchTextField.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusGained(java.awt.event.FocusEvent evt) {
                if (SearchTextField.getText().equals("Search...")) {
                    SearchTextField.setText("");
                    SearchTextField.setForeground(java.awt.Color.WHITE);
                }
            }

            @Override
            public void focusLost(java.awt.event.FocusEvent evt) {
                if (SearchTextField.getText().isEmpty()) {
                    SearchTextField.setText("Search...");
                    SearchTextField.setForeground(new java.awt.Color(153, 153, 153));
                }
            }
        });

        // Add document listener for real-time search with delay
        SearchTextField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                startSearchTimer();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                startSearchTimer();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                startSearchTimer();
            }
        });

        FolderPanel.setLayout(new java.awt.BorderLayout(5, 5));

        // Add padding to the SearchTextField
        javax.swing.JPanel searchPanel = new javax.swing.JPanel(new java.awt.BorderLayout());
        searchPanel.setBorder(javax.swing.BorderFactory.createEmptyBorder(5, 5, 0, 5));
        searchPanel.add(SearchTextField, java.awt.BorderLayout.CENTER);

        FolderPanel.add(searchPanel, java.awt.BorderLayout.NORTH);

        ListFolderPanel.setLayout(new java.awt.BorderLayout());
        FolderPanel.add(ListFolderPanel, java.awt.BorderLayout.CENTER);

        // Settings Button at the bottom
        javax.swing.JPanel folderBottomPanel = new javax.swing.JPanel(new java.awt.BorderLayout());
        folderBottomPanel.setBorder(javax.swing.BorderFactory.createEmptyBorder(5, 5, 5, 5));
        folderBottomPanel.setBackground(new Color(18, 18, 18));
        
        javax.swing.JButton genreSettingsButton = new javax.swing.JButton("⚙ Settings");
        genreSettingsButton.setBackground(new Color(45, 45, 45));
        genreSettingsButton.setForeground(Color.WHITE);
        genreSettingsButton.setFont(new Font("Arial", Font.BOLD, 12));
        genreSettingsButton.setFocusPainted(false);
        genreSettingsButton.addActionListener(e -> {
            new showtime.manager.view.GenreSettingsDialog(Main.this).setVisible(true);
            // Refresh table after settings change
            showtime.manager.renderer.ShowtimeTableRenderer.TimelinePanel.refreshColors();
            scrollableTable.repaint();
        });
        
        folderBottomPanel.add(genreSettingsButton, java.awt.BorderLayout.CENTER);
        FolderPanel.add(folderBottomPanel, java.awt.BorderLayout.SOUTH);

        // ==================== SHOWTIME PANEL WITH FIXED COLUMN ====================
        ShowtimePanel.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(45, 45, 45)));
        ShowtimePanel.setLayout(new java.awt.BorderLayout());

        // Create and set up the showtime table model
        showtimeModel = new ShowtimeTableModel();
        showtimeModel.debugPrintStructure();

        // Create the fixed day column table
        fixedColumnTable = new javax.swing.JTable(showtimeModel);
        fixedColumnTable.setRowHeight(70);
        fixedColumnTable.setGridColor(new Color(45, 45, 45));
        fixedColumnTable.setShowGrid(true);
        fixedColumnTable.setIntercellSpacing(new Dimension(1, 1));
        fixedColumnTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        fixedColumnTable.setAutoResizeMode(javax.swing.JTable.AUTO_RESIZE_OFF);
        fixedColumnTable.setPreferredScrollableViewportSize(new Dimension(100, 0));

        // Remove Timeline column from Fixed Table
        if (fixedColumnTable.getColumnCount() >= 2) {
            fixedColumnTable.getColumnModel().removeColumn(fixedColumnTable.getColumnModel().getColumn(1));
            TableColumn dayColumn = fixedColumnTable.getColumnModel().getColumn(0);
            dayColumn.setPreferredWidth(100);
            dayColumn.setMinWidth(100);
            dayColumn.setMaxWidth(120);
            dayColumn.setCellRenderer(new DefaultTableCellRenderer() {
                @Override
                public Component getTableCellRendererComponent(JTable table, Object value,
                        boolean isSelected, boolean hasFocus, int row, int column) {
                    Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

                    if (value instanceof String && ((String) value).contains("<html>")) {
                        setText((String) value);
                    } else if (value != null) {
                        setText("<html><center>" + value.toString() + "</center></html>");
                    }

                    setBackground(isSelected ? new Color(0, 173, 181, 100) : new Color(25, 25, 25));
                    setForeground(new Color(220, 220, 220));
                    setFont(new Font("Arial", Font.BOLD, 12));
                    setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
                    setVerticalAlignment(javax.swing.SwingConstants.CENTER);

                    return c;
                }
            });
        }

        // Create the main scrollable table (with timeline column only)
        scrollableTable = new javax.swing.JTable(showtimeModel);
        scrollableTable.setDefaultRenderer(Object.class, new ShowtimeTableRenderer());
        scrollableTable.setRowHeight(70);
        scrollableTable.setGridColor(new Color(45, 45, 45));
        scrollableTable.setShowGrid(true);
        scrollableTable.setIntercellSpacing(new Dimension(1, 1));
        scrollableTable.setFillsViewportHeight(true);
        scrollableTable.setAutoResizeMode(javax.swing.JTable.AUTO_RESIZE_OFF);
        scrollableTable.setDragEnabled(true);
        scrollableTable.setDropMode(DropMode.INSERT_ROWS);
        scrollableTable.setTransferHandler(new ScheduleTransferHandler(showtimeModel));
        scrollableTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // Sync selection between the two tables
        fixedColumnTable.setSelectionModel(scrollableTable.getSelectionModel());

        // Remove Day column from Scrollable Table
        if (scrollableTable.getColumnCount() >= 2) {
            try {
                scrollableTable.getColumnModel().removeColumn(scrollableTable.getColumnModel().getColumn(0));
                // Timeline is now at display index 0
                TableColumn timelineColumn = scrollableTable.getColumnModel().getColumn(0);
                timelineColumn.setPreferredWidth(showtime.manager.renderer.ShowtimeTableRenderer.pixelsPerHour * 24);
                timelineColumn.setCellRenderer(new ShowtimeTableRenderer());
            } catch (Exception e) {
                System.err.println("Error setting column properties: " + e.getMessage());
            }
        }

        // Create scroll pane for the main table
        scrollPane = new JScrollPane(scrollableTable);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.getViewport().setBackground(new Color(18, 18, 18));
        scrollPane.setBorder(BorderFactory.createEmptyBorder());

        // Set the fixed column table as the row header
        scrollPane.setRowHeaderView(fixedColumnTable);

        // Setup corner component for top-left intersection
        javax.swing.JPanel cornerPanel = new javax.swing.JPanel();
        cornerPanel.setBackground(new Color(35, 35, 35));
        scrollPane.setCorner(JScrollPane.UPPER_LEFT_CORNER, cornerPanel);

        // ==================== TIME HEADER (RULER) ====================
        // Add a time ruler at the top of the timeline
        timeHeader = new TimeHeaderPanel();
        scrollPane.setColumnHeaderView(timeHeader);

        // Add mouse listeners
        addTableMouseListeners();

        ShowtimePanel.add(scrollPane, java.awt.BorderLayout.CENTER);

        // Mark initialization as complete
        isInitializing = false;

        // ==================== DETAILS PANEL WITH SCROLL ====================
        DetailsPanel.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(45, 45, 45)));
        DetailsPanel.setPreferredSize(new Dimension(400, 200));
        DetailsPanel.setLayout(new java.awt.BorderLayout());

        // Create a scroll pane for the details
        JScrollPane detailsScrollPane = new JScrollPane();
        detailsScrollPane.setBorder(BorderFactory.createEmptyBorder());
        detailsScrollPane.setBackground(new Color(18, 18, 18));
        detailsScrollPane.getViewport().setBackground(new Color(18, 18, 18));
        detailsScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        detailsScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        // Add details label to DetailsPanel
        final JLabel detailsLabel = new JLabel();
        detailsLabel.setVerticalAlignment(JLabel.TOP);
        detailsLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        detailsLabel.setText("<html><i>Select an item to view description</i></html>");

        // Set the label as the viewport view
        detailsScrollPane.setViewportView(detailsLabel);

        // Add scroll pane to details panel
        DetailsPanel.add(detailsScrollPane, java.awt.BorderLayout.CENTER);

        // ==================== UPLOAD PANEL ====================
        UploadPanel.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(45, 45, 45)));
        UploadPanel.setBackground(new java.awt.Color(30, 30, 30));
        UploadPanel.setCursor(new Cursor(Cursor.HAND_CURSOR));
        UploadPanel.setPreferredSize(new Dimension(100, 80));

        // Add label to UploadPanel
        JLabel uploadLabel = new JLabel("📤 Click to Upload New Video", JLabel.CENTER);
        uploadLabel.setFont(new Font("Arial", Font.BOLD, 14));
        uploadLabel.setForeground(new java.awt.Color(0, 173, 181));
        UploadPanel.setLayout(new java.awt.BorderLayout());
        UploadPanel.add(uploadLabel, java.awt.BorderLayout.CENTER);

        // ==================== BUTTON PANEL ====================
        ButtonPanel.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(45, 45, 45)));

        refreshButton.setText("🔄 Refresh");
        refreshButton.setPreferredSize(new Dimension(120, 30));

        exportButton.setText("📊 Export to Excel");
        exportButton.setPreferredSize(new Dimension(150, 30));
        exportButton.setBackground(new Color(0, 120, 60));
        exportButton.setForeground(Color.WHITE);
        exportButton.setFont(new Font("Arial", Font.BOLD, 12));

        importButton.setText("📥 Import from Excel");
        importButton.setPreferredSize(new Dimension(150, 30));
        importButton.setBackground(new Color(120, 60, 0));
        importButton.setForeground(Color.WHITE);
        importButton.setFont(new Font("Arial", Font.BOLD, 12));

        deleteDbButton.setText("🗑️ Delete DB");
        deleteDbButton.setPreferredSize(new Dimension(120, 30));
        deleteDbButton.setBackground(new Color(180, 0, 0));
        deleteDbButton.setForeground(Color.WHITE);
        deleteDbButton.setFont(new Font("Arial", Font.BOLD, 12));

        javax.swing.GroupLayout ButtonPanelLayout = new javax.swing.GroupLayout(ButtonPanel);
        ButtonPanel.setLayout(ButtonPanelLayout);
        ButtonPanelLayout.setHorizontalGroup(
                ButtonPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, ButtonPanelLayout.createSequentialGroup()
                                .addContainerGap(50, Short.MAX_VALUE)
                                .addComponent(deleteDbButton)
                                .addGap(10, 10, 10)
                                .addComponent(importButton)
                                .addGap(10, 10, 10)
                                .addComponent(exportButton)
                                .addGap(10, 10, 10)
                                .addComponent(refreshButton)
                                .addContainerGap()));
        ButtonPanelLayout.setVerticalGroup(
                ButtonPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(ButtonPanelLayout.createSequentialGroup()
                                .addGap(15, 15, 15)
                                .addGroup(ButtonPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                        .addComponent(deleteDbButton)
                                        .addComponent(importButton)
                                        .addComponent(exportButton)
                                        .addComponent(refreshButton))
                                .addContainerGap(15, Short.MAX_VALUE)));

        // ==================== NAVIGATION PANEL ====================
        NavigationPanel.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(45, 45, 45)));
        NavigationPanel.setBackground(new java.awt.Color(25, 25, 25));
        NavigationPanel.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 10, 5));

        // Previous Week Button
        prevWeekButton.setText("◀ Previous Week");
        prevWeekButton.setFont(new Font("Arial", Font.BOLD, 12));
        prevWeekButton.setPreferredSize(new Dimension(120, 30));

        // Current Week Button
        currentWeekButton.setText("📅 Current Week");
        currentWeekButton.setFont(new Font("Arial", Font.BOLD, 12));
        currentWeekButton.setPreferredSize(new Dimension(120, 30));

        // Next Week Button
        nextWeekButton.setText("Next Week ▶");
        nextWeekButton.setFont(new Font("Arial", Font.BOLD, 12));
        nextWeekButton.setPreferredSize(new Dimension(120, 30));

        // Week Range Label
        weekRangeLabel.setFont(new Font("Arial", Font.BOLD, 14));
        weekRangeLabel.setForeground(new Color(0, 173, 181));
        weekRangeLabel.setPreferredSize(new Dimension(300, 30));
        weekRangeLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        weekRangeLabel.setText("📅 " + showtimeModel.getWeekRange());

        // Add to navigation panel
        NavigationPanel.add(prevWeekButton);
        NavigationPanel.add(weekRangeLabel);
        NavigationPanel.add(currentWeekButton);
        NavigationPanel.add(nextWeekButton);

        // ==================== MAIN LAYOUT ====================
        getContentPane().setLayout(new java.awt.BorderLayout(5, 5));

        // 1. Navigation Panel at Top
        getContentPane().add(NavigationPanel, java.awt.BorderLayout.NORTH);

        // 2. Right Panel with split pane for resizable details
        javax.swing.JPanel rightPanel = new javax.swing.JPanel(new java.awt.BorderLayout(0, 5));

        // Create vertical split pane for Showtime and Details
        JSplitPane rightSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        rightSplitPane.setTopComponent(ShowtimePanel);
        rightSplitPane.setBottomComponent(DetailsPanel);
        rightSplitPane.setDividerLocation(450);
        rightSplitPane.setResizeWeight(0.7);
        rightSplitPane.setContinuousLayout(true);
        rightSplitPane.setBorder(BorderFactory.createEmptyBorder());

        rightPanel.add(rightSplitPane, java.awt.BorderLayout.CENTER);

        // 3. Main Split Pane (Folder Tree on left, Right Panel on right)
        javax.swing.JSplitPane mainSplitPane = new javax.swing.JSplitPane(javax.swing.JSplitPane.HORIZONTAL_SPLIT);
        mainSplitPane.setDividerLocation(280);
        mainSplitPane.setLeftComponent(FolderPanel);
        mainSplitPane.setRightComponent(rightPanel);

        getContentPane().add(mainSplitPane, java.awt.BorderLayout.CENTER);

        // 4. Bottom Area (Upload and Buttons)
        javax.swing.JPanel bottomContainer = new javax.swing.JPanel(new java.awt.BorderLayout(0, 5));
        bottomContainer.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 5, 5, 5));

        bottomContainer.add(UploadPanel, java.awt.BorderLayout.CENTER);
        bottomContainer.add(ButtonPanel, java.awt.BorderLayout.SOUTH);

        getContentPane().add(bottomContainer, java.awt.BorderLayout.SOUTH);

        // Initialize search timer (300ms delay after typing)
        searchTimer = new Timer(300, e -> performSearch());
        searchTimer.setRepeats(false);

        // Setup custom components
        setupFolderPanel();
        setupUploadPanel();
        setupRefreshButton();
        setupNavigationButtons();
        setupKeyboardShortcuts();

        // Initialize database
        System.out.println("\n=== Starting Application ===");
        DatabaseManager.initialize();
        DatabaseManager.debugPrintTables();
        DatabaseManager.verifyScheduleTableStructure();

        pack();
        setLocationRelativeTo(null);
        setExtendedState(MAXIMIZED_BOTH);
    }

    private void addTableMouseListeners() {
        // Mouse listener for the table
        scrollableTable.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mousePressed(java.awt.event.MouseEvent evt) {
                handleTableMousePressed(evt, scrollableTable);
            }

            public void mouseReleased(java.awt.event.MouseEvent evt) {
                handleTableMouseReleased(evt, scrollableTable);
            }
        });

        scrollableTable.addMouseWheelListener(new java.awt.event.MouseWheelListener() {
            @Override
            public void mouseWheelMoved(java.awt.event.MouseWheelEvent e) {
                if (e.isControlDown()) {
                    int rotation = e.getWheelRotation();
                    int zoomChange = -rotation * 10; // 5 pixels per hour per notch (less sensitive)
                    int currentZoom = showtime.manager.renderer.ShowtimeTableRenderer.pixelsPerHour;
                    int newZoom = Math.max(50, Math.min(10000, currentZoom + zoomChange)); // allow extreme zoom

                    if (newZoom != currentZoom) {
                        showtime.manager.renderer.ShowtimeTableRenderer.pixelsPerHour = newZoom;

                        // Update table column width
                        scrollableTable.getColumnModel().getColumn(0).setPreferredWidth(newZoom * 24);

                        scrollableTable.revalidate();
                        scrollableTable.repaint();
                        if (timeHeader != null) {
                            timeHeader.revalidate();
                            timeHeader.repaint();
                        }
                    }
                }
            }
        });

        // Mouse motion listener for drag
        scrollableTable.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseDragged(java.awt.event.MouseEvent evt) {
                if (selectedScheduleItem != null) {
                    TransferHandler handler = scrollableTable.getTransferHandler();
                    if (handler instanceof ScheduleTransferHandler) {
                        ((ScheduleTransferHandler) handler).setDraggedItem(selectedScheduleItem);
                    }
                    if (handler != null) {
                        handler.exportAsDrag(scrollableTable, evt, TransferHandler.MOVE);
                    }
                }
            }
        });
    }

    private void handleTableMousePressed(java.awt.event.MouseEvent evt, JTable table) {
        int row = table.rowAtPoint(evt.getPoint());
        int col = table.columnAtPoint(evt.getPoint());

        if (col == 0 && row >= 0 && row < showtimeModel.getRowCount()) {
            @SuppressWarnings("unchecked")
            java.util.List<ScheduleItem> dailyItems = (java.util.List<ScheduleItem>) showtimeModel
                    .getValueAt(row, 1);

            if (dailyItems != null && !dailyItems.isEmpty()) {
                int clickX = evt.getX() - table.getCellRect(row, col, true).x;
                double pixelsPerSecond = showtime.manager.renderer.ShowtimeTableRenderer.pixelsPerHour / 3600.0;
                int currentX = 5;

                for (int i = 0; i < dailyItems.size(); i++) {
                    ScheduleItem item = dailyItems.get(i);
                    int blockWidth = (int) (item.getDurationSeconds() * pixelsPerSecond);
                    if (blockWidth < 40)
                        blockWidth = 40;

                    if (clickX >= currentX && clickX <= currentX + blockWidth) {
                        selectedScheduleItem = item;
                        selectedScheduleRow = row;
                        selectedSchedulePosition = i;
                        table.setRowSelectionInterval(row, row);
                        System.out.println("Selected schedule item: " + item.getVideoName() +
                                " at position " + i + " in day " + row);
                        break;
                    }
                    currentX += blockWidth + 4;
                }
            }
        }
    }

    private void handleTableMouseReleased(java.awt.event.MouseEvent evt, JTable table) {
        int row = table.rowAtPoint(evt.getPoint());
        int col = table.columnAtPoint(evt.getPoint());

        if (col == 0 && row >= 0 && row < showtimeModel.getRowCount()) {
            @SuppressWarnings("unchecked")
            java.util.List<ScheduleItem> dailyItems = (java.util.List<ScheduleItem>) showtimeModel
                    .getValueAt(row, 1);

            if (dailyItems != null) {
                int clickX = evt.getX() - table.getCellRect(row, col, true).x;
                double pixelsPerSecond = showtime.manager.renderer.ShowtimeTableRenderer.pixelsPerHour / 3600.0;
                int currentX = 5;

                for (ScheduleItem item : dailyItems) {
                    int blockWidth = (int) (item.getDurationSeconds() * pixelsPerSecond);
                    if (blockWidth < 40)
                        blockWidth = 40;

                    if (clickX >= currentX && clickX <= currentX + blockWidth) {
                        if (javax.swing.SwingUtilities.isRightMouseButton(evt)) {
                            javax.swing.JPopupMenu popup = new javax.swing.JPopupMenu();

                            javax.swing.JMenuItem editItemMenu = new javax.swing.JMenuItem("📝 Edit Schedule");
                            editItemMenu.addActionListener(e -> {
                                showEditScheduleDialog(item);
                            });
                            popup.add(editItemMenu);

                            javax.swing.JMenuItem copyItemMenu = new javax.swing.JMenuItem("📄 Copy Block");
                            copyItemMenu.addActionListener(e -> {
                                copiedScheduleItem = item;
                                System.out.println("Copied: " + copiedScheduleItem.getVideoName());
                            });
                            popup.add(copyItemMenu);

                            javax.swing.JMenuItem pasteItemMenu = new javax.swing.JMenuItem("📋 Paste Block");
                            pasteItemMenu.addActionListener(e -> {
                                pasteScheduleItem(item.getScheduleDate(), selectedSchedulePosition + 1);
                            });
                            pasteItemMenu.setEnabled(copiedScheduleItem != null);
                            popup.add(pasteItemMenu);

                            javax.swing.JMenuItem editVideoMenu = new javax.swing.JMenuItem("✎ Edit Video Info");
                            editVideoMenu.addActionListener(e -> {
                                showEditVideoDialog(item);
                            });
                            popup.add(editVideoMenu);
                            popup.addSeparator();

                            javax.swing.JMenuItem deleteItem = new javax.swing.JMenuItem(
                                    "❌ Delete '" + item.getVideoName() + "'");
                            deleteItem.addActionListener(e -> {
                                int confirm = javax.swing.JOptionPane.showConfirmDialog(Main.this,
                                        "Are you sure you want to remove this video from "
                                                + item.getScheduleDate() + "?",
                                        "Delete Item",
                                        javax.swing.JOptionPane.YES_NO_OPTION);
                                if (confirm == javax.swing.JOptionPane.YES_OPTION) {
                                    showtime.manager.utils.ScheduleManager.deleteScheduleItem(item.getId());
                                    showtime.manager.utils.ScheduleManager.reflowDay(item.getScheduleDate());
                                    showtimeModel.loadScheduleForWeek();
                                    table.repaint();
                                    JLabel detailsLabel = findDetailsLabel();
                                    if (detailsLabel != null) {
                                        detailsLabel.setText(
                                                "<html><i>Item deleted successfully. Select an item to view description.</i></html>");
                                    }
                                    JScrollPane detailsScrollPane = findScrollPane();
                                    if (detailsScrollPane != null) {
                                        detailsScrollPane.getViewport().setViewPosition(new java.awt.Point(0, 0));
                                    }
                                }
                            });
                            popup.add(deleteItem);
                            popup.show(evt.getComponent(), evt.getX(), evt.getY());
                        } else {
                            JLabel detailsLabel = findDetailsLabel();
                            if (detailsLabel != null) {
                                showScheduleItemDetails(item, detailsLabel);
                                JScrollPane detailsScrollPane = findScrollPane();
                                if (detailsScrollPane != null) {
                                    detailsScrollPane.getViewport().setViewPosition(new java.awt.Point(0, 0));
                                }
                            }
                        }
                        break;
                    }
                    currentX += blockWidth + 4;
                }
            }
        }

        java.awt.EventQueue.invokeLater(() -> {
            // Do NOT reset selectedScheduleItem here if we want Ctrl+C to work after release,
            // because mouseRelease clears the selection right away!
            // Wait, actually `selectedScheduleItem` is set in mousePressed.
            // If we clear it in mouseReleased, Ctrl+C won't work unless held while clicking.
            // Let's remove the clearance code so the selection remains active.
        });
    }

    private void pasteScheduleItem(LocalDate targetDate, int insertIndex) {
        if (copiedScheduleItem == null) return;
        
        ScheduleItem clone = new ScheduleItem();
        clone.setVideoId(copiedScheduleItem.getVideoId());
        clone.setScheduleDate(targetDate);
        clone.setStatus("Scheduled");
        clone.setDurationSeconds(copiedScheduleItem.getDurationSeconds());
        
        boolean success = showtime.manager.utils.ScheduleManager.insertNewScheduleItemAtIndex(clone, targetDate, insertIndex);
        if (success) {
            System.out.println("Pasted " + copiedScheduleItem.getVideoName() + " to " + targetDate);
            refreshAll();
        }
    }

    // ==================== SETUP METHODS ====================

    private void setupFolderPanel() {
        folderPanel = new FolderPanel();

        folderPanel.setDetailsCallback(new FolderPanel.DetailsCallback() {
            @Override
            public void onItemSelected(String title, String details) {
                updateDetailsPanel(title, details);
            }
        });

        // Auto-refresh the schedule timetable whenever a video is edited (e.g. duration changed)
        folderPanel.setScheduleSavedCallback(() -> refreshAll());

        ListFolderPanel.setLayout(new BorderLayout());
        ListFolderPanel.removeAll();
        ListFolderPanel.add(folderPanel, BorderLayout.CENTER);
        ListFolderPanel.revalidate();
        ListFolderPanel.repaint();
    }

    private void setupUploadPanel() {
        UploadPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                showUploadDialog();
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                UploadPanel.setBackground(new java.awt.Color(45, 45, 45));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                UploadPanel.setBackground(new java.awt.Color(30, 30, 30));
            }
        });
    }

    private void setupRefreshButton() {
        refreshButton.addActionListener(e -> refreshAll());
        exportButton.addActionListener(e -> exportSchedule());
        importButton.addActionListener(e -> importSchedule());
        deleteDbButton.addActionListener(e -> {
            int confirm = javax.swing.JOptionPane.showConfirmDialog(Main.this,
                    "Are you absolutely sure you want to delete the entire database?\n" +
                    "This action cannot be undone and will erase all your videos and schedule data.",
                    "Delete Database",
                    javax.swing.JOptionPane.YES_NO_OPTION,
                    javax.swing.JOptionPane.WARNING_MESSAGE);
            if (confirm == javax.swing.JOptionPane.YES_OPTION) {
                boolean success = showtime.manager.model.DatabaseManager.deleteDatabase();
                if (success) {
                    javax.swing.JOptionPane.showMessageDialog(Main.this, "Database deleted successfully. The application will now exit.", "Success", javax.swing.JOptionPane.INFORMATION_MESSAGE);
                    System.exit(0);
                } else {
                    javax.swing.JOptionPane.showMessageDialog(Main.this, "Failed to delete database. It may be in use.", "Error", javax.swing.JOptionPane.ERROR_MESSAGE);
                }
            }
        });
    }

    private static final String E1 = "ZWNoYW4=";
    private static final String E2 = "ZWNoYW4gaXMgaGVyZQ==";

    private static String dec(String s) {
        return new String(java.util.Base64.getDecoder().decode(s));
    }

    private void showEasterEgg() {
        String title = "✨ Special Access Granted";
        String details = "<html>" +
                "<head><style>" +
                ".title { color: #FF2E93; font-size: 20px; font-weight: bold; margin: 0 0 5px 0; text-shadow: 0 0 10px rgba(255,46,147,0.5); }" +
                ".badge { background: linear-gradient(135deg, #FF2E93, #FF8A00); color: white; padding: 3px 10px; border-radius: 12px; font-size: 11px; display: inline-block; margin-bottom: 10px; font-weight: bold; }" +
                ".hr { border: none; border-top: 1px solid #FF2E93; margin: 10px 0; opacity: 0.3; }" +
                ".info-box { background: linear-gradient(135deg, rgba(30,30,30,0.8), rgba(20,20,20,0.8)); border: 1px solid rgba(255,46,147,0.3); border-radius: 8px; padding: 15px; margin-top: 15px; box-shadow: 0 4px 15px rgba(0,0,0,0.5); }" +
                ".glow-text { color: #00E5FF; text-shadow: 0 0 5px rgba(0,229,255,0.5); }" +
                "</style></head>" +
                "<body>" +
                "<div class='title'>✨ Easter Egg Discovered! ✨</div>" +
                "<div class='badge'>🚀 Developer Signature</div>" +
                "<hr class='hr'>" +
                "<div class='info-box'>" +
                "<span style='color: #E0E0E0; font-size: 13px; line-height: 1.6;'>" +
                "Greeting user! You found the hidden signature:<br>" +
                "<b style='font-size: 18px;' class='glow-text'>👋 Echan is here!</b><br><br>" +
                "<span style='color: #888; font-style: italic;'>\"Coding is the closest thing we have to magic.\"</span>" +
                "</span>" +
                "</div>" +
                "</body>" +
                "</html>";
        updateDetailsPanel(title, details);
        System.out.println("[EASTER EGG] Echan is here!");
    }

    private void setupKeyboardShortcuts() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(new KeyEventDispatcher() {
            private final StringBuilder keyBuffer = new StringBuilder();

            @Override
            public boolean dispatchKeyEvent(KeyEvent e) {
                if (!isActive()) return false;

                if (e.getID() == KeyEvent.KEY_TYPED) {
                    char c = Character.toLowerCase(e.getKeyChar());
                    if (Character.isLetterOrDigit(c) || c == ' ') {
                        keyBuffer.append(c);
                        if (keyBuffer.length() > 30) {
                            keyBuffer.delete(0, keyBuffer.length() - 30);
                        }
                        String typed = keyBuffer.toString();
                        if (typed.endsWith(dec(E1)) || typed.endsWith(dec(E2)) || typed.endsWith(dec(E2).replace(" ", ""))) {
                            javax.swing.SwingUtilities.invokeLater(() -> showEasterEgg());
                        }
                    }
                    return false;
                }

                // Only act on KEY_PRESSED and only when Main window is active
                if (e.getID() != KeyEvent.KEY_PRESSED) return false;

                // Don't intercept when typing inside a text field / text area (except Escape)
                java.awt.Component focused = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
                boolean inTextInput = (focused instanceof javax.swing.JTextField
                        || focused instanceof javax.swing.JTextArea
                        || focused instanceof javax.swing.JSpinner);

                int code = e.getKeyCode();
                boolean ctrl = e.isControlDown();

                // Escape – clear search and return focus to table
                if (code == KeyEvent.VK_ESCAPE) {
                    SearchTextField.setText("Search...");
                    SearchTextField.setForeground(new java.awt.Color(153, 153, 153));
                    if (scrollableTable != null) scrollableTable.requestFocusInWindow();
                    return true;
                }

                if (inTextInput) return false;

                // F5 – Refresh all
                if (code == KeyEvent.VK_F5 && !ctrl) {
                    refreshAll();
                    return true;
                }

                // Ctrl+N – New upload
                if (ctrl && code == KeyEvent.VK_N) {
                    showUploadDialog();
                    return true;
                }

                // Ctrl+E – Export
                if (ctrl && code == KeyEvent.VK_E) {
                    exportSchedule();
                    return true;
                }

                // Ctrl+I - Import
                if (ctrl && code == KeyEvent.VK_I) {
                    importSchedule();
                    return true;
                }

                // Ctrl+C - Copy selected schedule block
                if (ctrl && code == KeyEvent.VK_C) {
                    if (selectedScheduleItem != null) {
                        copiedScheduleItem = selectedScheduleItem;
                        System.out.println("Copied: " + copiedScheduleItem.getVideoName());
                    }
                    return true;
                }

                // Ctrl+V - Paste copied schedule block
                if (ctrl && code == KeyEvent.VK_V) {
                    if (copiedScheduleItem != null) {
                        int targetRow = scrollableTable.getSelectedRow();
                        if (targetRow < 0 && selectedScheduleRow >= 0) {
                            targetRow = selectedScheduleRow;
                        } else if (targetRow < 0) {
                            targetRow = 0; // Default to first day if nothing selected
                        }
                        
                        LocalDate targetDate = showtimeModel.getDateAtRow(targetRow);
                        if (targetDate == null) {
                            targetDate = showtimeModel.getCurrentWeekStart().plusDays(targetRow);
                        }
                        int targetPos = (targetRow == selectedScheduleRow && selectedSchedulePosition >= 0) 
                                        ? selectedSchedulePosition + 1 : 9999;
                                        
                        pasteScheduleItem(targetDate, targetPos);
                    }
                    return true;
                }

                // Ctrl+F – Focus search
                if (ctrl && code == KeyEvent.VK_F) {
                    SearchTextField.requestFocusInWindow();
                    SearchTextField.selectAll();
                    return true;
                }

                // TABLE SHORTCUTS (when focus is on scrollableTable)
                if (focused == scrollableTable) {
                    // Enter – Edit selected schedule item
                    if (code == KeyEvent.VK_ENTER) {
                        if (selectedScheduleItem != null) {
                            showEditScheduleDialog(selectedScheduleItem);
                            return true;
                        }
                    }

                    // Delete – Delete selected schedule item
                    if (code == KeyEvent.VK_DELETE) {
                        if (selectedScheduleItem != null) {
                            int confirm = javax.swing.JOptionPane.showConfirmDialog(Main.this,
                                    "Are you sure you want to remove this video from "
                                            + selectedScheduleItem.getScheduleDate() + "?",
                                    "Delete Item",
                                    javax.swing.JOptionPane.YES_NO_OPTION);
                            if (confirm == javax.swing.JOptionPane.YES_OPTION) {
                                showtime.manager.utils.ScheduleManager.deleteScheduleItem(selectedScheduleItem.getId());
                                showtime.manager.utils.ScheduleManager.reflowDay(selectedScheduleItem.getScheduleDate());
                                showtimeModel.loadScheduleForWeek();
                                scrollableTable.repaint();
                                selectedScheduleItem = null;
                                selectedScheduleRow = -1;
                            }
                            return true;
                        }
                    }
                }

                return false;
            }
        });
    }

    private void setupNavigationButtons() {
        prevWeekButton.addActionListener(e -> previousWeek());
        nextWeekButton.addActionListener(e -> nextWeek());
        currentWeekButton.addActionListener(e -> currentWeek());
    }

    // ==================== NAVIGATION METHODS ====================

    private void previousWeek() {
        if (showtimeModel != null) {
            showtimeModel.previousWeek();
            updateWeekRangeLabel();
            // Force table to refresh completely
            scrollableTable.revalidate();
            scrollableTable.repaint();
            // Also refresh the scroll pane's column header (time ruler) if needed
            scrollPane.getColumnHeader().revalidate();
            scrollPane.getColumnHeader().repaint();
        }
    }

    private void nextWeek() {
        if (showtimeModel != null) {
            showtimeModel.nextWeek();
            updateWeekRangeLabel();
            // Force table to refresh completely
            scrollableTable.revalidate();
            scrollableTable.repaint();
            // Also refresh the scroll pane's column header (time ruler) if needed
            scrollPane.getColumnHeader().revalidate();
            scrollPane.getColumnHeader().repaint();
        }
    }

    private void currentWeek() {
        if (showtimeModel != null) {
            showtimeModel.currentWeek();
            updateWeekRangeLabel();
            // Force table to refresh completely
            scrollableTable.revalidate();
            scrollableTable.repaint();
            // Also refresh the scroll pane's column header (time ruler) if needed
            scrollPane.getColumnHeader().revalidate();
            scrollPane.getColumnHeader().repaint();
        }
    }

    private void updateWeekRangeLabel() {
        if (weekRangeLabel != null && showtimeModel != null) {
            weekRangeLabel.setText("📅 " + showtimeModel.getWeekRange());
        }
    }

    private void refreshScheduleTable() {
        if (scrollableTable != null) {
            scrollableTable.repaint();
            scrollableTable.revalidate();
        }
    }

    private void refreshAll() {
        refreshFolderList();
        refreshScheduleTable();
        if (showtimeModel != null) {
            showtimeModel.loadScheduleForWeek();
        }
        verifyTransferHandler();
    }

    // ==================== SEARCH METHODS ====================

    private void startSearchTimer() {
        if (searchTimer != null) {
            searchTimer.restart();
        }
    }

    private void performSearch() {
        String searchText = SearchTextField.getText().trim();
        if (searchText.equals("Search...") || searchText.isEmpty()) {
            if (folderPanel != null) {
                folderPanel.refreshTree();
            }
        } else if (searchText.equalsIgnoreCase(dec(E1)) || searchText.equalsIgnoreCase(dec(E2))) {
            showEasterEgg();
        } else {
            if (folderPanel != null) {
                folderPanel.searchTree(searchText);
            }
        }
    }

    // ==================== DETAILS PANEL METHODS ====================

    private void updateDetailsPanel(String title, String details) {
        JLabel detailsLabel = findDetailsLabel();
        if (detailsLabel != null) {
            String formattedDetails = formatDetailsHtml(details);
            detailsLabel.setText(formattedDetails);
            detailsLabel.setVerticalAlignment(JLabel.TOP);

            JScrollPane detailsScrollPane = findScrollPane();
            if (detailsScrollPane != null) {
                detailsScrollPane.getViewport().setViewPosition(new java.awt.Point(0, 0));
            }
        }
    }

    private JLabel findDetailsLabel() {
        JScrollPane scrollPane = findScrollPane();
        if (scrollPane != null && scrollPane.getViewport().getView() instanceof JLabel) {
            return (JLabel) scrollPane.getViewport().getView();
        }
        return null;
    }

    private JScrollPane findScrollPane() {
        for (java.awt.Component comp : DetailsPanel.getComponents()) {
            if (comp instanceof JScrollPane) {
                return (JScrollPane) comp;
            }
        }
        return null;
    }

    private String formatDetailsHtml(String details) {
        if (!details.startsWith("<html>")) {
            details = "<html>" + details + "</html>";
        }
        return details;
    }

    private void showEditScheduleDialog(ScheduleItem item) {
        JDialog dialog = new JDialog(this, "Edit Schedule", true);
        dialog.setLayout(new BorderLayout());

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 5, 5);

        // 1. Time Field
        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(new JLabel("Start Time (HH:mm):"), gbc);

        gbc.gridx = 1;
        JTextField timeField = new JTextField(item.getStartTime().toString());
        panel.add(timeField, gbc);

        // 2. Status Field
        gbc.gridx = 0;
        gbc.gridy = 1;
        panel.add(new JLabel("Status:"), gbc);

        gbc.gridx = 1;
        String[] statuses = { "Scheduled", "Completed", "Cancelled", "Postponed" };
        JComboBox<String> statusCombo = new JComboBox<>(statuses);
        statusCombo.setSelectedItem(item.getStatus());
        panel.add(statusCombo, gbc);

        // 3. Loop Field
        gbc.gridx = 0;
        gbc.gridy = 2;
        panel.add(new JLabel("Loop Until Time (HH:mm):"), gbc);

        gbc.gridx = 1;
        JTextField loopField = new JTextField("");
        loopField.setToolTipText("Leave blank to not loop. Enter a 24H target time to clone this block until it hits that time.");
        panel.add(loopField, gbc);

        // Buttons
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton saveBtn = new JButton("Save Changes");
        saveBtn.setBackground(new Color(0, 173, 181));
        saveBtn.setForeground(Color.WHITE);

        // Remove or comment out this method if you have it:
        // private void updateScheduleStatus(int scheduleId, String status) { ... }

        // Then in the save button action, use ScheduleManager.updateScheduleStatus:
        saveBtn.addActionListener(e -> {
            try {
                LocalTime newTime = LocalTime.parse(timeField.getText().trim());
                String newStatus = (String) statusCombo.getSelectedItem();
                String loopStr = loopField.getText().trim();

                item.setStartTime(newTime);
                item.setStatus(newStatus);

                if (ScheduleManager.updateScheduleItemDateAndTime(
                        item.getId(), item.getScheduleDate(), newTime)) {

                    // Update the status separately
                    ScheduleManager.updateScheduleStatus(item.getId(), newStatus);

                    if (!loopStr.isEmpty()) {
                        try {
                            LocalTime loopUntilTime = LocalTime.parse(loopStr);
                            ScheduleManager.loopScheduleItem(item.getId(), loopUntilTime);
                        } catch (Exception parseEx) {
                            System.err.println("Ignored invalid loop time: " + loopStr);
                        }
                    }

                    showtimeModel.loadScheduleForWeek();
                    scrollableTable.repaint();
                    dialog.dispose();

                    // Update details panel if this item is selected
                    JLabel detailsLabel = findDetailsLabel();
                    if (detailsLabel != null) {
                        showScheduleItemDetails(item, detailsLabel);
                    }

                    JOptionPane.showMessageDialog(dialog,
                            "Schedule updated successfully!",
                            "Success",
                            JOptionPane.INFORMATION_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(dialog,
                            "Failed to update schedule!",
                            "Error",
                            JOptionPane.ERROR_MESSAGE);
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog,
                        "Invalid Time Format (use HH:mm)",
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
                ex.printStackTrace();
            }
        });

        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> dialog.dispose());

        btnPanel.add(saveBtn);
        btnPanel.add(cancelBtn);

        dialog.add(panel, BorderLayout.CENTER);
        dialog.add(btnPanel, BorderLayout.SOUTH);

        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private void updateScheduleStatus(int scheduleId, String status) {
        String sql = "UPDATE schedule SET status = ? WHERE id = ?";
        try (Connection conn = showtime.manager.model.DatabaseManager.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, status);
            pstmt.setInt(2, scheduleId);
            pstmt.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String escapeHtml(String text) {
        if (text == null)
            return "";
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private void showScheduleItemDetails(ScheduleItem item, JLabel detailsLabel) {
        if (detailsLabel == null)
            return;

        int s = item.getDurationSeconds();
        int hours = s / 3600;
        int minutes = (s % 3600) / 60;
        int seconds = s % 60;
        String durationStr = String.format("%02d:%02d:%02d", hours, minutes, seconds);

        String seriesInfo = "";
        if (item.getSeriesName() != null && !item.getSeriesName().isEmpty() && !item.getSeriesName().equals("null")) {
            seriesInfo = "[" + item.getSeriesName() + "] ";
        }

        String episodeInfo = "";
        if (item.getSeasonNumber() > 0 && item.getEpisodeNumber() > 0) {
            episodeInfo = String.format("<div class='episode-badge'>S%d E%d</div>",
                    item.getSeasonNumber(), item.getEpisodeNumber());
        }

        LocalTime startTime = item.getStartTime();
        LocalTime endTime = startTime.plusSeconds(item.getDurationSeconds());

        java.time.format.DateTimeFormatter ampmFormatter = java.time.format.DateTimeFormatter.ofPattern("hh:mm a");
        String timeRange = String.format("%s - %s",
                startTime.format(ampmFormatter),
                endTime.format(ampmFormatter));

        String details = "<html>" +
                "<head>" +
                "<style>" +
                "body { font-family: Arial, sans-serif; margin: 0; padding: 0; background-color: #1e1e1e; }" +
                ".title { color: #00D1FF; font-size: 18px; font-weight: bold; margin: 0 0 5px 0; word-wrap: break-word; }"
                +
                ".hr { border: none; border-top: 1px solid #444; margin: 10px 0; }" +
                ".info-table { width: 100%; border-collapse: collapse; font-size: 13px; margin-bottom: 10px; }" +
                ".info-table td { padding: 8px 0; vertical-align: top; border-bottom: 1px solid #333; }" +
                ".label { color: #00D1FF; width: 100px; padding-right: 15px; font-weight: bold; width: 90px; }" +
                ".value { color: #E0E0E0; word-wrap: break-word; word-break: break-all; }" +
                ".tip-box { background-color: #222; border-left: 3px solid #00D1FF; border-radius: 2px; padding: 12px; margin-top: 15px; }"
                +
                ".tip-title { color: #B0B0B0; font-weight: bold; margin-bottom: 5px; }" +
                ".tip-text { color: #9E9E9E; line-height: 1.4; }" +
                ".episode-badge { background-color: #333; color: #00D1FF; padding: 2px 8px; border-radius: 12px; border: 1px solid #444; font-size: 11px; display: inline-block; margin-top: 5px; margin-bottom: 5px; }"
                +
                ".info-footer { margin-top: 15px; padding: 10px; background-color: #252525; border-radius: 4px; border: 1px solid #333; font-size: 10px; color: #888; }"
                +
                "</style>" +
                "</head>" +
                "<body>" +
                "<div class='title'>" + escapeHtml(seriesInfo + item.getVideoName()) + "</div>" +
                episodeInfo +
                "<hr class='hr'>" +
                "<table class='info-table'>" +
                "<tr><td class='label'>Duration:</td><td class='value'>" + escapeHtml(durationStr) + "</td></tr>" +
                "<tr><td class='label'>Time:</td><td class='value'>" + escapeHtml(timeRange) + "</td></tr>" +
                "<tr><td class='label'>Date:</td><td class='value'>" + escapeHtml(item.getScheduleDate().toString())
                + " ("
                + escapeHtml(item.getDayOfWeek()) + ")</td></tr>" +
                "<tr><td class='label'>Status:</td><td class='value'><span style='color:#4CAF50;'>"
                + escapeHtml(item.getStatus()) + "</span></td></tr>";

        if (item.getVideoType() != null && !item.getVideoType().isEmpty()) {
            details += "<tr><td class='label'>Type:</td><td class='value'>" + escapeHtml(item.getVideoType())
                    + "</td></tr>";
        }

        if (item.getSeriesName() != null && !item.getSeriesName().isEmpty() && !item.getSeriesName().equals("null")) {
            details += "<tr><td class='label'>Series:</td><td class='value'>" + escapeHtml(item.getSeriesName())
                    + "</td></tr>";
        }

        if (item.getDescription() != null && !item.getDescription().isEmpty()) {
            details += "<tr><td class='label'>Description:</td><td class='value' style='font-style:italic; color:#BBB;'>"
                    + escapeHtml(item.getDescription()) + "</td></tr>";
        }

        if (item.getAudioLangs() != null && !item.getAudioLangs().isEmpty()) {
            details += "<tr><td class='label'>Audio Langs:</td><td class='value'>" + escapeHtml(item.getAudioLangs())
                    + "</td></tr>";
        }

        if (item.getSubLangs() != null && !item.getSubLangs().isEmpty()) {
            details += "<tr><td class='label'>Subtitles:</td><td class='value'>" + escapeHtml(item.getSubLangs())
                    + "</td></tr>";
        }

        if (item.getRemarks() != null && !item.getRemarks().isEmpty()) {
            details += "<tr><td class='label'>Remarks:</td><td class='value' style='color:#00adb5;'>"
                    + escapeHtml(item.getRemarks()) + "</td></tr>";
        }

        details += "</table>" +
                "<div class='tip-box'>" +
                "<div class='tip-title'>💡 Tip:</div>" +
                "<div class='tip-text'>You can drag this item to a different day or time to reschedule it.</div>" +
                "</div>" +
                "<div class='info-footer'>" +
                "<b>ID:</b> " + item.getId() + " | <b>Video ID:</b> " + item.getVideoId() + "<br>" +
                "<b>Duration (seconds):</b> " + item.getDurationSeconds() +
                "</div>" +
                "</body>" +
                "</html>";

        detailsLabel.setText(details);
    }

    // ==================== DIALOG METHODS ====================

    private void showUploadDialog() {
        UploadDialog dialog = new UploadDialog(this);
        dialog.setVisible(true);

        if (dialog.isSaved()) {
            refreshAll();
        }
    }

    private void showEditVideoDialog(ScheduleItem scheduleItem) {
        // Fetch the video details from database
        showtime.manager.model.VideoItem video = showtime.manager.model.DatabaseManager.getVideoById(scheduleItem.getVideoId());
        
        if (video == null) {
            System.err.println("❌ Could not load video ID: " + scheduleItem.getVideoId());
            javax.swing.JOptionPane.showMessageDialog(this,
                    "Could not load video details. Video ID: " + scheduleItem.getVideoId(),
                    "Error",
                    javax.swing.JOptionPane.ERROR_MESSAGE);
            return;
        }

        System.out.println("✅ Loaded video: " + video.getVideoName() + " (Type: " + video.getType() + ")");

        // Open UploadDialog in edit mode
        UploadDialog dialog = new UploadDialog(this, video);
        dialog.setVisible(true);

        if (dialog.isSaved()) {
            refreshAll();
        }
    }

    private void refreshFolderList() {
        if (folderPanel != null) {
            folderPanel.refreshTree();
        }
    }

    /** Prompts the user for a save location and exports the current week to Excel. */
    private void exportSchedule() {
        if (showtimeModel == null) return;

        // Determine week range from model
        LocalDate startDate = showtimeModel.getCurrentWeekStart();
        int days = showtimeModel.getRowCount();

        // Build name based on date range: Schedule HankukTV (DD-MM-YYYY - DD-MM-YYYY).xlsx
        LocalDate endDate = startDate.plusDays(days - 1);
        java.time.format.DateTimeFormatter rangeFmt = java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy");
        String rangeStr = "(" + startDate.format(rangeFmt) + " - " + endDate.format(rangeFmt) + ")";
        String defaultName = "Schedule HankukTV " + rangeStr + ".xlsx";

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export Schedule to Excel");
        chooser.setFileFilter(new FileNameExtensionFilter("Excel Workbook (*.xlsx)", "xlsx"));
        chooser.setSelectedFile(new File(defaultName));

        int result = chooser.showSaveDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) return;

        File file = chooser.getSelectedFile();
        if (!file.getName().toLowerCase().endsWith(".xlsx")) {
            file = new File(file.getAbsolutePath() + ".xlsx");
        }

        // Check if file exists and ask for overwrite confirmation
        if (file.exists()) {
            int confirmOverwrite = javax.swing.JOptionPane.showConfirmDialog(
                this,
                "The file '" + file.getName() + "' already exists.\nDo you want to overwrite it?",
                "Confirm Overwrite",
                javax.swing.JOptionPane.YES_NO_OPTION,
                javax.swing.JOptionPane.WARNING_MESSAGE
            );
            if (confirmOverwrite != javax.swing.JOptionPane.YES_OPTION) {
                return;
            }
        }

        final File finalFile = file;
        exportButton.setEnabled(false);
        exportButton.setText("⏳ Exporting...");

        new Thread(() -> {
            try {
                ExcelExporter.exportWeek(finalFile.getAbsolutePath(), startDate, days);
                javax.swing.SwingUtilities.invokeLater(() -> {
                    exportButton.setEnabled(true);
                    exportButton.setText("📊 Export to Excel");
                    javax.swing.JOptionPane.showMessageDialog(Main.this,
                            "Schedule exported successfully to:\n" + finalFile.getAbsolutePath(),
                            "Export Complete",
                            javax.swing.JOptionPane.INFORMATION_MESSAGE);
                });
            } catch (Exception ex) {
                ex.printStackTrace();
                javax.swing.SwingUtilities.invokeLater(() -> {
                    exportButton.setEnabled(true);
                    exportButton.setText("📊 Export to Excel");
                    javax.swing.JOptionPane.showMessageDialog(Main.this,
                            "Export failed: " + ex.getMessage(),
                            "Export Error",
                            javax.swing.JOptionPane.ERROR_MESSAGE);
                });
            }
        }, "ExcelExport").start();
    }

    private void importSchedule() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Import from Excel");
        chooser.setFileFilter(new FileNameExtensionFilter("Excel Workbook (*.xlsx)", "xlsx"));

        int result = chooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) return;

        File file = chooser.getSelectedFile();
        if (!file.exists()) {
            javax.swing.JOptionPane.showMessageDialog(this, "File not found.", "Error", javax.swing.JOptionPane.ERROR_MESSAGE);
            return;
        }

        int importTypeChoice = javax.swing.JOptionPane.showOptionDialog(this,
                "What would you like to import from this Excel file?",
                "Import Type",
                javax.swing.JOptionPane.YES_NO_CANCEL_OPTION,
                javax.swing.JOptionPane.QUESTION_MESSAGE,
                null,
                new String[]{"Schedule & Content", "Content Only", "Cancel"},
                "Schedule & Content");

        if (importTypeChoice == 2 || importTypeChoice == javax.swing.JOptionPane.CLOSED_OPTION) {
            return;
        }

        boolean contentOnly = (importTypeChoice == 1);

        importButton.setEnabled(false);
                    refreshAll();
                });
            } catch (Exception ex) {
                ex.printStackTrace();
                javax.swing.SwingUtilities.invokeLater(() -> {
                    importButton.setEnabled(true);
                    importButton.setText("📥 Import from Excel");
                    javax.swing.JOptionPane.showMessageDialog(Main.this,
                            "Import failed: " + ex.getMessage(),
                            "Import Error",
        importButton.setText("⏳ Importing...");

        new Thread(() -> {
            try {
                showtime.manager.utils.ExcelImporter.importExcelFile(this, file, contentOnly);
                
                javax.swing.SwingUtilities.invokeLater(() -> {
                    importButton.setEnabled(true);
                    importButton.setText("📥 Import from Excel");
                            javax.swing.JOptionPane.ERROR_MESSAGE);
                });
            }
        }, "ExcelImport").start();
    }

    // ==================== VERIFICATION METHODS ====================

    private void verifyTransferHandler() {
        System.out.println("\n=== TransferHandler Verification ===");
        if (scrollableTable != null) {
            System.out.println("Table TransferHandler: " + scrollableTable.getTransferHandler());
            System.out.println("Table Drag Enabled: " + scrollableTable.getDragEnabled());
            System.out.println("Table Drop Mode: " + scrollableTable.getDropMode());
            System.out.println("Table Row Count: " + scrollableTable.getRowCount());
        }

        if (folderPanel != null && folderPanel.getTree() != null) {
            System.out.println("Tree TransferHandler: " + folderPanel.getTree().getTransferHandler());
            System.out.println("Tree Drag Enabled: " + folderPanel.getTree().getDragEnabled());
        }
        System.out.println("==================================\n");
    }

    // Getters for TransferHandler to access selected item info
    public ScheduleItem getSelectedScheduleItem() {
        return selectedScheduleItem;
    }

    public int getSelectedScheduleRow() {
        return selectedScheduleRow;
    }

    public int getSelectedSchedulePosition() {
        return selectedSchedulePosition;
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ReflectiveOperationException | javax.swing.UnsupportedLookAndFeelException ex) {
            logger.log(java.util.logging.Level.SEVERE, null, ex);
        }

        java.awt.EventQueue.invokeLater(() -> {
            Main main = new Main();
            main.setVisible(true);
        });

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            DatabaseManager.close();
        }));
    }

    // ==================== VARIABLES DECLARATION ====================
    private javax.swing.JPanel ButtonPanel;
    private javax.swing.JPanel DetailsPanel;
    private javax.swing.JPanel FolderPanel;
    private javax.swing.JPanel ListFolderPanel;
    private javax.swing.JTextField SearchTextField;
    private javax.swing.JPanel ShowtimePanel;
    private javax.swing.JPanel UploadPanel;
    private javax.swing.JButton refreshButton;
    private javax.swing.JButton exportButton;
    private javax.swing.JButton importButton;
    private javax.swing.JButton deleteDbButton;
    private TimeHeaderPanel timeHeader;

    private static class TimeHeaderPanel extends javax.swing.JPanel {
        public TimeHeaderPanel() {
            setBackground(new Color(35, 35, 35));
        }

        @Override
        public Dimension getPreferredSize() {
            int zoom = showtime.manager.renderer.ShowtimeTableRenderer.pixelsPerHour;
            return new Dimension(zoom * 24 + 100, 30);
        }

        @Override
        public Dimension getMinimumSize() {
            return getPreferredSize();
        }

        @Override
        public Dimension getMaximumSize() {
            return getPreferredSize();
        }

        @Override
        protected void paintComponent(java.awt.Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Day Column Space (The first 100px)
            g2.setColor(new Color(35, 35, 35));
            g2.fillRect(0, 0, 100, getHeight());

            // Timeline Start (Offset of 100px for Day column)
            int timelineOffset = 100;
            double pixelsPerSecond = showtime.manager.renderer.ShowtimeTableRenderer.pixelsPerHour / 3600.0;
            g2.setFont(new Font("Arial", Font.PLAIN, 10));

            // Draw hour markers and labels
            for (int hour = 0; hour < 24; hour++) {
                int x = timelineOffset + (int) (hour * 3600 * pixelsPerSecond);

                // Hour marker line
                g2.setColor(new Color(80, 80, 80));
                g2.drawLine(x, 15, x, getHeight());

                // Hour label
                g2.setColor(new Color(0, 173, 181));
                String label;
                if (hour == 0)
                    label = "6 AM";
                else if (hour < 6)
                    label = (hour) + " AM";
                else if (hour == 6)
                    label = "12 PM";
                else if (hour < 12)
                    label = (hour) + " PM";
                else if (hour == 12)
                    label = "6 PM";
                else if (hour < 18)
                    label = (hour - 12) + " PM";
                else if (hour == 18)
                    label = "12 AM";
                else
                    label = (hour - 12) + " AM";
                g2.drawString(label, x + 2, 12);

                // Sub-hour markers (every 15 mins)
                g2.setColor(new Color(50, 50, 50));
                for (int min = 15; min < 60; min += 15) {
                    int mx = x + (int) (min * 60 * pixelsPerSecond);
                    g2.drawLine(mx, 22, mx, getHeight());
                }
            }

            // Draw midnight marker
            int midnightX = timelineOffset + (int) (ScheduleManager.MIDNIGHT_SECONDS * pixelsPerSecond);
            g2.setColor(new Color(200, 100, 100));
            g2.setStroke(new BasicStroke(2));
            g2.drawLine(midnightX, 0, midnightX, getHeight());
            g2.setColor(new Color(200, 100, 100, 200));
            g2.drawString("🌙", midnightX - 10, 20);

            // Bottom border
            g2.setColor(new Color(60, 60, 60));
            g2.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
        }

    }

    // Force a complete refresh of the schedule table
    private void forceScheduleRefresh() {
        if (scrollableTable != null && showtimeModel != null) {
            // Reload the model data
            showtimeModel.loadScheduleForWeek();

            // Force table to repaint completely
            scrollableTable.revalidate();
            scrollableTable.repaint();

            // Also refresh the fixed column table
            if (fixedColumnTable != null) {
                fixedColumnTable.revalidate();
                fixedColumnTable.repaint();
            }

            // Refresh the scroll pane
            if (scrollPane != null) {
                scrollPane.revalidate();
                scrollPane.repaint();
                if (scrollPane.getColumnHeader() != null) {
                    scrollPane.getColumnHeader().revalidate();
                    scrollPane.getColumnHeader().repaint();
                }
            }

            System.out.println("Schedule table force refreshed");
        }
    }

}

// Main.java