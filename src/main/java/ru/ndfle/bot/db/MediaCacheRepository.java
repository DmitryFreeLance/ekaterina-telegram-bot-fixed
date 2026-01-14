package ru.ndfle.bot.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;

public class MediaCacheRepository {
    private static final Logger log = LoggerFactory.getLogger(MediaCacheRepository.class);
    private final Database db;

    public MediaCacheRepository(Database db) {
        this.db = db;
    }

    public String getFileId(String cacheKey) {
        if (cacheKey == null || cacheKey.isBlank()) return null;
        try (var c = db.getConnection();
             var ps = c.prepareStatement("SELECT file_id FROM media_cache WHERE cache_key=?")) {
            ps.setString(1, cacheKey);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString(1);
            }
        } catch (Exception e) {
            log.warn("getFileId failed: {}", e.toString());
        }
        return null;
    }

    public void putFileId(String cacheKey, String type, String fileId) {
        if (cacheKey == null || cacheKey.isBlank()) return;
        if (fileId == null || fileId.isBlank()) return;
        String now = OffsetDateTime.now().toString();
        try (var c = db.getConnection();
             var ps = c.prepareStatement("""
                     INSERT INTO media_cache(cache_key, file_id, type, updated_at)
                     VALUES(?,?,?,?)
                     ON CONFLICT(cache_key) DO UPDATE SET
                       file_id=excluded.file_id,
                       type=excluded.type,
                       updated_at=excluded.updated_at
                     """)) {
            ps.setString(1, cacheKey);
            ps.setString(2, fileId);
            ps.setString(3, type == null ? "unknown" : type);
            ps.setString(4, now);
            ps.executeUpdate();
        } catch (Exception e) {
            log.warn("putFileId failed: {}", e.toString());
        }
    }
}
