package ru.ndfle.bot.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.objects.User;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public class UserRepository {
    private static final Logger log = LoggerFactory.getLogger(UserRepository.class);
    private final Database db;

    public UserRepository(Database db) {
        this.db = db;
    }

    public void upsert(User u) {
        long id = u.getId();
        String now = OffsetDateTime.now().toString();
        try (var c = db.getConnection()) {
            try (var ps = c.prepareStatement("""
                    INSERT INTO users(user_id, username, first_name, last_name, created_at, last_seen)
                    VALUES(?,?,?,?,?,?)
                    ON CONFLICT(user_id) DO UPDATE SET
                      username=excluded.username,
                      first_name=excluded.first_name,
                      last_name=excluded.last_name,
                      last_seen=excluded.last_seen
                    """)) {
                ps.setLong(1, id);
                ps.setString(2, u.getUserName());
                ps.setString(3, u.getFirstName());
                ps.setString(4, u.getLastName());
                ps.setString(5, now);
                ps.setString(6, now);
                ps.executeUpdate();
            }
        } catch (Exception e) {
            log.warn("upsert user failed: {}", e.toString());
        }
    }

    public List<Long> listAllUserIds() {
        List<Long> ids = new ArrayList<>();
        try (var c = db.getConnection();
             var st = c.createStatement();
             var rs = st.executeQuery("SELECT user_id FROM users ORDER BY user_id")) {
            while (rs.next()) ids.add(rs.getLong(1));
        } catch (Exception e) {
            log.warn("listAllUserIds failed: {}", e.toString());
        }
        return ids;
    }
}
