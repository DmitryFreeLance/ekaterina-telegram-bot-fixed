package ru.ndfle.bot.service;

import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import ru.ndfle.bot.db.ContextRepository;
import ru.ndfle.bot.menu.ButtonType;
import ru.ndfle.bot.menu.MenuButton;
import ru.ndfle.bot.menu.MenuNode;
import ru.ndfle.bot.menu.MenuTree;
import ru.ndfle.bot.model.ConversationState;
import ru.ndfle.bot.model.UserContext;

import java.util.ArrayList;
import java.util.List;

public class NavigationService {
    private final MenuTree menuTree;
    private final ContextRepository contextRepository;

    public NavigationService(MenuTree menuTree, ContextRepository contextRepository) {
        this.menuTree = menuTree;
        this.contextRepository = contextRepository;
    }

    public SendMessage toMenu(long chatId, UserContext ctx) {
        ctx.currentNode = "start";
        ctx.backStack.clear();
        ctx.state = ConversationState.NONE;
        contextRepository.save(ctx);
        return renderSend(chatId, menuTree.get("start"));
    }

    public EditMessageText toMenuEdit(long chatId, int messageId, UserContext ctx) {
        ctx.currentNode = "start";
        ctx.backStack.clear();
        ctx.state = ConversationState.NONE;
        contextRepository.save(ctx);
        return renderEdit(chatId, messageId, menuTree.get("start"));
    }

    public SendMessage goTo(long chatId, UserContext ctx, String nodeId) {
        if (!menuTree.exists(nodeId)) nodeId = "start";
        if (ctx.currentNode != null && !ctx.currentNode.equals(nodeId)) {
            ctx.backStack.push(ctx.currentNode);
        }
        ctx.currentNode = nodeId;
        ctx.state = ConversationState.NONE;
        contextRepository.save(ctx);
        return renderSend(chatId, menuTree.get(nodeId));
    }

    public EditMessageText goToEdit(long chatId, int messageId, UserContext ctx, String nodeId) {
        if (!menuTree.exists(nodeId)) nodeId = "start";
        if (ctx.currentNode != null && !ctx.currentNode.equals(nodeId)) {
            ctx.backStack.push(ctx.currentNode);
        }
        ctx.currentNode = nodeId;
        ctx.state = ConversationState.NONE;
        contextRepository.save(ctx);
        return renderEdit(chatId, messageId, menuTree.get(nodeId));
    }

    public SendMessage back(long chatId, UserContext ctx) {
        if (ctx.backStack.isEmpty()) return toMenu(chatId, ctx);
        String prev = ctx.backStack.pop();
        ctx.currentNode = prev;
        ctx.state = ConversationState.NONE;
        contextRepository.save(ctx);
        return renderSend(chatId, menuTree.get(prev));
    }

    public EditMessageText backEdit(long chatId, int messageId, UserContext ctx) {
        if (ctx.backStack.isEmpty()) {
            ctx.currentNode = "start";
            ctx.state = ConversationState.NONE;
            contextRepository.save(ctx);
            return renderEdit(chatId, messageId, menuTree.get("start"));
        }
        String prev = ctx.backStack.pop();
        ctx.currentNode = prev;
        ctx.state = ConversationState.NONE;
        contextRepository.save(ctx);
        return renderEdit(chatId, messageId, menuTree.get(prev));
    }

    private SendMessage renderSend(long chatId, MenuNode node) {
        return SendMessage.builder()
                .chatId(chatId)
                .text(node.textHtml)
                .parseMode(ParseMode.HTML)
                .replyMarkup(toMarkup(node))
                .disableWebPagePreview(true)
                .build();
    }

    private EditMessageText renderEdit(long chatId, int messageId, MenuNode node) {
        EditMessageText em = new EditMessageText();
        em.setChatId(chatId);
        em.setMessageId(messageId);
        em.setText(node.textHtml);
        em.setParseMode(ParseMode.HTML);
        em.setDisableWebPagePreview(true);
        em.setReplyMarkup(toMarkup(node));
        return em;
    }

    private InlineKeyboardMarkup toMarkup(MenuNode node) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (List<MenuButton> row : node.keyboard) {
            List<InlineKeyboardButton> r = new ArrayList<>();
            for (MenuButton b : row) {
                InlineKeyboardButton btn = new InlineKeyboardButton();
                btn.setText(b.text);
                if (b.type == ButtonType.URL) {
                    btn.setUrl(b.url);
                } else if (b.type == ButtonType.NAV) {
                    btn.setCallbackData("N:" + b.to);
                } else if (b.type == ButtonType.BACK) {
                    btn.setCallbackData("B");
                } else if (b.type == ButtonType.MENU) {
                    btn.setCallbackData("M");
                } else if (b.type == ButtonType.ACTION) {
                    btn.setCallbackData(b.action);
                }
                r.add(btn);
            }
            rows.add(r);
        }
        InlineKeyboardMarkup m = new InlineKeyboardMarkup();
        m.setKeyboard(rows);
        return m;
    }
}
