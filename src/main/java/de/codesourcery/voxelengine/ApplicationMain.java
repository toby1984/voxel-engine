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
import com.badlogic.gdx.math.Vector3;

import de.codesourcery.voxelengine.engine.ChunkManager;
import de.codesourcery.voxelengine.engine.PlayerController;
import de.codesourcery.voxelengine.engine.RayMarcher;
import de.codesourcery.voxelengine.engine.ShaderManager;
import de.codesourcery.voxelengine.engine.TaskScheduler;
import de.codesourcery.voxelengine.engine.WorldRenderer;
import de.codesourcery.voxelengine.model.BlockKey;
import de.codesourcery.voxelengine.model.Chunk;
import de.codesourcery.voxelengine.model.ChunkKey;
import de.codesourcery.voxelengine.model.World;

public class ApplicationMain implements ApplicationListener {

    private static final Logger LOG = Logger.getLogger(ApplicationMain.class);

    private final File CHUNK_DIR = new File("/home/tobi/tmp/chunks");

    private static final StringBuilder stringBuilder = new StringBuilder();
    
    private static final BlockKey TMP_SELECTION = new BlockKey();
    private static final Vector3 TMP1 = new Vector3();

    private FPSLogger fpsLogger;

    private World world;
    private PerspectiveCamera camera;
    private ChunkManager chunkManager;
    private WorldRenderer worldRenderer;
    private ShaderManager shaderManager;
    private PlayerController playerController;
    private SpriteBatch spriteBatch;
    private BitmapFont font;    
    private final TaskScheduler taskScheduler = new TaskScheduler();

    private final RayMarcher rayMarcher = new RayMarcher();
    
    private int frameCounter;


    @Override
    public void create() 
    {
        font = new BitmapFont();

        fpsLogger = new FPSLogger();
        camera = new PerspectiveCamera();
        camera.near = 0.01f;
        camera.far = WorldRenderer.RENDER_DISTANCE_CHUNKS*World.CHUNK_WIDTH;

        spriteBatch = new SpriteBatch();
        chunkManager = new ChunkManager( CHUNK_DIR , taskScheduler );

        shaderManager = new ShaderManager();
        world = new World( shaderManager, chunkManager , camera );

        world.player.setPosition(-115,13,-13);
        world.player.lookAt( -115 , 13 , -200 );

        worldRenderer = new WorldRenderer( world , shaderManager );
        playerController = new PlayerController( world.player );

        Gdx.input.setInputProcessor( playerController );
    }

    @Override
    public void dispose() 
    {
        chunkManager.dispose();
        taskScheduler.dispose();
        spriteBatch.dispose();
        font.dispose();
        shaderManager.dispose();
        world.dispose();
    }

    @Override
    public void pause() {
    }

    @Override
    public void render() 
    {
        final float deltaTime = Gdx.graphics.getDeltaTime();

        fpsLogger.log(); 

        // process keyboard/mouse inputs
        playerController.update( deltaTime );

        // tick player (applies physics etc.)
        world.player.update( deltaTime ); // updates camera 

        // clear viewport 
        Gdx.gl30.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        Gdx.gl30.glClearColor( 0 , 0 , 0 , 1 );
        Gdx.gl30.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        // render world
        worldRenderer.render(deltaTime);

        /* Update selection.
         * 
         * This method must be called AFTER the world has been rendered
         * because world rendering will load all chunks in range
         */
        updateSelection();
        
        // render selection
        world.selectedBlock.render();
        
        renderUI();

        // reset flags so we're again able to detect camera movement on the next frame 
        world.player.resetMovementFlags();
    }

