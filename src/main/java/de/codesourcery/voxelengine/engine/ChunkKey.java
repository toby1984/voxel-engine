package de.codesourcery.voxelengine.engine;

public final class ChunkKey 
{
    private static final long MASK        =               0b1_1111_1111_1111_1111_1111;
    private static final int NEGATIVE_BITS = 0b1111_1111_1110_0000_0000_0000_0000_0000;
    
    public int x;
    public int y;
    public int z;
    
    public ChunkKey(int x, int y, int z) 
    {
        this.x = x;
        this.y = y;
        this.z = z;
    }
    
    public long toID() {
        return toID(x,y,z);
    }
    
    public static long toID(int x,int y,int z) 
    {
        // pack coordinates into long value in Z|Y|X order
        return (z & MASK ) << 42 | ( y & MASK ) << 21 | ( x & MASK ); 
    }
    
    public static ChunkKey fromID(long value) {
        return new ChunkKey( getX(value), getY(value),getZ(value) );
    }
    
    public static void fromID(long value,ChunkKey toPopulate) 
    {
        toPopulate.x = getX(value);
        toPopulate.y = getY(value);
        toPopulate.z = getZ(value);
    }    
    
    public static int getX(long chunkID) 
    {
        final int value = (int) (chunkID & MASK);
        if ( (value & 1<<20) != 0 ) { // negative, perform sign extension
            return value | NEGATIVE_BITS;
        }
        return value;
    }
    
    public static int getY(long chunkID) {
        final int value = (int) ((chunkID >> 21) & MASK);
        if ( (value & 1<<20) != 0 ) { // negative, perform sign extension
            return value | NEGATIVE_BITS;
        }
        return value;
    }
    
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
    
    public float dst(int otherx,int othery,int otherz) 
    {
        final int dx = otherx - this.x;
        final int dy = othery - this.y;
        final int dz = otherz - this.z;
        return (float) Math.sqrt( dx*dx + dy*dy + dz*dz );
    }  
    
    public float dst2(int otherx,int othery,int otherz) 
    {
        final int dx = otherx - this.x;
        final int dy = othery - this.y;
        final int dz = otherz - this.z;
        return dx*dx + dy*dy + dz*dz;
    }      
    
    public float dst(ChunkKey other) 
    {
        final int dx = other.x - this.x;
        final int dy = other.y - this.y;
        final int dz = other.z - this.z;
        return (float) Math.sqrt( dx*dx + dy*dy + dz*dz );
    }
    
    public float dst2(ChunkKey other) {
        final int dx = other.x - this.x;
        final int dy = other.y - this.y;
        final int dz = other.z - this.z;
        return dx*dx + dy*dy + dz*dz;
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

    public long frontNeighbour() {
        return ChunkKey.toID( x , y , z+1 );
    }

    public long backNeighbour() {
        return ChunkKey.toID( x , y , z-1 );
    }    
    
    public long leftNeighbour() {
        return ChunkKey.toID( x-1 , y , z );
    }    
    
    public long rightNeighbour() {
        return ChunkKey.toID( x+1 , y , z );
    }    
    
    public long topNeighbour() {
        return ChunkKey.toID( x , y+1 , z );
    }    
    
    public long bottomNeighbour() {
        return ChunkKey.toID( x , y-1 , z );
    }     
}