package com.crygier.nodemcu.emu;

import com.crygier.nodemcu.Emulation;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigValue;
import org.luaj.vm2.*;
import org.luaj.vm2.lib.TwoArgFunction;

import java.util.HashMap;
import java.util.Map;

import static com.crygier.nodemcu.util.LuaFunctionUtil.*;

public class Wifi extends TwoArgFunction {
    private final Emulation emulation;
    private final Map<String,WifiMeta> wifis = new HashMap<>();

    public Wifi(Emulation emulation) {
        this.emulation = emulation;

        for(Map.Entry<String,ConfigValue> wifi : emulation.config.getConfig("wifi").root().entrySet()) {
            Config meta = ((ConfigObject) wifi.getValue()).toConfig();
            wifis.put(wifi.getKey(), new WifiMeta(
                    meta.getString("SSID"),
                    wifi.getKey(),
                    meta.getInt("RSSI"),
                    meta.getString("auth mode"),
                    meta.getInt("Channel")
            ));
        }
    }

    public static final Integer STATION                     = 0;
    public static final Integer SOFTAP                      = 1;
    public static final Integer STATIONAP                   = 2;
    public static final Integer NULLMODE                    = 3;

    public static final Integer STATION_IDLE                = 0;
    public static final Integer STATION_CONNECTING          = 1;
    public static final Integer STATION_WRONG_PASSWORD      = 2;
    public static final Integer STATION_NO_AP_FOUND         = 3;
    public static final Integer STATION_CONNECT_FAIL        = 4;
    public static final Integer STATION_GOT_IP              = 5;

    private Integer mode;
    private Integer status = 0;
    private String ssid;
    private String password;

    @Override
    public LuaValue call(LuaValue modname, LuaValue env) {
        LuaTable wifi = new LuaTable();

        // Methods
        wifi.set("setmode", oneArgConsumer(this::setMode));
        wifi.set("getmode", zeroArgFunction(this::getMode));

        // Constants
        wifi.set("STATION", STATION);
        wifi.set("SOFTAP", SOFTAP);
        wifi.set("STATIONAP", STATIONAP);
        wifi.set("NULLMODE", NULLMODE);

        // Station sub-object
        LuaTable sta = new LuaTable();
        sta.set("status", zeroArgFunction(this::getStationStatus));
        sta.set("config", varargsFunction(this::setStationConfig));
        sta.set("connect", zeroArgFunction(this::stationConnect));
        sta.set("getap", varargsFunction(this::getAp));
        sta.set("getip", zeroArgFunction(this::getIp));
        wifi.set("sta", sta);

        // AccesPoint sub-object
        LuaTable ap = new LuaTable();
        ap.set("getmac", zeroArgFunction(this::getMac));
        wifi.set("ap", ap);

        env.set("wifi", wifi);
        env.get("package").get("loaded").set("wifi", wifi);

        return wifi;
    }

    private Integer getStationStatus() {
        return status;
    }

    private LuaTable inNewFormat(Map<String,WifiMeta> wifis) {
        LuaValue[] table = new LuaValue[2*wifis.size()];
        int i = 0;
        for(Map.Entry<String,WifiMeta> wifi : wifis.entrySet()) {
            WifiMeta meta = wifi.getValue();
            table[i++] = LuaString.valueOf(meta.bssid);
            table[i++] = LuaValue.valueOf(meta.ssid + ", " + meta.rssi + ", " + meta.authMode + ", " + meta.channel);
        }
        return LuaValue.tableOf(table);
    }

    // SSID : Authmode, RSSI, BSSID, Channel
    private LuaTable inOldFormat(Map<String,WifiMeta> wifis) {
        LuaValue[] table = new LuaValue[2*wifis.size()];
        int i = 0;
        for(Map.Entry<String,WifiMeta> wifi : wifis.entrySet()) {
            WifiMeta meta = wifi.getValue();
            table[i++] = LuaString.valueOf(meta.ssid);
            table[i++] = LuaValue.valueOf(meta.authMode + ", " + meta.rssi + ", " + meta.bssid + ", " + meta.channel);
        }
        return LuaValue.tableOf(table);
    }

    private LuaValue getAp(Varargs value) {
        String ssidFilter = null;
        String bssidFilter = null;
        int channelFilter = 0;
        boolean showHidden = false;

        boolean newFormat = false;

        LuaFunction callback;

        if(value.isfunction(1)) {
            callback = value.checkfunction(1);

        } else if(value.isfunction(2)) {
            newFormat = value.checkboolean(1);
            callback = value.checkfunction(2);

        } else if(value.isfunction(3)) {
            //TODO: read filter
            newFormat = value.checkboolean(2);
            callback = value.checkfunction(3);

        } else {
            return LuaValue.error("wifi.getap: no callback provided");
        }

        Map<String,WifiMeta> wifis = this.wifis;
        //TODO: use filter

        LuaValue result = newFormat ? inNewFormat(wifis) : inOldFormat(wifis);
        callback.call(result);
        return result;
    }

    private void setMode(LuaValue value) {
        mode = value.toint();
    }

    private Integer getMode() {
        return mode;
    }

    private void setStationConfig(Varargs args) {
        ssid = args.arg1().toString();
        password = args.arg(2).toString();

        boolean auto = args.arg(3) == null || args.arg(3).toboolean();
        if (auto) {
            stationConnect();
        }
    }

    private void stationConnect() {
        status = STATION_GOT_IP;
    }

    private LuaValue getIp() {
        if(status == STATION_GOT_IP) {
            //TODO get ip
            return LuaValue.valueOf("192.168.4.1  255.255.255.0  192.168.4.1");
        }
        return LuaValue.NIL;
    }

    private String getMac() {
        //TODO get mac
        return "1A-33-44-FE-55-BB";
    }

    public static class WifiMeta {
        final String ssid;
        final String bssid;
        final int rssi;
        final String authMode;
        final int channel;

        public WifiMeta(String ssid, String bssid, int rssi, String authMode, int channel) {
            this.ssid = ssid;
            this.bssid = bssid;
            this.rssi = rssi;
            this.authMode = authMode;
            this.channel = channel;
        }
    }
}
