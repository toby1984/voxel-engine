package de.codesourcery.voxelengine.asseteditor;

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

    public final TextureConfig texture = new TextureConfig();

    private boolean isAutoGenerated;

    public BlockSideDefinition(BlockSide side) {
        this.side = side;
    }

    public void markAutoGenerated() {
        this.isAutoGenerated = true;
    }

    public boolean isAutoGenerated() {
        return isAutoGenerated;
    }

    public boolean isValid(BlockDefinition parent, TextureResolver resolver) 
    {
        if ( side == null ) {
            System.err.println("Side not set");
            return false;
        }

        if ( ! isAutoGenerated() && texture.isNoTextureAssigned() ) {
            System.err.println("Side lacks texture: "+this);
            return false;
        }
        if ( ! texture.isValid( resolver ) ) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "BlockSideDefinition [side=" + side + ", auto_generated="+isAutoGenerated+","+texture;
    }

    public boolean isTextureAssigned() {
        return texture.isTextureAssigned();
    }

    public String getInputTexture() {
        return texture.getInputTexture();
    }

    public void setInputTexture(String inputTexture) {
        this.texture.setInputTexture( inputTexture );
    }
}
