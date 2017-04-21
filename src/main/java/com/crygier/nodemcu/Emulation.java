package com.crygier.nodemcu;

import com.crygier.nodemcu.emu.*;
import com.crygier.nodemcu.util.ConfigUtil;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.lib.jse.JsePlatform;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class Emulation {
    public final ScheduledExecutorService luaThread = Executors.newScheduledThreadPool(1);
    public final Config config = ConfigUtil.withFallback(ConfigFactory.load(), "env");

    public final Node node = new Node(this);
    public final Http http = new Http(this);
    public final Wifi wifi = new Wifi(this);
    public final Timers timers = new Timers(this);
    public final Gpio gpio = new Gpio(this);
    public final Net net = new Net(this);
    public final Mqtt mqtt = new Mqtt(this);
    public final Spi spi = new Spi(this);
    public final Apa102 apa102 = new Apa102(this);

    public void start(Path dir, Path initFile) {
        Path envDir = dir.toAbsolutePath().normalize();
        luaThread.execute(() -> {
            Globals globals = JsePlatform.debugGlobals();

            globals.load(node);
            globals.load(http);
            globals.load(wifi);
            globals.load(timers);
            globals.load(gpio);
            globals.load(net);
            globals.load(mqtt);
            globals.load(spi);
            globals.load(apa102);

            // JSON Module implemented in Lua - as it's rather difficult to convert from a LuaTable to Map and vice versa
            LuaTable cjson = (LuaTable) globals.load(Main.class.getResourceAsStream("/Json.lua"), "cjson.lua", "bt", globals).invoke();
            globals.set("cjson", cjson);

            String packagePath = envDir + "/?.lua";
            globals.get("package").set("path", globals.get("package").get("path") + ";" + packagePath);

            try (InputStream initFileStream = Files.newInputStream(envDir.resolve(initFile))) {
                globals.load(initFileStream, initFile.getFileName().toString(), "bt", globals).invoke();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }
}
