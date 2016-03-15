package de.codesourcery.voxelengine.model;

import com.badlogic.gdx.math.Vector3;

/**
 * Holds (x,y,z) block coordinates inside a chunk. 
 *
 * @author tobias.gierke@code-sourcery.de
 */
public final class BlockKey 
{
    public static final int INVALID = -1; // can never occur since we use 3*10 = 30 bits for encoding chunk coordinates so bits 30 and 31 can never be set
    
    private static final Vector3 TMP = new Vector3();
    
    private static final int MASK = 0b11_1111_1111; // 10 bits per component
    
    public int x;
    public int y;
    public int z;
    
    public BlockKey() {
    }
    
    public BlockKey(int blockID) {
        populateFromID(blockID);
    }
    
    public BlockKey cpy() {
        return new BlockKey( x,y,z );
    }
    
    public BlockKey(int x, int y, int z) 
    {
        this.x = x;
        this.y = y;
        this.z = z;
    }
    
    public static BlockKey fromID(int blockId) {
        return new BlockKey(blockId);
    }
    
    public int toID() 
    {
        return toID(x,y,z);
    }
    
    public static int toID(int blockX,int blockY,int blockZ) {
        // 10 bits per (unsigned) coordinate = 30 bits total
        // z | y | x
        return blockZ << 20 | blockY << 10 | blockX;
    }    
    
    public static int getX(int blockID) {
        return blockID & MASK;
    }
    
    public static int getY(int blockID) {
        return (blockID >> 10) & MASK;
    }   
    
    public static int getZ(int blockID) {
        return (blockID >> 20) & MASK;
    }      
    
    public static int toID(BlockKey key) {
        return toID( key.x , key.y , key.z );
    }
    
    public void populateFromID(int blockID) 
    {
        set( getX(blockID) , getY(blockID) , getZ(blockID) );
    }
    
    public BlockKey set(int x,int y,int z) {
        this.x = x;
        this.y = y;
        this.z = z;
        return this;
    }
    
    public BlockKey set(BlockKey other) {
        this.x = other.x;
        this.y = other.y;
        this.z = other.z;
        return this;
    }
    
    /**
     * Sets this block key to the block left of a given block.
     *  
     * @param other
     * @return true if we crossed a chunk boundary, otherwise false
     */
    public boolean leftOf(BlockKey other) {
        this.y = other.y;
        this.z = other.z;
        
        if ( other.x - 1 >= 0 ) {
            this.x = other.x -1;
            return false;
        }
        // different chunk
        this.x = World.CHUNK_SIZE-1;
        return true;
    }
    
    /**
     * Sets this block key to the block right of a given block.
     *  
     * @param other
     * @return true if we crossed a chunk boundary, otherwise false
     */
    public boolean rightOf(BlockKey other) {
        this.y = other.y;
        this.z = other.z;
        
        if ( other.x + 1 < World.CHUNK_SIZE ) {
            this.x = other.x+1;
            return false;
        }
        // different chunk
        this.x = 0;
        return true;
    }    
    
    /**
     * Sets this block key to the block on top of a given block.
     *  
     * @param other
     * @return true if we crossed a chunk boundary, otherwise false
     */
    public boolean topOf(BlockKey other) {
        this.x = other.x;
        this.z = other.z;
        
        if ( other.y + 1 < World.CHUNK_SIZE) {
            this.y = other.y + 1;
            return false;
        }
        // different chunk
        this.y = 0;
        return true;
    }    
    
    /**
     * Sets this block key to the block below a given block.
     *  
     * @param other
     * @return true if we crossed a chunk boundary, otherwise false
     */
    public boolean bottomOf(BlockKey other) {
        this.x = other.x;
        this.z = other.z;
        
        if ( other.y - 1 >= 0 ) {
            this.y = other.y - 1;
            return false;
        }
        // different chunk
        this.y = World.CHUNK_SIZE-1;
        return true;
    }     
    
    /**
     * Sets this block key to the block in front of a given block.
     *  
     * @param other
     * @return true if we crossed a chunk boundary, otherwise false
     */
    public boolean frontOf(BlockKey other) {
        this.x = other.x;
        this.y = other.y;
        
        if ( other.z + 1 < World.CHUNK_SIZE ) {
            this.z = other.z + 1;
            return false;
        }
        // different chunk
        this.z = 0;
        return true;
    }   

    /**
     * Sets this block key to the block behind of a given block.
     *  
     * @param other
     * @return true if we crossed a chunk boundary, otherwise false
     */
    public boolean backOf(BlockKey other) {
        this.x = other.x;
        this.y = other.y;
        
        if ( other.z - 1 >= 0 ) {
            this.z = other.z - 1;
            return false;
        }
        // different chunk
        this.z = World.CHUNK_SIZE-1;
        return true;
    }    
    
    
    @Override
    public String toString() {
        return "Block["+x+","+y+","+z+"]";
    }

    @Override
    public int hashCode() {
        int result = 31 + x;
        result = 31 * result + y;
        return 31 * result + z;
    }
    
    public boolean equals(int x,int y,int z) {
        return this.x == x && this.y == y && this.z == z;
    }    

    @Override
    public boolean equals(Object obj) 
    {
        if ( this == obj ) {
            return true;
        }        
        if ( obj instanceof BlockKey ) 
        {
            final BlockKey that = (BlockKey) obj;
            return this.x == that.x &&
                   this.y == that.y &&
                   this.z == that.z;
        }
        return true;
    }    
    
    public BlockKey frontNeighbour() {
        return new BlockKey( x , y , z+1 );
    }

    public BlockKey backNeighbour() {
        return new BlockKey( x , y , z-1 );
    }    
    
