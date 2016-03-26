package de.codesourcery.voxelengine.asseteditor;

import java.io.IOException;

import org.apache.commons.lang3.StringUtils;

public class ItemDefinition 
{
    public int itemId;
    public String name;
    
    public final TextureConfig texture = new TextureConfig();
    
    public boolean canCreateBlock;
    public boolean canDestroyBlock;
    
    public BlockDefinition createdBlock;
    
    public boolean isValid(TextureResolver resolver) throws IOException 
    {
        if ( itemId < 0 ) {
            return false;
        }
        if ( StringUtils.isBlank( name ) ) {
            return false;
        }
        if ( canCreateBlock && createdBlock == null ) {
            return false;
        }
        return texture.isTextureAssigned() && texture.isValid( resolver );
    }
}