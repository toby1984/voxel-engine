package de.codesourcery.voxelengine;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;

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
import de.codesourcery.voxelengine.engine.TextureManager;
import de.codesourcery.voxelengine.engine.WorldRenderer;
import de.codesourcery.voxelengine.model.BlockKey;
import de.codesourcery.voxelengine.model.BlockType;
import de.codesourcery.voxelengine.model.Chunk;
import de.codesourcery.voxelengine.model.ChunkKey;
import de.codesourcery.voxelengine.model.Item;
import de.codesourcery.voxelengine.model.World;
import de.codesourcery.voxelengine.utils.IBlockSelection;
import de.codesourcery.voxelengine.utils.SelectedBlock;

public class ApplicationMain implements ApplicationListener {

    private static final Logger LOG = Logger.getLogger(ApplicationMain.class);

    private static final StringBuilder stringBuilder = new StringBuilder();

    private static final int SELECTION_RANGE_IN_BLOCKS = 10;
    
    public static final boolean RENDER_DEBUG_UI = true;
    
    private static final BlockKey TMP_SELECTION = new BlockKey();
    private static final Vector3 TMP1 = new Vector3();

    private final SelectedBlock currentTarget = new SelectedBlock();
    
    private World world;
    private PerspectiveCamera camera;
    private ChunkManager chunkManager;
    private WorldRenderer worldRenderer;
    private ShaderManager shaderManager;
    private PlayerController playerController;
    private SpriteBatch spriteBatch;
    private BitmapFont font;    
    private final TaskScheduler taskScheduler = new TaskScheduler();
    
    private final TextureManager textureManager = new TextureManager();
    
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

        world.player.setPosition(-115,13,50);
        world.player.lookAt( -1000 , 13 , 0 );

