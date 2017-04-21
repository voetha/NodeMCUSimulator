package com.crygier.nodemcu.util;

import com.typesafe.config.Config;

public class ConfigUtil {
    public static int optInt(Config config, String path, int def) {
        return config.hasPath(path) ? config.getInt(path) : def;
    }

    public static Config withFallback(Config config, String path) {
        return config.withFallback(config.getConfig(path));
    }
}