    public BlockKey leftNeighbour() {
        return new BlockKey( x-1 , y , z );
    }    
    
    public BlockKey rightNeighbour() {
        return new BlockKey( x+1 , y , z );
    }    
    
    public BlockKey topNeighbour() {
        return new BlockKey( x , y+1 , z );
    }    
    
    public BlockKey bottomNeighbour() {
        return new BlockKey( x , y-1 , z );
    }
    
    /**
     * Populates a {@link BlockKey} instance using world coordinates.
     * 
     * @param worldCoords
     * @param result Block key to populate
     * 
     * @return <code>result</code> (for chaining)
     */    
    public static BlockKey getBlockKey( long chunkID , Vector3 worldCoords , BlockKey result ) 
    {
        final Vector3 center = ChunkKey.getChunkCenter( chunkID , TMP );
        final int bx = (int) Math.floor( (worldCoords.x - center.x + World.CHUNK_HALF_WIDTH) / World.BLOCK_SIZE );
        final int by = (int) Math.floor( (worldCoords.y - center.y + World.CHUNK_HALF_WIDTH) / World.BLOCK_SIZE );
        final int bz = (int) Math.floor( (worldCoords.z - center.z + World.CHUNK_HALF_WIDTH) / World.BLOCK_SIZE );
        if ( bx < 0 || by < 0 || bz < 0 || bx >= World.CHUNK_SIZE || by >= World.CHUNK_SIZE || bz >= World.CHUNK_SIZE ) {
            throw new RuntimeException("Internal error, world coordinates "+worldCoords+" maps to ("+bx+","+by+","+bz+") in chunk "+ChunkKey.fromID( chunkID )+" @ center "+center);
        }
        result.set( bx , by , bz );
        return result;
    }    
    
    /**
     * Populates a {@link BlockKey} instance using world coordinates.
     * 
     * @param worldCoords
     * @param result Block key to populate
     * 
     * @return <code>result</code> (for chaining)
     */    
    public static int getBlockID( long chunkID , Vector3 worldCoords ) 
    {
        final Vector3 center = ChunkKey.getChunkCenter( chunkID , TMP );
        final int bx = (int) Math.floor( (worldCoords.x - center.x + World.CHUNK_HALF_WIDTH) / World.BLOCK_SIZE );
        final int by = (int) Math.floor( (worldCoords.y - center.y + World.CHUNK_HALF_WIDTH) / World.BLOCK_SIZE );
        final int bz = (int) Math.floor( (worldCoords.z - center.z + World.CHUNK_HALF_WIDTH) / World.BLOCK_SIZE );
        if ( bx < 0 || by < 0 || bz < 0 || bx >= World.CHUNK_SIZE || by >= World.CHUNK_SIZE || bz >= World.CHUNK_SIZE ) {
            throw new RuntimeException("Internal error, world coordinates "+worldCoords+" maps to ("+bx+","+by+","+bz+") in chunk "+ChunkKey.fromID( chunkID )+" @ center "+center);
        }
        return toID(bx,by,bz);
    }    
    
    /**
     * Returns the center coordinates of a given block within this chunk.
     * 
     * @param blockX
     * @param blockY
     * @param blockZ
     * @param result
     * @return
     */
    public static Vector3 getBlockCenter(long chunkID, int blockX,int blockY,int blockZ,Vector3 result) 
    {
        final Vector3 center = ChunkKey.getChunkCenter(chunkID,TMP); 
        result.x = (blockX*World.BLOCK_SIZE ) + center.x - World.CHUNK_HALF_WIDTH;
        result.y = (blockY*World.BLOCK_SIZE ) + center.y - World.CHUNK_HALF_WIDTH;
        result.z = (blockZ*World.BLOCK_SIZE ) + center.z - World.CHUNK_HALF_WIDTH;
        return result;
    }    
    
    /**
     * Returns the center coordinates of a given block within this chunk.
     * 
     * @param blockX
     * @param blockY
     * @param blockZ
     * @param result
     * @return
     */
    public static Vector3 getBlockCenter(long chunkID, int blockID,Vector3 result) 
    {
        final Vector3 center = ChunkKey.getChunkCenter(chunkID,TMP); 
        
        final int blockX = getX(blockID);
        final int blockY = getY(blockID);
        final int blockZ = getZ(blockID);
        result.x = (blockX*World.BLOCK_SIZE ) + center.x - World.CHUNK_HALF_WIDTH + World.HALF_BLOCK_SIZE;
        result.y = (blockY*World.BLOCK_SIZE ) + center.y - World.CHUNK_HALF_WIDTH + World.HALF_BLOCK_SIZE;
        result.z = (blockZ*World.BLOCK_SIZE ) + center.z - World.CHUNK_HALF_WIDTH + World.HALF_BLOCK_SIZE;
        return result;
    }
    
    /**
     * Returns the center coordinates of a given block within this chunk.
     * 
     * @param blockX
     * @param blockY
     * @param blockZ
     * @param result
     * @return
     */
    public static Vector3 getBlockCenter(ChunkKey chunk, int blockX,int blockY,int blockZ,Vector3 result) 
    {
        final Vector3 center = ChunkKey.getChunkCenter(chunk,TMP); 
        
        result.x = (blockX*World.BLOCK_SIZE ) + center.x - World.CHUNK_HALF_WIDTH + World.HALF_BLOCK_SIZE;
        result.y = (blockY*World.BLOCK_SIZE ) + center.y - World.CHUNK_HALF_WIDTH + World.HALF_BLOCK_SIZE;
        result.z = (blockZ*World.BLOCK_SIZE ) + center.z - World.CHUNK_HALF_WIDTH + World.HALF_BLOCK_SIZE;
        return result;
    }     
}