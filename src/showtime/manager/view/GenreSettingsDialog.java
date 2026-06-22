package showtime.manager.view;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.util.Map;
import showtime.manager.model.DatabaseManager;

/**
 * Dialog for managing genre (content type) colors.
 */
public class GenreSettingsDialog extends JDialog {
    private JTable table;
    private DefaultTableModel model;
    private Map<String, String> colors;

    public GenreSettingsDialog(Frame parent) {
        super(parent, "Genre Color Settings", true);
        initComponents();
        loadData();
    }

    private void initComponents() {
        setLayout(new BorderLayout());
        getContentPane().setBackground(new Color(30, 30, 30));
        setSize(400, 500);
        setLocationRelativeTo(getOwner());

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(45, 45, 45));
        header.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));
        JLabel title = new JLabel("Customize Genre Colors");
        title.setFont(new Font("Arial", Font.BOLD, 14));
        title.setForeground(Color.WHITE);
        header.add(title, BorderLayout.WEST);
        add(header, BorderLayout.NORTH);

        // Table
        String[] cols = {"Genre Name", "Color"};
        model = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 1; // Only color column is clickable
            }
        };

        table = new JTable(model);
        table.setBackground(new Color(30, 30, 30));
        table.setForeground(Color.WHITE);
        table.setRowHeight(35);
        table.setGridColor(new Color(45, 45, 45));
        table.setSelectionBackground(new Color(0, 173, 181, 60));
        table.setFillsViewportHeight(true);
        
        // Custom renderer for the color column
        table.getColumnModel().getColumn(1).setCellRenderer(new ColorRenderer());
        
        // Add click listener for the color column
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                int col = table.columnAtPoint(e.getPoint());
                if (col == 1 && row >= 0) {
                    pickColor(row);
                }
            }
        });

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(new Color(30, 30, 30));
        add(scroll, BorderLayout.CENTER);

        // Buttons
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        footer.setBackground(new Color(30, 30, 30));
        footer.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JButton saveBtn = new JButton("Save Changes");
        saveBtn.setBackground(new Color(0, 173, 181));
        saveBtn.setForeground(Color.WHITE);
        saveBtn.setFont(new Font("Arial", Font.BOLD, 12));
        saveBtn.addActionListener(e -> saveChanges());

        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> dispose());

        footer.add(cancelBtn);
        footer.add(saveBtn);
        add(footer, BorderLayout.SOUTH);
    }

    private void loadData() {
        model.setRowCount(0);
        colors = DatabaseManager.getContentTypeColors();
        for (Map.Entry<String, String> entry : colors.entrySet()) {
            model.addRow(new Object[]{entry.getKey(), entry.getValue()});
        }
    }

    private void pickColor(int row) {
        String currentHex = (String) model.getValueAt(row, 1);
        Color initial = Color.decode(currentHex);
        Color chosen = JColorChooser.showDialog(this, "Pick Color for " + model.getValueAt(row, 0), initial);
        if (chosen != null) {
            String hex = String.format("#%02x%02x%02x", chosen.getRed(), chosen.getGreen(), chosen.getBlue());
            model.setValueAt(hex, row, 1);
        }
    }

    private void saveChanges() {
        for (int i = 0; i < model.getRowCount(); i++) {
            String name = (String) model.getValueAt(i, 0);
            String hex = (String) model.getValueAt(i, 1);
            DatabaseManager.updateContentTypeColor(name, hex);
        }
        dispose();
    }

    // Helper class to render color squares
    private static class ColorRenderer extends JPanel implements TableCellRenderer {
        public ColorRenderer() {
            setOpaque(true);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            try {
                setBackground(Color.decode((String) value));
            } catch (Exception e) {
                setBackground(Color.GRAY);
            }
            // Add some padding
            setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(4, 10, 4, 10, table.getBackground()),
                BorderFactory.createLineBorder(Color.WHITE, 1)
            ));
            return this;
        }
    }
}
