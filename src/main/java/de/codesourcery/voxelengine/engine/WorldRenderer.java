package de.codesourcery.voxelengine.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.lang3.Validate;
import org.apache.log4j.Logger;

import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Frustum;
import com.badlogic.gdx.math.Plane;

import de.codesourcery.voxelengine.shaders.ShaderManager;

public class WorldRenderer
{
    private static final Logger LOG = Logger.getLogger(WorldRenderer.class);
    
    private static final boolean DEBUG = false;
    
    /**
     * How far 
     */
    public static final float RENDER_DISTANCE = World.WORLD_CHUNK_WIDTH*3;
    public static final float RENDER_DISTANCE_SQUARED = 3*RENDER_DISTANCE*RENDER_DISTANCE; // dx*dx + dy*dy + dz*dz
    
    public static final boolean RENDER_WIREFRAME =false;
    
    private final World world;
    
    private final Map<ChunkKey,Chunk> loadedChunks = new HashMap<>(400);
    
    private final ShaderProgram shader;
    
    private int visibleChunkCount=0;
    private int frameCounter; // TODO: Remove debug code
    
    private final VertexDataBuffer vertexBuffer = new VertexDataBuffer();
    
    public WorldRenderer(World world,ShaderManager shaderManager) 
    {
        Validate.notNull(world, "world must not be NULL");
        Validate.notNull(shaderManager,"shaderManager must not be NULL");
        this.world = world;
        this.shader = shaderManager.getShader( RENDER_WIREFRAME ? ShaderManager.WIREFRAME_SHADER : ShaderManager.FLAT_SHADER );
    }
    
    public int getLoadedChunkCount() {
        return loadedChunks.size();
    }
    
    public int getVisibleChunkCount() {
        return visibleChunkCount;
    }
    
    public void render(float deltaTime) 
    {
        final PerspectiveCamera camera = world.camera;
        
        frameCounter++;
        
        final boolean doLog = true; // (frameCounter% 300) == 0;
        
        final ChunkKey centerChunk = world.getChunkCoordinates( world.camera.position );
        
        /* Determine chunks that are inside the view frustum
         * 
         * Note that I intentionally add +1 to the distance because building the mesh for
         * any given chunk also requires looking at the chunk's neighbours and 
         * if we'd just load all the chunks that are within the rendering distance
         * the outer chunks wouldn't have their neighbours loaded and thus a NPE would
         * happen during mesh building.
         */
        final int distanceInChunks = 1+(int) Math.ceil(RENDER_DISTANCE/World.WORLD_CHUNK_WIDTH); 
        final int distanceInChunksSquared = 3*(distanceInChunks)*(distanceInChunks);// dx*dx+dy*dy+dz*dz with dx == dy == dz
        
        final Set<ChunkKey> visibleChunks = new HashSet<>(100);
        visibleChunks.clear();
        
        final Frustum f = world.camera.frustum;
        final List<ChunkKey> toLoad = new ArrayList<>( (2*distanceInChunks+1)*(2*distanceInChunks+1) );
        
        final int xmin = centerChunk.x - distanceInChunks;
        final int xmax = centerChunk.x + distanceInChunks;
        
        final int ymin = centerChunk.y - distanceInChunks;
        final int ymax = centerChunk.y + distanceInChunks;
        
        final int zmin = centerChunk.z - distanceInChunks;
        final int zmax = centerChunk.z + distanceInChunks;
        
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
                    final ChunkKey key = new ChunkKey( x,y,z );
                    if ( ! loadedChunks.containsKey( key ) ) 
                    {
                        toLoad.add( key );
                    }                     
                    if ( ! ( borderX || borderY || borderZ ) ) 
                    {
                        final float pz = z * World.WORLD_CHUNK_WIDTH;
                        if ( intersectsSphere(f,px,py,pz,World.WORLD_CHUNK_HALF_WIDTH) ) 
//                        if ( f.sphereInFrustum( px,py,pz, World.WORLD_CHUNK_HALF_WIDTH )  ) 
                        { 
                            visibleChunks.add( key  );
                        }
                    }
                }                 
            }            
        }
        visibleChunkCount = visibleChunks.size();
        
        // unload chunks that are out-of-range
        final List<Chunk> toUnload = new ArrayList<>( loadedChunks.size() );
        for (Iterator<Entry<ChunkKey, Chunk>> it = loadedChunks.entrySet().iterator(); it.hasNext();) 
        {
            final Chunk chunk = it.next().getValue();
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
            for ( Chunk chunk : world.chunkManager.getChunks( toLoad ) ) 
            {
                loadedChunks.put( chunk.chunkKey , chunk );
            }
        }
        
        /*
         * Rebuild & render visible chunks.
         */
        shader.begin();
        
        if ( ! WorldRenderer.RENDER_WIREFRAME ) {
            shader.setUniformMatrix("u_modelView", camera.view );
            shader.setUniformMatrix("u_normalMatrix", world.player.normalMatrix() );
        }
        shader.setUniformMatrix("u_modelViewProjection", camera.combined );
        
        int totalTriangles = 0;
        for ( ChunkKey key : visibleChunks ) 
        {
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
                        System.err.println("Failed to build mesh for "+chunk+" while center chunk is at "+centerChunk);
                        throw e;
                    }
                }                
                totalTriangles += chunk.mesh.render( shader , camera , doLog );
            }
        }
        shader.end();        
        if ( doLog ) {
            LOG.debug("render(): Total triangles: "+totalTriangles);
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
            chunk.mesh = new VoxelMesh( world.chunkManager );
        }
        chunk.mesh.buildMesh( chunk , vertexBuffer );
    }
}