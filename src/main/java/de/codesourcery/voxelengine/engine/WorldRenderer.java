package de.codesourcery.voxelengine.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.apache.commons.lang3.Validate;
import org.apache.log4j.Logger;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Frustum;
import com.badlogic.gdx.math.Plane;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.LongMap;
import com.badlogic.gdx.utils.LongMap.Entries;
import com.badlogic.gdx.utils.Queue;

import de.codesourcery.voxelengine.model.BlockType;
import de.codesourcery.voxelengine.model.Chunk;
import de.codesourcery.voxelengine.model.ChunkKey;
import de.codesourcery.voxelengine.model.Player;
import de.codesourcery.voxelengine.model.World;
import de.codesourcery.voxelengine.utils.IntQueue;

/**
 * Responsible for rendering the game world.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public class WorldRenderer implements Disposable
{
    private static final Logger LOG = Logger.getLogger(WorldRenderer.class);

    public static final int RENDER_DISTANCE_CHUNKS = 3;

    private static final int MAX_CHUNKS_TO_LOAD = (2*RENDER_DISTANCE_CHUNKS+1)*(2*RENDER_DISTANCE_CHUNKS+1)*(2*RENDER_DISTANCE_CHUNKS+1);

    public static final boolean CULL_FACES = true;

    public static final boolean DEPTH_BUFFER = true;    
    
    public static final float[] HIGHLIGHT_COLOR = new float[] {1,0,0,1};
    public static final float[] SELECTION_COLOR = new float[] {1,1,1,1};

    // Debug renderer
    public static final boolean RENDER_WIREFRAME =false;

    private final World world;

    private ChunkKey centerChunk=null;
    private long previousChunkID=ChunkKey.INVALID;

    // TODO: This map should really be just a Set but libgdx doesn't provide this
    private final LongMap<Chunk> visibleChunks = new LongMap<>(100); // populated with the chunk IDs of all chunks that intersect the view frustum
    private final LongMap<Chunk> loadedChunks = new LongMap<>(400); // Holds all chunks that are currently loaded because they're within view distance of the camera
    private final List<Chunk> chunksToRebuild = new ArrayList<>(MAX_CHUNKS_TO_LOAD);
    
    private Chunk[] visibleChunkList = new Chunk[ MAX_CHUNKS_TO_LOAD ];
    public int visibleChunkCount=0; 

    private final Queue<Chunk> lightChunkQueue = new Queue<Chunk>( 65535 );
    private final IntQueue lightBlockQueue = new IntQueue( World.BLOCKS_IN_CHUNK );

    // Comparator used to sort chunks in top->down (+y -> -y ) order for
    // properly calculating the influence of sun light
    private static final Comparator<Chunk> Y_COMPARATOR = new Comparator<Chunk>() {

        @Override
        public int compare(Chunk o1, Chunk o2) 
        {
            return Integer.compare( o2.chunkKey.y , o1.chunkKey.y ); 
        }
    }; 

    // shader to use for rendering chunks
    private final ShaderProgram chunkShader;

    // the player instance
    private final Player player;

    public int totalTriangles=0; // TODO: Debug code, remove

    // float[] array to use when meshing a chunk
    private final VertexDataBuffer vertexBuffer = new VertexDataBuffer();

    private final SkyBox skyBox;

    private final Texture blocksTexture;

    public WorldRenderer(World world,ShaderManager shaderManager,TextureManager textureManager) 
    {
        Validate.notNull(world, "world must not be NULL");
        Validate.notNull(shaderManager,"shaderManager must not be NULL");
        Validate.notNull(textureManager,"textureManager must not be NULL");
        this.world = world;
        this.player = world.player;
        this.chunkShader = shaderManager.getShader( RENDER_WIREFRAME ? ShaderManager.WIREFRAME_SHADER : ShaderManager.TEXTURED_SHADER );
        this.skyBox = new SkyBox( shaderManager );
        this.blocksTexture = textureManager.getTexture( TextureManager.BLOCKS_TEXTUREATLAS );
    }

    /**
     * Returns the number of chunks loaded because they're within viewing distance of the player.
     * 
     * TODO: Debug code, remove when done
     * @return
     */
    public int getLoadedChunkCount() {
        return loadedChunks.size;
    }

    /**
     * Render the world.
     * 
     * @param deltaTime Delta time (seconds) to previous frame
     */
    public void render(float deltaTime) 
    {
        final PerspectiveCamera camera = world.camera;

        final long centerChunkID = player.cameraChunkID;

        visibleChunks.clear();
        visibleChunks.put( centerChunkID , null );
        int visibleChunkCount = 0;

        if ( previousChunkID != centerChunkID || centerChunk == null ) // player has moved to a different chunk
        {
            centerChunk = ChunkKey.fromID( centerChunkID );

            final int distanceInChunksSquared = 3*(RENDER_DISTANCE_CHUNKS)*(RENDER_DISTANCE_CHUNKS);// dx*dx+dy*dy+dz*dz with dx == dy == dz

            /* Determine chunks that intersect with the view frustum.
             * 
             * Note that I intentionally add +1 to the distance because building the mesh for
             * any given chunk also requires looking at the chunk's neighbours when
             * considering the boundary blocks. 
             * If we'd just load all the chunks that are within the rendering distance
             * the outer chunks wouldn't have their neighbours loaded and a NPE would
             * happen during mesh building when trying to access the neighbour.
             */            
            final List<Long> toLoad = new ArrayList<>( MAX_CHUNKS_TO_LOAD );

            final Frustum f = world.camera.frustum;

            final int xmin = centerChunk.x - RENDER_DISTANCE_CHUNKS;
            final int xmax = centerChunk.x + RENDER_DISTANCE_CHUNKS;
            final int ymin = centerChunk.y - RENDER_DISTANCE_CHUNKS;
            final int ymax = centerChunk.y + RENDER_DISTANCE_CHUNKS;
            final int zmin = centerChunk.z - RENDER_DISTANCE_CHUNKS;
            final int zmax = centerChunk.z + RENDER_DISTANCE_CHUNKS;

            for ( int x = xmin ; x <= xmax ; x++ ) 
            {
                final float px = x * World.CHUNK_WIDTH;
                final boolean borderX = (x == xmin) || (x == xmax); 
                for ( int y = ymin ; y <= ymax ; y++ ) 
                {
                    final boolean borderY = (y == ymin) || (y == ymax); 
                    final float py = y * World.CHUNK_WIDTH;
                    for ( int z = zmin ; z <= zmax ; z++ ) 
                    {
                        final long chunkID = ChunkKey.toID( x , y , z );
                        final Chunk loaded = loadedChunks.get( chunkID ); 
                        if ( loaded == null ) 
                        {
                            toLoad.add( chunkID );
                        }                     
                        final boolean borderZ = ( z == zmin ) || (z == zmax);
                        if ( ! ( borderX || borderY || borderZ ) ) 
                        {
                            final float pz = z * World.CHUNK_WIDTH;
                            // TODO: Culling against enclosing sphere selects way more chunks than necessary...maybe use AABB instead ?
                            if ( intersectsSphere(f,px,py,pz,World.CHUNK_ENCLOSING_SPHERE_RADIUS) ) 
                            { 
                                visibleChunks.put( chunkID , loaded );
                                if ( loaded != null ) {
                                    visibleChunkList[visibleChunkCount++]=loaded;
                                }
                            }
                        }
                    }                 
                }            
            }

            // unload all chunks that are no longer within range
            final List<Chunk> toUnload = new ArrayList<>( loadedChunks.size );
            final Entries<Chunk> entries = loadedChunks.entries();
            while ( entries.hasNext )
            {
                final Chunk chunk = entries.next().value;
                final ChunkKey key = chunk.chunkKey;
                if ( centerChunk.dst2( key ) > distanceInChunksSquared ) 
                {
                    entries.remove();
                    chunk.setIsInUse( false ); // crucial otherwise chunk unloading will fail because sanity check triggers
                    chunk.disposeVBO();
                    toUnload.add( chunk );
                }
            }

            // bluk-unload chunks
            if ( ! toUnload.isEmpty() ) {
                System.out.println("*** Unloading: "+toUnload.size()+" chunks");
                world.chunkManager.unloadChunks( toUnload );
            }

            // bulk-load missing chunks
            if ( ! toLoad.isEmpty() ) 
            {
                System.out.println("*** Loading "+toLoad.size()+" chunks");
                for ( Chunk chunk : world.chunkManager.getChunks( toLoad ) ) 
                {
                    final long chunkID = chunk.chunkKey.toID();
                    loadedChunks.put( chunkID , chunk );
                    if ( visibleChunks.containsKey( chunkID ) ) {
                        visibleChunks.put( chunkID , chunk );
                        visibleChunkList[visibleChunkCount++]=chunk;
                    }
                }
            }       
            previousChunkID = centerChunkID;
        } 
        else 
        {
            // camera is still within the same chunk, just determine the visible chunks
            // since all chunks within view distance have already been loaded
            final Frustum f = world.camera.frustum;

            final int xmin = centerChunk.x - RENDER_DISTANCE_CHUNKS;
            final int xmax = centerChunk.x + RENDER_DISTANCE_CHUNKS;
            final int ymin = centerChunk.y - RENDER_DISTANCE_CHUNKS;
            final int ymax = centerChunk.y + RENDER_DISTANCE_CHUNKS;
            final int zmin = centerChunk.z - RENDER_DISTANCE_CHUNKS;
            final int zmax = centerChunk.z + RENDER_DISTANCE_CHUNKS;

            for ( int x = xmin ; x <= xmax ; x++ ) 
            {
                final float px = x * World.CHUNK_WIDTH;
                final boolean borderX = (x == xmin) || (x == xmax); 
                for ( int y = ymin ; y <= ymax ; y++ ) 
                {
                    final boolean borderY = (y == ymin) || (y == ymax); 
                    final float py = y * World.CHUNK_WIDTH;
                    for ( int z = zmin ; z <= zmax ; z++ ) 
                    {
                        final boolean borderZ = ( z == zmin ) || (z == zmax); 
                        if ( ! ( borderX || borderY || borderZ ) ) 
                        {
                            final float pz = z * World.CHUNK_WIDTH;
                            // TODO: Culling against enclosing sphere selects way more chunks than necessary...maybe use AABB instead ? 
                            if ( intersectsSphere(f,px,py,pz,World.CHUNK_ENCLOSING_SPHERE_RADIUS) ) 
                            { 
                                final long chunkID = ChunkKey.toID( x, y, z );
                                final Chunk chunk = loadedChunks.get( chunkID );
                                visibleChunks.put( chunkID ,  chunk );
                                if ( chunk != null ) {
                                    visibleChunkList[visibleChunkCount++]=chunk;
                                }
                            }
                        }
                    }                 
                }            
            }
        }
        this.visibleChunkCount = visibleChunkCount;

        // sort chunks in descending Y-coordinate order
        // in order to propagate sunlight from top -> bottom
        Arrays.sort( visibleChunkList , 0 , visibleChunkCount , Y_COMPARATOR );

        chunksToRebuild.clear();
        
        // lighting & re-meshing
        for ( int i = 0 ; i < visibleChunkCount ; i++ ) 
        {
            final Chunk chunk = visibleChunkList[i];
            if ( chunk.needsRebuild() ) 
            {
                if ( chunk.isNotEmpty() ) 
                {
                    chunksToRebuild.add( chunk );
                } else {
                    // set light levels even on empty chunks as well since
                    // (sun)light needs to be propagated to adjacent chunks 
                    chunk.setLightLevel( Chunk.LIGHTLEVEL_SUNLIGHT );
                    chunk.clearFlags( Chunk.FLAG_NEEDS_REBUILD );
                }
            }
        }
        
        if ( ! chunksToRebuild.isEmpty() ) 
        {
            calculateLighting( chunksToRebuild );
            
            for (int i = 0 , len = chunksToRebuild.size() ; i < len ; i++)
            {
                final Chunk chunk = chunksToRebuild.get(i);
                buildMesh( chunk );
                chunk.clearFlags( Chunk.FLAG_NEEDS_REBUILD );
            }
        }

        // render skybox 
        skyBox.render( world.camera );

        // Render visible,non-empty chunks
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

        Gdx.gl30.glEnable( GL20.GL_TEXTURE_2D );

        Gdx.gl30.glEnable ( GL20.GL_BLEND);
        Gdx.gl30.glBlendFunc ( GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        
        blocksTexture.bind();

        chunkShader.begin();

        if ( ! WorldRenderer.RENDER_WIREFRAME ) {
            //            chunkShader.setUniformMatrix("u_modelView", camera.view );
            //            chunkShader.setUniformMatrix("u_normalMatrix", player.normalMatrix() );
        }
        chunkShader.setUniformMatrix("u_modelViewProjection", camera.combined );

        int totalTriangles = 0;
        for ( int i = 0 ; i < visibleChunkCount ; i++ ) 
        {
            final Chunk chunk = visibleChunkList[i];
            if ( chunk.isNotEmpty() ) 
            {
                totalTriangles += chunk.renderer.render( chunkShader , false );
            }
        }
        this.totalTriangles = totalTriangles;
        chunkShader.end();   

        Gdx.gl30.glDisable( GL20.GL_TEXTURE_2D );
        
        Gdx.gl30.glDisable( GL20.GL_BLEND);
    }

    private void applySunlight(Chunk chunk) 
    {
        chunk.setLightLevel( (byte) 0 );

        // set light level to "sunlight" on all empty blocks in columns starting from top -> bottom
        // enqueuing all those blocks for flood-filling 
        for ( int z = 0 ; z < World.CHUNK_SIZE ; z++ ) 
        {
            for ( int x = 0 ; x < World.CHUNK_SIZE ; x++ ) 
            {
                if ( chunk.isBlockEmpty(x,World.CHUNK_SIZE-1,z) ) 
                {
                    final byte lightLevel = chunk.topNeighbour == null ? Chunk.LIGHTLEVEL_SUNLIGHT : chunk.topNeighbour.getLightLevel(x,0,z);
                    chunk.setLightLevel( x , World.CHUNK_SIZE-1 , z , lightLevel );
                    lightBlockQueue.push( Chunk.blockIndex( x , World.CHUNK_SIZE-1 , z ) );
                    lightChunkQueue.addLast( chunk );
                    for ( int y = World.CHUNK_SIZE-2 ; y >= 0 ; y --) 
                    {
                        final int blockIndex = Chunk.blockIndex( x , y , z );
                        if ( chunk.isBlockNotEmpty( blockIndex ) ) 
                        {
                            break;
                        }
                        chunk.setLightLevel( blockIndex , lightLevel );
                        lightBlockQueue.push( blockIndex );
                        lightChunkQueue.addLast( chunk );
                    }       
                }
            }                        
        }        
    }

    /**
     * Calculates light levels on each (empty) block by performing
     * a flood-fill starting at each light source.
     * 
     * @param chunks
     */
    private void calculateLighting(List<Chunk> chunks) 
    {
        // algorithm uses two queues (one for blocks and one for the chunk the block is in) 
        // instead of a single queue and something like a "QueueEntry" class to get around the 
        // need to do a massive number of object allocations (and thus generate a lot of GC pressure)
        lightBlockQueue.clear();
        lightChunkQueue.clear();

        // apply top-down sunlight to all chunks
        // and enqueue all light-emitting blocks as well
        for (int i = 0 , len = chunks.size() ; i < len ; i++) 
        {
            final Chunk chunk = chunks.get(i);
            applySunlight( chunk );

            // enqueue light-emitting (glowing) blocks
            for ( int blockIndex = 0 ; blockIndex < World.BLOCKS_IN_CHUNK ; blockIndex++ ) 
            {
                final int bt = chunk.getBlockType( blockIndex );
                if ( BlockType.emitsLight( bt ) ) {
                    chunk.setLightLevel( blockIndex , BlockType.getEmittedLightLevel( bt ) ); 
                    lightBlockQueue.push( blockIndex );
                    lightChunkQueue.addLast( chunk );
                }
            }
        }

        // recursively visit adjacent blocks each enqueued block
        // until the light level reaches 0
        while ( lightBlockQueue.isNotEmpty() ) 
        {
            final Chunk chunk = lightChunkQueue.removeFirst();
            final int blockIndex = lightBlockQueue.pop();
            
            final int x = Chunk.blockIndexX( blockIndex );
            final int y = Chunk.blockIndexY( blockIndex );
            final int z = Chunk.blockIndexZ( blockIndex );

            final byte currentLevel = chunk.getLightLevel( blockIndex );
            final byte newLevel = (byte) (currentLevel - 1);

            Chunk toCheck;
            int blockIdx; 
            boolean enqueue;
            
            // check top neighbour
            if ( (y+1) == World.CHUNK_SIZE ) 
            {
                toCheck = chunk.topNeighbour;
                blockIdx = Chunk.blockIndex(x,0,z);
                enqueue = toCheck != null && toCheck.isBlockEmpty( blockIdx );
            } else { // block to check is within current chunk
                toCheck = chunk;
                blockIdx = Chunk.blockIndex(x,y+1,z);
                enqueue = chunk.isBlockEmpty( blockIdx );
            }
            if ( enqueue && toCheck.getLightLevel( blockIdx ) < newLevel-1 ) 
            {
                doEnqueue(newLevel, toCheck, blockIdx);
            }
            
            // check bottom neighbour
            if ( (y-1) < 0 ) 
            {
                toCheck = chunk.bottomNeighbour;
                blockIdx = Chunk.blockIndex(x,World.CHUNK_SIZE-1,z);
                enqueue = toCheck != null && toCheck.isBlockEmpty( blockIdx );
            } else { // block to check is within current chunk
                toCheck = chunk;
                blockIdx = Chunk.blockIndex(x,y-1,z);
                enqueue = chunk.isBlockEmpty( blockIdx );
            }
            if ( enqueue && toCheck.getLightLevel( blockIdx ) < newLevel-1 ) 
            {
                doEnqueue(newLevel, toCheck, blockIdx);
            }            
            
            // check left neighbour
            if ( (x-1) < 0 ) 
            {
                toCheck = chunk.leftNeighbour;
                blockIdx = Chunk.blockIndex(World.CHUNK_SIZE-1,y,z);
                enqueue = toCheck != null && toCheck.isBlockEmpty( blockIdx );
            } else { // block to check is within current chunk
                toCheck = chunk;
                blockIdx = Chunk.blockIndex(x-1,y,z);
                enqueue = chunk.isBlockEmpty( blockIdx );
            }
            if ( enqueue && toCheck.getLightLevel( blockIdx ) < newLevel-1 ) 
            {
                doEnqueue(newLevel, toCheck, blockIdx);
            }  
            
            // check right neighbour
            if ( (x+1) == World.CHUNK_SIZE ) 
            {
                toCheck = chunk.rightNeighbour;
                blockIdx = Chunk.blockIndex(0,y,z);
                enqueue = toCheck != null && toCheck.isBlockEmpty( blockIdx );
            } else { // block to check is within current chunk
                toCheck = chunk;
                blockIdx = Chunk.blockIndex(x+1,y,z);
                enqueue = chunk.isBlockEmpty( blockIdx );
            }
            if ( enqueue && toCheck.getLightLevel( blockIdx ) < newLevel-1 ) 
            {
                doEnqueue(newLevel, toCheck, blockIdx);
            }  
            
            // check front neighbour
            if ( (z+1) == World.CHUNK_SIZE ) 
            {
                toCheck = chunk.frontNeighbour;
                blockIdx = Chunk.blockIndex(x,y,0);
                enqueue = toCheck != null && toCheck.isBlockEmpty( blockIdx );
            } else { // block to check is within current chunk
                toCheck = chunk;
                blockIdx = Chunk.blockIndex(x,y,z+1);
                enqueue = chunk.isBlockEmpty( blockIdx );
            }
            if ( enqueue && toCheck.getLightLevel( blockIdx ) < newLevel-1 ) 
            {
                doEnqueue(newLevel, toCheck, blockIdx);
            }             
            
            // check back neighbour
            if ( (z-1) < 0 ) 
            {
                toCheck = chunk.backNeighbour;
                blockIdx = Chunk.blockIndex(x,y,World.CHUNK_SIZE-1);
                enqueue = toCheck != null && toCheck.isBlockEmpty( blockIdx );
            } else { // block to check is within current chunk
                toCheck = chunk;
                blockIdx = Chunk.blockIndex(x,y,z-1);
                enqueue = chunk.isBlockEmpty( blockIdx );
            }
            if ( enqueue && toCheck.getLightLevel( blockIdx ) < newLevel-1 ) 
            {
                doEnqueue(newLevel, toCheck, blockIdx);
            }             
        }
    }

    private void doEnqueue(final byte newLevel, Chunk toCheck, int blockIdx) 
    {
        toCheck.setLightLevel( blockIdx , newLevel );
        if ( newLevel > 1 ) {
            lightBlockQueue.push( blockIdx );
            lightChunkQueue.addLast( toCheck );
        }
    }

    private static boolean intersectsSphere(Frustum f,float x,float y,float z,float radius) 
    {
        for(int i = 0; i < 6; ++i) 
        {
            final Plane plane = f.planes[i]; 
            final float dist = plane.normal.dot( x , y, z ) + plane.d;

            if (dist < -radius) {
                return false; // outside of frustum
            }

            if ( Math.abs(dist) < radius) {
                return true; // intersects with frustum
            }
        }
        // inside frustum
        return true;
    }

    private void buildMesh(Chunk chunk) 
    {
        if ( chunk.renderer == null ) 
        {
            chunk.renderer = new ChunkRenderer(chunk);
        }
        LOG.debug("buildMesh(): Building mesh for "+chunk);
        chunk.renderer.buildMesh( vertexBuffer );
    }

    @Override
    public void dispose() {
        skyBox.dispose();
    }
}