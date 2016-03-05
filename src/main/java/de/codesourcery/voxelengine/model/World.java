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
    public static final int CHUNK_SIZE = 32;
    
    public static final float CHUNK_BLOCK_SIZE = 1f;
    public static final float CHUNK_HALF_BLOCK_SIZE = CHUNK_BLOCK_SIZE/2f;
    
    public static final float CHUNK_WIDTH = CHUNK_BLOCK_SIZE*CHUNK_SIZE;
    public static final float CHUNK_HALF_WIDTH = CHUNK_WIDTH/2f;
    
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