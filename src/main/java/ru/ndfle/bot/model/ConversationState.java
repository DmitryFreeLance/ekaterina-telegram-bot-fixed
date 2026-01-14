package ru.ndfle.bot.model;

public enum ConversationState {
    NONE,

    // BK survey text steps
    BK_WAIT_POSITION_TEXT,
    BK_WAIT_REAL_ESTATE_TEXT,

    // Admin broadcast flow
    ADMIN_WAIT_BROADCAST_TEXT,

    // Review flow
    REVIEW_WAIT_COMMENT_TEXT,

    // Document upload flow
    DOC_UPLOAD_WAIT_MEDIA
}
