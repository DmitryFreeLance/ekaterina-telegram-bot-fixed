package ru.ndfle.bot.menu;

public class MenuButton {
    public final ButtonType type;
    public final String text;

    // NAV
    public final String to;

    // URL
    public final String url;

    // ACTION
    public final String action;

    private MenuButton(ButtonType type, String text, String to, String url, String action) {
        this.type = type;
        this.text = text;
        this.to = to;
        this.url = url;
        this.action = action;
    }

    public static MenuButton nav(String text, String to) {
        return new MenuButton(ButtonType.NAV, text, to, null, null);
    }

    public static MenuButton url(String text, String url) {
        return new MenuButton(ButtonType.URL, text, null, url, null);
    }

    public static MenuButton back(String text) {
        return new MenuButton(ButtonType.BACK, text, null, null, null);
    }

    public static MenuButton menu(String text) {
        return new MenuButton(ButtonType.MENU, text, null, null, null);
    }

    public static MenuButton action(String text, String action) {
        return new MenuButton(ButtonType.ACTION, text, null, null, action);
    }
}
