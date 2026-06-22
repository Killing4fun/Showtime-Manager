package showtime.manager.renderer;

import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import showtime.manager.model.DatabaseManager;
import showtime.manager.model.ScheduleItem;
import showtime.manager.utils.ScheduleManager;

public class ShowtimeTableRenderer implements TableCellRenderer {
    private TimelinePanel timelinePanel = new TimelinePanel();
    public static int pixelsPerHour = 400; // Default zoom level (increased for better visibility)

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
            int row, int column) {
        // Ensure colors are loaded
        if (TimelinePanel.genreColors.isEmpty()) {
            TimelinePanel.refreshColors();
        }
        if (value instanceof List) {
            @SuppressWarnings("unchecked")
            List<ScheduleItem> items = (List<ScheduleItem>) value;
            timelinePanel.setItems(items);
            // Set dynamic width for the timeline column based on zoom
            timelinePanel.setPreferredSize(new Dimension(pixelsPerHour * 24, 70));
        } else {
            timelinePanel.setItems(null);
        }
        timelinePanel.setBackground(isSelected ? new Color(0, 173, 181, 60) : new Color(18, 18, 18));
        return timelinePanel;
    }

    public static class TimelinePanel extends JPanel {
        private List<ScheduleItem> items;
        private static final Color DEFAULT_BLOCK_COLOR = new Color(66, 66, 66);
        public static final Map<String, Color> genreColors = new HashMap<>();

        public static void refreshColors() {
            Map<String, String> dbColors = DatabaseManager.getContentTypeColors();
            genreColors.clear();
            for (Map.Entry<String, String> entry : dbColors.entrySet()) {
                try {
                    genreColors.put(entry.getKey().toLowerCase(), Color.decode(entry.getValue()));
                } catch (NumberFormatException e) {
                    genreColors.put(entry.getKey().toLowerCase(), DEFAULT_BLOCK_COLOR);
                }
            }
        }

        public void setItems(List<ScheduleItem> items) {
            this.items = items;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            // Draw background first
            g.setColor(new Color(25, 25, 25));
            g.fillRect(0, 0, getWidth(), getHeight());

            if (items == null || items.isEmpty()) {
                // Draw empty message
                g.setColor(Color.GRAY);
                g.setFont(new Font("Arial", Font.ITALIC, 12));
                g.drawString("-- Empty --", 10, getHeight() / 2);
                return;
            }

            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int height = getHeight();
            double pixelsPerSecond = pixelsPerHour / 3600.0;

            // Draw hour lines and labels
            g2.setColor(new Color(45, 45, 45));
            for (int hour = 0; hour < 24; hour++) {
                int x = (int) (hour * 3600 * pixelsPerSecond);
                g2.drawLine(x, 0, x, height);

                // Draw hour labels at the top
                g2.setColor(new Color(150, 150, 150));
                g2.setFont(new Font("Arial", Font.PLAIN, 9));
                String label;
                if (hour == 0)
                    label = "6 AM";
                else if (hour == 6)
                    label = "12 PM";
                else if (hour == 12)
                    label = "6 PM";
                else if (hour == 18)
                    label = "12 AM";
                else
                    label = "";
                g2.drawString(label, x + 2, 12);
            }

            // Draw quarter-hour markers
            g2.setColor(new Color(35, 35, 35));
            for (int hour = 0; hour < 24; hour++) {
                for (int min = 15; min < 60; min += 15) {
                    int x = (int) ((hour * 3600 + min * 60) * pixelsPerSecond);
                    g2.drawLine(x, 15, x, height);
                }
            }

            // Draw midnight marker
            int midnightX = (int) (ScheduleManager.MIDNIGHT_SECONDS * pixelsPerSecond);
            g2.setColor(new Color(200, 100, 100));
            g2.setStroke(new BasicStroke(2));
            g2.drawLine(midnightX, 0, midnightX, height);
            g2.setColor(new Color(200, 100, 100, 200));
            g2.setFont(new Font("Arial", Font.BOLD, 10));
            g2.drawString("🌙 MIDNIGHT", midnightX + 5, 25);

            // Draw each schedule block
            int currentX = 0;
            for (int i = 0; i < items.size(); i++) {
                ScheduleItem item = items.get(i);
                int startSeconds = ScheduleManager.getSecondsSinceBroadcastStart(item.getStartTime());
                int endSeconds = startSeconds + item.getDurationSeconds();

                if (endSeconds > ScheduleManager.TOTAL_SECONDS_IN_DAY) {
                    endSeconds = ScheduleManager.TOTAL_SECONDS_IN_DAY;
                }

                int startX = (int) (startSeconds * pixelsPerSecond);
                int blockWidth = (int) ((endSeconds - startSeconds) * pixelsPerSecond);

                // Minimum width for visibility — but never overlap the next block
                if (blockWidth < 10) {
                    // Find the next block's startX so we don't paint over it
                    int maxAllowedWidth = 10;
                    if (i + 1 < items.size()) {
                        ScheduleItem next = items.get(i + 1);
                        int nextStartSeconds = ScheduleManager.getSecondsSinceBroadcastStart(next.getStartTime());
                        int nextStartX = (int) (nextStartSeconds * pixelsPerSecond);
                        maxAllowedWidth = Math.max(1, nextStartX - startX);
                    }
                    blockWidth = Math.min(10, maxAllowedWidth);
                }

                // Choose color based on type
                Color blockColor = DEFAULT_BLOCK_COLOR;
                String typeKey = "";
                
                if (item.getVideoType() != null && !item.getVideoType().isEmpty()) {
                    typeKey = item.getVideoType().toLowerCase();
                } else if (item.getSeriesName() != null && !item.getSeriesName().isEmpty()) {
                    typeKey = item.getSeriesName().toLowerCase();
                }

                if (!typeKey.isEmpty()) {
                    // Try exact match or partial match
                    if (genreColors.containsKey(typeKey)) {
                        blockColor = genreColors.get(typeKey);
                    } else {
                        // Look for a genre that is contained within the string
                        for (Map.Entry<String, Color> entry : genreColors.entrySet()) {
                            if (typeKey.contains(entry.getKey())) {
                                blockColor = entry.getValue();
                                break;
                            }
                        }
                    }
                }

                // Draw block
                g2.setColor(blockColor);
                g2.fillRoundRect(startX, 5, blockWidth, height - 10, 10, 10);

                // Draw border
                g2.setColor(new Color(255, 255, 255, 80));
                g2.drawRoundRect(startX, 5, blockWidth, height - 10, 10, 10);

                // Draw text inside block
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("Arial", Font.BOLD, 10));
                FontMetrics fm = g2.getFontMetrics();
                int lineHeight = fm.getHeight();

                String title = item.getDisplayName();
                String dur = ScheduleManager.formatDuration(item.getDurationSeconds());

                // Clip text to block
                Shape oldClip = g2.getClip();
                int clipWidth = Math.max(0, blockWidth - 10);
                g2.clipRect(startX + 5, 5, clipWidth, height - 10);

                int textY = 18;
                g2.drawString(title, startX + 5, textY);

                g2.setFont(new Font("Arial", Font.PLAIN, 9));
                textY += lineHeight + 2;
                
                String durText = dur;
                if (item.isLoop()) {
                    g2.setColor(new Color(255, 235, 59)); // Bright yellow for contrast
                    g2.setFont(new Font("Arial", Font.BOLD, 9));
                    g2.drawString(" [LOOP]", startX + 5, textY);
                    
                    int loopLabelWidth = g2.getFontMetrics().stringWidth(" [LOOP]");
                    g2.setColor(Color.WHITE);
                    g2.setFont(new Font("Arial", Font.PLAIN, 9));
                    g2.drawString(" - " + durText, startX + 5 + loopLabelWidth, textY);
                } else {
                    g2.drawString(durText, startX + 5, textY);
                }

                g2.setClip(oldClip);

                currentX = startX + blockWidth;
            }
        }

        @Override
        public Dimension getPreferredSize() {
            // Dynamic width based on zoom level: pixelsPerHour * 24 hours
            return new Dimension(pixelsPerHour * 24, 70);
        }

        @Override
        public Dimension getMinimumSize() {
            return getPreferredSize();
        }

        @Override
        public Dimension getMaximumSize() {
            return getPreferredSize();
        }

    }
}

//ShowtimeTableModel