package de.codesourcery.voxelengine;

import java.io.File;

import org.apache.log4j.Logger;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.FPSLogger;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController;

import de.codesourcery.voxelengine.engine.ChunkManager;
import de.codesourcery.voxelengine.engine.World;
import de.codesourcery.voxelengine.engine.WorldRenderer;
import de.codesourcery.voxelengine.shaders.ShaderManager;

public class ApplicationMain implements ApplicationListener {

    private static final Logger LOG = Logger.getLogger(ApplicationMain.class);
    
    private static final boolean CULL_FACES = false;

    private static final boolean DEPTH_BUFFER = true;

    private final File CHUNK_DIR = new File("/home/tobi/tmp/chunks");
    
    private FPSLogger logger;
    
    private World world;
    private PerspectiveCamera camera;
    private ChunkManager chunkManager;
    private WorldRenderer renderer;
    private ShaderManager shaderManager;
    private CameraInputController cameraController;
    private SpriteBatch spriteBatch;
    private BitmapFont font;    
    
    @Override
    public void create() 
    {
        font = new BitmapFont();
        
        logger = new FPSLogger();
        camera = new PerspectiveCamera();
        
        camera.position.set(0,25,25);
        camera.lookAt( 0 , 0 , 0 );
        camera.near = 0.1f;
        camera.far = 1000f;
        spriteBatch = new SpriteBatch();
        chunkManager = new ChunkManager( CHUNK_DIR );
        world = new World( chunkManager , camera );
        shaderManager = new ShaderManager();
        renderer = new WorldRenderer( world , shaderManager );
        cameraController = new CameraInputController( camera );
        cameraController.autoUpdate = false;
        Gdx.input.setInputProcessor( cameraController );
    }

    @Override
    public void dispose() 
    {
        renderer.dispose();
        spriteBatch.dispose();
        font.dispose();
        chunkManager.dispose();
        shaderManager.dispose();
    }

    @Override
    public void pause() {
    }

    @Override
    public void render() 
    {
        logger.log();
        
        cameraController.update();
        camera.update(true);
        
        Gdx.gl30.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        Gdx.gl30.glClearColor( 0 , 0 , 0 , 1 );
        Gdx.gl30.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        
        if ( CULL_FACES ) {
            Gdx.gl30.glEnable( GL20.GL_CULL_FACE );
        } else {
            Gdx.gl30.glDisable( GL20.GL_CULL_FACE );
        }
        if ( DEPTH_BUFFER ) {
            Gdx.gl30.glEnable(GL20.GL_DEPTH_TEST);
        } else {
            Gdx.gl30.glDisable(GL20.GL_DEPTH_TEST);
        }
        
        renderer.render( camera );
        
        renderUI();
    }
    
    private void renderUI() {
        Gdx.graphics.getGL30().glDisable( GL30.GL_DEPTH_TEST);
        Gdx.graphics.getGL30().glDisable( GL30.GL_CULL_FACE );

        spriteBatch.begin();
        final int centerX = Gdx.graphics.getWidth()/2;
        final int centerY = Gdx.graphics.getHeight()/2;

        // debug output
        final float fontHeight = 12;

        float y = Gdx.graphics.getHeight() - 15;
        font.draw(spriteBatch, "FPS: "+Gdx.graphics.getFramesPerSecond(), 10 , y );
        
        y += 15;
        font.draw(spriteBatch, "Camera pos: "+camera.position, 10, y );
        spriteBatch.end();        
    }

    @Override
    public void resize(int width, int height) 
    {
        camera.viewportHeight = height;
        camera.viewportWidth = width;
        LOG.info("resize(): "+width+"x"+height);
    }

    @Override
    public void resume() {
    }
}