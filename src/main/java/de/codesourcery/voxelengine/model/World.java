package de.codesourcery.voxelengine.model;

import org.apache.commons.lang3.Validate;

import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;

import de.codesourcery.voxelengine.engine.ChunkManager;
import de.codesourcery.voxelengine.engine.SelectedBlock;
import de.codesourcery.voxelengine.engine.ShaderManager;

public class World implements Disposable
{
    /**
     * Width/heigh/depth of a chunk in blocks.
     */
    public static final int CHUNK_SIZE = 32;
    
    /**
     * Size of a block in world coordinates.
     */
    public static final float BLOCK_SIZE = 1f;
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
    
    public final SelectedBlock selectedBlock;

    /**
     * Unit-testing only.
     */
    public World(ShaderManager shaderManager) {
        this.chunkManager = null;
        this.camera = null;
        this.player = new Player(this,null);
        this.selectedBlock = new SelectedBlock( this , shaderManager );
    }
    
    public World(ShaderManager shaderManager,ChunkManager chunkManager,PerspectiveCamera camera) 
    {
        Validate.notNull(chunkManager, "chunkManager must not be NULL");
        this.chunkManager = chunkManager;
        this.camera = camera;
        this.player = new Player(this,camera);
        this.selectedBlock = new SelectedBlock( this , shaderManager );        
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
        selectedBlock.dispose();
    }
}