package de.codesourcery.voxelengine.blockeditor;

import java.util.Arrays;

import de.codesourcery.voxelengine.engine.BlockSide;

public class BlockDefinition 
{
    public int blockType;
    public String name;
    
    public boolean emitsLight;
    public byte lightLevel;
    
    public boolean opaque;
    
    public final BlockSideDefinition[] sides;
    
    public BlockDefinition() 
    {
        sides = new BlockSideDefinition[6];
        for ( BlockSide side : BlockSide.values() ) {
            sides[ side.ordinal() ] = new BlockSideDefinition( side );
        }
    }
    
    public BlockDefinition(int blockType,String name) {
        this();
        this.blockType = blockType;
        this.name = name;
    }
    
    public BlockSideDefinition get(BlockSide side) {
        return sides[ side.ordinal() ];
    }

    @Override
    public String toString() {
        return "BlockDefinition [blockType=" + blockType + ", name=" + name
                + ", emitsLight=" + emitsLight + ", lightLevel=" + lightLevel
                + ", opaque=" + opaque + ", sides=" + Arrays.toString(sides)
                + "]";
    }
}
