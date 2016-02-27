package de.codesourcery.voxelengine.engine;

import org.apache.commons.lang3.Validate;

import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.Vector3;

public class World
{
    public static final int WORLD_CHUNK_SIZE = 32;
    
    public static final float WORLD_CHUNK_BLOCK_SIZE = 1f;
    public static final float WORLD_CHUNK_HALF_BLOCK_SIZE = WORLD_CHUNK_BLOCK_SIZE/2f;
    
    public static final float WORLD_CHUNK_WIDTH = WORLD_CHUNK_BLOCK_SIZE*WORLD_CHUNK_SIZE;
    public static final float WORLD_CHUNK_HALF_WIDTH = WORLD_CHUNK_WIDTH/2f;
    
    public final ChunkManager chunkManager;
    
    public final Player player = new Player(this);
    
    public final PerspectiveCamera camera;

    /**
     * Unit-testing only.
     */
    World() {
        this.chunkManager = null;
        this.camera = null;
    }
    
    public World(ChunkManager chunkManager,PerspectiveCamera camera) 
    {
        Validate.notNull(chunkManager, "chunkManager must not be NULL");
        this.chunkManager = chunkManager;
        this.camera = camera;
    }
    
    /**
     * Returns the chunk that contains a given point (world coordinates).
     * 
     * @param v
     * @return
     */
    public Chunk getWorldChunk(Vector3 v) 
    {
        return chunkManager.getChunk( getChunkCoordinates(v) );
    }

    /**
     * Returns the chunk coordinates for a given point (world coordinates).
     * 
     * @param v
     * @return
     */
    public ChunkKey getChunkCoordinates(Vector3 v) 
    {
        final int chunkX = (int) Math.floor( (v.x + WORLD_CHUNK_HALF_WIDTH) / WORLD_CHUNK_WIDTH);
        final int chunkY = (int) Math.floor( (v.y + WORLD_CHUNK_HALF_WIDTH) / WORLD_CHUNK_WIDTH);
        final int chunkZ = (int) Math.floor( (v.z + WORLD_CHUNK_HALF_WIDTH) / WORLD_CHUNK_WIDTH);
        return new ChunkKey( chunkX , chunkY , chunkZ );
    }    
}