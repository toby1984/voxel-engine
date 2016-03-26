package de.codesourcery.voxelengine.asseteditor;

import java.io.IOException;

import org.apache.commons.lang3.StringUtils;

import de.codesourcery.voxelengine.asseteditor.BlockSideDefinition.Rotation;

public class TextureConfig 
{
    // top-left (u,v) coordinates in texture atlas
    public float u0,v0;
    
    // bottom-right (u,v) coordinates in texture atlas 
    public float u1,v1;
    
    // path to input texture that makes up this side of a block
    private String inputTexture;
    
    // whether to flip the input texture before applying rotation
    public boolean flip;

    // rotation of input texture
    public Rotation rotation=Rotation.NONE;
    
    
    public boolean isValid(TextureResolver resolver) 
    {
        try {
            if ( StringUtils.isNotBlank( inputTexture ) && ! resolver.isValidTexture( inputTexture ) ) {
                System.err.println("Missing invalid texture: "+inputTexture);
                return false;
            }
            if ( rotation == null ) {
                System.err.println("Rotation not set ?");
                return false;
            }
            if ( u0 < 0 || u1 < 0 || v0 > 1 || v1 > 1 ) {
                System.err.println("Invalid texture coordinates: "+this);
                return false;
            }
            return true;
        }
        catch (IOException e) 
        {
            e.printStackTrace();
            return false;
        }
    }    
    
    public String getInputTexture() {
        return inputTexture;
    }
    
    public void setInputTexture(String inputTexture) {
        if ( inputTexture == null ) {
            throw new IllegalArgumentException("Refusing to set NULL texture");
        }
        this.inputTexture = inputTexture;
    }
    
    public boolean isTextureAssigned() {
        return StringUtils.isNotBlank( inputTexture );
    }
    
    public boolean isNoTextureAssigned() {
        return ! isTextureAssigned();
    }

    @Override
    public String toString() {
        return "TextureConfig [u0=" + u0 + ", v0=" + v0 + ", u1=" + u1 + ", v1="
                + v1 + ", inputTexture=" + inputTexture + ", flip=" + flip
                + ", rotation=" + rotation + "]";
    }    
}
