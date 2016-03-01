package de.codesourcery.voxelengine.engine;

public final class ChunkKey {

    public final int x;
    public final int y;
    public final int z;
    
    public ChunkKey(int x, int y, int z) 
    {
        this.x = x;
        this.y = y;
        this.z = z;
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
    
    @Override
    public int hashCode() 
    {
        int result = 31 + x;
        result = 31 * result + y;
        return 31 * result + z;
    }

    public ChunkKey frontNeighbour() {
        return new ChunkKey( x , y , z+1 );
    }

    public ChunkKey backNeighbour() {
        return new ChunkKey( x , y , z-1 );
    }    
    
    public ChunkKey leftNeighbour() {
        return new ChunkKey( x-1 , y , z );
    }    
    
    public ChunkKey rightNeighbour() {
        return new ChunkKey( x+1 , y , z );
    }    
    
    public ChunkKey topNeighbour() {
        return new ChunkKey( x , y+1 , z );
    }    
    
    public ChunkKey bottomNeighbour() {
        return new ChunkKey( x , y-1 , z );
    }     
}