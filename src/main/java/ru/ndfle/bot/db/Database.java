package ru.ndfle.bot.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public class Database {
    private static final Logger log = LoggerFactory.getLogger(Database.class);

    private final String sqlitePath;
    private final String jdbcUrl;

    public Database(String sqlitePath) {
        this.sqlitePath = sqlitePath;
        this.jdbcUrl = "jdbc:sqlite:" + sqlitePath;
    }

    public Connection getConnection() throws Exception {
        return DriverManager.getConnection(jdbcUrl);
    }

    public void init() throws Exception {
        // Ensure parent dir exists
        Path p = Path.of(sqlitePath).toAbsolutePath();
        if (p.getParent() != null) {
            Files.createDirectories(p.getParent());
        }

        try (Connection c = getConnection(); Statement st = c.createStatement()) {
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS users (
                  user_id INTEGER PRIMARY KEY,
                  username TEXT,
                  first_name TEXT,
                  last_name TEXT,
                  created_at TEXT NOT NULL,
                  last_seen TEXT NOT NULL
                );
            """);

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS user_context (
                  user_id INTEGER PRIMARY KEY,
                  current_node TEXT NOT NULL,
                  back_stack_json TEXT NOT NULL,
                  state TEXT NOT NULL,
                  bk_step INTEGER NOT NULL,
                  bk_answers_json TEXT NOT NULL,
                  review_stars INTEGER NOT NULL DEFAULT 0
                );
            """);

            // Backward compatibility: if old DB without review_stars
            try {
                st.executeUpdate("ALTER TABLE user_context ADD COLUMN review_stars INTEGER NOT NULL DEFAULT 0;");
            } catch (Exception ignored) {}

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS admins (
                  user_id INTEGER PRIMARY KEY,
                  added_at TEXT NOT NULL
                );
            """);

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS bk_requests (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  user_id INTEGER NOT NULL,
                  created_at TEXT NOT NULL,
                  payload_json TEXT NOT NULL,
                  FOREIGN KEY(user_id) REFERENCES users(user_id)
                );
            """);

            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_bk_requests_created_at ON bk_requests(created_at);");

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS media_cache (
                  cache_key TEXT PRIMARY KEY,
                  file_id TEXT NOT NULL,
                  type TEXT NOT NULL,
                  updated_at TEXT NOT NULL
                );
            """);

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS reviews (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  user_id INTEGER NOT NULL,
                  stars INTEGER NOT NULL,
                  comment TEXT NOT NULL,
                  created_at TEXT NOT NULL,
                  FOREIGN KEY(user_id) REFERENCES users(user_id)
                );
            """);

            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_reviews_created_at ON reviews(created_at);");
        }

        log.info("SQLite initialized at {}", Path.of(sqlitePath).toAbsolutePath());
    }
}
