package de.codesourcery.voxelengine.blockeditor;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.filechooser.FileFilter;

import org.apache.commons.lang3.StringUtils;

import de.codesourcery.voxelengine.blockeditor.BlockSideDefinition.Rotation;
import de.codesourcery.voxelengine.engine.BlockSide;

public class BlockSideDefinitionPanel extends JPanel 
{
    public BlockSideDefinition model;
    public TransformingTextureResolver resolver;
    
    /*
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
    public final BlockSide side;
    
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
     */
    
    private final JComboBox<BlockSide> side = new JComboBox<>( BlockSide.values() );
    private final JCheckBox flip = new JCheckBox();
    private final JComboBox<BlockSideDefinition.Rotation> rotation = new JComboBox<>( BlockSideDefinition.Rotation.values() );
    private final JTextField inputTexture = new JTextField();
    private final JButton fileSelector = new JButton("Choose...");
    private final ImagePanel preview = new ImagePanel();
    
    private Path baseDirectory;

    private IValueChangedListener<Object> changeListener = (obj, childrenChangedAsWell) -> {};
    
    public BlockSideDefinitionPanel() 
    {
        setLayout( new GridBagLayout() );
        
        int y = 0;
        
        // add preview
        GridBagConstraints cnstrs = new GridBagConstraints();
        cnstrs.weightx = 0; cnstrs.weighty = 0;
        cnstrs.gridx = 0 ; cnstrs.gridy = y;
        cnstrs.gridheight = 1 ; cnstrs.gridwidth = 3;
        cnstrs.fill = GridBagConstraints.NONE;
        preview.setPreferredSize( new Dimension(50,50 ) );
        preview.setBorder( BorderFactory.createLineBorder( Color.BLACK ) );
        add( preview , cnstrs );
        y++;
        
        // add fields
        side.setEditable( false );
        addInputField( "Side:" , side, y );
        y++;

        fileSelector.addActionListener( ev -> selectFile() );
        inputTexture.setEditable( false );
        inputTexture.setEnabled( false );
        addInputField( "Texture:" , inputTexture , fileSelector , y ).setColumns(20);
        y++;
        
        addInputField( "Flip:" , flip , y ).addActionListener( ev -> updatePreview() );
        y++;
        
        addInputField( "Rotation:" , rotation , y ).addActionListener( ev -> updatePreview() );
        y++;        
        
        // add save button
        final JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout( new FlowLayout() );
        
        final JButton save = new JButton("Save");
        save.addActionListener( ev -> saveChanges() );
        buttonPanel.add(save);
        
        // add cancel button
        final JButton cancel = new JButton("Cancel");
        cancel.addActionListener( ev -> modelChanged() );
        buttonPanel.add( cancel );
        
        cnstrs = new GridBagConstraints();
        cnstrs.weightx = 0.5; cnstrs.weighty = 0;
        cnstrs.gridx = 0 ; cnstrs.gridy = y;
        cnstrs.gridheight = 1 ; cnstrs.gridwidth = 3;
        cnstrs.fill = GridBagConstraints.HORIZONTAL;
        add( buttonPanel , cnstrs );
        y++;        
    }
    
    private void selectFile() 
    {
        final String path = resolver.selectTextureFile();
        if ( path == null ) { 
            inputTexture.setText( "" );
        } else {
            inputTexture.setText( resolver.toRelativePath( path ) );
        }
        updatePreview();        
    }
    
    private <T extends JComponent> T addInputField(String label,T component,int y ) 
    {
        GridBagConstraints cnstrs = new GridBagConstraints();
        cnstrs.insets = new Insets(5,5,5,5);
        cnstrs.weightx = 0.5; cnstrs.weighty = 0;
        cnstrs.gridx = 0 ; cnstrs.gridy = y;
        cnstrs.gridheight = 1 ; cnstrs.gridwidth = 1;
        cnstrs.fill = GridBagConstraints.HORIZONTAL;  
        cnstrs.anchor = GridBagConstraints.WEST;
        add( new JLabel(label) , cnstrs );
        
        cnstrs = new GridBagConstraints();
        cnstrs.insets = new Insets(5,5,5,5);
        cnstrs.weightx = 0.5; cnstrs.weighty = 0;
        cnstrs.gridx = 1 ; cnstrs.gridy = y;
        cnstrs.gridheight = 1 ; cnstrs.gridwidth = 2;
        cnstrs.fill = GridBagConstraints.HORIZONTAL;  
        cnstrs.anchor = GridBagConstraints.WEST;
        add( component , cnstrs );        
        return component;
    }
    
