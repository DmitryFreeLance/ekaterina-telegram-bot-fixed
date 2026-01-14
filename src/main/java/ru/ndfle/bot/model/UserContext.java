package ru.ndfle.bot.model;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

public class UserContext {
    public long userId;

    /** Текущий экран меню (id узла) */
    public String currentNode = "start";

    /** Стек "вернуться назад" */
    public Deque<String> backStack = new ArrayDeque<>();

    public ConversationState state = ConversationState.NONE;

    /** Шаг опроса по справке БК (0 = не идёт) */
    public int bkStep = 0;

    /** Ответы опроса по справке БК */
    public Map<String, String> bkAnswers = new LinkedHashMap<>();

    /** Оценка отзыва (1..5), пока ждём текст */
    public int reviewStars = 0;

    public UserContext() {}

    public UserContext(long userId) {
        this.userId = userId;
    }
}
