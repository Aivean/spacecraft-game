package com.aivean;

import com.aivean.spacecraft.SpaceCraftGame;
import com.badlogic.gdx.backends.lwjgl.LwjglApplication;
import com.badlogic.gdx.backends.lwjgl.LwjglApplicationConfiguration;

public class DesktopApplication {
    public static void main(String[] args) {
        LwjglApplicationConfiguration config = new
                LwjglApplicationConfiguration();

        config.title = "LigGDX test";

        config.width = 1440;
        config.height = 768;

        config.x = 0;
        config.y = 0;

        config.samples = 4;

        new LwjglApplication(SpaceCraftGame.instance(), config);
    }
}
