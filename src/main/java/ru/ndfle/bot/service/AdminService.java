package ru.ndfle.bot.service;

import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import ru.ndfle.bot.db.AdminRepository;
import ru.ndfle.bot.db.BkRequestRepository;
import ru.ndfle.bot.db.ContextRepository;
import ru.ndfle.bot.db.UserRepository;
import ru.ndfle.bot.model.ConversationState;
import ru.ndfle.bot.model.UserContext;

import java.util.Set;
import java.util.stream.Collectors;

public class AdminService {
    private final AdminRepository adminRepository;
    private final ContextRepository contextRepository;
    private final BkRequestRepository bkRequestRepository;
    private final UserRepository userRepository;

    public AdminService(AdminRepository adminRepository,
                        ContextRepository contextRepository,
                        BkRequestRepository bkRequestRepository,
                        UserRepository userRepository) {
        this.adminRepository = adminRepository;
        this.contextRepository = contextRepository;
        this.bkRequestRepository = bkRequestRepository;
        this.userRepository = userRepository;
    }

    public boolean isAdmin(long userId) {
        return adminRepository.isAdmin(userId);
    }

    public Set<Long> listAdmins() {
        return adminRepository.listAdminIds();
    }

    public boolean addAdmin(long userId) {
        return adminRepository.addAdmin(userId);
    }

    public boolean removeAdmin(long userId) {
        return adminRepository.removeAdmin(userId);
    }

    public BotApiMethod<?> handleAction(long chatId, UserContext ctx, String action) {
        return switch (action) {
            case "A:SURVEYS" -> listSurveys(chatId);
            case "A:BROADCAST" -> startBroadcast(chatId, ctx);
            case "A:ADMINS" -> adminsInfo(chatId);
            default -> html(chatId, "Неизвестная команда админа: " + escapeHtml(action));
        };
    }

    private BotApiMethod<?> listSurveys(long chatId) {
        var rows = bkRequestRepository.last(10);
        if (rows.isEmpty()) {
            return html(chatId, "📭 <b>Заявок пока нет.</b>");
        }
        StringBuilder sb = new StringBuilder();
        sb.append("📥 <b>Последние заявки БК</b>\n\n");
        for (var r : rows) {
            sb.append("• #").append(r.id())
                    .append(" — userId: ").append(r.userId())
                    .append(" — ").append(escapeHtml(r.createdAt()))
                    .append("\n");
        }
        sb.append("\nМожно открыть детали заявки в базе (таблица <code>bk_requests</code>).");
        return html(chatId, sb.toString());
    }

    private BotApiMethod<?> startBroadcast(long chatId, UserContext ctx) {
        ctx.state = ConversationState.ADMIN_WAIT_BROADCAST_TEXT;
        contextRepository.save(ctx);

        return html(chatId, """
                📩 <b>Рассылка</b>

                Отправьте следующим сообщением текст, который нужно разослать всем пользователям бота.

                ⚠️ Внимание:
                • Рассылка уйдёт всем пользователям, которые когда‑либо писали боту.
                • Можно использовать HTML (<b>жирный</b>, <i>курсив</i>, <code>код</code>).

                Чтобы отменить — напишите /start или нажмите «Вернуться в меню».
                """);
    }

    public BroadcastResult performBroadcast(long chatId, UserContext ctx, String htmlText) {
        ctx.state = ConversationState.NONE;
        contextRepository.save(ctx);

        var userIds = userRepository.listAllUserIds();
        return new BroadcastResult(userIds, htmlText);
    }

    public record BroadcastResult(java.util.List<Long> userIds, String htmlText) {}

    private BotApiMethod<?> adminsInfo(long chatId) {
        Set<Long> admins = adminRepository.listAdminIds();
        String list = admins.isEmpty()
                ? "—"
                : admins.stream().map(String::valueOf).collect(Collectors.joining(", "));

        return html(chatId, """
                👥 <b>Администраторы</b>

                Текущие admin IDs:
                <code>""" + list + "</code>");
    }

    public BotApiMethod<?> html(long chatId, String html) {
        SendMessage sm = new SendMessage();
        sm.setChatId(chatId);
        sm.setText(html);
        sm.setParseMode(ParseMode.HTML);
        sm.setDisableWebPagePreview(true);
        return sm;
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
