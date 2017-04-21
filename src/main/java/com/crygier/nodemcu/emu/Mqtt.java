package com.crygier.nodemcu.emu;

import com.crygier.nodemcu.Emulation;
import com.crygier.nodemcu.util.LuaFunctionUtil;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.luaj.vm2.*;
import org.luaj.vm2.lib.TwoArgFunction;
import org.luaj.vm2.lib.VarArgFunction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class Mqtt extends TwoArgFunction {
    private final Emulation emulation;

    public Mqtt(Emulation emulation) {
        this.emulation = emulation;
    }

    @Override
    public LuaValue call(LuaValue modname, LuaValue env) {
        LuaTable mqtt = new LuaTable();

        // Methods
        mqtt.set("Client", new MqttClient());

        env.set("mqtt", mqtt);
        env.get("package").get("loaded").set("mqtt", mqtt);

        return mqtt;
    }

    public final class MqttClient extends VarArgFunction {

        private Map<LuaTable, MqttClientStatus> clientStatusMap = new HashMap<>();

        public Varargs invoke(Varargs args) {
            LuaTable mqttClient = new LuaTable();
            MqttClientStatus clientStatus = new MqttClientStatus();
            clientStatusMap.put(mqttClient, clientStatus);

            clientStatus.clientId = args.arg(1).tojstring();
            clientStatus.keepAlive = args.arg(2).toint();
            clientStatus.userName = !args.arg(3).isnil() ? args.arg(3).tojstring() : null;
            clientStatus.password = !args.arg(4).isnil() ? args.arg(4).tojstring() : null;
            clientStatus.cleanSession = args.arg(5).isnil() || args.arg(5).toboolean();

            mqttClient.set("close", LuaFunctionUtil.oneArgConsumer(this::close));
            mqttClient.set("connect", LuaFunctionUtil.varargsFunction(this::connect));
            mqttClient.set("lwt", LuaFunctionUtil.varargsFunction(this::lwt));
            mqttClient.set("on", LuaFunctionUtil.threeArgFunction(this::on));
            mqttClient.set("publish", LuaFunctionUtil.varargsFunction(this::publish));
            mqttClient.set("subscribe", LuaFunctionUtil.varargsFunction(this::subscribe));
            mqttClient.set("unsubscribe", LuaFunctionUtil.varargsFunction(this::unsubscribe));

            return mqttClient;
        }

        private boolean close(LuaValue self) {
            MqttClientStatus clientStatus = clientStatusMap.get(self);

            try {
                if(clientStatus.client.isConnected()) {
                    clientStatus.client.disconnect();
                }
                clientStatus.client.close();

                return true;
            } catch (MqttException e) {
                e.printStackTrace();
                return false;
            }
        }

        private boolean connect(Varargs args) {
            LuaTable self = (LuaTable) args.arg(1);
            String host = args.arg(2).tojstring();
            Integer port = !args.arg(3).isnil() ? args.arg(3).toint() : 1883;
            Boolean secure = !args.arg(4).isnil() && args.arg(4).toboolean();
            Boolean autoreconnect = !args.arg(5).isnil() && args.arg(5).toboolean();
            LuaClosure callback = !args.arg(6).isnil() ? (LuaClosure) args.arg(6) : null;

            MqttClientStatus clientStatus = clientStatusMap.get(self);

            try {
                clientStatus.client = new org.eclipse.paho.client.mqttv3.MqttClient("tcp://" + host + ":" + port, clientStatus.clientId, new MemoryPersistence());
                MqttConnectOptions options = new MqttConnectOptions();
                options.setCleanSession(clientStatus.cleanSession);
                options.setKeepAliveInterval(clientStatus.keepAlive);
                if(clientStatus.lwt != null) {
                    options.setWill(clientStatus.lwt.topic, clientStatus.lwt.message.getBytes(), clientStatus.lwt.qos, clientStatus.lwt.retain);
                }

                if (clientStatus.userName != null) {
                    options.setUserName(clientStatus.userName);
                    options.setPassword(clientStatus.password.toCharArray());
                }

                clientStatus.client.setCallback(new MqttCallback() {
                    @Override
                    public void connectionLost(Throwable cause) {
                        if (clientStatus.callbacks.containsKey("offline")) {
                            emulation.luaThread.execute(() -> clientStatus.callbacks.get("offline").call(self));
                        }
                    }

                    @Override
                    public void messageArrived(String topic, MqttMessage message) throws Exception {
                        if (clientStatus.callbacks.containsKey("message")) {
                            emulation.luaThread.execute(() ->
                                clientStatus.callbacks.get("message").call(self, LuaString.valueOf(topic), LuaString.valueOf(message.getPayload()))
                            );
                        }
                    }

                    @Override
                    public void deliveryComplete(IMqttDeliveryToken token) {
                        // No events for this in NodeMCU
                    }
                });

                clientStatus.client.connect(options);

                // Callbacks!
                if (callback != null)
                    callback.call(self);

                // On 'connect' Callback
                if (clientStatus.callbacks.containsKey("connect"))
                    clientStatus.callbacks.get("connect").call(self);

                return true;
            } catch (MqttException e) {
                e.printStackTrace();
                return false;
            }
        }

        private void lwt(Varargs args) {
            LuaTable self = (LuaTable) args.arg(1);
            String topic = args.arg(2).tojstring();
            String message = args.arg(3).tojstring();
            int qos = args.optint(4, 0);
            boolean retain = args.optboolean(5, false);

            MqttClientStatus clientStatus = clientStatusMap.get(self);
            clientStatus.lwt = new LastWillTestament();
            clientStatus.lwt.topic = topic;
            clientStatus.lwt.message = message;
            clientStatus.lwt.qos = qos;
            clientStatus.lwt.retain = retain;
        }

        private void on(Varargs args) {
            LuaTable self = (LuaTable) args.arg(1);
            String event = args.arg(2).tojstring();
            LuaClosure callback = (LuaClosure) args.arg(3);

            MqttClientStatus clientStatus = clientStatusMap.get(self);
            clientStatus.callbacks.put(event, callback);
        }

        private boolean publish(Varargs args) {
            LuaTable self = (LuaTable) args.arg(1);
            String topic = args.arg(2).tojstring();
            String payload = args.arg(3).tojstring();
            Integer qos = args.arg(4).toint();
            boolean retain = !args.arg(5).isnil() && args.arg(5).toboolean();
            LuaClosure callback = !args.arg(6).isnil() ? (LuaClosure) args.arg(6) : null;

            MqttClientStatus clientStatus = clientStatusMap.get(self);

            try {
                clientStatus.client.publish(topic, payload.getBytes(), qos, retain);

                if (callback != null)
                    callback.call(self);

                return true;
            } catch (MqttException e) {
                e.printStackTrace();
                return false;
            }
        }

        private boolean subscribe(Varargs args) {
            LuaTable self = (LuaTable) args.arg(1);
            String topic = args.arg(2).tojstring();
            Integer qos = args.arg(3).toint();
            LuaClosure callback = !args.arg(4).isnil() ? (LuaClosure) args.arg(4) : null;

            MqttClientStatus clientStatus = clientStatusMap.get(self);

            try {
                clientStatus.client.subscribe(topic, qos);

                if (callback != null)
                    callback.call(self);

                return true;
            } catch (MqttException e) {
                e.printStackTrace();
                return false;
            }
        }

        //TODO: test
        private boolean unsubscribe(Varargs args) {
            LuaTable self = (LuaTable) args.arg(1);

            ArrayList<String> topicList = new ArrayList<>();
            if(args.arg(2).isstring()) {
                topicList.add(args.checkjstring(2));
            } else {
                LuaTable tps = args.checktable(2);
                int i = 1;
                LuaValue t;
                while((t = tps.get(i)) != LuaValue.NIL) {
                    topicList.add(t.checkjstring());
                    i++;
                }
            }
            String[] topics = new String[topicList.size()];
            Arrays.setAll(topics, topicList::get);

            LuaFunction callback = !args.arg(3).isnil() ? args.checkfunction(3) : null;

            MqttClientStatus clientStatus = clientStatusMap.get(self);
            try {
                for(String topic : topics) {
                    clientStatus.client.unsubscribe(topic);
                }

                if (callback != null)
                    callback.call(self);

                return true;
            } catch (MqttException e) {
                e.printStackTrace();
                return false;
            }
        }
    }

    public static final class MqttClientStatus {
        String clientId;
        Integer keepAlive;          // In Seconds
        String userName;
        String password;
        Boolean cleanSession;
        Map<String, LuaClosure> callbacks = new HashMap<>();
        IMqttClient client;
        LastWillTestament lwt;
    }

    public static final class LastWillTestament {
        String topic;
        String message;
        int qos;
        boolean retain;
    }
}
