package de.codesourcery.voxelengine.engine;

import com.badlogic.gdx.math.Vector3;

import de.codesourcery.voxelengine.model.BlockKey;
import de.codesourcery.voxelengine.model.ChunkKey;
import de.codesourcery.voxelengine.model.World;

public class RayMarcher {

    private static final float STEP_INC = World.HALF_BLOCK_SIZE;
    
    public final Vector3 currentPoint = new Vector3();
    
    public long chunkID;
    public int chunkX;
    public int chunkY;
    public int chunkZ;
    
    public int blockID;
    public final BlockKey block=new BlockKey();
    
    private final Vector3 direction = new Vector3();
    private final Vector3 step = new Vector3();
    
    public float distance = 0;
    
    public void set(Vector3 point,Vector3 direction) {
        this.currentPoint.set(point);
        this.direction.set( direction );
        this.step.set( direction ).scl( STEP_INC );
        
        this.distance = 0;
        
        setChunk();
        setBlock();
    }
    
    private void setChunk()
    {
        final long chunkID = ChunkKey.getChunkID( currentPoint );
        this.chunkID = chunkID;
        
        chunkX = ChunkKey.getX( chunkID );
        chunkY = ChunkKey.getY( chunkID );
        chunkZ = ChunkKey.getZ( chunkID );
    }
    
    private void setBlock() 
    {
        final int blockID = BlockKey.getBlockID( chunkID , currentPoint );
        this.blockID = blockID;
        block.populateFromID( blockID );
    }
    
    public void advance() 
    {
        currentPoint.add( step );
        distance += STEP_INC;
        
        final long newChunkID = ChunkKey.getChunkID( currentPoint );
        if ( newChunkID != chunkID ) {
            setChunk();
        } 
        setBlock();
    }
}
