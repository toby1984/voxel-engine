package de.codesourcery.voxelengine.engine;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.Validate;
import org.apache.log4j.Logger;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Frustum;
import com.badlogic.gdx.math.Plane;
import com.badlogic.gdx.utils.LongMap;
import com.badlogic.gdx.utils.LongMap.Entries;
import com.badlogic.gdx.utils.LongMap.Keys;

import de.codesourcery.voxelengine.model.Chunk;
import de.codesourcery.voxelengine.model.ChunkKey;
import de.codesourcery.voxelengine.model.Player;
import de.codesourcery.voxelengine.model.World;

/**
 * Responsible for rendering the game world.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public class WorldRenderer
{
    private static final Logger LOG = Logger.getLogger(WorldRenderer.class);

    // dummy value used when abusing a Map as a Set
    private static final Long DUMMY_VALUE = new Long(3L);

    public static final int RENDER_DISTANCE_CHUNKS = 3;
    
    public static final boolean CULL_FACES = true;
    
    public static final boolean DEPTH_BUFFER = true;    

    // Debug renderer
    public static final boolean RENDER_WIREFRAME =false;

    private final World world;

    private ChunkKey centerChunk=null;
    private long previousChunkID=0xdeadbeef;

    // TODO: This map should really be just a Set but libgdx doesn't provide this
    private final LongMap<Long> visibleChunks = new LongMap<>(100); // populated with the chunk IDs of all chunks that intersect the view frustum
    private final LongMap<Chunk> loadedChunks = new LongMap<>(400); // Holds all chunks that are currently loaded because they're within view distance of the camera

    // shader to use for rendering chunks
    private final ShaderProgram chunkShader;

    // the player instance
    private final Player player;
    
    private int visibleChunkCount=0; // TODO: Debug code, remove

    // float[] array to use when meshing a chunk
    private final VertexDataBuffer vertexBuffer = new VertexDataBuffer();

    public WorldRenderer(World world,ShaderManager shaderManager) 
    {
        Validate.notNull(world, "world must not be NULL");
        Validate.notNull(shaderManager,"shaderManager must not be NULL");
        this.world = world;
        this.player = world.player;
        this.chunkShader = shaderManager.getShader( RENDER_WIREFRAME ? ShaderManager.WIREFRAME_SHADER : ShaderManager.FLAT_SHADER );
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
     * Returns the number of chunks that actually intersect with the view frustum.
     * 
     * TODO: Debug code, remove when done     
     * @return
     */
    public int getVisibleChunkCount() {
        return visibleChunkCount;
    }

    /**
     * Render the world.
     * 
     * @param deltaTime Delta time (seconds) to previous frame
     */
    public void render(float deltaTime) 
    {
        final PerspectiveCamera camera = world.camera;

        final boolean doLog = LOG.isTraceEnabled(); // (frameCounter% 300) == 0;

        final long centerChunkID = player.cameraChunkID;

        visibleChunks.clear();
        visibleChunks.put( centerChunkID ,DUMMY_VALUE );

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
            final List<Long> toLoad = new ArrayList<>( (2*RENDER_DISTANCE_CHUNKS+1)*(2*RENDER_DISTANCE_CHUNKS+1) );

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
                        final long chunkID = ChunkKey.toID( x , y , z );
                        if ( ! loadedChunks.containsKey( chunkID ) ) 
                        {
                            toLoad.add( chunkID );
                        }                     
                        if ( ! ( borderX || borderY || borderZ ) ) 
                        {
                            final float pz = z * World.CHUNK_WIDTH;
                            if ( intersectsSphere(f,px,py,pz,World.CHUNK_HALF_WIDTH) ) 
                            { 
                                visibleChunks.put( chunkID , DUMMY_VALUE );
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
                    loadedChunks.put( chunk.chunkKey.toID() , chunk );
                }
            }       

            previousChunkID = centerChunkID;
        } 
        else 
        {
            // camera is still within the same chunk, just determine the visible chunks
            // , all chunks in view distance have already been loaded
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
                            if ( intersectsSphere(f,px,py,pz,World.CHUNK_HALF_WIDTH) ) 
                            { 
                                visibleChunks.put( ChunkKey.toID( x, y, z ) ,DUMMY_VALUE );
                            }
                        }
                    }                 
                }            
            }
        }

        visibleChunkCount = visibleChunks.size;
        
         // Rebuild & render visible chunks.
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
        
        chunkShader.begin();

        if ( ! WorldRenderer.RENDER_WIREFRAME ) {
            chunkShader.setUniformMatrix("u_modelView", camera.view );
            chunkShader.setUniformMatrix("u_normalMatrix", player.normalMatrix() );
        }
        chunkShader.setUniformMatrix("u_modelViewProjection", camera.combined );

        int totalTriangles = 0;

        final Keys it = visibleChunks.keys();
        while ( it.hasNext )
        {
            final long key=it.next(); 
            final Chunk chunk = loadedChunks.get(key);
            if ( chunk.isNotEmpty() ) 
            {
                if ( chunk.needsRebuild() ) 
                {
                    try {
                        buildMesh( chunk );
                    } 
                    catch(RuntimeException e) 
                    {
                        LOG.error("Failed to build mesh for "+chunk+" while center chunk is at "+ChunkKey.fromID( centerChunkID ) );
                        throw e;
                    }
                }                
                totalTriangles += chunk.mesh.render( chunkShader , doLog );
            }
        }
        chunkShader.end();        
        if ( doLog ) {
            LOG.trace("render(): Total triangles: "+totalTriangles);
        }
    }

    private static boolean intersectsSphere(Frustum f,float x,float y,float z,float radius) 
    {
        for(int i = 0; i < 6; ++i) 
        {
            final Plane plane = f.planes[i]; // distance to this plane
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
        if ( chunk.mesh == null ) 
        {
            chunk.mesh = new ChunkRenderer();
        }
        LOG.debug("buildMesh(): Building mesh for "+chunk);
        chunk.mesh.buildMesh( chunk , vertexBuffer );
    }
}