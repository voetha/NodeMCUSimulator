package com.crygier.nodemcu;

import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) throws Exception {
        Emulation emulation = new Emulation();

        if(args.length >= 1) {
            Path initFile = Paths.get(args[0]).toAbsolutePath().normalize();
            Path dir = initFile.getParent();

            emulation.start(dir, dir.relativize(initFile));
        } else {
            System.out.println("Usage: ./gradlew run -Prunargs='<path-to-lua-file-to-run>'");
        }
    }
}
