package de.codesourcery.voxelengine.blockeditor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

public class BlockConfig 
{
    // size of the (square) texture atlas texture in pixels
    public int textureAtlasSize = 1024;

    // size of a (square) block texture in pixels
    public int blockTextureSize = 64;

    public final List<BlockDefinition> blocks = new ArrayList<>();

    public String baseDirectory;
    
    @FunctionalInterface
    public interface BlockVisitor 
    {
        public void visit(BlockDefinition bd,boolean isFirst,boolean isLast);
    }
    
    public BlockConfig() 
    {
        final BlockDefinition bd = new BlockDefinition();
        bd.name="Air";
        bd.opaque = false;
        bd.setEditable(false);
        add( bd );
    }
    
    public void visitByAscendingTypeId(BlockVisitor def) 
    {
        final List<BlockDefinition> sorted = new ArrayList<>( this.blocks );
        final Comparator<BlockDefinition> order = (a,b) -> Integer.compare( a.blockType , b.blockType );
        Collections.sort( sorted , order );
        for ( int i = 0 , len = sorted.size() ; i < len ; i++ ) 
        {
            def.visit( sorted.get(i) , i == 0 , (i+1) >= len );
        }
    }
    
    public boolean isValid(TextureResolver resolver) 
    {
        if ( textureAtlasSize > 0 && blockTextureSize > 0 && StringUtils.isNotBlank( baseDirectory ) ) 
        {
            for ( BlockDefinition bl : blocks ) 
            {
                if ( ! bl.isValid( resolver ) ) 
                {
                    System.err.println("=== Block config has invalid blocks: "+this);
                    return false;
                }
            }
            
            final Set<Integer> ids = blocks.stream().map( bd -> bd.blockType ).collect( Collectors.toSet() );
            final OptionalInt minValue = ids.stream().mapToInt( bd -> bd.intValue() ).min();
            final OptionalInt maxValue = ids.stream().mapToInt( bd -> bd.intValue() ).max();
            
            if ( minValue.isPresent() ) 
            {
                if ( minValue.getAsInt() != 0 ) 
                {
                    System.err.println("=== Lowest block type ID must be 0");
                    return false;
                }
                for ( int i = minValue.getAsInt() , max = maxValue.getAsInt() ; i <= max ; i++ ) {
                    if ( ! ids.contains( Integer.valueOf( i ) ) ) {
                        System.err.println("=== Block type IDs must not have gaps: "+this);
                        return false;
                    }
                }
            }
            return true;
        }
        System.err.println("=== BlockConfig has invalid fields: "+this);
        return false;
    }
    
    public int nextAvailableBlockTypeId() 
    {
        int i = 0;
outer:
        while ( true ) 
        {
            for ( BlockDefinition def : blocks ) {
                if ( def.blockType == i ) 
                {
                    i++;
                    continue outer;
                }
            }
            return i;
        }
    }

    public void add(BlockDefinition def) 
    {
        this.blocks.add( def );
    }

    @Override
    public String toString() {
        return "BlockConfig [textureAtlasSize=" + textureAtlasSize
                + ", blockTextureSize=" + blockTextureSize + "]";
    }
}