package com.googlesource.gerrit.plugins.aicodereview.listener;

import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.git.WorkQueue;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.aicodereview.config.Configuration;
import java.util.concurrent.ScheduledExecutorService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class EventHandlerExecutor {
  private final Injector injector;
  private final ScheduledExecutorService executor;

  @Inject
  EventHandlerExecutor(
      Injector injector,
      WorkQueue workQueue,
      @PluginName String pluginName,
      PluginConfigFactory pluginConfigFactory) {
    this.injector = injector;
    int maximumPoolSize =
        pluginConfigFactory.getFromGerritConfig(pluginName).getInt("maximumPoolSize", 2);
    this.executor = workQueue.createQueue(maximumPoolSize, "ChatGPT request executor");
  }

  public void execute(Configuration config, Event event) {
    GerritEventContextModule contextModule = new GerritEventContextModule(config, event);
    EventHandlerTask task =
        injector.createChildInjector(contextModule).getInstance(EventHandlerTask.class);
    executor.execute(task);
  }
}