    private void updateSelection() 
    {
        world.selectedBlock.clearSelection();
        
        // "aiming" starts at center of screen
        TMP1.set( Gdx.graphics.getWidth()/2f ,Gdx.graphics.getHeight()/2 , 0);
        world.camera.unproject( TMP1 ); // unproject to point on the near plane
        rayMarcher.set( TMP1 , world.camera.direction );

        // advance ray by one block so we don't select the block the player is currently in
        rayMarcher.advance();
        rayMarcher.advance();

        rayMarcher.distance = 0;

        while ( rayMarcher.distance < 10*World.CHUNK_BLOCK_SIZE ) { // only try to find selection at most 10 blocks away
            
            Chunk chunk = chunkManager.getChunk( rayMarcher.chunkID );
            
            if ( chunk.isBlockNotEmpty( rayMarcher.block ) ) { // we hit a non-empty block
                world.selectedBlock.setSelected( rayMarcher.chunkID , rayMarcher.blockID );
                break;
            } 
            
            // we hit an empty block, only select it if any of the adjacent blocks is not empty

            // check left neighbour
            if ( TMP_SELECTION.leftOf( rayMarcher.block )  ) 
            {
                chunk = chunk.leftNeighbour;
            }
            if ( chunk.isBlockNotEmpty( TMP_SELECTION ) )
            {
                world.selectedBlock.setSelected( rayMarcher.chunkID , rayMarcher.blockID );
                break;
            }
            // check right neighbour
            if ( TMP_SELECTION.rightOf( rayMarcher.block) ) 
            {
                chunk = chunk.rightNeighbour;
                TMP_SELECTION.x = 0;
            }
            if ( chunk.isBlockNotEmpty( TMP_SELECTION ) )
            {
                world.selectedBlock.setSelected( rayMarcher.chunkID , rayMarcher.blockID );
                break;
            }   
            // check top neighbour
            if ( TMP_SELECTION.topOf( rayMarcher.block ) )
            {
                chunk = chunk.topNeighbour;
                TMP_SELECTION.y = 0;
            }
            if ( chunk.isBlockNotEmpty( TMP_SELECTION ) )
            {
                world.selectedBlock.setSelected( rayMarcher.chunkID , rayMarcher.blockID );
                break;
            }         
            // check bottom neighbour
            if ( TMP_SELECTION.bottomOf( rayMarcher.block ) ) 
            {
                chunk = chunk.bottomNeighbour;
                TMP_SELECTION.y = World.CHUNK_SIZE-1;
            }
            if ( chunk.isBlockNotEmpty( TMP_SELECTION ) )
            {
                world.selectedBlock.setSelected( rayMarcher.chunkID , rayMarcher.blockID );
                break;
            }                
            // check back neighbour
            if ( TMP_SELECTION.backOf( rayMarcher.block ) ) 
            {
                chunk = chunk.backNeighbour;
                TMP_SELECTION.z = World.CHUNK_SIZE-1;
            }
            if ( chunk.isBlockNotEmpty( TMP_SELECTION ) )
            {
                world.selectedBlock.setSelected( rayMarcher.chunkID , rayMarcher.blockID );
                break;
            }    
            // check front neighbour
            if ( TMP_SELECTION.frontOf( rayMarcher.block ) )
            {
                chunk = chunk.frontNeighbour;
                TMP_SELECTION.z = 0;
            }
            if ( chunk.isBlockNotEmpty( TMP_SELECTION ) )
            {
                world.selectedBlock.setSelected( rayMarcher.chunkID , rayMarcher.blockID );
                break;
            }                 
            rayMarcher.advance();
        }        
    }

    private void renderUI()
    {
        Gdx.graphics.getGL30().glDisable( GL30.GL_DEPTH_TEST);
        Gdx.graphics.getGL30().glDisable( GL30.GL_CULL_FACE );

        spriteBatch.begin();

        // debug output
        final float fontHeight = 14;

        float y = Gdx.graphics.getHeight() - fontHeight;
        font.draw(spriteBatch, append("FPS: " , Gdx.graphics.getFramesPerSecond() ), 10 , y );

        final int chunkX = ChunkKey.getX( world.player.cameraChunkID );
        final int chunkY = ChunkKey.getY( world.player.cameraChunkID );
        final int chunkZ = ChunkKey.getZ( world.player.cameraChunkID );
        
        y -= fontHeight;
        font.draw(spriteBatch, append( "Current chunk: ",chunkX,chunkY,chunkZ) , 10 , y );

        y -= fontHeight;
        font.draw(spriteBatch, append("Camera pos: ",camera.position) , 10, y );

        y -= fontHeight;
        font.draw(spriteBatch, append("Player feet pos: ",world.player.feetPosition()) , 10, y );

        y -= fontHeight;
        font.draw(spriteBatch, append("Player head pos: ",world.camera.position), 10, y );        

        y -= fontHeight;
        font.draw(spriteBatch, append("Player direction: ",world.camera.direction), 10, y );

        y -= fontHeight;
        font.draw(spriteBatch, append("Loaded chunks: ",worldRenderer.getLoadedChunkCount()), 10, y );       

        y -= fontHeight;
        font.draw(spriteBatch, append("Visible chunks: ",worldRenderer.getVisibleChunkCount()), 10, y );          

        spriteBatch.end();        
    }
    
    private static String append(String s1,Vector3 object) {
        stringBuilder.setLength(0);
        return stringBuilder.append( s1 ).append('(').append( object.x ).append(',').append( object.y ).append(',').append( object.z).append(')').toString();
    }
    
    private static String append(String s1,int x,int y,int z) {
        return stringBuilder.append( s1 ).append('(').append( x ).append(',').append( y ).append(',').append( z ).append(')').toString();
    }    
    
    private static String append(String s1,float object) {
        return stringBuilder.append( s1 ).append( object ).toString();
    }
    
    private static String append(String s1,int object) {
        return stringBuilder.append( s1 ).append( object ).toString();
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