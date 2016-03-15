package de.codesourcery.voxelengine.utils;

import com.badlogic.gdx.math.Vector3;

import de.codesourcery.voxelengine.model.BlockKey;
import de.codesourcery.voxelengine.model.ChunkKey;
import de.codesourcery.voxelengine.model.World;

public class CubicBlockSelection implements IBlockSelection 
{
    private int sizeInBlocks;
    
    // minimum in WORLD coordinates
    // this has ALWAYS be to be the center of a block
    private final Vector3 min = new Vector3();
    
    // maximum in WORLD coordinates
    // this has ALWAYS be to be the center of a block
    private final Vector3 max = new Vector3();
    
    private final Vector3 p1 = new Vector3();
    private final Vector3 p2 = new Vector3();
    
    private boolean p1Set;
    
    private boolean hasChanged;
    
    private final Vector3 tmp = new Vector3();
    
    @Override
    public int size() 
    {
        return sizeInBlocks;
    }

    @Override
    public void visitSelection(SelectionVisitor visitor) 
    {
        if ( isEmpty() ) {
            return;
        }
        
        int dx = 1+(int) ((max.x - min.x ) / World.BLOCK_SIZE);
        int dy = 1+(int) ((max.y - min.y ) / World.BLOCK_SIZE);
        int dz = 1+(int) ((max.z - min.z ) / World.BLOCK_SIZE);
        
        for ( int x = 0 ; x < dx ; x++ ) 
        {
            float px = min.x + x*World.BLOCK_SIZE;
            for ( int y = 0 ; y < dy ; y++ ) 
            {
                float py = min.y + y*World.BLOCK_SIZE;
                for ( int z = 0 ; z < dz ; z++ ) {
                    float pz = min.z + z*World.BLOCK_SIZE;
                    tmp.set(px,py,pz);
                    final long chunkID = ChunkKey.getChunkID( tmp );
                    final int blockID = BlockKey.getBlockID( chunkID , tmp );
                    visitor.visit( chunkID,blockID);
                }
            }
        }
    }

    @Override
    public boolean hasChanged() {
        return hasChanged;
    }

    @Override
    public void resetChanged() {
        this.hasChanged = false;
    }

    @Override
    public void set(long chunkId, int blockId) 
    {
        clear();
        add( chunkId , blockId );
    } 

    @Override
    public boolean remove(long chunkId, int blockId) {
        throw new RuntimeException("method not implemented: remove");
    }

    @Override
    public void add(long chunkId, int blockId) 
    {
        final int bx = BlockKey.getX( blockId );
        final int by = BlockKey.getY( blockId );
        final int bz = BlockKey.getZ( blockId );
        BlockKey.getBlockCenter(chunkId, bx,by,bz,tmp);        
        if ( ! p1Set ) 
        {
            p1.set( tmp );
            min.set( tmp );
            max.set( tmp );
            p1Set = true;
            sizeInBlocks = 1;
            hasChanged = true;
            return;
        } 
        p2.set( tmp );
        
        min.x = p1.x < p2.x ? p1.x : p2.x;
        min.y = p1.y < p2.y ? p1.y : p2.y;
        min.z = p1.z < p2.z ? p1.z : p2.z;
        
        max.x = p1.x > p2.x ? p1.x : p2.x;
        max.y = p1.y > p2.y ? p1.y : p2.y;
        max.z = p1.z > p2.z ? p1.z : p2.z;

        int dx = 1+(int) ((max.x - min.x ) / World.BLOCK_SIZE);
        int dy = 1+(int) ((max.y - min.y ) / World.BLOCK_SIZE);
        int dz = 1+(int) ((max.z - min.z ) / World.BLOCK_SIZE);
        
        sizeInBlocks = dx*dy*dz;
        hasChanged = true;
    }

    @Override
    public void clear() 
    {
        if ( size() > 0 ) 
        {
            p1Set = false;
            sizeInBlocks = 0;
            hasChanged = true;
        }
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public boolean isNotEmpty() {
        return size() != 0;
    }

    @Override
    public boolean isPartOfSelection(long chunkId, int blockId) {
        if ( isEmpty() ) {
            return false;
        }
        BlockKey.getBlockCenter( chunkId,blockId,tmp);
        return tmp.x >= min.x && tmp.x <= max.x && tmp.y >= min.y && tmp.y <= max.y && tmp.z >= min.z && tmp.z <= max.z;
    }
}