package de.codesourcery.voxelengine.model;

import org.apache.commons.lang3.Validate;

import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;

import de.codesourcery.voxelengine.engine.*;

public class World implements Disposable
{
    /**
     * Width/heigh/depth of a chunk in blocks.
     */
    public static final int CHUNK_SIZE = 32;
    
    /**
     * Total number of blocks in a chunk.
     */
    public static final int BLOCKS_IN_CHUNK = World.CHUNK_SIZE*World.CHUNK_SIZE*World.CHUNK_SIZE;
    
    /**
     * Size of a block in world space.
     */
    public static final float BLOCK_SIZE = 1f;
    
    /**
     * Size of half a block in world space.
     */
    public static final float HALF_BLOCK_SIZE = BLOCK_SIZE/2f;
    
    /**
     * width/height/depth of chunk in world space.
     */
    public static final float CHUNK_WIDTH = BLOCK_SIZE*CHUNK_SIZE;
    
    /**
     * Half width/height/depth of chunk in world space.
     */    
    public static final float CHUNK_HALF_WIDTH = CHUNK_WIDTH/2f;
    
    /**
     * Radius of a sphere that encloses a chunk (used when culling against view frustum).
     */
    public static final float CHUNK_ENCLOSING_SPHERE_RADIUS = (float) Math.sqrt( 3*CHUNK_HALF_WIDTH*CHUNK_HALF_WIDTH); // sqrt( dx*dx +dy*dy +dz*dz )
    
    public final ChunkManager chunkManager;
    
    public final Player player;
    
    public final PerspectiveCamera camera;
    
    public final BlockSelectionRenderer highlightedBlock;

    /**
     * Unit-testing only.
     */
    public World(ShaderManager shaderManager) {
        this.chunkManager = null;
        this.camera = null;
        this.player = new Player(this,null);
        this.highlightedBlock = new BlockSelectionRenderer( this , shaderManager , WorldRenderer.HIGHLIGHT_COLOR);
    }
    
    public World(ShaderManager shaderManager,ChunkManager chunkManager,PerspectiveCamera camera) 
    {
        Validate.notNull(chunkManager, "chunkManager must not be NULL");
        this.chunkManager = chunkManager;
        this.camera = camera;
        this.player = new Player(this,camera);
        this.highlightedBlock = new BlockSelectionRenderer( this , shaderManager , WorldRenderer.HIGHLIGHT_COLOR );        
    }
    
    /**
     * Returns the chunk that contains a given point (world coordinates).
     * 
     * @param v
     * @return
     */
    public Chunk getWorldChunk(Vector3 v) 
    {
        return chunkManager.getChunk( ChunkKey.getChunkID(v) );
    }

    @Override
    public void dispose() {
        highlightedBlock.dispose();
    }
}