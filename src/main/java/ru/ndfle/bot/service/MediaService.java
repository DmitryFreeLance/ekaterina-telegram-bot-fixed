package ru.ndfle.bot.service;

import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.send.SendVideo;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.ndfle.bot.db.MediaCacheRepository;

import java.io.File;
import java.nio.file.Path;

public class MediaService {
    private static final Logger log = LoggerFactory.getLogger(MediaService.class);

    private final MediaCacheRepository mediaCacheRepository;
    private final Path mediaDir;

    public MediaService(MediaCacheRepository mediaCacheRepository, String mediaDir) {
        this.mediaCacheRepository = mediaCacheRepository;
        this.mediaDir = Path.of(mediaDir == null ? "media" : mediaDir);
    }

    public SendVideo buildVideo(long chatId, String cacheKey, String filename, String captionHtml, InlineKeyboardMarkup markup) {
        String cached = mediaCacheRepository.getFileId(cacheKey);
        InputFile input;
        if (cached != null && !cached.isBlank()) {
            input = new InputFile(cached);
        } else {
            File f = mediaDir.resolve(filename).toFile();
            input = new InputFile(f);
        }

        SendVideo sv = new SendVideo();
        sv.setChatId(String.valueOf(chatId));
        sv.setVideo(input);

        if (captionHtml != null && !captionHtml.isBlank()) {
            sv.setCaption(captionHtml);
            sv.setParseMode(ParseMode.HTML);
        }
        if (markup != null) {
            sv.setReplyMarkup(markup);
        }
        return sv;
    }

    public void updateCacheFromSentMessage(String cacheKey, Message sent) {
        try {
            if (sent == null) return;
            if (sent.getVideo() != null && sent.getVideo().getFileId() != null) {
                mediaCacheRepository.putFileId(cacheKey, "video", sent.getVideo().getFileId());
            } else if (sent.getDocument() != null && sent.getDocument().getFileId() != null) {
                mediaCacheRepository.putFileId(cacheKey, "document", sent.getDocument().getFileId());
            }
        } catch (Exception e) {
            log.warn("updateCacheFromSentMessage failed: {}", e.toString());
        }
    }
}
