package ru.ndfle.bot.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;

public class ReviewRepository {
    private static final Logger log = LoggerFactory.getLogger(ReviewRepository.class);
    private final Database db;

    public ReviewRepository(Database db) {
        this.db = db;
    }

    public long insert(long userId, int stars, String comment) {
        String now = OffsetDateTime.now().toString();
        int s = Math.max(1, Math.min(5, stars));
        String cmt = comment == null ? "" : comment.trim();
        if (cmt.isBlank()) cmt = "—";

        try (var c = db.getConnection();
             var ps = c.prepareStatement("""
                INSERT INTO reviews(user_id, stars, comment, created_at)
                VALUES(?,?,?,?)
             """, java.sql.Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, userId);
            ps.setInt(2, s);
            ps.setString(3, cmt);
            ps.setString(4, now);
            ps.executeUpdate();

            try (var keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getLong(1);
            }
        } catch (Exception e) {
            log.warn("insert review failed: {}", e.toString());
        }
        return -1;
    }
}
