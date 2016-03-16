package de.codesourcery.voxelengine;

import com.badlogic.gdx.backends.lwjgl.LwjglApplication;
import com.badlogic.gdx.backends.lwjgl.LwjglApplicationConfiguration;

public class Main
{
    public static void main (String[] args)
    {
        LwjglApplicationConfiguration config = new LwjglApplicationConfiguration();
        config.useGL30 = true;
        config.vSyncEnabled = true;
        config.foregroundFPS=0;

        // config.fullscreen=Constants.BENCHMARK_MODE;
        config.width = 800;
        config.height = 600;
        new LwjglApplication(new ApplicationMain(), config);
    }
}
