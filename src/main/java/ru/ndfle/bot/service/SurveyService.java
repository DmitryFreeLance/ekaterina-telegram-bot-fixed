package ru.ndfle.bot.service;

import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import ru.ndfle.bot.db.AdminRepository;
import ru.ndfle.bot.db.BkRequestRepository;
import ru.ndfle.bot.db.ContextRepository;
import ru.ndfle.bot.model.ConversationState;
import ru.ndfle.bot.model.UserContext;
import ru.ndfle.bot.util.Json;

import java.time.OffsetDateTime;
import java.util.*;

public class SurveyService {

    public record SurveyResult(BotApiMethod<?> toUser, String adminHtml, Set<Long> adminIds) {}

    private record Option(String key, String label) {}
    private record Question(String key, String text, List<Option> options, boolean needsText) {}

    private final List<Question> bkQuestions;
    private final ContextRepository contextRepository;
    private final BkRequestRepository bkRequestRepository;
    private final AdminRepository adminRepository;
    private final NavigationService navigationService;

    public SurveyService(ContextRepository contextRepository,
                         BkRequestRepository bkRequestRepository,
                         AdminRepository adminRepository,
                         NavigationService navigationService) {
        this.contextRepository = contextRepository;
        this.bkRequestRepository = bkRequestRepository;
        this.adminRepository = adminRepository;
        this.navigationService = navigationService;

        this.bkQuestions = List.of(
                new Question("family", "👨‍👩‍👧‍👦 <b>Выберите количество членов семьи:</b>",
                        List.of(
                                new Option("0", "Без детей и супруга(ги)"),
                                new Option("1", "Я и супруг(а)"),
                                new Option("2", "Я, супруг(а) и 1 ребёнок"),
                                new Option("3", "Я, супруг(а) и 2 ребёнка"),
                                new Option("4", "Я, супруг(а), 3+ детей")
                        ), false
                ),
                new Question("urgency", "⏱️ <b>Как срочно надо? (выберите сроки)</b>",
                        List.of(
                                new Option("0", "В течение 3х дней"),
                                new Option("1", "В течение недели"),
                                new Option("2", "В течение 2х недель"),
                                new Option("3", "В течение месяца")
                        ), false
                ),
                new Question("property", "🏠 <b>Что есть в собственности (квартиры/дома/авто/участки)?</b>",
                        List.of(
                                new Option("0", "Нет ничего"),
                                new Option("1", "Не более 2х объектов недвижимости"),
                                new Option("2", "Не более 3х объектов недвижимости"),
                                new Option("3", "Не более 4х объектов недвижимости"),
                                new Option("4", "5 объектов и более")
                        ), false
                ),
                new Question("securities", "📈 <b>Были ли ценные бумаги и акции в отчётном году?</b>",
                        List.of(
                                new Option("0", "Нет, не было"),
                                new Option("1", "Да, были до 10 шт"),
                                new Option("2", "Да, были 10–30 шт"),
                                new Option("3", "Да, были 30+ шт")
                        ), false
                ),
                new Question("accounts", "🏦 <b>Сколько счетов в банках?</b>",
                        List.of(
                                new Option("0", "До 5 шт"),
                                new Option("1", "До 10 шт"),
                                new Option("2", "До 20 шт"),
                                new Option("3", "До 50 шт")
                        ), false
                ),
                new Question("position", "🏢 <b>Куда устраиваетесь?</b>\n\nНапишите ответ сообщением в чате ✍️", List.of(), true),
                new Question("real_estate_list", "🏠 <b>Перечислите свою недвижимость в собственности</b>\n\nНапишите ответ сообщением в чате ✍️", List.of(), true)
        );
    }

    public SurveyResult startBk(long chatId, UserContext ctx, Integer editMessageIdOrNull) {
        ctx.bkStep = 1;
        ctx.bkAnswers.clear();
        ctx.state = ConversationState.NONE;
        contextRepository.save(ctx);

        return new SurveyResult(renderQuestion(chatId, editMessageIdOrNull, 1), null, null);
    }

    public SurveyResult answerBk(long chatId, UserContext ctx, Integer editMessageIdOrNull, int step, String optionKey) {
        if (ctx.bkStep <= 0) {
            return startBk(chatId, ctx, editMessageIdOrNull);
        }
        if (step != ctx.bkStep) {
            return new SurveyResult(renderQuestion(chatId, editMessageIdOrNull, ctx.bkStep), null, null);
        }

        Question q = bkQuestions.get(step - 1);
        Option chosen = q.options.stream().filter(o -> o.key.equals(optionKey)).findFirst().orElse(null);
        if (chosen == null) {
            return new SurveyResult(renderQuestion(chatId, editMessageIdOrNull, ctx.bkStep), null, null);
        }

        ctx.bkAnswers.put(q.key, chosen.label);
        ctx.bkStep++;

        if (ctx.bkStep > bkQuestions.size()) {
            // finished (should not happen, last questions are text)
            ctx.bkStep = 0;
            contextRepository.save(ctx);
            return new SurveyResult(navigationService.goTo(chatId, ctx, "bk_after_survey"), null, null);
        }

        Question next = bkQuestions.get(ctx.bkStep - 1);
        if (next.needsText) {
            ctx.state = stateForTextQuestion(next.key);
            contextRepository.save(ctx);
            return new SurveyResult(askText(chatId, next.text), null, null);
        }

        contextRepository.save(ctx);
        return new SurveyResult(renderQuestion(chatId, editMessageIdOrNull, ctx.bkStep), null, null);
    }

