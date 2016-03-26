package de.codesourcery.voxelengine.asseteditor;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.imageio.ImageIO;

import org.apache.commons.lang3.StringUtils;

public class AssetConfigTextureResolver implements TextureResolver 
{
    public final AssetConfig config;
    
    public AssetConfigTextureResolver(AssetConfig config) {
        this.config = config;
    }

    @Override
    public BufferedImage resolve(String path) throws IOException 
    {
        try ( InputStream in = new FileInputStream( resolvePath( path ).toFile() )  ) 
        {
            return ImageIO.read( in );
        }
    }
    
    private Path resolvePath(String path) throws IOException 
    {
        if ( StringUtils.isBlank( config.baseDirectory ) ) {
            throw new IOException("No base directory set");
        }
        if ( StringUtils.isBlank( path ) ) 
        {
            throw new IOException("Empty path ?");
        }
        if ( path.startsWith("/" ) ) {
            return Paths.get( path );
        }
       final Path parent = Paths.get( config.baseDirectory );
       return parent.resolve( path );
    }

    @Override
    public String selectTextureFile() 
    {
        if ( StringUtils.isBlank( config.baseDirectory ) ) {
            throw new RuntimeException("No base directory set");
        }
        return TextureResolver.selectTextureFile( Paths.get( config.baseDirectory ) );
    }

    @Override
    public String toRelativePath(String input) 
    {
        if ( StringUtils.isBlank( input ) || StringUtils.isBlank( config.baseDirectory ) ) 
        {
            return input;
        }
        if ( ! input.startsWith( "/" ) ) {
            return input;
        }
        if ( new File( input ).getParentFile().equals( new File( config.baseDirectory ) ) ) 
        {
            final String name = new File(input).getAbsolutePath();
            final String baseName = new File( config.baseDirectory ).getAbsolutePath();
            return name.substring( baseName.length()+1 );
        }
        return input;
    }

    @Override
    public boolean isValidTexture(String path) throws IOException 
    {
        return Files.exists( resolvePath( path ) );
    }
}