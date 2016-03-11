package de.codesourcery.voxelengine.blockeditor;

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
    
    public BlockSideDefinition(BlockSide side) {
        this.side = side;
    }

    @Override
    public String toString() {
        return "BlockSideDefinition [side=" + side + ", u0=" + u0 + ", v0=" + v0
                + ", u1=" + u1 + ", v1=" + v1 + ", inputTexture=" + inputTexture
                + ", flip=" + flip + ", rotation=" + rotation + "]";
    }
}