        worldRenderer = new WorldRenderer( world , shaderManager , textureManager );
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
        worldRenderer.dispose();
        textureManager.dispose();
    }

    @Override
    public void pause() {
    }

    @Override
    public void render() 
    {
        final float deltaTime = Gdx.graphics.getDeltaTime();

        // clear viewport 
        Gdx.gl30.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        Gdx.gl30.glClearColor( 0 , 0 , 0 , 1 );
        Gdx.gl30.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        // process keyboard/mouse inputs
        playerController.update( deltaTime );

        // update player (applies physics etc.)
        world.player.update( deltaTime ); 
        
        // render world
        worldRenderer.render(deltaTime);

        /* Update selection.
         * 
         * This method must be called AFTER the world has been rendered
         * because world rendering loads all chunks in range and we want
         * to check those chunks for collisions with the view ray. 
         */
        final Item tool = world.player.toolbar.getSelectedItem();
        final boolean playerCanChangeBlock = tool != null && (tool.canCreateBlock() || tool.canDestroyBlock() );

        if ( playerCanChangeBlock ) 
        {
            final Item item = world.player.toolbar.getSelectedItem();
            final boolean gotTarget = findCurrentTarget( item.canCreateBlock() , item.canDestroyBlock() ,  playerController.leftShiftPressed() && world.currentSelection.hasSelection() );
            if (  gotTarget ) 
            {
                world.currentTarget.setSelected( currentTarget.chunkID , currentTarget.blockID );
                if ( playerController.leftShiftPressed() ) 
                {
                    world.currentSelection.addSelected( currentTarget.chunkID , currentTarget.blockID );
                } else {
                    world.currentSelection.clearSelection();
                }
            }
        } else {
            world.currentTarget.clearSelection();            
        }

        if ( playerCanChangeBlock && playerController.buttonPressed() && ( world.currentTarget.hasSelection() || world.currentSelection.hasSelection() ) )
        {
            final IBlockSelection selection = world.currentSelection.hasSelection() ? world.currentSelection.selection : world.currentTarget.selection;
            if ( playerController.leftButtonPressed()  )
            {
            	final HashSet<Chunk> touchedChunks = new HashSet<Chunk>( selection.size() );
            	selection.visitSelection( (chunkID,blockID) -> 
            	{
                    final Chunk selectedChunk = chunkManager.getChunk( chunkID );
                    final int bx = BlockKey.getX( blockID );
                    final int by = BlockKey.getY( blockID );
                    final int bz = BlockKey.getZ( blockID );

                    boolean blockChanged = false;
                    if ( tool.canCreateBlock() ) 
                    {
                        if ( selectedChunk.isBlockEmpty( bx , by , bz ) ) 
                        {
                            playerController.buttonPressRegistered();
                            if ( tool.createBlock( selectedChunk ,  bx , by , bz ) ) 
                            {
                            	touchedChunks.add( selectedChunk );
                            	blockChanged = true;
                            }
                        }
                    } 
                    else if ( tool.canDestroyBlock() ) 
                    {
                        if ( selectedChunk.isBlockNotEmpty( bx , by , bz ) ) 
                        {
                            playerController.buttonPressRegistered();
                            touchedChunks.add( selectedChunk );                            
                            selectedChunk.setBlockTypeAndInvalidate( Chunk.blockIndex( bx , by , bz ) , BlockType.AIR );
                            blockChanged = true;
                        }
                    }
                    if ( blockChanged ) 
                    {
                        if ( bx == 0 ) {
                            selectedChunk.leftNeighbour.setFlags( Chunk.FLAG_NEEDS_REBUILD );
                        } else if ( bx == World.CHUNK_SIZE-1 ) {
                            selectedChunk.rightNeighbour.setFlags( Chunk.FLAG_NEEDS_REBUILD );
                        }
                        if ( by == 0 ) {
                            selectedChunk.bottomNeighbour.setFlags( Chunk.FLAG_NEEDS_REBUILD );
                        } else if ( by == World.CHUNK_SIZE -1 ) {
                            selectedChunk.topNeighbour.setFlags( Chunk.FLAG_NEEDS_REBUILD );
                        }
                        if ( bz == 0 ) {
                            selectedChunk.backNeighbour.setFlags( Chunk.FLAG_NEEDS_REBUILD );
                        } else if ( bz == World.CHUNK_SIZE -1 ) {
                            selectedChunk.frontNeighbour.setFlags( Chunk.FLAG_NEEDS_REBUILD );
                        }                        
                    }
            	});
            	
            	if ( ! touchedChunks.isEmpty() ) 
            	{
            	    if ( selection == world.currentSelection.selection) 
            	    {
            	        selection.clear();
            	    }
            		touchedChunks.forEach( chunk -> 
            		{
            			if ( tool.canCreateBlock() ) {
            				chunk.clearFlags( Chunk.FLAG_EMPTY );
            			} else if ( tool.canDestroyBlock() ) {
            				chunk.updateIsEmptyFlag();
            			}
            			chunk.setFlags( Chunk.FLAG_NEEDS_REBUILD | Chunk.FLAG_NEEDS_SAVE );
            		});
            	}
            } 
        } 

        // render selection
        world.currentSelection.render();
        
        // render current target
        world.currentTarget.render();

        if ( RENDER_DEBUG_UI ) {
            renderUI();
        }

        // reset flags so we're again able to detect camera movement on the next frame 
        world.player.resetMovementFlags();
    }

    private boolean findCurrentTarget(boolean canSelectEmpty,boolean canSelectOccupied,boolean checkAgainstSelection) 
    {
        if ( canSelectEmpty && canSelectOccupied ) {
        	throw new IllegalArgumentException("Internal error, not implemented - can either select occupied or empty cells but not both");
        }
        if ( ! (canSelectEmpty || canSelectOccupied ) ) {
        	throw new IllegalArgumentException("You need to select either empty or occupied cells");
        }
        
        setupRayMarching();
        if ( checkAgainstSelection ) 
        {
        	for ( int i = 0 ; i < 5 ; i++ ) {
        		rayMarcher.advance(); // +HALF_BLOCK_SIZE
        		rayMarcher.advance(); // +HALF_BLOCK_SIZE
        	}
        	currentTarget.set( rayMarcher.chunkID , rayMarcher.blockID );
        	return true;
        } 
        
        boolean hitNonEmptyBlock=false;
        for ( ; rayMarcher.distance < SELECTION_RANGE_IN_BLOCKS * World.BLOCK_SIZE ; rayMarcher.advance() ) { // only try to find selection at most 10 blocks away

            final Chunk chunk = chunkManager.getChunk( rayMarcher.chunkID );

            hitNonEmptyBlock = chunk.isBlockNotEmpty( rayMarcher.block );
            if ( hitNonEmptyBlock ) 
            {
            	if ( canSelectOccupied ) {
            	    currentTarget.set( rayMarcher.chunkID , rayMarcher.blockID );
            		return true;
            	}
            	break;
            }
        }
        
        if ( hitNonEmptyBlock ) // trace ray back to origin while looking for the first empty block 
        {
        	while ( rayMarcher.distance > 0 ) 
        	{
        		rayMarcher.stepBack();
        		final Chunk chunk = chunkManager.getChunk( rayMarcher.chunkID );
        		if ( chunk.isBlockEmpty( rayMarcher.block ) ) 
        		{
            		currentTarget.set( rayMarcher.chunkID , rayMarcher.blockID );
            		return true;
        		}
        	}
        }
        
        // forward tracing didn't hit any block within selection range,
        // try again but look for a non-empty neighbouring block instead

        setupRayMarching();

        for ( ; rayMarcher.distance < SELECTION_RANGE_IN_BLOCKS * World.BLOCK_SIZE ; rayMarcher.advance() ) { // only try to find selection at most 10 blocks away

            Chunk chunk = chunkManager.getChunk( rayMarcher.chunkID );

            // check left neighbour
            if ( TMP_SELECTION.leftOf( rayMarcher.block )  ) 
            {
                chunk = chunk.leftNeighbour;
            }
            if ( chunk.isBlockNotEmpty( TMP_SELECTION ) )
            {
                currentTarget.set( rayMarcher.chunkID , rayMarcher.blockID );
                return true;
            }
            // check right neighbour
            if ( TMP_SELECTION.rightOf( rayMarcher.block) ) 
            {
                chunk = chunk.rightNeighbour;
                TMP_SELECTION.x = 0;
            }
            if ( chunk.isBlockNotEmpty( TMP_SELECTION ) )
            {
                currentTarget.set( rayMarcher.chunkID , rayMarcher.blockID );
                return true;
            }   
            // check top neighbour
            if ( TMP_SELECTION.topOf( rayMarcher.block ) )
            {
                chunk = chunk.topNeighbour;
                TMP_SELECTION.y = 0;
            }
            if ( chunk.isBlockNotEmpty( TMP_SELECTION ) )
            {
                currentTarget.set( rayMarcher.chunkID , rayMarcher.blockID );
                return true;
            }         
            // check bottom neighbour
            if ( TMP_SELECTION.bottomOf( rayMarcher.block ) ) 
            {
                chunk = chunk.bottomNeighbour;
                TMP_SELECTION.y = World.CHUNK_SIZE-1;
            }
            if ( chunk.isBlockNotEmpty( TMP_SELECTION ) )
            {
                currentTarget.set( rayMarcher.chunkID , rayMarcher.blockID );
                return true;
            }                
            // check back neighbour
            if ( TMP_SELECTION.backOf( rayMarcher.block ) ) 
            {
                chunk = chunk.backNeighbour;
                TMP_SELECTION.z = World.CHUNK_SIZE-1;
            }
            if ( chunk.isBlockNotEmpty( TMP_SELECTION ) )
            {
                currentTarget.set( rayMarcher.chunkID , rayMarcher.blockID );
                return true;
            }    
            // check front neighbour
            if ( TMP_SELECTION.frontOf( rayMarcher.block ) )
            {
                chunk = chunk.frontNeighbour;
                TMP_SELECTION.z = 0;
            }
            if ( chunk.isBlockNotEmpty( TMP_SELECTION ) )
            {
                currentTarget.set( rayMarcher.chunkID , rayMarcher.blockID );
                return true;
            }                 
        }        
        currentTarget.invalidate();
        return false;
    }
    
	private void setupRayMarching() 
	{
		// "aiming" starts at center of screen
        TMP1.set( Gdx.graphics.getWidth()/2f ,Gdx.graphics.getHeight()/2 , 0);
        world.camera.unproject( TMP1 ); // unproject to point on the near plane
        rayMarcher.set( TMP1 , world.camera.direction );

        // advance ray by one block so we don't select the block the player is currently standing in
        rayMarcher.advance();
        rayMarcher.advance();

        rayMarcher.distance = 0;
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
        font.draw(spriteBatch, append("Player direction: ",world.camera.direction), 10, y );

        y -= fontHeight;
        font.draw(spriteBatch, append("Loaded chunks: ",worldRenderer.getLoadedChunkCount()), 10, y );       

        y -= fontHeight;
        font.draw(spriteBatch, append("Visible chunks: ",worldRenderer.visibleChunkCount), 10, y );   
        
        y -= fontHeight;
        font.draw(spriteBatch, append("Total triangles: ",worldRenderer.totalTriangles), 10, y );  
        
        if ( currentTarget.isValid() ) 
        {
            y -= fontHeight;
            final long chunkID = currentTarget.chunkID;
            final int blockID = currentTarget.blockID;
            font.draw(spriteBatch, append("Selection: ",chunkID,blockID) , 10, y );
            y -= fontHeight;
            final int bx = BlockKey.getX( blockID );
            final int by = BlockKey.getY( blockID );
            final int bz = BlockKey.getZ( blockID );
            final byte level = chunkManager.getChunk( chunkID ).getLightLevel( bx , by, bz );
            font.draw(spriteBatch, append("Light level: ",level) , 10, y );            
        } else {
            y -= fontHeight;
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