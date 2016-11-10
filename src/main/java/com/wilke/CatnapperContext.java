package com.wilke;

import com.wilke.feed.FeedAggregate;
import com.wilke.storage.JsonStore;
import com.wilke.util.DaemonThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@WebListener
public final class CatnapperContext implements ServletContextListener {

    private static final Logger log = LoggerFactory.getLogger(CatnapperContext.class);

    private static volatile CatnapperContext INSTANCE;

    private volatile JsonStore store;
    private volatile ExecutorService executorService;
    private volatile String pathToFeeds;
    private volatile int maxConcTasks = Runtime.getRuntime().availableProcessors() * 2;
    private volatile int clientCacheTimeout = 3; // hours

    @Override
    public void contextInitialized(final ServletContextEvent sce) {
        final ServletContext ctx = sce.getServletContext();

        // maximum concurrent tasks
        String param = ctx.getInitParameter("maxConcTasks");
        if (param != null && !param.isEmpty()) {
            this.maxConcTasks = Integer.valueOf(param);
        }

        // cache timeout of client-side conditional get
        param = ctx.getInitParameter("clientCacheTimeout");
        if (param != null && !param.isEmpty())
            this.clientCacheTimeout = Integer.valueOf(param);

        // path for JsonStore
        param = ctx.getInitParameter("pathToFeeds");
        if (param == null || param.isEmpty()) {
            param = ctx.getRealPath("/WEB-INF/conf/");
            if (param == null)
                throw new RuntimeException("Catnapper: Failed to translate config folder to filesystem path. Make sure the .war file was extracted!");
        }
        this.pathToFeeds = param;
        this.executorService = Executors.newFixedThreadPool(this.maxConcTasks(), DaemonThreadFactory.INSTANCE);
        this.store = new JsonStore(this.pathToFeeds());

        CatnapperContext.INSTANCE = this;

        log.info("Parameter [pathToFeeds]        : {}", pathToFeeds);
        log.info("Parameter [maxConcTasks]       : {}", maxConcTasks);
        log.info("Parameter [clientCacheTimeout] : {}", clientCacheTimeout);
    }

    @Override
    public void contextDestroyed(final ServletContextEvent sce) {
        final JsonStore store = this.store;
        if (store != null)
            store.close();

        final ExecutorService executorService = this.executorService;
        if (executorService != null)
            executorService.shutdown();

        // shutdown logback
        if (LoggerFactory.getILoggerFactory() instanceof ch.qos.logback.classic.LoggerContext)
            ((ch.qos.logback.classic.LoggerContext) LoggerFactory.getILoggerFactory()).stop();
    }

    public static CatnapperContext instance() {
        return CatnapperContext.INSTANCE;
    }

    public String pathToFeeds() {
        return this.pathToFeeds;
    }

    public int maxConcTasks() {
        return this.maxConcTasks;
    }

    public long clientCacheTimeout() {
        return this.clientCacheTimeout;
    }

    public ExecutorService asyncExecutorService() {
        return this.executorService;
    }

    @Deprecated
    public FeedAggregate getAggregate(final String identifier) {
        return store.getAggregate(identifier);
    }

    @Deprecated
    public String setAggregate(final FeedAggregate aggregate, final String newUrls) {
        return store.writeFile(aggregate, newUrls);
    }
}
