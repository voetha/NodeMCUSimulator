package com.crygier.nodemcu.emu;

import com.crygier.nodemcu.Emulation;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.TwoArgFunction;

public class Net extends TwoArgFunction {
    private final Emulation emulation;

    public Net(Emulation emulation) {
        this.emulation = emulation;
    }

    public static final Integer TCP                         = 0;
    public static final Integer UDP                         = 1;

    @Override
    public LuaValue call(LuaValue modname, LuaValue env) {
        LuaTable net = new LuaTable();

        // Methods
        net.set("createConnection", new NetClient());
        net.set("createServer", new NetServer());

        // Constants
        net.set("TCP", TCP);
        net.set("UDP", UDP);

        env.set("net", net);
        env.get("package").get("loaded").set("net", net);

        return net;
    }

}