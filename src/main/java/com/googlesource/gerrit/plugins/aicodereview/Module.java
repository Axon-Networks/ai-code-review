package com.googlesource.gerrit.plugins.aicodereview;

import com.google.gerrit.server.events.EventListener;
import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import com.googlesource.gerrit.plugins.aicodereview.listener.GerritListener;

public class Module extends AbstractModule {
  @Override
  protected void configure() {
    Multibinder<EventListener> eventListenerBinder =
        Multibinder.newSetBinder(binder(), EventListener.class);
    eventListenerBinder.addBinding().to(GerritListener.class);
  }
}