    /**
     * Handles BK text answers (position, and the last free question).
     */
    public SurveyResult finishBkWithText(long chatId, UserContext ctx, User user, String textAnswer) {
        String ta = textAnswer == null ? "" : textAnswer.trim();
        if (ta.isBlank()) ta = "—";

        // Step numbers are 1-indexed
        // 6 -> "position"
        // 7 -> "real_estate_list"
        if (ctx.bkStep == 6 && ctx.state == ConversationState.BK_WAIT_POSITION_TEXT) {
            ctx.bkAnswers.put("position", ta);
            ctx.bkStep = 7;

            Question next = bkQuestions.get(6); // step 7
            ctx.state = stateForTextQuestion(next.key);
            contextRepository.save(ctx);

            return new SurveyResult(askText(chatId, next.text), null, null);
        }

        if (ctx.bkStep == 7 && ctx.state == ConversationState.BK_WAIT_REAL_ESTATE_TEXT) {
            ctx.bkAnswers.put("real_estate_list", ta);

            // Persist request
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", "bk");
            payload.put("createdAt", OffsetDateTime.now().toString());
            payload.put("userId", user.getId());
            payload.put("username", user.getUserName());
            payload.put("firstName", user.getFirstName());
            payload.put("lastName", user.getLastName());
            payload.put("answers", new LinkedHashMap<>(ctx.bkAnswers));

            long requestId = bkRequestRepository.insert(user.getId(), Json.toJson(payload));

            String adminHtml = buildAdminMessage(requestId, user, ctx.bkAnswers);

            // Reset survey state
            contextRepository.resetBk(ctx);

            // Navigate user to after-survey node
            BotApiMethod<?> toUser = navigationService.goTo(chatId, ctx, "bk_after_survey");

            return new SurveyResult(toUser, adminHtml, adminRepository.listAdminIds());
        }

        // Out of sync -> go to BK main
        return new SurveyResult(navigationService.goTo(chatId, ctx, "bk_main"), null, null);
    }

    private BotApiMethod<?> renderQuestion(long chatId, Integer editMessageIdOrNull, int step) {
        Question q = bkQuestions.get(step - 1);

        if (editMessageIdOrNull != null) {
            EditMessageText em = new EditMessageText();
            em.setChatId(chatId);
            em.setMessageId(editMessageIdOrNull);
            em.setText(q.text);
            em.setParseMode(ParseMode.HTML);
            em.setReplyMarkup(questionMarkup(step, q));
            return em;
        }

        SendMessage sm = new SendMessage();
        sm.setChatId(chatId);
        sm.setText(q.text);
        sm.setParseMode(ParseMode.HTML);
        sm.setReplyMarkup(questionMarkup(step, q));
        return sm;
    }

    private SendMessage askText(long chatId, String textHtml) {
        SendMessage sm = new SendMessage();
        sm.setChatId(chatId);
        sm.setText(textHtml);
        sm.setParseMode(ParseMode.HTML);
        return sm;
    }

    private InlineKeyboardMarkup questionMarkup(int step, Question q) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (Option o : q.options) {
            InlineKeyboardButton b = new InlineKeyboardButton();
            b.setText(o.label);
            b.setCallbackData("BK:" + step + ":" + o.key);
            rows.add(List.of(b));
        }
        InlineKeyboardMarkup m = new InlineKeyboardMarkup();
        m.setKeyboard(rows);
        return m;
    }

    private String buildAdminMessage(long requestId, User user, Map<String, String> answers) {
        String displayName = escapeHtml((user.getFirstName() == null ? "" : user.getFirstName()) + " " + (user.getLastName() == null ? "" : user.getLastName())).trim();
        if (displayName.isEmpty()) displayName = "Пользователь";

        String username = user.getUserName();
        String mention = "<a href=\"tg://user?id=" + user.getId() + "\">" + displayName + "</a>";
        String tag = (username == null || username.isBlank()) ? "—" : "@" + escapeHtml(username);

        StringBuilder sb = new StringBuilder();
        sb.append("🧾 <b>Новая заявка: Справка БК</b>\n\n");
        sb.append("🆔 <b>ID заявки:</b> ").append(requestId).append("\n");
        sb.append("👤 <b>Клиент:</b> ").append(mention).append("\n");
        sb.append("🔖 <b>Тег:</b> ").append(tag).append("\n\n");

        int i = 1;
        for (Map.Entry<String, String> e : answers.entrySet()) {
            sb.append(i++).append(") ").append("<b>").append(escapeHtml(keyToTitle(e.getKey()))).append(":</b> ")
                    .append(escapeHtml(e.getValue())).append("\n");
        }

        return sb.toString();
    }

    private static ConversationState stateForTextQuestion(String key) {
        return switch (key) {
            case "position" -> ConversationState.BK_WAIT_POSITION_TEXT;
            case "real_estate_list" -> ConversationState.BK_WAIT_REAL_ESTATE_TEXT;
            default -> ConversationState.BK_WAIT_POSITION_TEXT;
        };
    }

    private static String keyToTitle(String key) {
        return switch (key) {
            case "family" -> "Состав семьи";
            case "urgency" -> "Сроки";
            case "property" -> "Собственность (объёмы)";
            case "securities" -> "Ценные бумаги/акции";
            case "accounts" -> "Счета в банках";
            case "position" -> "Куда устраивается";
            case "real_estate_list" -> "Недвижимость в собственности (перечень)";
            default -> key;
        };
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
