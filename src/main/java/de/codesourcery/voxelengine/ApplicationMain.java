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

import de.codesourcery.voxelengine.engine.ChunkKey;
import de.codesourcery.voxelengine.engine.ChunkManager;
import de.codesourcery.voxelengine.engine.PlayerController;
import de.codesourcery.voxelengine.engine.TaskScheduler;
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
    private PlayerController playerController;
    private SpriteBatch spriteBatch;
    private BitmapFont font;    
    private final TaskScheduler taskScheduler = new TaskScheduler();
    
    @Override
    public void create() 
    {
        font = new BitmapFont();
        
        logger = new FPSLogger();
        camera = new PerspectiveCamera();
        camera.near = 0.01f;
        camera.far = WorldRenderer.RENDER_DISTANCE_CHUNKS*World.WORLD_CHUNK_WIDTH;
        
        spriteBatch = new SpriteBatch();
        chunkManager = new ChunkManager( CHUNK_DIR , taskScheduler );
        
        world = new World( chunkManager , camera );
        
        world.player.setPosition(-115,13,-13);
        world.player.lookAt( -115 , 13 , -200 );
        
        shaderManager = new ShaderManager();
        renderer = new WorldRenderer( world , shaderManager );
        playerController = new PlayerController( world.player );

        Gdx.input.setInputProcessor( playerController );
    }

    @Override
    public void dispose() 
    {
        chunkManager.dispose();
        // dispose task scheduler AFTER chunk manager since it will use the
        // thread scheduler to save dirty chunks prior to exiting the application
        taskScheduler.dispose();
        spriteBatch.dispose();
        font.dispose();
        shaderManager.dispose();
    }

    @Override
    public void pause() {
    }

    @Override
    public void render() 
    {
        logger.log();

        // update player
        final float deltaTime = Gdx.graphics.getDeltaTime();
        playerController.update( deltaTime );
        world.player.update( deltaTime ); // updates camera as well   
        
        // render 
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
        
        renderer.render(deltaTime);
        
        renderUI();
    }
    
    private void renderUI()
    {
        Gdx.graphics.getGL30().glDisable( GL30.GL_DEPTH_TEST);
        Gdx.graphics.getGL30().glDisable( GL30.GL_CULL_FACE );

        spriteBatch.begin();

        // debug output
        final float fontHeight = 14;

        float y = Gdx.graphics.getHeight() - fontHeight;
        font.draw(spriteBatch, "FPS: "+Gdx.graphics.getFramesPerSecond(), 10 , y );
        
        y -= fontHeight;
        final int chunkX = ChunkKey.getX( world.player.cameraChunkID );
        final int chunkY = ChunkKey.getY( world.player.cameraChunkID );
        final int chunkZ = ChunkKey.getZ( world.player.cameraChunkID );
        font.draw(spriteBatch, "Current chunk: ("+chunkX+","+chunkY+","+","+chunkZ+")" , 10 , y );
        
        y -= fontHeight;
        font.draw(spriteBatch, "Camera pos: "+camera.position, 10, y );
        
        y -= fontHeight;
        font.draw(spriteBatch, "Player feet pos: "+world.player.feetPosition() , 10, y );
        
        y -= fontHeight;
        font.draw(spriteBatch, "Player head pos: "+world.player.headPosition(), 10, y );        
        
        y -= fontHeight;
        font.draw(spriteBatch, "Player direction: "+world.player.direction, 10, y );
        
        y -= fontHeight;
        font.draw(spriteBatch, "Loaded chunks: "+renderer.getLoadedChunkCount(), 10, y );       

        y -= fontHeight;
        font.draw(spriteBatch, "Visible chunks: "+renderer.getVisibleChunkCount(), 10, y );          
        
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