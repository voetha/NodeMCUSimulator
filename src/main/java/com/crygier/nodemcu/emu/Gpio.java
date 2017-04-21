package com.crygier.nodemcu.emu;

import com.crygier.nodemcu.Emulation;
import org.luaj.vm2.*;
import org.luaj.vm2.lib.TwoArgFunction;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static com.crygier.nodemcu.util.LuaFunctionUtil.*;

public class Gpio extends TwoArgFunction {
    private final Emulation emulation;

    public Gpio(Emulation emulation) {
        this.emulation = emulation;
    }

    public static final Integer OUTPUT                      = 0;
    public static final Integer INPUT                       = 1;
    public static final Integer INT                         = 2;

    public static final Integer PULLUP                      = 0;
    public static final Integer FLOAT                       = 1;

    public static final Integer HIGH                        = 0;
    public static final Integer LOW                         = 1;

    private Map<Integer, PinState> pinStates = new HashMap<>();
    private Consumer<PinState> onChangeHandler;

    @Override
    public LuaValue call(LuaValue modname, LuaValue env) {
        LuaTable gpio = new LuaTable();

        // Methods
        gpio.set("mode", threeArgFunction(this::setMode));
        gpio.set("read", oneArgFunction(this::read));
        gpio.set("trig", threeArgFunction(this::trig));
        gpio.set("write", twoArgFunction(this::write));
        gpio.set("serout", varargsFunction(this::serout));

        // Constants
        gpio.set("OUTPUT", OUTPUT);
        gpio.set("INPUT", INPUT);
        gpio.set("INT", INT);
        gpio.set("PULLUP", PULLUP);
        gpio.set("FLOAT", FLOAT);
        gpio.set("HIGH", HIGH);
        gpio.set("LOW", LOW);

        env.set("gpio", gpio);
        env.get("package").get("loaded").set("gpio", gpio);

        return gpio;
    }

    public void setOnChangeHandler(Consumer<PinState> consumer) {
        this.onChangeHandler = consumer;
    }

    private void setMode(Varargs args) {
        Integer pin = args.arg(1).toint();
        Integer mode = args.arg(2).toint();
        Integer pullup = args.arg(3) != null ? args.arg(3).toint() : FLOAT;
        Integer level = Objects.equals(pullup, PULLUP) ? 1 : 0;

        PinState pinState = new PinState();
        pinState.pin = pin;
        pinState.mode = mode;
        pinState.pullup = pullup;
        pinState.level = level;

        pinStates.put(pin, pinState);
    }

    private Integer read(LuaValue pin) {
        PinState pinState = getPinState(pin.toint());
        return pinState.level;
    }

    private void trig(Varargs args) {
        Integer pin = args.arg(1).toint();
        String type = args.arg(2).toString();
        LuaClosure callback = (LuaClosure) args.arg(3);

        PinState pinState = getPinState(pin);
        pinState.trigType = type;
        pinState.trigCallback = callback;
    }

    private void toggle(int pin) {
        setPinValue(pin, 1 - getPinState(pin).level);
    }

    private void write(LuaValue pin, LuaValue level) {
        setPinValue(pin.toint(), level.toint());
    }

    public void setPinValue(Integer pin, Integer level) {
        PinState pinState = getPinState(pin);
        Integer previousLevel = pinState.level;

        pinState.level = level;

        if (!Objects.equals(previousLevel, pinState.level)) {
            System.out.println("pin " + pin + " changed from " + previousLevel + " to " + pinState.level);
            if (pinState.trigCallback != null) {
                if (pinState.level == 0 && ("down".equals(pinState.trigType) || "both".equals(pinState.trigType) || "low".equals(pinState.trigType)))
                    pinState.trigCallback.call(LuaValue.valueOf(pinState.level));
                else if (pinState.level == 1 && ("up".equals(pinState.trigType) || "both".equals(pinState.trigType) || "high".equals(pinState.trigType)))
                    pinState.trigCallback.call(LuaValue.valueOf(pinState.level));
            }

            if (onChangeHandler != null)
                onChangeHandler.accept(pinState);
        }
    }

    private void seroutSync(int pin, int cycles, int[] delays) {
        int delayIx = 0;
        while(cycles > 0) {
            try {
                Thread.sleep(delays[delayIx] * 1000);
                toggle(pin);

                delayIx++;
                if (delayIx == delays.length) {
                    delayIx = 0;
                    cycles--;
                }
            } catch (InterruptedException e) {
                System.err.println("catched InterruptedException while sleeping in sync seroute");
                e.printStackTrace();
            }
        }
    }

    private void seroutASyncCall(int pin, int delayIx, int cycles, int[] delays, LuaFunction callback) {
        toggle(pin);
        seroutASync(pin, delayIx, cycles, delays, callback);
    }

    private void seroutASync(int pin, int delayIx, int cycles, int[] delays, LuaFunction callback) {
        if(cycles > 0 || (cycles == 0 && delayIx < delays.length-1)) {
            emulation.luaThread.schedule(
                    () -> seroutASyncCall(pin, (delayIx+1) % delays.length, cycles - (delayIx == delays.length-1 ? 1 : 0), delays, callback),
                    delays[delayIx],
                    TimeUnit.MICROSECONDS
            );
        } else if(callback != null) {
            callback.call();
        }
    }

    private void serout(Varargs args) {
        LuaInteger pin = args.checkinteger(1);
        LuaInteger startLevel = LuaInteger.valueOf(args.checkinteger(2).toint() == HIGH ? HIGH : LOW);
        LuaTable delayTimes = args.checktable(3);
        int cycleNum = args.optint(4, 1);
        boolean async = !args.isnil(5);
        LuaFunction callback = args.isfunction(5) ? args.checkfunction(5) : null;

        ArrayList<Integer> delayList = new ArrayList<>();
        int i = 1; // lua starts arrays at index 1
        LuaValue v;
        while((v = delayTimes.get(i++)) != LuaValue.NIL) {
            delayList.add(v.toint());
        }
        int[] delays = new int[delayList.size()];
        for(i = 0; i < delayList.size(); i++) {
            delays[i] = delayList.get(i);
        }

        write(pin, startLevel);
        if(async) {
            seroutASync(pin.toint(), 0, cycleNum, delays, callback);
        } else {
            seroutSync(pin.toint(), cycleNum, delays);
        }
    }

    public PinState getPinState(Integer pin) {
        PinState pinState = pinStates.get(pin);
        if (pinState == null) {
            pinState = new PinState();
            pinState.mode = OUTPUT;
            pinState.pullup = FLOAT;
            pinState.level = 1;
            pinState.trigType = "both";

            pinStates.put(pin, pinState);
        }

        return pinState;
    }

    public static final class PinState {
        public Integer pin;
        public Integer mode;
        public Integer pullup;
        public Integer level;
        public String trigType;
        LuaClosure trigCallback;
    }
}
