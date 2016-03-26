package de.codesourcery.voxelengine.asseteditor;

import java.io.File;

import javax.swing.JFileChooser;

public class EditorUtils 
{
    public static File selectFileToSave(String path) {
        
        final JFileChooser chooser;
        if ( path == null ) 
        {
            chooser = new JFileChooser();
        } else {
            chooser = new JFileChooser( path );
        }
        if ( path != null ) {
            chooser.setSelectedFile( new File(path) );
        }
        final int result = chooser.showSaveDialog( null );
        if ( result == JFileChooser.APPROVE_OPTION ) {
            return chooser.getSelectedFile();
        }
        return null;
    }
    
    public static File selectDirToSave(String path) {
        
        final JFileChooser chooser;
        if ( path == null ) 
        {
            chooser = new JFileChooser();
        } else {
            chooser = new JFileChooser( path );
        }
        chooser.setFileSelectionMode( JFileChooser.DIRECTORIES_ONLY );
        if ( path != null ) {
            chooser.setSelectedFile( new File(path) );
        }
        final int result = chooser.showSaveDialog( null );
        if ( result == JFileChooser.APPROVE_OPTION ) {
            return chooser.getSelectedFile();
        }
        return null;
    }    
    
}
