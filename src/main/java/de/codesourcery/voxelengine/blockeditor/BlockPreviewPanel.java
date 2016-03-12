package de.codesourcery.voxelengine.blockeditor;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;

public class BlockPreviewPanel extends JPanel 
{
    private static final Dimension IMAGE_SIZE = new Dimension(32,32);
    
    private ImagePanel top = new ImagePanel();
    private ImagePanel bottom = new ImagePanel();
    private ImagePanel left = new ImagePanel();
    private ImagePanel right = new ImagePanel();
    private ImagePanel front = new ImagePanel();
    private ImagePanel back = new ImagePanel();
    
    private TextureResolver textureResolver;
    private BlockDefinition model;
    
    public BlockPreviewPanel() 
    {
        setLayout( new GridBagLayout() );
        
        addPreview( "Top" , top , 0 );
        addPreview( "Bottom" , bottom , 1 );
        addPreview( "Left" , left, 2 );
        addPreview( "Right" , right , 3 );
        addPreview( "Front" , front , 4 );
        addPreview( "Back" , back , 5 );
    }
    
    private void addPreview(String label , ImagePanel panel , int x) 
    {
        panel.setBorder( BorderFactory.createLineBorder( Color.BLACK ) );
        panel.setPreferredSize( IMAGE_SIZE );
        
        GridBagConstraints cnstrs = new GridBagConstraints();
        cnstrs.insets = new Insets(5,5,5,5);
        cnstrs.weightx = 0.5; cnstrs.weighty = 0;
        cnstrs.gridx = x ; cnstrs.gridy = 0;
        cnstrs.gridheight = 1 ; cnstrs.gridwidth = 1;
        cnstrs.fill = GridBagConstraints.RELATIVE;  
        cnstrs.anchor = GridBagConstraints.NORTH; 
        add( new JLabel(label) , cnstrs );
        
        cnstrs = new GridBagConstraints();
        cnstrs.insets = new Insets(5,5,5,5);
        cnstrs.weightx = 0.5; cnstrs.weighty = 0;
        cnstrs.gridx = x ; cnstrs.gridy = 1;
        cnstrs.gridheight = 1 ; cnstrs.gridwidth = 1;
        cnstrs.fill = GridBagConstraints.RELATIVE;  
        cnstrs.anchor = GridBagConstraints.NORTH; 
        add( panel , cnstrs );        
    }

    public void setModel(BlockDefinition model) 
    {
        this.model = model;
        modelChanged();
    }
    
    private void setSide(ImagePanel panel,BlockSideDefinition def) 
    {
        if ( def.isTextureAssigned() ) 
        {
            final TransformingTextureResolver res = new TransformingTextureResolver( this.textureResolver );
            res.setTransform( def );
            try {
                panel.setImage( res.resolve( def.inputTexture ) );
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            panel.setImage( null );
        }
    }
    
    private void modelChanged() 
    {
        if ( this.model != null ) 
        {
            for (BlockSideDefinition blockDef : this.model.sides) 
            {
                switch( blockDef.side ) 
                {
                    case SIDE_FRONT: setSide( front , blockDef ); break;
                    case SIDE_BACK: setSide( back , blockDef ); break;
                    case SIDE_LEFT: setSide( left , blockDef ); break;
                    case SIDE_RIGHT: setSide( right , blockDef ); break;
                    case SIDE_TOP: setSide( top , blockDef ); break;
                    case SIDE_BOTTOM: setSide( bottom, blockDef ); break;
                    default:
                        throw new RuntimeException("Unhandled case: "+blockDef.side);
                }
            }
        } else {
            front.setImage( null );
            back.setImage( null );
            left.setImage( null );
            right.setImage( null );
            top.setImage( null );
            bottom.setImage( null );
        }
    }
    
    public void setTextureResolver(TextureResolver textureResolver) {
        this.textureResolver = textureResolver;
        modelChanged();
    }
    
    public TextureResolver getTextureResolver() {
        return textureResolver;
    }
}