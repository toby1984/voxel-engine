package de.codesourcery.voxelengine.engine;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
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
import com.badlogic.gdx.utils.Disposable;

import de.codesourcery.voxelengine.shaders.ShaderManager;

public class WorldRenderer implements Disposable
{
    private static final Logger LOG = Logger.getLogger(WorldRenderer.class);
    
    /**
     * How far 
     */
    protected static final float RENDER_DISTANCE = 100f;
    protected static final float RENDER_DISTANCE_SQUARED = RENDER_DISTANCE*RENDER_DISTANCE;
    
    public static final boolean RENDER_WIREFRAME =false;
    
    private final World world;
    
    private final Map<ChunkKey,Chunk> loadedChunks = new HashMap<>();
    
    private final ShaderProgram shader;
    
    private int frameCounter; // TODO: Remove debug code

    public WorldRenderer(World world,ShaderManager shaderManager) 
    {
        Validate.notNull(world, "world must not be NULL");
        Validate.notNull(shaderManager,"shaderManager must not be NULL");
        this.world = world;
        this.shader = shaderManager.getShader( RENDER_WIREFRAME ? ShaderManager.WIREFRAME_SHADER : ShaderManager.FLAT_SHADER );
    }
    
    public void render(float deltaTime) 
    {
        final PerspectiveCamera camera = world.camera;
        
        frameCounter++;
        
        final boolean doLog = false; // (frameCounter% 300) == 0;
        
        final ChunkKey center = world.getChunkCoordinates( world.camera.position );
        
        // determine chunks that are inside the view frustum
        final int distanceInChunks = 1+ (int) (RENDER_DISTANCE/World.WORLD_CHUNK_WIDTH); 
        
        final Set<ChunkKey> visibleChunks = new HashSet<>();
        final Frustum f = world.camera.frustum;
        final Vector3 chunkCenter = new Vector3();
        for ( int x = center.x - distanceInChunks, xmax = center.x + distanceInChunks  ; x < xmax ; x++ ) 
        {
            chunkCenter.x = x * World.WORLD_CHUNK_WIDTH;
            for ( int y = center.y - distanceInChunks, ymax = center.y + distanceInChunks  ; y < ymax ; y++ ) 
            {
                chunkCenter.y = y * World.WORLD_CHUNK_WIDTH;
                for ( int z = center.z - distanceInChunks, zmax = center.z + distanceInChunks  ; z < zmax ; z++ ) 
                {
                    chunkCenter.z = z * World.WORLD_CHUNK_WIDTH;
                    if ( f.sphereInFrustum( chunkCenter , World.WORLD_CHUNK_HALF_WIDTH ) ) { // TODO: Maybe check against actual bounding box here? Slower but more accurate
                        final ChunkKey key = new ChunkKey(x,y,z );
                        visibleChunks.add( key  );
                    }
                }                 
            }            
        }
        
        for (Iterator<Entry<ChunkKey, Chunk>> it = loadedChunks.entrySet() .iterator(); it.hasNext();) 
        {
            final Chunk chunk = it.next().getValue();
            if ( ! visibleChunks.contains( chunk.chunkKey ) && chunk.distanceSquared( world.camera.position ) > RENDER_DISTANCE_SQUARED ) 
            {
                chunk.clearFlags( Chunk.FLAG_DONT_UNLOAD );
                it.remove();
                world.chunkManager.unloadChunk( chunk );
            }
        }
        
        if ( doLog && LOG.isDebugEnabled() ) {
            LOG.debug("render(): Loaded chunks: "+world.chunkManager.getLoadedChunkCount()+" / in-range: "+loadedChunks.size());
        }           
        
        /*
         * Rebuild & render visible chunks.
         */
        
        shader.begin();
        shader.setUniformMatrix("u_modelView", camera.view );
        shader.setUniformMatrix("u_modelViewProjection", camera.combined );
        final Matrix4 normalMatrix = camera.view.cpy().toNormalMatrix();
        shader.setUniformMatrix("u_normalMatrix", normalMatrix );
        
        int totalTriangles = 0;
        for ( ChunkKey key : visibleChunks ) 
        {
            Chunk chunk = loadedChunks.get(key);
            if ( chunk == null )
            {
                chunk = world.chunkManager.getChunk( key );
                loadedChunks.put( key , chunk  );
            } 
            
            chunk.setFlags( Chunk.FLAG_DONT_UNLOAD );
            if ( chunk.isNotEmpty() ) 
            {
                if ( chunk.needsRebuild() ) { 
                    buildMesh( chunk );
                }
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
        chunk.mesh.buildMesh( chunk );
    }

    @Override
    public void dispose() 
    {
        for ( Chunk chunk : loadedChunks.values() ) 
        {
            chunk.clearFlags( Chunk.FLAG_DONT_UNLOAD );
            world.chunkManager.unloadChunk( chunk );
        }
    }
}