package de.codesourcery.voxelengine.model;

public class Item 
{
    public final int type;
    public final String name;

    private boolean canDestroyBlock;
    private boolean canCreateBlock;
    
    private int createdBlockType;
    
    public Item(int type,String name) 
    {
        this.type = type;
        this.name = name;
    }
    
    public Item setCreatedBlockType(int createdBlockType) 
    {
        this.createdBlockType = createdBlockType;
        canCreateBlock = true;
        return this;
    }
    
    public Item setCanDestroyBlock(boolean canDestroyBlock) 
    {
        if ( canCreateBlock() ) {
            throw new IllegalStateException("An item can either create or destroy a block but not both");
        }
        this.canDestroyBlock = canDestroyBlock;
        return this;
    }
    
    public boolean canDestroyBlock() {
        return canDestroyBlock;
    }
    
    public boolean canCreateBlock() {
        return canCreateBlock;
    }
    
    /**
     * Create block at a given location.
     *  
     * @param chunk
     * @param blockX
     * @param blockY
     * @param blockZ
     * 
     * @return <code>true</code> if the chunk has been changed, otherwise <code>false</code>
     */
    public boolean createBlock(Chunk chunk,int blockX,int blockY,int blockZ) 
    {
        if ( ! canCreateBlock() ) {
            throw new IllegalStateException("createBlock() called on item that cannot create one ?");
        }
        chunk.setBlockType( blockX, blockY , blockZ , createdBlockType );
        return true;
    }
    
    @Override
    public String toString() 
    {
        return "Item[ type="+type+", name="+name+"]";
    } 
}