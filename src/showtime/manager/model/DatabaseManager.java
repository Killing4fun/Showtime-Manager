package showtime.manager.model;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DatabaseManager {
    private static final String DB_URL;
    
    static {
        String os = System.getProperty("os.name").toLowerCase();
        String userHome = System.getProperty("user.home");
        java.io.File dir;

        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            if (appData == null) {
                appData = userHome;
            }
            dir = new java.io.File(appData, "ShowtimeManagerData");
        } else if (os.contains("mac")) {
            // On Mac, standard hidden app data goes to Library/Application Support
            // The Library folder is hidden by default in modern macOS
            dir = new java.io.File(userHome, "Library/Application Support/ShowtimeManagerData");
        } else {
            // Linux/Unix: use a hidden folder in home directory
            dir = new java.io.File(userHome, ".showtimemanager");
        }

        if (!dir.exists()) {
            dir.mkdirs();
            if (os.contains("win")) {
                try {
                    // Hide the directory on Windows
                    java.nio.file.Files.setAttribute(dir.toPath(), "dos:hidden", true);
                } catch (Exception e) {
                    // Ignore
                }
            }
        }
        
        java.io.File newDbFile = new java.io.File(dir, "showtime.db");
        java.io.File oldDbFile = new java.io.File("showtime.db");
        
        // Migrate old database if it exists and new one doesn't
        if (oldDbFile.exists() && !newDbFile.exists()) {
            try {
                java.nio.file.Files.move(oldDbFile.toPath(), newDbFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                System.out.println("Migrated existing database to hidden folder: " + dir.getAbsolutePath());
            } catch (Exception e) {
                System.err.println("Failed to migrate database: " + e.getMessage());
            }
        }
        
        DB_URL = "jdbc:sqlite:" + newDbFile.getAbsolutePath().replace('\\', '/');
    }

    private static Connection connection = null;
    private static boolean isInitialized = false;

    // Initialize database
    public static synchronized void initialize() {
        // Force re-initialize if connection is closed
        try {
            if (connection != null && connection.isClosed()) {
                System.out.println("Connection was closed, reconnecting...");
                connection = null;
                isInitialized = false;
            }
        } catch (SQLException e) {
            // Ignore
        }

        if (isInitialized && connection != null) {
            System.out.println("Database already initialized");
            return;
        }

        System.out.println("\n=== Initializing Database ===");

        try {
            // First, try to load the driver
            System.out.println("1. Loading SQLite JDBC driver...");
            Class.forName("org.sqlite.JDBC");
            System.out.println("   ✅ Driver loaded successfully");

            // Then create connection
            System.out.println("2. Creating database connection...");
            connection = DriverManager.getConnection(DB_URL);
            System.out.println("   ✅ Connected to database: " + DB_URL);

            // Set foreign keys support
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA foreign_keys = ON;");
                System.out.println("   ✅ Foreign keys enabled");
            }

            // Set journal mode for better performance
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA journal_mode = WAL;");
            }

            // Create tables
            System.out.println("3. Creating tables...");
            createTables();

            // Run migrations
            System.out.println("4. Running migrations...");
            migrateDatabase();

            isInitialized = true;
            System.out.println("✅ Database initialization complete!\n");

        } catch (ClassNotFoundException e) {
            System.err.println("\n❌ ERROR: SQLite JDBC driver not found!");
            e.printStackTrace();

        } catch (SQLException e) {
            System.err.println("\n❌ ERROR: Failed to connect to database!");
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void createTables() {
        if (connection == null) {
            System.err.println("❌ No database connection!");
            return;
        }

        String[] createStatements = {
                // Create content_type table
                """
                        CREATE TABLE IF NOT EXISTS content_type (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            name TEXT UNIQUE NOT NULL
                        );
                        """,

                // Create series table
                """
                        CREATE TABLE IF NOT EXISTS series (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            type_id INTEGER NOT NULL,
                            name TEXT NOT NULL,
                            FOREIGN KEY (type_id) REFERENCES content_type(id) ON DELETE CASCADE
                        );
                        """,

                // Create season table
                """
                        CREATE TABLE IF NOT EXISTS season (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            series_id INTEGER NOT NULL,
                            season_number INTEGER NOT NULL,
                            FOREIGN KEY (series_id) REFERENCES series(id) ON DELETE CASCADE
                        );
                        """,

                // Create video table
                """
                        CREATE TABLE IF NOT EXISTS video (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            season_id INTEGER NOT NULL,
                            episode_number INTEGER NOT NULL,
                            name TEXT NOT NULL,
                            description TEXT,
                            audio_langs TEXT,
                            sub_langs TEXT,
                            remarks TEXT,
                            has_seasons INTEGER DEFAULT 1,
                            duration INTEGER,
                            upload_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                            FOREIGN KEY (season_id) REFERENCES season(id) ON DELETE CASCADE
                        );
                        """,

                // Create schedule table
                """
                        CREATE TABLE IF NOT EXISTS schedule (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            video_id INTEGER NOT NULL,
                            start_time TEXT NOT NULL,
                            end_time TEXT NOT NULL,
                            schedule_date TEXT NOT NULL,
                            day_of_week TEXT NOT NULL,
                            status TEXT DEFAULT 'Scheduled',
                            duration INTEGER DEFAULT 0,
                            is_loop INTEGER DEFAULT 0,
                            remarks TEXT,
                            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                            FOREIGN KEY (video_id) REFERENCES video(id) ON DELETE CASCADE,
                            UNIQUE(schedule_date, start_time)  -- This prevents duplicate entries
                        );
                        """
        };

        try {
            int tableCount = 0;
            for (String sql : createStatements) {
                if (!sql.trim().isEmpty()) {
                    try (Statement stmt = connection.createStatement()) {
                        stmt.execute(sql);
                        tableCount++;
                    }
                }
            }
            System.out.println("   ✅ Created/verified " + tableCount + " tables");

            // Insert default content types
            insertDefaultContentTypes();

            // Verify tables
            verifyTables();

        } catch (SQLException e) {
            System.err.println("❌ Error creating tables!");
            e.printStackTrace();
        }
    }

    private static void insertDefaultContentTypes() {
        String[] defaultTypes = { };
        int added = 0;

        for (String type : defaultTypes) {
            String sql = "INSERT OR IGNORE INTO content_type (name) VALUES (?)";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, type);
                int rows = pstmt.executeUpdate();
                if (rows > 0) {
                    added++;
                }
            } catch (SQLException e) {
                System.err.println("Error inserting default content type: " + type);
            }
        }
        if (added > 0) {
            System.out.println("   ✅ Added " + added + " default content types");
        }
    }

    private static void migrateDatabase() {
        if (connection == null)
            return;

        try (Statement stmt = connection.createStatement()) {
            DatabaseMetaData meta = connection.getMetaData();

            // Check for audio_langs in video table
            ResultSet rs = meta.getColumns(null, null, "video", "audio_langs");
            if (!rs.next()) {
                System.out.println("   ➤ Adding 'audio_langs' column to 'video' table...");
                stmt.execute("ALTER TABLE video ADD COLUMN audio_langs TEXT;");
                System.out.println("     ✅ Column added");
            }
            rs.close();

            // Check for sub_langs in video table
            rs = meta.getColumns(null, null, "video", "sub_langs");
            if (!rs.next()) {
                System.out.println("   ➤ Adding 'sub_langs' column to 'video' table...");
                stmt.execute("ALTER TABLE video ADD COLUMN sub_langs TEXT;");
                System.out.println("     ✅ Column added");
            }
            rs.close();

            // Check for remarks in video table
            rs = meta.getColumns(null, null, "video", "remarks");
            if (!rs.next()) {
                System.out.println("   ➤ Adding 'remarks' column to 'video' table...");
                stmt.execute("ALTER TABLE video ADD COLUMN remarks TEXT;");
                System.out.println("     ✅ Column added");
            }
            rs.close();

            // Check for duration in schedule table
            rs = meta.getColumns(null, null, "schedule", "duration");
            if (!rs.next()) {
                System.out.println("   ➤ Adding 'duration' column to 'schedule' table...");
                stmt.execute("ALTER TABLE schedule ADD COLUMN duration INTEGER DEFAULT 0;");
                System.out.println("     ✅ Column added");
            }
            rs.close();

            // Check for is_loop in schedule table
            rs = meta.getColumns(null, null, "schedule", "is_loop");
            if (!rs.next()) {
                System.out.println("   ➤ Adding 'is_loop' column to 'schedule' table...");
                stmt.execute("ALTER TABLE schedule ADD COLUMN is_loop INTEGER DEFAULT 0;");
                System.out.println("     ✅ Column added");
            }
            rs.close();

            // Check for remarks in schedule table
            rs = meta.getColumns(null, null, "schedule", "remarks");
            if (!rs.next()) {
                System.out.println("   ➤ Adding 'remarks' column to 'schedule' table...");
                stmt.execute("ALTER TABLE schedule ADD COLUMN remarks TEXT;");
                System.out.println("     ✅ Column added");
            }
            rs.close();
            
            // Check for color in content_type table
            rs = meta.getColumns(null, null, "content_type", "color");
            if (!rs.next()) {
                System.out.println("   ➤ Adding 'color' column to 'content_type' table...");
                stmt.execute("ALTER TABLE content_type ADD COLUMN color TEXT;");
                System.out.println("     ✅ Column added");
                
                // Set default colors for standard genres
                stmt.execute("UPDATE content_type SET color = '#FF8F00' WHERE LOWER(name) LIKE '%drama%';");
                stmt.execute("UPDATE content_type SET color = '#2979FF' WHERE LOWER(name) LIKE '%kpop%';");
                stmt.execute("UPDATE content_type SET color = '#D500F9' WHERE LOWER(name) LIKE '%movie%';");
                stmt.execute("UPDATE content_type SET color = '#424242' WHERE color IS NULL;");
            }
            rs.close();
            
            // Check for has_seasons in video table
            rs = meta.getColumns(null, null, "video", "has_seasons");
            if (!rs.next()) {
                System.out.println("   ➤ Adding 'has_seasons' column to 'video' table...");
                stmt.execute("ALTER TABLE video ADD COLUMN has_seasons INTEGER DEFAULT 1;");
                System.out.println("     ✅ Column added");
            }
            rs.close();

        } catch (SQLException e) {
            System.err.println("❌ Error during migration!");
            e.printStackTrace();
        }
    }

    private static void verifyTables() {
        try {
            DatabaseMetaData meta = connection.getMetaData();
            String[] tables = { "content_type", "series", "season", "video", "schedule" };

            System.out.println("   === Table Verification ===");
            for (String tableName : tables) {
                ResultSet rs = meta.getTables(null, null, tableName, null);
                if (rs.next()) {
                    System.out.println("     ✓ " + tableName);
                } else {
                    System.err.println("     ✗ " + tableName + " MISSING!");
                }
                rs.close();
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Add the missing tableExists method
    public static boolean tableExists(String tableName) {
        // First ensure we're connected
        if (!isConnected()) {
            // Try to re-initialize
            initialize();
            if (!isConnected()) {
                return false;
            }
        }

        try {
            DatabaseMetaData meta = connection.getMetaData();
            ResultSet rs = meta.getTables(null, null, tableName, null);
            boolean exists = rs.next();
            rs.close();
            return exists;
        } catch (SQLException e) {
            System.err.println("Error checking if table exists: " + tableName);
            return false;
        }
    }

    // Add debugPrintTables method
    public static void debugPrintTables() {
        // First ensure we're connected
        if (!isConnected()) {
            initialize();
            if (!isConnected()) {
                System.err.println("Cannot debug tables: No database connection");
                return;
            }
        }

        try {
            DatabaseMetaData meta = connection.getMetaData();
            ResultSet rs = meta.getTables(null, null, "%", new String[] { "TABLE" });

            System.out.println("\n=== Tables in Database ===");
            int count = 0;
            while (rs.next()) {
                String tableName = rs.getString("TABLE_NAME");
                System.out.println("  " + (count + 1) + ". " + tableName);
                count++;
            }
            System.out.println("Total tables: " + count);
            System.out.println("===========================\n");
            rs.close();

        } catch (SQLException e) {
            System.err.println("Error listing tables!");
            e.printStackTrace();
        }
    }

    // Get connection (public accessor) - synchronized to prevent race conditions
    public static synchronized Connection getConnection() throws SQLException {
        // Check if connection is valid
        try {
            if (connection == null || connection.isClosed()) {
                System.out.println("Reconnecting to database...");
                initialize();
            } else {
                // Test connection
                try (Statement stmt = connection.createStatement()) {
                    stmt.executeQuery("SELECT 1");
                }
            }
        } catch (SQLException e) {
            System.out.println("Connection lost, reinitializing...");
            initialize();
        }

        if (connection == null) {
            throw new SQLException("Unable to establish database connection");
        }

        return connection;
    }

    // Delete the database file
    public static boolean deleteDatabase() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            System.err.println("Error closing connection before deleting DB: " + e.getMessage());
        }

        String dbPath = DB_URL.replace("jdbc:sqlite:", "");
        java.io.File dbFile = new java.io.File(dbPath);
        if (dbFile.exists()) {
            boolean deleted = dbFile.delete();
            if (deleted) {
                System.out.println("Database file deleted: " + dbFile.getAbsolutePath());
                return true;
            } else {
                System.err.println("Could not delete database file: " + dbFile.getAbsolutePath());
                return false;
            }
        }
        return true; // Already doesn't exist
    }

    // Check if connection is valid - synchronized
    public static synchronized boolean isConnected() {
        try {
            if (connection == null) {
                return false;
            }
            if (connection.isClosed()) {
                return false;
            }
            // Test the connection with a simple query
            try (Statement stmt = connection.createStatement()) {
                stmt.executeQuery("SELECT 1");
            }
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    // Ensure connection is alive
    public static synchronized void ensureConnection() {
        try {
            if (!isConnected()) {
                System.out.println("Connection lost, reconnecting...");
                initialize();
            }
        } catch (Exception e) {
            initialize();
        }
    }

    // Get all content types
    public static List<String> getContentTypes() {
        List<String> types = new ArrayList<>();

        ensureConnection();

        if (!isConnected()) {
            System.err.println("❌ Cannot get content types: No database connection");
            return types;
        }

        String sql = "SELECT name FROM content_type ORDER BY name";

        try (Statement stmt = connection.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                types.add(rs.getString("name"));
            }
            System.out.println("📋 Loaded " + types.size() + " content types");

        } catch (SQLException e) {
            System.err.println("❌ Error getting content types!");
            e.printStackTrace();
        }

        return types;
    }

    // Get all content types and their colors
    public static Map<String, String> getContentTypeColors() {
        Map<String, String> colors = new HashMap<>();
        ensureConnection();
        if (!isConnected())
            return colors;

        String sql = "SELECT name, color FROM content_type";
        try (Statement stmt = connection.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String name = rs.getString("name");
                String color = rs.getString("color");
                colors.put(name, color != null ? color : "#424242");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return colors;
    }

    // Update color for a content type
    public static boolean updateContentTypeColor(String name, String colorHex) {
        ensureConnection();
        if (!isConnected())
            return false;

        String sql = "UPDATE content_type SET color = ? WHERE name = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, colorHex);
            pstmt.setString(2, name);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // Add new content type
    public static boolean addContentType(String name) {
        ensureConnection();

        if (!isConnected()) {
            System.err.println("❌ Cannot add content type: No database connection");
            return false;
        }

        // Check if already exists
        String checkSql = "SELECT COUNT(*) FROM content_type WHERE name = ?";
        try (PreparedStatement checkStmt = connection.prepareStatement(checkSql)) {
            checkStmt.setString(1, name);
            ResultSet rs = checkStmt.executeQuery();
            if (rs.next() && rs.getInt(1) > 0) {
                System.out.println("ℹ️ Content type '" + name + "' already exists");
                return false;
            }
        } catch (SQLException e) {
            System.err.println("Error checking content type: " + e.getMessage());
        }

        // Insert new content type with default gray color
        String sql = "INSERT INTO content_type (name, color) VALUES (?, '#424242')";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, name);
            int rows = pstmt.executeUpdate();

            if (rows > 0) {
                System.out.println("✅ Added new content type: " + name);
                return true;
            }
        } catch (SQLException e) {
            System.err.println("❌ Error adding content type: " + name);
            System.err.println("Error: " + e.getMessage());
        }
        return false;
    }

    // Get type ID
    public static int getTypeId(String typeName) {
        ensureConnection();
        if (!isConnected())
            return -1;

        String sql = "SELECT id FROM content_type WHERE name = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, typeName);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("id");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    // Get all series for a specific type
    public static List<String> getSeriesByType(int typeId) {
        List<String> seriesList = new ArrayList<>();
        ensureConnection();
        if (!isConnected())
            return seriesList;

        String sql = "SELECT name FROM series WHERE type_id = ? ORDER BY name";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, typeId);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                seriesList.add(rs.getString("name"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return seriesList;
    }

    // Get series ID by name and type
    public static int getSeriesId(String seriesName, int typeId) {
        ensureConnection();
        if (!isConnected())
            return -1;

        String sql = "SELECT id FROM series WHERE name = ? AND type_id = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, seriesName);
            pstmt.setInt(2, typeId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return rs.getInt("id");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return -1;
    }

    // Create new series
    public static int createSeries(String seriesName, int typeId) {
        ensureConnection();
        if (!isConnected())
            return -1;

        String sql = "INSERT INTO series (type_id, name) VALUES (?, ?)";

        try (PreparedStatement pstmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setInt(1, typeId);
            pstmt.setString(2, seriesName);
            int rows = pstmt.executeUpdate();

            if (rows > 0) {
                ResultSet rs = pstmt.getGeneratedKeys();
                if (rs.next()) {
                    int id = rs.getInt(1);
                    System.out.println("✅ Created new series: " + seriesName);
                    return id;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return -1;
    }

    // Get or create season
    public static int getOrCreateSeason(int seriesId, int seasonNumber) {
        ensureConnection();
        if (!isConnected())
            return -1;

        // Check if exists
        String sql = "SELECT id FROM season WHERE series_id = ? AND season_number = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, seriesId);
            pstmt.setInt(2, seasonNumber);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("id");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // Create new season
        sql = "INSERT INTO season (series_id, season_number) VALUES (?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setInt(1, seriesId);
            pstmt.setInt(2, seasonNumber);
            int rows = pstmt.executeUpdate();

            if (rows > 0) {
                ResultSet rs = pstmt.getGeneratedKeys();
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    // Insert video
    public static boolean insertVideo(int seasonId, int episodeNumber, String name, int durationSeconds,
            String description, String audioLangs, String subLangs, String remarks, boolean hasSeasons) {
        ensureConnection();

        if (!isConnected()) {
            System.err.println("No database connection for insertVideo");
            return false;
        }

        String sql = "INSERT INTO video (season_id, episode_number, name, duration, description, audio_langs, sub_langs, remarks, has_seasons) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, seasonId);
            pstmt.setInt(2, episodeNumber);
            pstmt.setString(3, name);
            pstmt.setInt(4, durationSeconds);
            pstmt.setString(5, description);
            pstmt.setString(6, audioLangs);
            pstmt.setString(7, subLangs);
            pstmt.setString(8, remarks);
            pstmt.setInt(9, hasSeasons ? 1 : 0);
            boolean result = pstmt.executeUpdate() > 0;
            System.out.println("Inserted video: " + name + ", result: " + result);
            return result;
        } catch (SQLException e) {
            System.err.println("Error inserting video: " + name);
            e.printStackTrace();
            return false;
        }
    }

    // Get all videos for display
    public static List<VideoItem> getAllVideos() {
        List<VideoItem> videos = new ArrayList<>();

        ensureConnection();

        if (!isConnected()) {
            System.err.println("⚠️ No database connection for getAllVideos");
            return videos;
        }

        String sql = """
                    SELECT
                        v.id,
                        ct.name as type_name,
                        s.name as series_name,
                        COALESCE(se.season_number, 1) as season_number,
                        COALESCE(v.episode_number, 1) as episode_number,
                        v.name as video_name,
                        v.description,
                        v.audio_langs,
                        v.sub_langs,
                        v.remarks,
                        v.has_seasons,
                        v.duration,
                        v.upload_date
                    FROM video v
                    LEFT JOIN season se ON v.season_id = se.id
                    LEFT JOIN series s ON se.series_id = s.id
                    LEFT JOIN content_type ct ON s.type_id = ct.id
                    ORDER BY v.upload_date DESC
                """;

        try (Statement stmt = connection.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                VideoItem video = new VideoItem();
                video.setId(rs.getInt("id"));
                video.setType(rs.getString("type_name"));
                video.setSeriesName(rs.getString("series_name"));
                video.setSeasonNumber(rs.getInt("season_number"));
                video.setEpisodeNumber(rs.getInt("episode_number"));
                video.setVideoName(rs.getString("video_name"));
                video.setDurationSeconds(rs.getInt("duration"));
                video.setDescription(rs.getString("description"));
                video.setAudioLangs(rs.getString("audio_langs"));
                video.setSubLangs(rs.getString("sub_langs"));
                video.setRemarks(rs.getString("remarks"));
                video.setHasSeasons(rs.getInt("has_seasons") == 1);
                video.setUploadDate(rs.getString("upload_date"));
                videos.add(video);
            }
            System.out.println("📋 Loaded " + videos.size() + " videos");

        } catch (SQLException e) {
            System.err.println("❌ Error loading videos!");
            e.printStackTrace();
        }
        return videos;
    }

    // Get a single video by ID
    public static VideoItem getVideoById(int videoId) {
        ensureConnection();

        if (!isConnected()) {
            System.err.println("⚠️ No database connection for getVideoById");
            return null;
        }

        String sql = """
                    SELECT
                        v.id,
                        ct.name as type_name,
                        s.name as series_name,
                        COALESCE(se.season_number, 1) as season_number,
                        COALESCE(v.episode_number, 1) as episode_number,
                        v.name as video_name,
                        v.description,
                        v.audio_langs,
                        v.sub_langs,
                        v.remarks,
                        v.has_seasons,
                        v.duration,
                        v.upload_date
                    FROM video v
                    LEFT JOIN season se ON v.season_id = se.id
                    LEFT JOIN series s ON se.series_id = s.id
                    LEFT JOIN content_type ct ON s.type_id = ct.id
                    WHERE v.id = ?
                """;

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, videoId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                VideoItem video = new VideoItem();
                video.setId(rs.getInt("id"));
                video.setType(rs.getString("type_name"));
                video.setSeriesName(rs.getString("series_name"));
                video.setSeasonNumber(rs.getInt("season_number"));
                video.setEpisodeNumber(rs.getInt("episode_number"));
                video.setVideoName(rs.getString("video_name"));
                video.setDurationSeconds(rs.getInt("duration"));
                video.setDescription(rs.getString("description"));
                video.setAudioLangs(rs.getString("audio_langs"));
                video.setSubLangs(rs.getString("sub_langs"));
                video.setRemarks(rs.getString("remarks"));
                video.setHasSeasons(rs.getInt("has_seasons") == 1);
                video.setUploadDate(rs.getString("upload_date"));
                return video;
            }

        } catch (SQLException e) {
            System.err.println("❌ Error loading video with ID: " + videoId);
            e.printStackTrace();
        }
        return null;
    }

    // Get a single video by name
    public static VideoItem getVideoByName(String name) {
        ensureConnection();

        if (!isConnected()) {
            System.err.println("⚠️ No database connection for getVideoByName");
            return null;
        }

        String sql = """
                    SELECT
                        v.id,
                        ct.name as type_name,
                        s.name as series_name,
                        COALESCE(se.season_number, 1) as season_number,
                        COALESCE(v.episode_number, 1) as episode_number,
                        v.name as video_name,
                        v.description,
                        v.audio_langs,
                        v.sub_langs,
                        v.remarks,
                        v.has_seasons,
                        v.duration,
                        v.upload_date
                    FROM video v
                    LEFT JOIN season se ON v.season_id = se.id
                    LEFT JOIN series s ON se.series_id = s.id
                    LEFT JOIN content_type ct ON s.type_id = ct.id
                    WHERE v.name = ?
                    LIMIT 1
                """;

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, name);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                VideoItem video = new VideoItem();
                video.setId(rs.getInt("id"));
                video.setType(rs.getString("type_name"));
                video.setSeriesName(rs.getString("series_name"));
                video.setSeasonNumber(rs.getInt("season_number"));
                video.setEpisodeNumber(rs.getInt("episode_number"));
                video.setVideoName(rs.getString("video_name"));
                video.setDurationSeconds(rs.getInt("duration"));
                video.setDescription(rs.getString("description"));
                video.setAudioLangs(rs.getString("audio_langs"));
                video.setSubLangs(rs.getString("sub_langs"));
                video.setRemarks(rs.getString("remarks"));
                video.setHasSeasons(rs.getInt("has_seasons") == 1);
                video.setUploadDate(rs.getString("upload_date"));
                return video;
            }

        } catch (SQLException e) {
            System.err.println("❌ Error loading video with name: " + name);
            e.printStackTrace();
        }
        return null;
    }

    // Check if episode already exists
    public static boolean episodeExists(int seriesId, int seasonNumber, int episodeNumber) {
        return episodeExistsExcluding(seriesId, seasonNumber, episodeNumber, -1);
    }

    // Check if episode already exists, excluding a specific ID
    public static boolean episodeExistsExcluding(int seriesId, int seasonNumber, int episodeNumber,
            int excludeVideoId) {
        ensureConnection();
        if (!isConnected())
            return false;

        String sql = """
                    SELECT COUNT(*) FROM video v
                    JOIN season s ON v.season_id = s.id
                    WHERE s.series_id = ? AND s.season_number = ? AND v.episode_number = ? AND v.id != ?
                """;

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, seriesId);
            pstmt.setInt(2, seasonNumber);
            pstmt.setInt(3, episodeNumber);
            pstmt.setInt(4, excludeVideoId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    // Check if season exists
    public static boolean seasonExists(int seriesId, int seasonNumber) {
        ensureConnection();
        if (!isConnected())
            return false;

        String sql = "SELECT COUNT(*) FROM season WHERE series_id = ? AND season_number = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, seriesId);
            pstmt.setInt(2, seasonNumber);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    // Get episode count for series
    public static int getEpisodeCountForSeries(int seriesId) {
        ensureConnection();
        if (!isConnected())
            return 0;

        String sql = """
                    SELECT COUNT(*) FROM video v
                    JOIN season s ON v.season_id = s.id
                    WHERE s.series_id = ?
                """;

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, seriesId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    // Get next episode number for series
    public static int getNextEpisodeNumber(int seriesId) {
        ensureConnection();
        if (!isConnected())
            return 1;

        String sql = """
                    SELECT MAX(v.episode_number) FROM video v
                    JOIN season s ON v.season_id = s.id
                    WHERE s.series_id = ?
                """;

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, seriesId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) + 1;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 1;
    }

    // Get current season for series
    public static int getCurrentSeason(int seriesId) {
        ensureConnection();
        if (!isConnected())
            return 1;

        String sql = "SELECT MAX(season_number) FROM season WHERE series_id = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, seriesId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return Math.max(rs.getInt(1), 1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 1;
    }

    // Delete video by its primary key ID
    public static boolean deleteVideo(int videoId) {
        ensureConnection();
        if (!isConnected())
            return false;

        String sql = "DELETE FROM video WHERE id = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, videoId);
            boolean result = pstmt.executeUpdate() > 0;
            System.out.println("Deleted video ID=" + videoId + ", result=" + result);
            return result;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // Delete episode
    public static boolean deleteEpisode(int seasonId, int episodeNumber) {
        ensureConnection();
        if (!isConnected())
            return false;

        String sql = "DELETE FROM video WHERE season_id = ? AND episode_number = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, seasonId);
            pstmt.setInt(2, episodeNumber);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // Delete series
    public static boolean deleteSeries(int seriesId) {
        ensureConnection();
        if (!isConnected())
            return false;

        String sql = "DELETE FROM series WHERE id = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, seriesId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // Update video metadata
    public static boolean updateVideoMetadata(int videoId, int seasonId, int episodeNum, String name,
            int duration, String description, String audioLangs, String subLangs, String remarks, boolean hasSeasons) {
        ensureConnection();
        if (!isConnected())
            return false;

        String sql = "UPDATE video SET season_id = ?, episode_number = ?, name = ?, duration = ?, description = ?, audio_langs = ?, sub_langs = ?, remarks = ?, has_seasons = ? WHERE id = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, seasonId);
            pstmt.setInt(2, episodeNum);
            pstmt.setString(3, name);
            pstmt.setInt(4, duration);
            pstmt.setString(5, description);
            pstmt.setString(6, audioLangs);
            pstmt.setString(7, subLangs);
            pstmt.setString(8, remarks);
            pstmt.setInt(9, hasSeasons ? 1 : 0);
            pstmt.setInt(10, videoId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * After a video's duration is updated, reset the per-row schedule override so every
     * scheduled row falls back to the video table's new duration, then reflow each affected day.
     */
    public static void syncScheduleDurationsForVideo(int videoId) {
        ensureConnection();
        if (!isConnected()) return;

        // 1. Collect all affected schedule_dates before resetting
        Set<String> affectedDates = new HashSet<>();
        String selectSql = "SELECT schedule_date FROM schedule WHERE video_id = ? AND duration > 0";
        try (PreparedStatement ps = connection.prepareStatement(selectSql)) {
            ps.setInt(1, videoId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    affectedDates.add(rs.getString("schedule_date"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return;
        }

        // 2. Reset duration override to 0 (so the CASE falls back to v.duration)
        String resetSql = "UPDATE schedule SET duration = 0, is_loop = 0 WHERE video_id = ? AND duration > 0";
        try (PreparedStatement ps = connection.prepareStatement(resetSql)) {
            ps.setInt(1, videoId);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            return;
        }

        // 3. Reflow each affected day so start/end times are recalculated
        for (String dateStr : affectedDates) {
            try {
                showtime.manager.utils.ScheduleManager.reflowDay(LocalDate.parse(dateStr));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // Close connection
    public static synchronized void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                System.out.println("✅ Database connection closed");
            }
            isInitialized = false;
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Verify schedule table structure
    public static void verifyScheduleTableStructure() {
        ensureConnection();
        if (!isConnected()) {
            System.out.println("Not connected to database");
            return;
        }

        try {
            DatabaseMetaData meta = connection.getMetaData();
            ResultSet columns = meta.getColumns(null, null, "schedule", null);

            System.out.println("\n=== Schedule Table Structure ===");
            while (columns.next()) {
                String columnName = columns.getString("COLUMN_NAME");
                String columnType = columns.getString("TYPE_NAME");
                System.out.println("  " + columnName + " : " + columnType);
            }
            columns.close();
            System.out.println("==============================\n");

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
// DatabaseManager