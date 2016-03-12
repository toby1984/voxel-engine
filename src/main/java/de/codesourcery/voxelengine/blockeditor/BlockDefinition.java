package de.codesourcery.voxelengine.blockeditor;

import java.util.Arrays;

import org.apache.commons.lang3.StringUtils;

import de.codesourcery.voxelengine.engine.BlockSide;

public class BlockDefinition 
{
    public int blockType;
    public String name;
    
    public boolean emitsLight;
    public byte lightLevel;
    
    public boolean opaque;
    public boolean editable = true;
    
    public final BlockSideDefinition[] sides;
    
    public BlockDefinition() 
    {
        sides = new BlockSideDefinition[6];
        for ( BlockSide side : BlockSide.values() ) {
            sides[ side.ordinal() ] = new BlockSideDefinition( side );
        }
    }
    
    public boolean isValid(TextureResolver resolver) 
    {
        if ( StringUtils.isNotBlank( name ) && lightLevel >= 0 ) 
        {
            for ( BlockSideDefinition def : sides ) {
                if ( ! def.isValid( this , resolver ) ) 
                {
                    System.err.println("Block definition "+this+" has invalid side "+def);
                    return false;
                }
            }
            return true;
        }
        System.err.println("Block definition has invalid fields: "+this);
        return false;
    }
    
    public BlockDefinition(int blockType,String name) {
        this();
        this.blockType = blockType;
        this.name = name;
    }
    
    public void setEditable(boolean yesNo) {
        this.editable = yesNo;
        Arrays.stream(sides).forEach( b -> b.editable = yesNo );
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
