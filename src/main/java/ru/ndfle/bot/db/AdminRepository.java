package ru.ndfle.bot.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

public class AdminRepository {
    private static final Logger log = LoggerFactory.getLogger(AdminRepository.class);
    private final Database db;

    public AdminRepository(Database db) {
        this.db = db;
    }

    public void ensureAdmins(Set<Long> adminIds) {
        if (adminIds == null || adminIds.isEmpty()) return;
        for (Long id : adminIds) addAdmin(id);
    }

    public boolean isAdmin(long userId) {
        try (var c = db.getConnection();
             var ps = c.prepareStatement("SELECT 1 FROM admins WHERE user_id=?")) {
            ps.setLong(1, userId);
            try (var rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            log.warn("isAdmin failed: {}", e.toString());
            return false;
        }
    }

    public Set<Long> listAdminIds() {
        Set<Long> ids = new LinkedHashSet<>();
        try (var c = db.getConnection();
             var st = c.createStatement();
             var rs = st.executeQuery("SELECT user_id FROM admins ORDER BY user_id")) {
            while (rs.next()) ids.add(rs.getLong(1));
        } catch (Exception e) {
            log.warn("listAdminIds failed: {}", e.toString());
        }
        return ids;
    }

    public boolean addAdmin(long userId) {
        try (var c = db.getConnection();
             var ps = c.prepareStatement("INSERT INTO admins(user_id, added_at) VALUES(?,?) ON CONFLICT(user_id) DO NOTHING")) {
            ps.setLong(1, userId);
            ps.setString(2, OffsetDateTime.now().toString());
            ps.executeUpdate();
            return true;
        } catch (Exception e) {
            log.warn("addAdmin failed: {}", e.toString());
            return false;
        }
    }

    public boolean removeAdmin(long userId) {
        try (var c = db.getConnection();
             var ps = c.prepareStatement("DELETE FROM admins WHERE user_id=?")) {
            ps.setLong(1, userId);
            ps.executeUpdate();
            return true;
        } catch (Exception e) {
            log.warn("removeAdmin failed: {}", e.toString());
            return false;
        }
    }
}
