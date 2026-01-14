package ru.ndfle.bot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.*;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendVideo;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.ndfle.bot.db.ContextRepository;
import ru.ndfle.bot.db.ReviewRepository;
import ru.ndfle.bot.db.UserRepository;
import ru.ndfle.bot.model.ConversationState;
import ru.ndfle.bot.model.UserContext;
import ru.ndfle.bot.service.AdminService;
import ru.ndfle.bot.service.MediaService;
import ru.ndfle.bot.service.NavigationService;
import ru.ndfle.bot.service.SurveyService;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class EkaterinaBot extends TelegramLongPollingBot {
    private static final Logger log = LoggerFactory.getLogger(EkaterinaBot.class);

    // ИП: ссылка Rutube (вместо отправки видео)
    private static final String XML_RUTUBE_URL = "https://rutube.ru/video/7eebce5e241d7c12d0b4bfb7175c906b/?r=a";

    // Оставил старую, если где-то ещё нужна
    private static final String XML_UPLOAD_URL = "https://ibkr-nalog.app/instructions/fns/upload-3ndfl-xml-file";

    private static final String DOC_UPLOAD_TEXT = """
            Вы можете загрузить документы в бот как с телефона, так и с компьютера.

            <b>На телефоне:</b>
            - нажмите на значок «скрепка» в нижнем левом углу и загрузите файлы с телефона (одним сообщением до 9 шт).

            <b>На компьютере:</b>
            - нажмите на значок «скрепка» в нижнем левом углу приложения и загрузите файлы с компьютера.

            <b>ВАЖНО‼️</b>

            Фото должно быть:
            · Без посторонних предметов в кадре.
            · Снято в светлое время суток или при хорошем освещении.
            · Четким и хорошо читаемым.

            Если готовы, то просто пришлите до 9 фото прямо сейчас👇

            Или Вы можете отправить их на электронную почту <code>ndfle@mail.ru</code>.

            В теме письма обязательно укажите ФИО и актуальный номер для связи.
            """;

    private final String botUsername;

    private final UserRepository userRepository;
    private final ContextRepository contextRepository;
    private final NavigationService navigationService;
    private final SurveyService surveyService;
    private final AdminService adminService;
    private final ReviewRepository reviewRepository;
    private final MediaService mediaService;

    // Anti-spam for upload acknowledgements
    private final Map<Long, Long> lastUploadAckAt = new ConcurrentHashMap<>();

    public EkaterinaBot(String botToken,
                        String botUsername,
                        UserRepository userRepository,
                        ContextRepository contextRepository,
                        NavigationService navigationService,
                        SurveyService surveyService,
                        AdminService adminService,
                        ReviewRepository reviewRepository,
                        MediaService mediaService) {
        super(botToken);
        this.botUsername = botUsername;
        this.userRepository = userRepository;
        this.contextRepository = contextRepository;
        this.navigationService = navigationService;
        this.surveyService = surveyService;
        this.adminService = adminService;
        this.reviewRepository = reviewRepository;
        this.mediaService = mediaService;
    }

    @Override
    public String getBotUsername() {
        return botUsername == null ? "" : botUsername;
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update.hasCallbackQuery()) {
                handleCallback(update.getCallbackQuery());
                return;
            }
            if (update.hasMessage()) {
                handleMessage(update.getMessage());
            }
        } catch (Exception e) {
            log.error("Update handling error", e);
        }
    }

    private void handleMessage(Message msg) throws TelegramApiException {
        if (msg.getFrom() != null) userRepository.upsert(msg.getFrom());
        if (msg.getFrom() == null) return;

        long chatId = msg.getChatId();
        long userId = msg.getFrom().getId();
        UserContext ctx = contextRepository.getOrCreate(userId);

        String text = msg.getText();

        // ✅ ВАЖНО: команды обрабатываем ПЕРВЫМИ (даже если ждём документы)
        if (text != null && text.startsWith("/")) {
            String[] parts = text.trim().split("\\s+");
            String cmd = parts[0].toLowerCase(Locale.ROOT);

            switch (cmd) {
                case "/start" -> {
                    contextRepository.resetAll(ctx);
                    executeSafely(navigationService.toMenu(chatId, ctx));
                }
                case "/admin" -> {
                    if (!adminService.isAdmin(userId)) {
                        executeSafely(simple(chatId, "⛔ <b>Нет доступа.</b>"));
                        return;
                    }
                    executeSafely(navigationService.goTo(chatId, ctx, "admin_panel"));
                }
                case "/admin_list" -> {
                    if (!adminService.isAdmin(userId)) {
                        executeSafely(simple(chatId, "⛔ <b>Нет доступа.</b>"));
                        return;
                    }
                    executeSafely(adminService.handleAction(chatId, ctx, "A:ADMINS"));
                }
                case "/admin_add" -> {
                    if (!adminService.isAdmin(userId)) {
                        executeSafely(simple(chatId, "⛔ <b>Нет доступа.</b>"));
                        return;
                    }
                    if (parts.length < 2) {
                        executeSafely(simple(chatId, "Использование: <code>/admin_add &lt;telegram_id&gt;</code>"));
                        return;
                    }
                    long id = Long.parseLong(parts[1]);
                    boolean added = adminService.addAdmin(id);
                    executeSafely(simple(chatId, added
                            ? "✅ Админ добавлен: <code>" + id + "</code>"
                            : "⚠️ Не удалось добавить (возможно, уже админ): <code>" + id + "</code>"));
                }
                case "/admin_del" -> {
                    if (!adminService.isAdmin(userId)) {
                        executeSafely(simple(chatId, "⛔ <b>Нет доступа.</b>"));
                        return;
                    }
                    if (parts.length < 2) {
                        executeSafely(simple(chatId, "Использование: <code>/admin_del &lt;telegram_id&gt;</code>"));
                        return;
                    }
                    long id = Long.parseLong(parts[1]);
                    boolean removed = adminService.removeAdmin(id);
                    executeSafely(simple(chatId, removed
                            ? "✅ Админ удалён: <code>" + id + "</code>"
                            : "⚠️ Не удалось удалить (возможно, не админ): <code>" + id + "</code>"));
                }
                default -> executeSafely(navigationService.toMenu(chatId, ctx));
            }
            return;
        }

        // Document upload flow
        if (ctx.state == ConversationState.DOC_UPLOAD_WAIT_MEDIA) {
            handleDocUploadMessage(chatId, ctx, msg);
            return;
        }

        // If user is in BK text step
        if (ctx.state == ConversationState.BK_WAIT_POSITION_TEXT || ctx.state == ConversationState.BK_WAIT_REAL_ESTATE_TEXT) {
            if (text == null) {
                executeSafely(simple(chatId, "Пожалуйста, отправьте ответ <b>текстом</b> сообщением."));
                return;
            }
            SurveyService.SurveyResult res = surveyService.finishBkWithText(chatId, ctx, msg.getFrom(), text);
            executeSafely(res.toUser());

            if (res.adminHtml() != null && res.adminIds() != null) {
                for (Long adminId : res.adminIds()) {
                    SendMessage sm = new SendMessage();
                    sm.setChatId(adminId);
                    sm.setText(res.adminHtml());
                    sm.setParseMode(ParseMode.HTML);
                    sm.setDisableWebPagePreview(true);
                    executeSafely(sm);
                }
            }
            return;
        }

        // Review flow: waiting for comment
        if (ctx.state == ConversationState.REVIEW_WAIT_COMMENT_TEXT) {
            if (text == null || text.isBlank()) {
                executeSafely(simple(chatId, "Пожалуйста, напишите <b>текстовый</b> комментарий одним сообщением ✍️"));
                return;
            }
            int stars = ctx.reviewStars <= 0 ? 5 : ctx.reviewStars;
            String comment = text.trim();

            long reviewId = reviewRepository.insert(userId, stars, comment);

            // Notify admins
            String adminHtml = buildReviewAdminMessage(reviewId, msg.getFrom(), stars, comment);
            for (Long adminId : adminService.listAdmins()) {
                SendMessage sm = new SendMessage();
                sm.setChatId(adminId);
                sm.setText(adminHtml);
                sm.setParseMode(ParseMode.HTML);
                sm.setDisableWebPagePreview(true);
                executeSafely(sm);
            }

            // Reset review state and show menu
            ctx.state = ConversationState.NONE;
            ctx.reviewStars = 0;
            contextRepository.save(ctx);

            executeSafely(simple(chatId, "✅ Спасибо за отзыв!"));
            executeSafely(navigationService.toMenu(chatId, ctx));
            return;
        }

        // Admin broadcast flow
        if (ctx.state == ConversationState.ADMIN_WAIT_BROADCAST_TEXT) {
            if (text == null) return;

            if (!adminService.isAdmin(userId)) {
                ctx.state = ConversationState.NONE;
                contextRepository.save(ctx);
                executeSafely(simple(chatId, "⛔ Доступ закрыт."));
                return;
            }

            var br = adminService.performBroadcast(chatId, ctx, text);

            int ok = 0;
            int fail = 0;
            for (Long uid : br.userIds()) {
                SendMessage sm = new SendMessage();
                sm.setChatId(uid);
                sm.setText(br.htmlText());
                sm.setParseMode(ParseMode.HTML);
                sm.setDisableWebPagePreview(true);
                try {
                    execute(sm);
                    ok++;
                } catch (Exception ex) {
                    fail++;
                }
            }

            executeSafely(simple(chatId, "✅ Рассылка завершена.\n\nУспешно: <b>" + ok + "</b>\nОшибок: <b>" + fail + "</b>"));
            return;
        }

        // Default: show menu (for text messages)
        if (text != null) {
            executeSafely(navigationService.toMenu(chatId, ctx));
        }
    }

    private void handleDocUploadMessage(long chatId, UserContext ctx, Message msg) throws TelegramApiException {
        User u = msg.getFrom();
        if (u == null) return;

        boolean hasMedia = msg.hasPhoto() || msg.hasDocument() || msg.hasVideo() || msg.hasAudio() || msg.hasVoice();
        if (!hasMedia) {
            // If user sends text while waiting, just remind (команды уже обработаны выше)
            if (msg.getText() != null && !msg.getText().isBlank()) {
                executeSafely(simple(chatId, "Пришлите, пожалуйста, фото/файлы документами. Чтобы выйти — нажмите «Вернуться в меню» или отправьте /start."));
            }
            return;
        }

        String header = buildUploadHeader(u);

        for (Long adminId : adminService.listAdmins()) {
            // header
            SendMessage sm = new SendMessage();
            sm.setChatId(adminId);
            sm.setText(header);
            sm.setParseMode(ParseMode.HTML);
            sm.setDisableWebPagePreview(true);
            executeSafely(sm);

            // forward media
            try {
                ForwardMessage fm = new ForwardMessage();
                fm.setChatId(adminId);
                fm.setFromChatId(chatId);
                fm.setMessageId(msg.getMessageId());
                execute(fm);
            } catch (Exception e) {
                // fallback: copy message (if forward disabled)
                try {
                    CopyMessage cm = new CopyMessage();
                    cm.setChatId(adminId);
                    cm.setFromChatId(chatId);
                    cm.setMessageId(msg.getMessageId());
                    execute(cm);
                } catch (Exception ignored) {}
            }
        }

        // Acknowledge user (throttled)
        long now = System.currentTimeMillis();
        long last = lastUploadAckAt.getOrDefault(ctx.userId, 0L);
        if (now - last > 4000) {
            lastUploadAckAt.put(ctx.userId, now);
            SendMessage ack = new SendMessage();
            ack.setChatId(chatId);
            ack.setText("✅ Документы получены и отправлены юристу.\n\nМожете отправить ещё файлы или нажмите кнопку ниже 👇");
            ack.setParseMode(ParseMode.HTML);
            ack.setReplyMarkup(menuOnlyMarkup());
            executeSafely(ack);
        }
    }

    private void handleCallback(CallbackQuery cq) throws TelegramApiException {
        if (cq.getMessage() == null) return;

        long chatId = cq.getMessage().getChatId();
        int messageId = cq.getMessage().getMessageId();
        boolean canEdit = (cq.getMessage() instanceof Message m) && m.hasText();

        if (cq.getFrom() != null) userRepository.upsert(cq.getFrom());
        if (cq.getFrom() == null) return;

        long userId = cq.getFrom().getId();
        UserContext ctx = contextRepository.getOrCreate(userId);

        String data = cq.getData();
        if (data == null) return;

        // Answer callback quickly
        executeSafely(AnswerCallbackQuery.builder()
                .callbackQueryId(cq.getId())
                .build());

        if (data.equals("B")) {
            if (ctx.bkStep > 0) contextRepository.resetBk(ctx);
            if (ctx.state == ConversationState.DOC_UPLOAD_WAIT_MEDIA) {
                ctx.state = ConversationState.NONE;
                contextRepository.save(ctx);
            }
            if (canEdit) executeSafely(navigationService.backEdit(chatId, messageId, ctx));
            else executeSafely(navigationService.back(chatId, ctx));
            return;
        }

        if (data.equals("M")) {
            contextRepository.resetAll(ctx);
            if (canEdit) executeSafely(navigationService.toMenuEdit(chatId, messageId, ctx));
            else executeSafely(navigationService.toMenu(chatId, ctx));
            return;
        }

        if (data.startsWith("N:")) {
            String nodeId = data.substring(2);

            if ("admin_panel".equals(nodeId) && !adminService.isAdmin(userId)) {
                executeSafely(alert(cq.getId(), "⛔ Нет доступа."));
                return;
            }

            // Leaving upload mode if any
            if (ctx.state == ConversationState.DOC_UPLOAD_WAIT_MEDIA) {
                ctx.state = ConversationState.NONE;
                contextRepository.save(ctx);
            }

            if (canEdit) executeSafely(navigationService.goToEdit(chatId, messageId, ctx, nodeId));
            else executeSafely(navigationService.goTo(chatId, ctx, nodeId));
            return;
        }

        if (data.equals("BK:START")) {
            SurveyService.SurveyResult res = surveyService.startBk(chatId, ctx, canEdit ? messageId : null);
            executeSafely(res.toUser());
            return;
        }

        if (data.startsWith("BK:")) {
            // BK:<step>:<opt>
            String[] p = data.split(":");
            if (p.length == 3) {
                int step = Integer.parseInt(p[1]);
                String opt = p[2];
                SurveyService.SurveyResult res = surveyService.answerBk(chatId, ctx, canEdit ? messageId : null, step, opt);
                executeSafely(res.toUser());
            }
            return;
        }

        if (data.startsWith("A:")) {
            if (!adminService.isAdmin(userId)) {
                executeSafely(alert(cq.getId(), "⛔ Нет доступа."));
                return;
            }
            BotApiMethod<?> m = adminService.handleAction(chatId, ctx, data);
            executeSafely(m);
            return;
        }

        if (data.equals("UPLOAD:START")) {
            ctx.state = ConversationState.DOC_UPLOAD_WAIT_MEDIA;
            contextRepository.save(ctx);

            SendMessage sm = new SendMessage();
            sm.setChatId(chatId);
            sm.setText(DOC_UPLOAD_TEXT);
            sm.setParseMode(ParseMode.HTML);
            sm.setDisableWebPagePreview(true);
            sm.setReplyMarkup(menuOnlyMarkup());
            executeSafely(sm);
            return;
        }

        if (data.startsWith("REVIEW:STAR:")) {
            int stars = 5;
            try {
                stars = Integer.parseInt(data.substring("REVIEW:STAR:".length()));
            } catch (Exception ignored) {}

            if (stars < 1) stars = 1;
            if (stars > 5) stars = 5;

            ctx.reviewStars = stars;
            ctx.state = ConversationState.REVIEW_WAIT_COMMENT_TEXT;
            contextRepository.save(ctx);

            String starsText = "⭐".repeat(stars);
            SendMessage sm = new SendMessage();
            sm.setChatId(chatId);
            sm.setText("Спасибо! Ваша оценка: <b>" + starsText + "</b>\n\nТеперь напишите, пожалуйста, текстовый комментарий одним сообщением ✍️");
            sm.setParseMode(ParseMode.HTML);
            sm.setDisableWebPagePreview(true);
            executeSafely(sm);
            return;
        }

        if (data.equals("MEDIA:VIDEO2")) {
            // Send 2.mp4 (как было)
            SendVideo sv = mediaService.buildVideo(chatId, "video2", "2.mp4", null, menuOnlyMarkup());
            try {
                Message sent = execute(sv);
                mediaService.updateCacheFromSentMessage("video2", sent);
            } catch (Exception e) {
                log.warn("send video2 failed: {}", e.toString());
                executeSafely(simple(chatId, "⚠️ Не удалось отправить видео. Проверьте, что файл <code>media/2.mp4</code> существует на сервере."));
            }
            return;
        }

        if (data.equals("MEDIA:VIDEO1_XML")) {
            // ✅ ИП: вместо видео — сообщение с Rutube ссылкой
            SendMessage sm = new SendMessage();
            sm.setChatId(chatId);
            sm.setParseMode(ParseMode.HTML);
            sm.setText("""
                    🎥 <b>Как отправить декларацию в XML для ИП</b>

                    Видео-инструкция:
                    """ + XML_RUTUBE_URL);
            sm.setReplyMarkup(menuOnlyMarkup());
            executeSafely(sm);
            return;
        }

        if (data.equals("MEDIA:VIDEO1_FL")) {
            // ✅ Физлица: отправляем видео 1.mp4
            SendVideo sv = mediaService.buildVideo(chatId, "video1", "1.mp4", null, menuOnlyMarkup());
            try {
                Message sent = execute(sv);
                mediaService.updateCacheFromSentMessage("video1", sent);
            } catch (Exception e) {
                log.warn("send video1 failed: {}", e.toString());
                executeSafely(simple(chatId, "⚠️ Не удалось отправить видео. Проверьте, что файл <code>media/1.mp4</code> существует на сервере."));
            }
            return;
        }
    }

    private AnswerCallbackQuery alert(String callbackQueryId, String text) {
        return AnswerCallbackQuery.builder()
                .callbackQueryId(callbackQueryId)
                .text(text)
                .showAlert(true)
                .build();
    }

    private SendMessage simple(long chatId, String html) {
        SendMessage sm = new SendMessage();
        sm.setChatId(chatId);
        sm.setText(html);
        sm.setParseMode(ParseMode.HTML);
        sm.setDisableWebPagePreview(true);
        return sm;
    }

    private void executeSafely(BotApiMethod<?> method) {
        if (method == null) return;
        try {
            execute(method);
        } catch (Exception e) {
            log.warn("execute failed: {}", e.toString());
        }
    }

    private static InlineKeyboardMarkup menuOnlyMarkup() {
        InlineKeyboardButton b = new InlineKeyboardButton();
        b.setText("🏠 Вернуться в меню");
        b.setCallbackData("M");
        InlineKeyboardMarkup m = new InlineKeyboardMarkup();
        m.setKeyboard(List.of(List.of(b)));
        return m;
    }

    private static String buildUploadHeader(User user) {
        String displayName = escapeHtml((user.getFirstName() == null ? "" : user.getFirstName()) + " " + (user.getLastName() == null ? "" : user.getLastName())).trim();
        if (displayName.isEmpty()) displayName = "Пользователь";
        String mention = "<a href=\"tg://user?id=" + user.getId() + "\">" + displayName + "</a>";
        String tag = (user.getUserName() == null || user.getUserName().isBlank()) ? "—" : "@" + escapeHtml(user.getUserName());
        return "📎 <b>Документы от клиента</b>\n"
                + "👤 " + mention + "\n"
                + "🔖 " + tag + "\n";
    }

    private static String buildReviewAdminMessage(long reviewId, User user, int stars, String comment) {
        String displayName = escapeHtml((user.getFirstName() == null ? "" : user.getFirstName()) + " " + (user.getLastName() == null ? "" : user.getLastName())).trim();
        if (displayName.isEmpty()) displayName = "Пользователь";
        String mention = "<a href=\"tg://user?id=" + user.getId() + "\">" + displayName + "</a>";
        String tag = (user.getUserName() == null || user.getUserName().isBlank()) ? "—" : "@" + escapeHtml(user.getUserName());

        String starsTxt = "⭐".repeat(Math.max(1, Math.min(5, stars)));

        return "⭐ <b>Новый отзыв</b>\n\n"
                + "🆔 <b>ID:</b> " + reviewId + "\n"
                + "👤 <b>Клиент:</b> " + mention + "\n"
                + "🔖 <b>Тег:</b> " + tag + "\n"
                + "⭐ <b>Оценка:</b> " + starsTxt + "\n\n"
                + "💬 <b>Комментарий:</b>\n"
                + "<i>" + escapeHtml(comment) + "</i>";
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}