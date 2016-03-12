package de.codesourcery.voxelengine.blockeditor;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;

import javax.imageio.ImageIO;

public class TextureAtlasBuilder 
{
    // size of border around each texture
    public int textureBorderSize = 1;
    
    public void build(BlockConfig config,TextureResolver resolver,OutputStream out) throws IOException 
    {
        ImageIO.write( build( config , resolver , false ) , "png" , out );
    }
    
    public BufferedImage build(BlockConfig config,TextureResolver resolver,boolean onlyAssignTextureCoordinates) throws IOException
    {
            // TODO: BufferedImage_TYPE_INT_ARGB assumes pre-multiplied alpha...
           final BufferedImage atlas = onlyAssignTextureCoordinates ? null : new BufferedImage( config.textureAtlasSize , config.textureAtlasSize , BufferedImage.TYPE_INT_ARGB );
           
           final int atlasW = config.textureAtlasSize;
           final int atlasH = config.textureAtlasSize;
           
           final Graphics2D graphics = onlyAssignTextureCoordinates ? null : atlas.createGraphics();
           
           int x = textureBorderSize;
           int y = textureBorderSize;
           
           int currentRowHeight = -1;
           for ( BlockDefinition blockDef : config.blocks ) 
           {
               for ( BlockSideDefinition sideDef : blockDef.sides ) 
               {
                   if ( ! sideDef.isTextureAssigned() ) {
                       continue;
                   }
                   final BufferedImage texture = resolver.resolve( sideDef.inputTexture );
                   final int texW = texture.getWidth();
                   final int texH = texture.getHeight();
                   
                   currentRowHeight = Math.max( currentRowHeight , texH+textureBorderSize );
                   if ( (x+texW+textureBorderSize) > atlasW ) 
                   {
                       x = textureBorderSize;
                       y += currentRowHeight;
                       currentRowHeight = -1;
                       if ( (y+texH+textureBorderSize) > atlasH) {
                           throw new IOException("Atlas size exceeded - texture "+sideDef+" does not fit");
                       }
                       if ( (x+texW+textureBorderSize) > atlasW) {
                           throw new IOException("Atlas size exceeded - texture "+sideDef+" does not fit");
                       }
                   }
                   
                   if ( ! onlyAssignTextureCoordinates ) {
                       graphics.drawImage( texture , x , y , config.blockTextureSize , config.blockTextureSize , null );
                   }
                   
                   final float u0 = x/(float) atlasW; 
                   final float v0 = (atlasH-y) /(float) atlasH; // (u,v) origin is bottom left corner
                   
                   final float u1 = (x+config.blockTextureSize) / (float) atlasW; 
                   final float v1 = (atlasH-y+config.blockTextureSize) / (float) atlasH; // (u,v) origin is bottom left corner                   
                   
                   sideDef.u0 = u0;
                   sideDef.v0 = v0;
                   
                   sideDef.u1 = u1;
                   sideDef.v1 = v1;
                   
                   x+= config.blockTextureSize+textureBorderSize;
               }
           }
           return atlas;
    }
}