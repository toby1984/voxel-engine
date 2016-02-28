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

    @Override
    public boolean equals(Object obj) 
    {
        if ( this == obj ) {
            return true;
        }        
        if ( obj instanceof ChunkKey ) 
        {
            final ChunkKey that = (ChunkKey) obj;
            return this.x == that.x &&
                   this.y == that.y &&
                   this.z == that.z;
        }
        return true;
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