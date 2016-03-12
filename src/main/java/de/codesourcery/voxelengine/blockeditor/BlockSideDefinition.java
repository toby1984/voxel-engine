package de.codesourcery.voxelengine.blockeditor;

import java.io.IOException;

import org.apache.commons.lang3.StringUtils;

import de.codesourcery.voxelengine.engine.BlockSide;

public class BlockSideDefinition 
{
    public static enum Rotation 
    {
        NONE(0),
        CW_90(90),
        CCW_90(270),
        ONE_HUNDRED_EIGHTY(180);
        
        public final int degrees;
        
        private Rotation(int degrees) {
            this.degrees = degrees;
        }
    }
    
    // side of this block
    public BlockSide side;
    
    // top-left (u,v) coordinates in texture atlas
    public float u0,v0;
    
    // bottom-right (u,v) coordinates in texture atlas 
    public float u1,v1;
    
    // path to input texture that makes up this side of a block
    public String inputTexture;
    
    // whether to flip the input texture before applying rotation
    public boolean flip;

    // rotation of input texture
    public Rotation rotation=Rotation.NONE;
    
    public boolean editable = true;
    
    public BlockSideDefinition(BlockSide side) {
        this.side = side;
    }
    
    public boolean isValid(BlockDefinition parent, TextureResolver resolver) 
    {
        try {
            if ( side == null ) {
                System.err.println("Side not set");
                return false;
            }
            if ( StringUtils.isNotBlank( inputTexture ) && ! resolver.isValidTexture( inputTexture ) ) {
                System.err.println("Missing invalid texture: "+inputTexture);
                return false;
            }
            if ( rotation == null ) {
                System.err.println("Rotation not set ?");
                return false;
            }
            if ( u0 < 0 || u1 < 0 || v0 > 1 || v1 > 1 ) {
                System.err.println("Invalid texture coordinates");
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

    @Override
    public String toString() {
        return "BlockSideDefinition [side=" + side + ", u0=" + u0 + ", v0=" + v0
                + ", u1=" + u1 + ", v1=" + v1 + ", inputTexture=" + inputTexture
                + ", flip=" + flip + ", rotation=" + rotation + "]";
    }
    
    public boolean isTextureAssigned() {
        return StringUtils.isNotBlank( this.inputTexture );
    }
}
