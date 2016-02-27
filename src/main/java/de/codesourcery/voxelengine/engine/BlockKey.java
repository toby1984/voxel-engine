package de.codesourcery.voxelengine.engine;

public final class BlockKey {

    public int x;
    public int y;
    public int z;
    
    public BlockKey() {
    }
    
    public BlockKey(int x, int y, int z) 
    {
        this.x = x;
        this.y = y;
        this.z = z;
    }
    
    public BlockKey set(int x,int y,int z) {
        this.x = x;
        this.y = y;
        this.z = z;
        return this;
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
}