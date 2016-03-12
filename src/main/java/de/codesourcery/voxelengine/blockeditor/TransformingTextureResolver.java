package de.codesourcery.voxelengine.blockeditor;

import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.IOException;

import org.apache.commons.lang3.Validate;

import de.codesourcery.voxelengine.blockeditor.BlockSideDefinition.Rotation;

public class TransformingTextureResolver implements TextureResolver 
{
    private final TextureResolver delegate;
    public boolean flip;
    public BlockSideDefinition.Rotation rotation = Rotation.NONE;
    
    public TransformingTextureResolver(TextureResolver delegate) {
        Validate.notNull(delegate, "delegate must not be NULL");
        this.delegate = delegate;
    }
    
    @Override
    public BufferedImage resolve(String path) throws IOException 
    {
        BufferedImage image = delegate.resolve( path );
        if ( flip ) 
        {
            final AffineTransform tx = AffineTransform.getScaleInstance(-1, 1);
            tx.translate(-image.getWidth(null) , 0 );
            final AffineTransformOp op = new AffineTransformOp(tx, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
            image = op.filter(image, null);
        }
        if ( rotation != Rotation.NONE ) 
        {
            final AffineTransform tx = new AffineTransform(); 
            tx.setToIdentity();
            tx.rotate( Math.toRadians( rotation.degrees ) , image.getWidth(null)/2f , image.getHeight(null)/2f );
            final AffineTransformOp op = new AffineTransformOp(tx, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
            image = op.filter(image, null);
        }
        return image;
    }
    
    public void setTransform(BlockSideDefinition def) 
    {
        this.flip = def.flip;
        this.rotation = def.rotation;
    }

    @Override
    public String selectTextureFile() {
        return delegate.selectTextureFile();
    }

    @Override
    public String toRelativePath(String input) {
        return delegate.toRelativePath(input);
    }

    @Override
    public boolean isValidTexture(String path) throws IOException {
        return delegate.isValidTexture( path );
    }
}