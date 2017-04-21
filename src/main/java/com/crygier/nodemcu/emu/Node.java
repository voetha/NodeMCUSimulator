package com.crygier.nodemcu.emu;

import com.crygier.nodemcu.Emulation;
import com.crygier.nodemcu.util.ConfigUtil;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.TwoArgFunction;

import static com.crygier.nodemcu.util.LuaFunctionUtil.*;

public class Node extends TwoArgFunction {
    private final Emulation emulation;

    private final int chipid;
    private final int flashid;

    public Node(Emulation emulation) {
        this.emulation = emulation;
        this.chipid = ConfigUtil.optInt(emulation.config, "node.chipid", (int)(Math.random() * 1000000));
        this.flashid = ConfigUtil.optInt(emulation.config, "node.flashid", (int)(Math.random() * 1000000));
    }

    @Override
    public LuaValue call(LuaValue modname, LuaValue env) {
        LuaTable node = new LuaTable();

        // Methods
        node.set("chipid", zeroArgFunction(this::getChipId));
        node.set("flashid", zeroArgFunction(this::getFlashId));

        env.set("node", node);
        env.get("package").get("loaded").set("node", node);

        return node;
    }

    private Integer getChipId() {
        return chipid;
    }

    private Integer getFlashId() {
        return flashid;
    }
}
