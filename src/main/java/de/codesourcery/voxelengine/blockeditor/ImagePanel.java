package de.codesourcery.voxelengine.blockeditor;

import java.awt.Graphics;
import java.awt.image.BufferedImage;

import javax.swing.JPanel;

public class ImagePanel extends JPanel 
{
    private BufferedImage image;
    
    public ImagePanel() {
    }
    
    @Override
    protected void paintComponent(Graphics g) 
    {
        super.paintComponent( g );
        if ( image != null ) 
        {
            g.drawImage( image , 0, 0 , getWidth() , getHeight() , null );
        }
    }

    public void setImage(BufferedImage image)
    {
        this.image = image;
        repaint();
    }
}