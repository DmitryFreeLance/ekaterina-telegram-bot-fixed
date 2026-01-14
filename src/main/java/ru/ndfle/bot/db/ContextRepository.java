package ru.ndfle.bot.db;

import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.ndfle.bot.model.ConversationState;
import ru.ndfle.bot.model.UserContext;
import ru.ndfle.bot.util.Json;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

public class ContextRepository {
    private static final Logger log = LoggerFactory.getLogger(ContextRepository.class);
    private static final TypeReference<Deque<String>> DEQUE_STR = new TypeReference<>() {};
    private static final TypeReference<Map<String, String>> MAP_STR = new TypeReference<>() {};

    private final Database db;

    public ContextRepository(Database db) {
        this.db = db;
    }

    public UserContext getOrCreate(long userId) {
        try (var c = db.getConnection()) {
            try (var ps = c.prepareStatement("SELECT current_node, back_stack_json, state, bk_step, bk_answers_json, review_stars FROM user_context WHERE user_id=?")) {
                ps.setLong(1, userId);
                try (var rs = ps.executeQuery()) {
                    if (rs.next()) {
                        UserContext ctx = new UserContext(userId);
                        ctx.currentNode = rs.getString(1);
                        ctx.backStack = Json.fromJson(rs.getString(2), DEQUE_STR, new ArrayDeque<>());
                        ctx.state = ConversationState.valueOf(rs.getString(3));
                        ctx.bkStep = rs.getInt(4);
                        ctx.bkAnswers = Json.fromJson(rs.getString(5), MAP_STR, new LinkedHashMap<>());
                        ctx.reviewStars = rs.getInt(6);
                        return ctx;
                    }
                }
            }

            // create new
            UserContext ctx = new UserContext(userId);
            ctx.currentNode = "start";
            ctx.backStack = new ArrayDeque<>();
            ctx.state = ConversationState.NONE;
            ctx.bkStep = 0;
            ctx.bkAnswers = new LinkedHashMap<>();
            ctx.reviewStars = 0;

            save(ctx);
            return ctx;

        } catch (Exception e) {
            log.warn("getOrCreate context failed: {}", e.toString());
            return new UserContext(userId);
        }
    }

    public void save(UserContext ctx) {
        try (var c = db.getConnection()) {
            try (var ps = c.prepareStatement("""
                    INSERT INTO user_context(user_id, current_node, back_stack_json, state, bk_step, bk_answers_json, review_stars)
                    VALUES(?,?,?,?,?,?,?)
                    ON CONFLICT(user_id) DO UPDATE SET
                      current_node=excluded.current_node,
                      back_stack_json=excluded.back_stack_json,
                      state=excluded.state,
                      bk_step=excluded.bk_step,
                      bk_answers_json=excluded.bk_answers_json,
                      review_stars=excluded.review_stars
                    """)) {
                ps.setLong(1, ctx.userId);
                ps.setString(2, ctx.currentNode == null ? "start" : ctx.currentNode);
                ps.setString(3, Json.toJson(ctx.backStack));
                ps.setString(4, ctx.state.name());
                ps.setInt(5, ctx.bkStep);
                ps.setString(6, Json.toJson(ctx.bkAnswers));
                ps.setInt(7, ctx.reviewStars);
                ps.executeUpdate();
            }
        } catch (Exception e) {
            log.warn("save context failed: {}", e.toString());
        }
    }

    public void resetBk(UserContext ctx) {
        ctx.state = ConversationState.NONE;
        ctx.bkStep = 0;
        ctx.bkAnswers.clear();
        save(ctx);
    }

    public void resetReview(UserContext ctx) {
        ctx.reviewStars = 0;
        if (ctx.state == ConversationState.REVIEW_WAIT_COMMENT_TEXT) {
            ctx.state = ConversationState.NONE;
        }
        save(ctx);
    }

    public void resetAll(UserContext ctx) {
        ctx.state = ConversationState.NONE;
        ctx.bkStep = 0;
        ctx.bkAnswers.clear();
        ctx.reviewStars = 0;
        save(ctx);
    }
}
