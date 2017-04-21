NodeMCU Simulator
=================

Simple Simulator for the NodeMCU (ESP8266) hardware. This project is VERY early on, and is not nearly built out.  It was
started because I was tired of the test cycle of writing the Lua files to hardware and testing with actual hardware.

This project is built around LuaJ (http://www.luaj.org/luaj/3.0/README.html) with the NodeMCU API mocked out in Java, the
best possible.  Since the NodeMCU API's (http://nodemcu.readthedocs.org/en/dev/) are implemented in Java rather than hardware
it will never be 100% correct, but this project aims to get close.

Please, if anyone is interested, help is definitely welcome to make this more complete.  


Running
-------

To run, simply build and execute the Main class. The first and only argument needs to be the path to the lua file you 
wish to execute. When using gradle to run the program use "-Pexecargs=..." to supply arguments. For example:

    ./gradlew run -Pexecargs="./init.lua"
    
You can overwrite configuration values on the command line. When using gradle to run the program you need to preprend 
"exec." to the values. If you wish to override, e.g., "node.chipid" you would need to use the following command:

    ./gradlew run -Pexecargs="./init.lua" -Dexec.node.chipid=12345

A sample blink program has been included (in 'init.lua').


The following API's have some / all work done:

Gpio
----

Keeps the pin states in memory, and allows for external interrupt / triggering.  All functions are working.

Net
---

Just the beginnings of the net API is complete.  It's just enough to create a simple client (No Server Yet).

Timer
-----

Large portion of the important parts of the API are complete (create; alarm, register, unregister, start and OO alternatives).  Needs some work to be complete
but works without blocking, so no busy CPU.

Wifi
----

Dummy module for now, just retains state. Can be initialized to simulate some available networks. Really just so I could run 'real' programs w/o it blowing up (Since a PC is
often operating just as a STATION and already connected).

Json
----

Fully implemented version (in Lua).  Easier to implement in lua (borrowed function), since there is no built in mechanism
to Coerce a Lua Table to a Java Map.

MQTT
----

MQTT Functionality is fully implemented.  There is a sample that utilizes test.mosquitto.com to connect to.
This is a free public test server that provides for simple testing.  See sample init.lua for examples.

---

As I do not have any NodeMCU all changes in this repository are __not guaranteed__ to behave like expected. Every 
function implemented behaves as documented. But as documentation is not always complete there is no guarantee every case 
behaves like it would on a real NodeMCU. 

Currently the following functions were implemented without comparing their behaviour with code running on hardware:

- Module gpio
  - gpio.serout
- Module mqtt
  - mqtt.Client:lwt
  - mqtt.Client:unsubscribe
- Module node
  - node.chipid
  - node.flashid
- Module tmr
  - tmr.create
  - tmr.create:register
  - tmr.create:start
  - tmr.create:alarm
  - tmr.create:stop
  - tmr.create:unregister
- Module wifi
  - wifi.getmode
  - wifi.sta.getap
  - wifi.sta.getip
  - wifi.ap.getmac