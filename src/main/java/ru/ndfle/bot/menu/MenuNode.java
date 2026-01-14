package ru.ndfle.bot.menu;

import java.util.List;

public class MenuNode {
    public final String id;
    public final String textHtml;
    public final List<List<MenuButton>> keyboard;

    public MenuNode(String id, String textHtml, List<List<MenuButton>> keyboard) {
        this.id = id;
        this.textHtml = textHtml;
        this.keyboard = keyboard;
    }
}