    private <T extends JComponent> T addInputField(String label,T component1,JComponent component2,int y ) 
    {
        GridBagConstraints cnstrs = new GridBagConstraints();
        cnstrs.weightx = 0.5; cnstrs.weighty = 0;
        cnstrs.gridx = 0 ; cnstrs.gridy = y;
        cnstrs.gridheight = 1 ; cnstrs.gridwidth = 1;
        cnstrs.fill = GridBagConstraints.HORIZONTAL;  
        add( new JLabel(label) , cnstrs );
        
        cnstrs = new GridBagConstraints();
        cnstrs.weightx = 0.5; cnstrs.weighty = 0;
        cnstrs.gridx = 1 ; cnstrs.gridy = y;
        cnstrs.gridheight = 1 ; cnstrs.gridwidth = 1;
        cnstrs.fill = GridBagConstraints.HORIZONTAL;  
        add( component1 , cnstrs );
        
        cnstrs = new GridBagConstraints();
        cnstrs.weightx = 0.5; cnstrs.weighty = 0;
        cnstrs.gridx = 2 ; cnstrs.gridy = y;
        cnstrs.gridheight = 1 ; cnstrs.gridwidth = 1;
        cnstrs.fill = GridBagConstraints.HORIZONTAL;  
        add( component2 , cnstrs );         
        return component1;
    }    
    
    public void setModel(BlockSideDefinition model) 
    {
        this.model = model;
        modelChanged();
    }
    
    private void modelChanged() 
    {
        final boolean editable = model != null && ! model.isAutoGenerated();
        if ( model == null ) 
        {
            side.setSelectedItem( null );
            flip.setSelected( false );
            rotation.setSelectedItem( BlockSideDefinition.Rotation.NONE );
            inputTexture.setText( "" );
        } else {
            side.setSelectedItem( model.side );
            flip.setSelected( model.flip );
            rotation.setSelectedItem( model.rotation );
            if ( model.getInputTexture() != null ) {
                inputTexture.setText( model.getInputTexture() );
            } else {
                inputTexture.setText( "" );
            }            
        }
            
        fileSelector.setEnabled( editable );
        side.setEditable( editable );
        side.setEnabled( editable );
        flip.setEnabled( editable );
        rotation.setEditable( editable );
        rotation.setEnabled( editable );
        updatePreview();
    }
    
    private void updatePreview() 
    {
        try 
        {
            resolver.flip = flip.isSelected();
            resolver.rotation = (Rotation) rotation.getSelectedItem();
            
            if ( StringUtils.isNotBlank( inputTexture.getText() ) ) 
            {
                preview.setImage( resolver.resolve( inputTexture.getText() ) );
            } else {
                preview.setImage( null );
            }
        } catch (IOException e) {
            e.printStackTrace();
        }        
    }
    
    private void saveChanges() 
    {
        this.model.side = (BlockSide) side.getSelectedItem();
        this.model.flip = flip.isSelected();
        this.model.rotation = (Rotation) rotation.getSelectedItem();
        this.model.setInputTexture(inputTexture.getText());
        updatePreview();
        this.changeListener.valueChanged( this.model, false );
    }
    
    public void setBaseDirectory(Path baseDirectory) {
        this.baseDirectory = baseDirectory;
    }

    public void setValueChangedListener(IValueChangedListener<Object> blockTree) 
    {
        this.changeListener = blockTree; 
    }
    
    public void setTextureResolver(TextureResolver resolver) 
    {
        this.resolver = new TransformingTextureResolver( resolver );
        updatePreview();
    }
}