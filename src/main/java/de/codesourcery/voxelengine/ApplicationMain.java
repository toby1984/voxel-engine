package de.codesourcery.voxelengine;

import java.io.File;
import java.io.IOException;

import org.apache.log4j.Logger;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
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
import de.codesourcery.voxelengine.model.BlockType;
import de.codesourcery.voxelengine.model.Chunk;
import de.codesourcery.voxelengine.model.ChunkKey;
import de.codesourcery.voxelengine.model.Item;
import de.codesourcery.voxelengine.model.World;

public class ApplicationMain implements ApplicationListener {

    private static final Logger LOG = Logger.getLogger(ApplicationMain.class);

    private static final StringBuilder stringBuilder = new StringBuilder();

    private static final BlockKey TMP_SELECTION = new BlockKey();
    private static final Vector3 TMP1 = new Vector3();

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

    @Override
    public void create() 
    {
        font = new BitmapFont();

        camera = new PerspectiveCamera();
        camera.near = 0.01f;
        camera.far = WorldRenderer.RENDER_DISTANCE_CHUNKS*World.CHUNK_WIDTH;

        spriteBatch = new SpriteBatch();
        
        final File chunkDir;
        try {
            final File tmpFile = File.createTempFile("test", "test" );
            tmpFile.delete();
            chunkDir = new File( tmpFile.getParentFile() , "chunks" );
            if ( ! chunkDir.exists() && ! chunkDir.mkdirs() ) {
                throw new IOException("Failed to create directory "+chunkDir.getAbsolutePath());
            }
            LOG.info("Chunk directory: "+chunkDir.getAbsolutePath());
        } 
        catch (IOException e) 
        {
            LOG.error("create(): Failed to create chunk directory",e);
            throw new RuntimeException("Failed to create chunk directory",e);
        }
        chunkManager = new ChunkManager( chunkDir , taskScheduler );

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
        final Item tool = world.player.toolbar.getSelectedItem();
        final boolean playerCanChangeBlock = tool != null && (tool.canCreateBlock() || tool.canDestroyBlock() );
        if ( playerCanChangeBlock ) 
        {
            final Item item = world.player.toolbar.getSelectedItem();
            updateSelection( item.canCreateBlock() , item.canDestroyBlock() );
        } else {
            world.selectedBlock.clearSelection();
        }

        if ( playerCanChangeBlock && playerController.buttonPressed() && world.selectedBlock.hasSelection() )
        {
            if ( playerController.leftButtonPressed()  )
            {
                final Chunk selectedChunk = chunkManager.getChunk( world.selectedBlock.chunkID );
                final int blockID = world.selectedBlock.blockID;
                final int bx = BlockKey.getX( blockID );
                final int by = BlockKey.getY( blockID );
                final int bz = BlockKey.getZ( blockID );

                boolean chunkChanged = false;
                if ( tool.canCreateBlock() ) 
                {
                    if ( selectedChunk.isBlockEmpty( bx , by , bz ) ) 
                    {
                        playerController.buttonPressRegistered();
                        if ( tool.createBlock( selectedChunk ,  bx , by , bz ) ) 
                        {
                            selectedChunk.clearFlags( Chunk.FLAG_EMPTY );
                            chunkChanged = true;
                        }
                    }
                } 
                else if ( tool.canDestroyBlock() ) 
                {
                    if ( selectedChunk.isBlockNotEmpty( bx , by , bz ) ) 
                    {
                        playerController.buttonPressRegistered();
                        selectedChunk.setBlockType( bx , by , bz , BlockType.BLOCKTYPE_AIR );
                        selectedChunk.updateIsEmptyFlag();
                        chunkChanged = true;                            
                    }
                }
                if ( chunkChanged ) {
                    selectedChunk.setFlags( Chunk.FLAG_NEEDS_REBUILD | Chunk.FLAG_NEEDS_SAVE );
                }
            } 
        } 

        // render selection
        world.selectedBlock.render();

        renderUI();

        // reset flags so we're again able to detect camera movement on the next frame 
        world.player.resetMovementFlags();
    }

    private void updateSelection(boolean canSelectEmpty,boolean canSelectOccupied) 
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

        for ( ; rayMarcher.distance < 10*World.CHUNK_BLOCK_SIZE ; rayMarcher.advance() ) { // only try to find selection at most 10 blocks away

            Chunk chunk = chunkManager.getChunk( rayMarcher.chunkID );

            final boolean blockOccupied = chunk.isBlockNotEmpty( rayMarcher.block );
            if ( blockOccupied ) 
            {
                if ( canSelectOccupied ) { // we hit a non-empty block
                    world.selectedBlock.setSelected( rayMarcher.chunkID , rayMarcher.blockID );
                }
                break;
            } 
            if ( ! canSelectEmpty )
            {
                continue;
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
        
        y -= fontHeight;
        if ( world.selectedBlock.hasSelection() ) 
        {
            font.draw(spriteBatch, append("Selection: ",world.selectedBlock.chunkID,world.selectedBlock.blockID) , 10, y );
        } else {
            font.draw(spriteBatch, "Selection: NONE" , 10, y );
        }

        y -= fontHeight;
        if ( world.player.toolbar.isItemSelected() ) 
        {
            final String name = world.player.toolbar.getSelectedItem().name;
            font.draw(spriteBatch, append("Selected tool: ", name) , 10, y );
        } else {
            font.draw(spriteBatch, "Selected tool: <NONE>", 10, y );
        }

        spriteBatch.end();        
    }

    private static CharSequence append(String s1,Vector3 object) {
        stringBuilder.setLength(0);
        return stringBuilder.append( s1 ).append('(').append( object.x ).append(',').append( object.y ).append(',').append( object.z).append(')');
    }

    private static CharSequence append(String s1,int x,int y,int z) {
        stringBuilder.setLength(0);
        return stringBuilder.append( s1 ).append('(').append( x ).append(',').append( y ).append(',').append( z ).append(')');
    }    

    private static CharSequence append(String s1,float object) {
        stringBuilder.setLength(0);
        return stringBuilder.append( s1 ).append( object );
    }
    
    private static CharSequence append(String s1,long chunkID,int blockID) {
        stringBuilder.setLength(0);
        final int chunkX = ChunkKey.getX( chunkID );
        final int chunkY = ChunkKey.getY( chunkID );
        final int chunkZ = ChunkKey.getZ( chunkID );
        final int blockX = BlockKey.getX( blockID );
        final int blockY = BlockKey.getY( blockID );
        final int blockZ = BlockKey.getZ( blockID );
        
        return stringBuilder.append( s1 ).append( "chunk (" ).append( chunkX ).append(',').append( chunkY ).append(',').append( chunkZ ).append(')')
                .append(" , block (").append( blockX ).append(',').append( blockY ).append(',').append( blockZ ).append(')');
    }    

    private static CharSequence append(String s1,String object) {
        stringBuilder.setLength(0);
        return stringBuilder.append( s1 ).append( object );
    }    

    private static CharSequence append(String s1,int object) {
        stringBuilder.setLength(0);
        return stringBuilder.append( s1 ).append( object );
    }    

    @Override
    public void resize(int width, int height) 
    {
        camera.viewportHeight = height;
        camera.viewportWidth = width;

        spriteBatch.getProjectionMatrix().setToOrtho2D(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        spriteBatch.setProjectionMatrix( spriteBatch.getProjectionMatrix() );        
        LOG.info("resize(): "+width+"x"+height);
    }

    @Override
    public void resume() {
    }
}