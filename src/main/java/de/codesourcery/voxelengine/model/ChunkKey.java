package de.codesourcery.voxelengine.model;

import com.badlogic.gdx.math.Vector3;

/**
 * Holds the (x,y,z) integer coordinates of a chunk (immutable value object suitable as map key).
 *
 * <p>The origin chunk (0,0,0) is centered around the world coordinates (0,0,0).</p>
 * 
 * <p>Since chunk coordinates are internally encoded into a 64-bit long value, there
 * are only 21 bits available for storing each axis component. Since I use signed values
 * the actual range of chunk coordinates has a minimum value of -2^21 (-2097152) and a maximum value of 2^21-1 (2097151).</p>
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public final class ChunkKey 
{
    /**
     * Chunk ID that can never occur , can be used to denote as an uninitialized chunk ID etc.
     */
    public static final long INVALID = -1; // can never occur since we use 3*21 = 63 bits for encoding chunk coordinates so bit 63 can never be set
    
    private static final long MASK        =               0b1_1111_1111_1111_1111_1111;
    private static final int NEGATIVE_BITS = 0b1111_1111_1110_0000_0000_0000_0000_0000;

    public final int x;
    public final int y;
    public final int z;

    /**
     * Create new instance.
     * 
     * @param x
     * @param y
     * @param z
     */
    public ChunkKey(int x, int y, int z) 
    {
        this.x = x;
        this.y = y;
        this.z = z;
    }
    
    /**
     * Returns the coordinates of this chunk encoded as a 64-bit value.
     * 
     * <p>Since chunk coordinates are internally encoded into a 64-bit long value, there
     * are only 21 bits available for storing each axis component. Since I use signed values
     * the actual range of chunk coordinates has a minimum value of -2^21 (-2097152) and a maximum value of 2^21-1 (2097151).</p>     
     * @return
     */
    public long toID() {
        return toID(x,y,z);
    }
    
    /**
     * Returns the coordinates of this chunk encoded as a 64-bit value.
     * 
     * <p>Since chunk coordinates are internally encoded into a 64-bit long value, there
     * are only 21 bits available for storing each axis component. Since I use signed values
     * the actual range of chunk coordinates has a minimum value of -2^21 (-2097152) and a maximum value of 2^21-1 (2097151).</p>
     * 
     * @param x
     * @param y
     * @param z
     * @return
     */    
    public static long toID(int x,int y,int z) 
    {
        // pack coordinates into long value in Z|Y|X order
        return (z & MASK ) << 42 | ( y & MASK ) << 21 | ( x & MASK ); 
    }

    /**
     * Creates a <code>ChunkKey</code> from its encoded 64-bit representation.
     * 
     * <p>Since chunk coordinates are internally encoded into a 64-bit long value, there
     * are only 21 bits available for storing each axis component. Since I use signed values
     * the actual range of chunk coordinates has a minimum value of -2^21 (-2097152) and a maximum value of 2^21-1 (2097151).</p>     
     * @param value
     * @return
     */
    public static ChunkKey fromID(long value) {
        return new ChunkKey( getX(value), getY(value),getZ(value) );
    }

    /**
     * Returns a chunk's x-coordinate from encoded 64-bit representation.
     * 
     * <p>Since chunk coordinates are internally encoded into a 64-bit long value, there
     * are only 21 bits available for storing each axis component. Since I use signed values
     * the actual range of chunk coordinates has a minimum value of -2^21 (-2097152) and a maximum value of 2^21-1 (2097151).</p>    
     *      
     * @param chunkID
     * @return
     */
    public static int getX(long chunkID) 
    {
        final int value = (int) (chunkID & MASK);
        if ( (value & 1<<20) != 0 ) { // negative, perform sign extension
            return value | NEGATIVE_BITS;
        }
        return value;
    }

    /**
     * Returns a chunk's y-coordinate from encoded 64-bit representation.
     * 
     * <p>Since chunk coordinates are internally encoded into a 64-bit long value, there
     * are only 21 bits available for storing each axis component. Since I use signed values
     * the actual range of chunk coordinates has a minimum value of -2^21 (-2097152) and a maximum value of 2^21-1 (2097151).</p>    
     *      
     * @param chunkID
     * @return
     */    
    public static int getY(long chunkID) {
        final int value = (int) ((chunkID >> 21) & MASK);
        if ( (value & 1<<20) != 0 ) { // negative, perform sign extension
            return value | NEGATIVE_BITS;
        }
        return value;
    }

    /**
     * Returns a chunk's z-coordinate from encoded 64-bit representation.
     * 
     * <p>Since chunk coordinates are internally encoded into a 64-bit long value, there
     * are only 21 bits available for storing each axis component. Since I use signed values
     * the actual range of chunk coordinates has a minimum value of -2^21 (-2097152) and a maximum value of 2^21-1 (2097151).</p>    
     *      
     * @param chunkID
     * @return
     */    
    public static int getZ(long chunkID) {
        final int value = (int) ((chunkID >> 42) & MASK);
        if ( (value & 1<<20) != 0 ) { // negative, perform sign extension
            return value | NEGATIVE_BITS;
        }
        return value;        
    }    

    @Override
    public String toString() {
        return "Chunk["+x+","+y+","+z+"]";
    }

    /**
     * Returns the distance in chunks between this coordinates and another chunk.
     * 
     * @param otherx
     * @param othery
     * @param otherz
     * @return <code>sqrt( (otherx-x)*(otherx-x)+(othery-y)*(othery-y)+(otherz-z)*(otherz-z)</code>
     */    
    public float dst(int otherx,int othery,int otherz) 
    {
        return (float) Math.sqrt( dst2(otherx,othery,otherz) );
    }  

    /**
     * Returns the squared distance in chunks between this coordinates and another chunk.
     * 
     * @param otherx
     * @param othery
     * @param otherz
     * @return <code>(otherx-x)*(otherx-x)+(othery-y)*(othery-y)+(otherz-z)*(otherz-z)</code>
     */      
    public float dst2(int otherx,int othery,int otherz) 
    {
        final int dx = otherx - this.x;
        final int dy = othery - this.y;
        final int dz = otherz - this.z;
        return dx*dx + dy*dy + dz*dz;
    }      

    /**
     * Returns the distance in chunks between this coordinates and another chunk.
     * 
     * @param otherx
     * @param othery
     * @param otherz
     * @return <code>sqrt( (other.x-x)*(other.x-x)+(other.y-y)*(other.y-y)+(other.z-z)*(other.z-z)</code>
     */     
    public float dst(ChunkKey other) 
    {
        return dst(other.x,other.y,other.z);
    }

    public float dst2(ChunkKey other) {
        return dst2( other.x , other.y , other.z );
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
        if ( obj instanceof ChunkKey ) 
        {
            final ChunkKey that = (ChunkKey) obj;
            return this.x == that.x && this.y == that.y && this.z == that.z;
        }
        return false;
    }    

    public static int hashCode(int x,int y,int z) 
    {
        int result = 31 + x;
        result = 31 * result + y;
        return 31 * result + z;
    }

    @Override
    public int hashCode() 
    {
        return hashCode(x,y,z);
    }

    /**
     * Returns the chunk ID of the chunk that is "in front" of this one.
     * 
     * <p>This method uses a right-handed coordinate system with
     * front/back assuming that a viewer is looking along the -z axis</p>
     * 
     * @return 
     * 
     * @see #toID()
     * @see #toID(int, int, int)
     */
    public long frontNeighbour() {
        return ChunkKey.toID( x , y , z+1 );
    }

    /**
     * Returns the chunk ID of the chunk that is "behind" this one.
     * 
     * <p>This method uses a right-handed coordinate system with
     * front/back assuming that a viewer is looking along the -z axis</p>
     * 
     * @return 
     * 
     * @see #toID()
     * @see #toID(int, int, int)
     */    
    public long backNeighbour() {
        return ChunkKey.toID( x , y , z-1 );
    }    

    /**
     * Returns the chunk ID of the chunk that is to the left of this one.
     * 
     * <p>This method uses a right-handed coordinate system with
     * front/back assuming that a viewer is looking along the -z axis</p>
     * 
     * @return 
     * 
     * @see #toID()
     * @see #toID(int, int, int)
     */     
    public long leftNeighbour() {
        return ChunkKey.toID( x-1 , y , z );
    }    

    /**
     * Returns the chunk ID of the chunk that is to the right of this one.
     * 
     * <p>This method uses a right-handed coordinate system with
     * front/back assuming that a viewer is looking along the -z axis</p>
     * 
     * @return 
     * 
     * @see #toID()
     * @see #toID(int, int, int)
     */     
    public long rightNeighbour() {
        return ChunkKey.toID( x+1 , y , z );
    }    

    /**
     * Returns the chunk ID of the chunk that is right above this one.
     * 
     * <p>This method uses a right-handed coordinate system with
     * front/back assuming that a viewer is looking along the -z axis</p>
     * 
     * @return 
     * 
     * @see #toID()
     * @see #toID(int, int, int)
     */     
    public long topNeighbour() {
        return ChunkKey.toID( x , y+1 , z );
    }    
    
    /**
     * Returns the chunk ID of the chunk that is right below this one.
     * 
     * <p>This method uses a right-handed coordinate system with
     * front/back assuming that a viewer is looking along the -z axis</p>
     * 
     * @return 
     * 
     * @see #toID()
     * @see #toID(int, int, int)
     */     
    public long bottomNeighbour() {
        return ChunkKey.toID( x , y-1 , z );
    }
    
    /**
     * Returns the 64-bit chunk IDfor a given point in world coordinates.
     * 
     * @param worldCoords
     * @return
     * @see #getBlockID(long, Vector3)
     */
    public static long getChunkID(Vector3 worldCoords) 
    {
        final int chunkX = (int) Math.floor( (worldCoords.x + World.CHUNK_HALF_WIDTH) / World.CHUNK_WIDTH);
        final int chunkY = (int) Math.floor( (worldCoords.y + World.CHUNK_HALF_WIDTH) / World.CHUNK_WIDTH);
        final int chunkZ = (int) Math.floor( (worldCoords.z + World.CHUNK_HALF_WIDTH) / World.CHUNK_WIDTH);
        return ChunkKey.toID( chunkX , chunkY , chunkZ );
    }
    
    /**
     * Returns the center coordinates of a given chunk.
     * 
     * @param key
     * @param result
     * @return
     */
    public static Vector3 getChunkCenter(ChunkKey key,Vector3 result) 
    {
        return result.set( key.x* World.CHUNK_WIDTH, key.y* World.CHUNK_WIDTH , key.z* World.CHUNK_WIDTH );
    }    
    
    /**
     * Returns the center coordinates of a given chunk.
     * 
     * @param key
     * @param result
     * @return
     */
    public static Vector3 getChunkCenter(long chunkID,Vector3 result) 
    {
        final int keyX = getX( chunkID );
        final int keyY = getY( chunkID );
        final int keyZ = getZ( chunkID );
        return result.set( keyX* World.CHUNK_WIDTH, keyY* World.CHUNK_WIDTH , keyZ* World.CHUNK_WIDTH );
    }     
}