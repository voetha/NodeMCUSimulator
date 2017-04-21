package com.crygier.nodemcu.emu;

import com.crygier.nodemcu.Emulation;
import org.luaj.vm2.LuaClosure;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.TwoArgFunction;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static com.crygier.nodemcu.util.LuaFunctionUtil.*;

public class Timers extends TwoArgFunction {
    public static final Integer ALARM_SINGLE                = 0;
    public static final Integer ALARM_SEMI                  = 1;
    public static final Integer ALARM_AUTO                  = 2;

    private Map<Integer, RunningTimer> timers = new HashMap<>();
    private Map<LuaTable, RunningTimer> timerObjects = new HashMap<>();

    private final Emulation emulation;

    public Timers(Emulation emulation) {
        this.emulation = emulation;
    }

    @Override
    public LuaValue call(LuaValue modname, LuaValue env) {
        LuaTable timers = new LuaTable();

        // Methods
        timers.set("alarm", varargsFunction(this::alarm));
        timers.set("register", varargsFunction(this::register));
        timers.set("unregister", oneArgConsumer(this::unregister));
        timers.set("start", oneArgConsumer(this::start));
        timers.set("stop", oneArgConsumer(this::stop));
        timers.set("create", zeroArgFunction(this::create));

        // Constants
        timers.set("ALARM_SINGLE", ALARM_SINGLE);
        timers.set("ALARM_SEMI", ALARM_SEMI);
        timers.set("ALARM_AUTO", ALARM_AUTO);

        env.set("tmr", timers);
        env.get("package").get("loaded").set("tmr", timers);

        return timers;
    }

    private void alarm(Varargs varargs) {
        register(varargs);
        start(varargs.arg1());
    }

    private LuaTable create() {
        LuaTable timer = new LuaTable();
        timerObjects.computeIfAbsent(timer, RunningTimer::new);

        timer.set("alarm", varargsFunction(this::alarm));
        timer.set("register", varargsFunction(this::register));
        timer.set("unregister", oneArgConsumer(this::unregister));
        timer.set("start", oneArgConsumer(this::start));
        timer.set("stop", oneArgConsumer(this::stop));

        return timer;
    }

    private void register(Varargs varargs) {
        Optional<RunningTimer> timer = byLuaValue(varargs.arg(1), true);
        Long intervalMs = varargs.arg(2).tolong();
        Integer mode = varargs.arg(3).toint();
        LuaClosure callback = (LuaClosure) varargs.arg(4);

        assert timer.isPresent() : "first argument must be either an id or a valid timer object";
        assert intervalMs > 0 : "Interval MUST be a positive integer";
        assert callback != null : "Callback MUST be provided";

        timer.ifPresent(t -> {
            t.intervalMs = intervalMs;
            t.mode = mode;
            t.callback = callback;
            t.running = false;
        });
    }

    private void unregister(LuaValue id) {
        Optional<RunningTimer> timer = byLuaValue(id, false);
        assert timer.isPresent() : "Please register timer first";
        timer.ifPresent(t -> {
            stopTimer(t);
            unregisterTimer(t);
        });
    }

    private void stop(LuaValue id) {
        Optional<RunningTimer> timer = byLuaValue(id, false);
        assert timer.isPresent() : "Please register timer first";
        timer.ifPresent(this::stopTimer);
    }

    private void start(LuaValue id) {
        Optional<RunningTimer> timer = byLuaValue(id, false);
        assert timer.isPresent() : "Please register timer first";
        timer.ifPresent(this::startTimer);
    }


    private Optional<RunningTimer> byLuaValue(LuaValue value, boolean createIfNumber) {
        if(value.isnumber()) {
            if(createIfNumber) {
                return Optional.of(timers.computeIfAbsent(value.toint(), RunningTimer::new));
            } else {
                return Optional.ofNullable(timers.get(value.toint()));
            }
        } else if(value.istable()) {
            return Optional.ofNullable(timerObjects.get(value.checktable()));
        } else {
            return Optional.empty();
        }
    }

    private void startTimer(RunningTimer timer) {
        if (ALARM_SINGLE.equals(timer.mode)) {
            timer.future = emulation.luaThread.schedule(() -> {
                timer.callback.call();
                unregisterTimer(timer);
            }, timer.intervalMs, TimeUnit.MILLISECONDS);
        } else if (ALARM_SEMI.equals(timer.mode)) {
            timer.future = emulation.luaThread.schedule(() -> {
                timer.callback.call();
                timer.running = false;
            }, timer.intervalMs, TimeUnit.MILLISECONDS);
        } else {
            timer.future = emulation.luaThread.scheduleAtFixedRate(() -> {
                try {
                    timer.callback.call();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }, timer.intervalMs, timer.intervalMs, TimeUnit.MILLISECONDS);
        }

        timer.running = true;
    }

    private void unregisterTimer(RunningTimer timer) {
        timers.remove(timer.key);
        timerObjects.remove(timer.key);
    }

    private void stopTimer(RunningTimer timer) {
        if(timer.future != null) timer.future.cancel(false);
    }

    private static class RunningTimer {
        final Object key;

        Long intervalMs;
        Integer mode;
        LuaClosure callback;
        Boolean running;
        ScheduledFuture<?> future;

        public RunningTimer(Object key) {
            this.key = key;
        }
    }
}
