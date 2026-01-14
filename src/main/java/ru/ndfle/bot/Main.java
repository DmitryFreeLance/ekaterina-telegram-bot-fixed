package ru.ndfle.bot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import ru.ndfle.bot.config.Env;
import ru.ndfle.bot.db.*;
import ru.ndfle.bot.menu.MenuTree;
import ru.ndfle.bot.service.AdminService;
import ru.ndfle.bot.service.MediaService;
import ru.ndfle.bot.service.NavigationService;
import ru.ndfle.bot.service.SurveyService;

import java.util.Set;

public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws Exception {
        String token = Env.require("BOT_TOKEN");
        String username = Env.optional("BOT_USERNAME", "EkaterinaTaxBot");
        String dbPath = Env.optional("SQLITE_PATH", "/data/bot.db");
        Set<Long> adminIds = Env.parseAdminIds(Env.optional("ADMIN_IDS", ""));
        String mediaDir = Env.optional("MEDIA_DIR", "media");

        Database db = new Database(dbPath);
        db.init();

        UserRepository userRepo = new UserRepository(db);
        ContextRepository ctxRepo = new ContextRepository(db);
        AdminRepository adminRepo = new AdminRepository(db);
        BkRequestRepository bkRepo = new BkRequestRepository(db);
        MediaCacheRepository mediaCacheRepo = new MediaCacheRepository(db);
        ReviewRepository reviewRepo = new ReviewRepository(db);

        // Ensure initial admins from env
        adminRepo.ensureAdmins(adminIds);

        MenuTree menuTree = new MenuTree();
        NavigationService nav = new NavigationService(menuTree, ctxRepo);
        AdminService adminService = new AdminService(adminRepo, ctxRepo, bkRepo, userRepo);
        SurveyService surveyService = new SurveyService(ctxRepo, bkRepo, adminRepo, nav);
        MediaService mediaService = new MediaService(mediaCacheRepo, mediaDir);

        EkaterinaBot bot = new EkaterinaBot(token, username, userRepo, ctxRepo, nav, surveyService, adminService, reviewRepo, mediaService);

        TelegramBotsApi api = new TelegramBotsApi(DefaultBotSession.class);
        api.registerBot(bot);

        log.info("Bot started as @{} with DB {} and media dir {}", username, dbPath, mediaDir);
    }
}
