package com.googlesource.gerrit.plugins.aicodereview.data;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import java.nio.file.Path;

@Singleton
public class PluginDataHandlerBaseProvider implements Provider<PluginDataHandler> {
    private static final String PATH_SUFFIX = ".data";
    private static final String PATH_GLOBAL = "global";

    private final Path defaultPluginDataPath;

    @Inject
    public PluginDataHandlerBaseProvider(@com.google.gerrit.extensions.annotations.PluginData Path defaultPluginDataPath) {
        this.defaultPluginDataPath = defaultPluginDataPath;
    }

    public PluginDataHandler get(String path) {
        return new PluginDataHandler(defaultPluginDataPath.resolve(path + PATH_SUFFIX));
    }

    @Override
    public PluginDataHandler get() {
        return get(PATH_GLOBAL);
    }
}
