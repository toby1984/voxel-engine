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
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;

import de.codesourcery.voxelengine.shaders.ShaderManager;

public class WorldRenderer
{
    private static final Logger LOG = Logger.getLogger(WorldRenderer.class);
    
    private static final boolean DEBUG = false;
    
    /**
     * How far 
     */
    public static final float RENDER_DISTANCE = World.WORLD_CHUNK_WIDTH*3;
    public static final float RENDER_DISTANCE_SQUARED = RENDER_DISTANCE*RENDER_DISTANCE;
    
    public static final boolean RENDER_WIREFRAME =false;
    
    private final World world;
    
    private final Map<ChunkKey,Chunk> loadedChunks = new HashMap<>();
    
    private final ShaderProgram shader;
    
    private int visibleChunkCount=0;
    private int frameCounter; // TODO: Remove debug code
    
    private final VertexDataBuffer vertexBuffer = new VertexDataBuffer();
    
    private final Vector3 chunkCenter = new Vector3();
    private final Set<ChunkKey> visibleChunks = new HashSet<>();

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
        
        final boolean doLog = (frameCounter% 300) == 0;
        
        final ChunkKey center = world.getChunkCoordinates( world.camera.position );
        
        // determine chunks that are inside the view frustum
        final int distanceInChunks = (int) Math.ceil(RENDER_DISTANCE/World.WORLD_CHUNK_WIDTH); 

        visibleChunks.clear();
        
        final Frustum f = world.camera.frustum;
        
        visibleChunkCount = 0;
        final List<ChunkKey> toLoad = new ArrayList<>( 50 );
        for ( int x = center.x - distanceInChunks, xmax = center.x + distanceInChunks  ; x <= xmax ; x++ ) 
        {
            chunkCenter.x = x * World.WORLD_CHUNK_WIDTH;
            for ( int y = center.y - distanceInChunks, ymax = center.y + distanceInChunks  ; y <= ymax ; y++ ) 
            {
                chunkCenter.y = y * World.WORLD_CHUNK_WIDTH;
                for ( int z = center.z - distanceInChunks, zmax = center.z + distanceInChunks  ; z <= zmax ; z++ ) 
                {
                    chunkCenter.z = z * World.WORLD_CHUNK_WIDTH;
                    // TODO: Maybe check against actual bounding box here? Slower but more accurate
                    if ( f.sphereInFrustum( chunkCenter , World.WORLD_CHUNK_HALF_WIDTH ) ) 
                    { 
                        visibleChunkCount++;
                        final ChunkKey key = new ChunkKey(x,y,z );
                        visibleChunks.add( key  );
                        if ( ! loadedChunks.containsKey( key ) ) 
                        {
                            toLoad.add( key );
                        }
                    }
                }                 
            }            
        }
        
        final List<Chunk> toUnload = new ArrayList<>( loadedChunks.size() );
        for (Iterator<Entry<ChunkKey, Chunk>> it = loadedChunks.entrySet().iterator(); it.hasNext();) 
        {
            final Chunk chunk = it.next().getValue();
            if ( ! visibleChunks.contains( chunk.chunkKey ) && chunk.distanceSquared( world.camera.position ) > RENDER_DISTANCE_SQUARED ) 
            {
                it.remove();
                chunk.setIsInUse( false ); // crucial otherwise chunk unloading will fail because of tripped sanity check
                toUnload.add( chunk );
            }
        }
        
        // bluk-unload chunks
        if ( ! toUnload.isEmpty() ) {
            world.chunkManager.unloadChunks( toUnload );
        }
        
        // bulk-load missing chunks
        if ( ! toLoad.isEmpty() ) 
        {
            final List<Chunk> loaded = world.chunkManager.getChunks( toLoad );
            for ( Chunk chunk : loaded ) 
            {
                if ( doLog && LOG.isDebugEnabled() ) {
                    LOG.debug("render(): Fetched chunk "+chunk);
                }  
                loadedChunks.put( chunk.chunkKey , chunk );
                if ( chunk.isNotEmpty() && chunk.needsRebuild() ) 
                {
                    buildMesh( chunk );
                }
            }
        }
        
        if ( doLog && LOG.isDebugEnabled() ) {
            LOG.debug("render(): Chunks loaded: "+loadedChunks.size() );
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
                totalTriangles += chunk.mesh.render( shader , camera , doLog );
            }
        }
        shader.end();        
        if ( doLog ) {
            LOG.debug("render(): Total triangles: "+totalTriangles);
        }
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