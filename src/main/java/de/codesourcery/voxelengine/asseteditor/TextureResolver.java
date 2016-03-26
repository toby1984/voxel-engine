package de.codesourcery.voxelengine.asseteditor;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileFilter;

import org.apache.commons.lang3.StringUtils;

public interface TextureResolver 
{
    public BufferedImage resolve(String path) throws IOException;
    
    public String selectTextureFile();
    
    public String toRelativePath(String input);
    
    public boolean isValidTexture(String path) throws IOException;
    
    public static String selectTextureFile(Path baseDirectory) 
    {
        final JFileChooser chooser;
        if ( baseDirectory == null ) {
            baseDirectory = Paths.get(".");
        } 
        chooser = new JFileChooser( baseDirectory.toFile() );
        chooser.setFileSelectionMode( JFileChooser.FILES_ONLY );
        chooser.setFileFilter( new FileFilter() 
        {
            private final String[] supportedExtensions = { ".png" , ".jpg" };
            
            @Override
            public boolean accept(File f) 
            {
                if ( f.isDirectory() ) {
                    return true;
                }
                if ( f.isFile() ) {
                    return Arrays.stream( supportedExtensions ).anyMatch( s -> f.getName().toLowerCase().endsWith( s ) );
                }
                return false;
            }

            @Override
            public String getDescription() 
            {
                return StringUtils.join( supportedExtensions ,"," );
            }
        } );
        int result = chooser.showOpenDialog( null );
        if ( result == JFileChooser.APPROVE_OPTION ) 
        {
            final File file = chooser.getSelectedFile();
            return file.getAbsolutePath();
        }
        return null;
    }
}