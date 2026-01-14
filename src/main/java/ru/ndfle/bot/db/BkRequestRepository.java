package ru.ndfle.bot.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public class BkRequestRepository {
    private static final Logger log = LoggerFactory.getLogger(BkRequestRepository.class);
    private final Database db;

    public BkRequestRepository(Database db) {
        this.db = db;
    }

    public long insert(long userId, String payloadJson) {
        String now = OffsetDateTime.now().toString();
        try (var c = db.getConnection();
             var ps = c.prepareStatement("""
                INSERT INTO bk_requests(user_id, created_at, payload_json)
                VALUES(?,?,?)
             """, java.sql.Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, userId);
            ps.setString(2, now);
            ps.setString(3, payloadJson);
            ps.executeUpdate();
            try (var keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getLong(1);
            }
        } catch (Exception e) {
            log.warn("insert bk_request failed: {}", e.toString());
        }
        return -1;
    }

    public record BkRequestRow(long id, long userId, String createdAt, String payloadJson) {}

    public List<BkRequestRow> last(int limit) {
        List<BkRequestRow> rows = new ArrayList<>();
        try (var c = db.getConnection();
             var ps = c.prepareStatement("""
                SELECT id, user_id, created_at, payload_json
                FROM bk_requests
                ORDER BY id DESC
                LIMIT ?
             """)) {
            ps.setInt(1, limit);
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new BkRequestRow(
                            rs.getLong(1),
                            rs.getLong(2),
                            rs.getString(3),
                            rs.getString(4)
                    ));
                }
            }
        } catch (Exception e) {
            log.warn("last bk_requests failed: {}", e.toString());
        }
        return rows;
    }
}
