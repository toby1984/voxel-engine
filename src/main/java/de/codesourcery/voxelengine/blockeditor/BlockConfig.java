package de.codesourcery.voxelengine.blockeditor;

import java.util.HashMap;
import java.util.Map;

public class BlockConfig 
{
    // size of the (square) texture atlas texture in pixels
    public int textureAtlasSize = 1024;
    
    // size of a (square) block texture in pixels
    public int blockTextureSize = 64;
    
    public final Map<Integer,BlockDefinition> blocks = new HashMap<>();
    
    public int nextAvailableBlockTypeId() 
    {
        int i = 0;
        for ( ; blocks.containsKey( Integer.valueOf( i ) ) ; i++ );
        return i;
    }

    public void add(BlockDefinition def) 
    {
        final BlockDefinition existing = blocks.get( def.blockType );
        if ( existing != null && existing != def ) {
            throw new IllegalArgumentException("Block type already in use: "+def);
        }
        blocks.put( def.blockType , def );
    }

    @Override
    public String toString() {
        return "BlockConfig [textureAtlasSize=" + textureAtlasSize
                + ", blockTextureSize=" + blockTextureSize + "]";
    }
}
