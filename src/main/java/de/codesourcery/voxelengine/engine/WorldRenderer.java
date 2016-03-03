package de.codesourcery.voxelengine.engine;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.lang3.Validate;
import org.apache.log4j.Logger;

import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Frustum;
import com.badlogic.gdx.math.Plane;
import com.badlogic.gdx.utils.LongMap;
import com.badlogic.gdx.utils.LongMap.Keys;

import de.codesourcery.voxelengine.shaders.ShaderManager;

public class WorldRenderer
{
    private static final Logger LOG = Logger.getLogger(WorldRenderer.class);

    private static final Long DUMMY_VALUE = new Long(3L);

    /**
     * How far 
     */
    public static final int RENDER_DISTANCE_CHUNKS = 3;

    public static final boolean RENDER_WIREFRAME =false;

    private final World world;

    private ChunkKey centerChunk=null;
    private long previousChunkID=0xdeadbeef;

    private final LongMap<Long> visibleChunks = new LongMap<>(100);
    private final LongMap<Chunk> loadedChunks = new LongMap<>(400);

    private final ShaderProgram shader;

    private final Player player;
    private int visibleChunkCount=0;

    private final VertexDataBuffer vertexBuffer = new VertexDataBuffer();

    public WorldRenderer(World world,ShaderManager shaderManager) 
    {
        Validate.notNull(world, "world must not be NULL");
        Validate.notNull(shaderManager,"shaderManager must not be NULL");
        this.world = world;
        this.player = world.player;
        this.shader = shaderManager.getShader( RENDER_WIREFRAME ? ShaderManager.WIREFRAME_SHADER : ShaderManager.FLAT_SHADER );
    }

    public int getLoadedChunkCount() {
        return loadedChunks.size;
    }

    public int getVisibleChunkCount() {
        return visibleChunkCount;
    }

    public void render(float deltaTime) 
    {
        final PerspectiveCamera camera = world.camera;

        final boolean doLog = LOG.isTraceEnabled(); // (frameCounter% 300) == 0;

        final long centerChunkID = player.cameraChunkID;

        visibleChunks.clear();
        visibleChunks.put( centerChunkID ,DUMMY_VALUE );

        if ( previousChunkID != centerChunkID || centerChunk == null ) 
        {
            centerChunk = ChunkKey.fromID( centerChunkID );

            /* Determine chunks that are inside the view frustum
             * 
             * Note that I intentionally add +1 to the distance because building the mesh for
             * any given chunk also requires looking at the chunk's neighbours and 
             * if we'd just load all the chunks that are within the rendering distance
             * the outer chunks wouldn't have their neighbours loaded and thus a NPE would
             * happen during mesh building.
             */

            final int distanceInChunksSquared = 3*(RENDER_DISTANCE_CHUNKS)*(RENDER_DISTANCE_CHUNKS);// dx*dx+dy*dy+dz*dz with dx == dy == dz

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
                final float px = x * World.WORLD_CHUNK_WIDTH;
                final boolean borderX = (x == xmin) || (x == xmax); 
                for ( int y = ymin ; y <= ymax ; y++ ) 
                {
                    final boolean borderY = (y == ymin) || (y == ymax); 
                    final float py = y * World.WORLD_CHUNK_WIDTH;
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
                            final float pz = z * World.WORLD_CHUNK_WIDTH;
                            if ( intersectsSphere(f,px,py,pz,World.WORLD_CHUNK_HALF_WIDTH) ) 
                            { 
                                visibleChunks.put( chunkID , DUMMY_VALUE );
                            }
                        }
                    }                 
                }            
            }

            // unload chunks that are out-of-range
            final List<Chunk> toUnload = new ArrayList<>( loadedChunks.size );
            for (Iterator<com.badlogic.gdx.utils.LongMap.Entry<Chunk>> it = loadedChunks.entries().iterator(); it.hasNext();) 
            {
                final Chunk chunk = it.next().value;
                final ChunkKey key = chunk.chunkKey;
                if ( centerChunk.dst2( key ) > distanceInChunksSquared ) 
                {
                    it.remove();
                    chunk.setIsInUse( false ); // crucial otherwise chunk unloading will fail because sanity check triggers
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
            final Frustum f = world.camera.frustum;

            final int xmin = centerChunk.x - RENDER_DISTANCE_CHUNKS;
            final int xmax = centerChunk.x + RENDER_DISTANCE_CHUNKS;
            final int ymin = centerChunk.y - RENDER_DISTANCE_CHUNKS;
            final int ymax = centerChunk.y + RENDER_DISTANCE_CHUNKS;
            final int zmin = centerChunk.z - RENDER_DISTANCE_CHUNKS;
            final int zmax = centerChunk.z + RENDER_DISTANCE_CHUNKS;

            for ( int x = xmin ; x <= xmax ; x++ ) 
            {
                final float px = x * World.WORLD_CHUNK_WIDTH;
                final boolean borderX = (x == xmin) || (x == xmax); 
                for ( int y = ymin ; y <= ymax ; y++ ) 
                {
                    final boolean borderY = (y == ymin) || (y == ymax); 
                    final float py = y * World.WORLD_CHUNK_WIDTH;
                    for ( int z = zmin ; z <= zmax ; z++ ) 
                    {
                        final boolean borderZ = ( z == zmin ) || (z == zmax); 
                        if ( ! ( borderX || borderY || borderZ ) ) 
                        {
                            final float pz = z * World.WORLD_CHUNK_WIDTH;
                            if ( intersectsSphere(f,px,py,pz,World.WORLD_CHUNK_HALF_WIDTH) ) 
                            { 
                                visibleChunks.put( ChunkKey.toID( x, y, z ) ,DUMMY_VALUE );
                            }
                        }
                    }                 
                }            
            }
        }

        visibleChunkCount = visibleChunks.size;    

        /*
         * Rebuild & render visible chunks.
         */
        shader.begin();

        if ( ! WorldRenderer.RENDER_WIREFRAME ) {
            shader.setUniformMatrix("u_modelView", camera.view );
            shader.setUniformMatrix("u_normalMatrix", player.normalMatrix() );
        }
        shader.setUniformMatrix("u_modelViewProjection", camera.combined );

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
                totalTriangles += chunk.mesh.render( shader , camera , doLog );
            }
        }
        shader.end();        
        if ( doLog ) {
            LOG.trace("render(): Total triangles: "+totalTriangles);
        }
    }

    private static boolean intersectsSphere(Frustum f,float x,float y,float z,float radius) 
    {
        // calculate our distances to each of the planes
        for(int i = 0; i < 6; ++i) 
        {
            // find the distance to this plane
            final Plane plane = f.planes[i];
            final float dist = plane.normal.dot( x , y, z ) + plane.d;

            // if this distance is < -sphere.radius, we are outside
            if (dist < -radius) {
                return false;
            }

            // else if the distance is between +- radius, then we intersect
            if ( Math.abs(dist) < radius) {
                return true;
            }
        }
        // otherwise fully in view
        return true;
    }

    private void buildMesh(Chunk chunk) 
    {
        if ( chunk.mesh == null ) 
        {
            chunk.mesh = new VoxelMesh();
        }
        LOG.debug("buildMesh(): Building mesh for "+chunk);
        chunk.mesh.buildMesh( chunk , vertexBuffer );
    }
}