package de.codesourcery.voxelengine.blockeditor;

import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

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
    
    private Path baseDirectory;

    private IValueChangedListener<Object> changeListener = obj -> {};
    
    public BlockSideDefinitionPanel() 
    {
        setLayout( new GridBagLayout() );
        
        side.setEditable( false );
        
        int y = 0;
        addInputField( "Side:" , side, y );
        y++;
        
        final JButton fileSelector = new JButton("Choose...");
        fileSelector.addActionListener( ev -> selectFile() );
        
        addInputField( "Texture:" , inputTexture , fileSelector , y );
        y++;
        
        addInputField( "Flip:" , flip , y );
        y++;
        
        addInputField( "Rotation:" , rotation , y );
        y++;        
        
        // add save button
        final JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout( new FlowLayout() );
        
        final JButton save = new JButton("Save");
        save.addActionListener( ev -> saveChanges() );
        buttonPanel.add(save);
        
        // add cancel button
        final JButton cancel = new JButton("Save");
        cancel.addActionListener( ev -> modelChanged() );
        buttonPanel.add( cancel );
        
        GridBagConstraints cnstrs = new GridBagConstraints();
        cnstrs.weightx = 0.5; cnstrs.weighty = 0;
        cnstrs.gridx = 0 ; cnstrs.gridy = y;
        cnstrs.gridheight = 1 ; cnstrs.gridwidth = 3;
        cnstrs.fill = GridBagConstraints.RELATIVE;
        add( buttonPanel );
        y++;        
    }
    
    private void selectFile() 
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
        chooser.showOpenDialog( null );
        
        final File file = chooser.getSelectedFile();
        if ( file == null ) 
        {
            inputTexture.setText( "" );
        } else {
            inputTexture.setText( file.getAbsolutePath() );
        }
    }
    
    private void addInputField(String label,JComponent component,int y ) 
    {
        GridBagConstraints cnstrs = new GridBagConstraints();
        cnstrs.weightx = 0.5; cnstrs.weighty = 0;
        cnstrs.gridx = 0 ; cnstrs.gridy = y;
        cnstrs.gridheight = 1 ; cnstrs.gridwidth = 1;
        cnstrs.fill = GridBagConstraints.RELATIVE;  
        add( new JLabel(label) , cnstrs );
        
        cnstrs = new GridBagConstraints();
        cnstrs.weightx = 0.5; cnstrs.weighty = 0;
        cnstrs.gridx = 1 ; cnstrs.gridy = y;
        cnstrs.gridheight = 1 ; cnstrs.gridwidth = 2;
        cnstrs.fill = GridBagConstraints.REMAINDER;  
        add( component , cnstrs );        
    }
    
    private void addInputField(String label,JComponent component1,JComponent component2,int y ) 
    {
        GridBagConstraints cnstrs = new GridBagConstraints();
        cnstrs.weightx = 0.5; cnstrs.weighty = 0;
        cnstrs.gridx = 0 ; cnstrs.gridy = y;
        cnstrs.gridheight = 1 ; cnstrs.gridwidth = 1;
        cnstrs.fill = GridBagConstraints.RELATIVE;  
        add( new JLabel(label) , cnstrs );
        
        cnstrs = new GridBagConstraints();
        cnstrs.weightx = 0.5; cnstrs.weighty = 0;
        cnstrs.gridx = 1 ; cnstrs.gridy = y;
        cnstrs.gridheight = 1 ; cnstrs.gridwidth = 1;
        cnstrs.fill = GridBagConstraints.RELATIVE;  
        add( component1 , cnstrs );
        
        cnstrs = new GridBagConstraints();
        cnstrs.weightx = 0.5; cnstrs.weighty = 0;
        cnstrs.gridx = 2 ; cnstrs.gridy = y;
        cnstrs.gridheight = 1 ; cnstrs.gridwidth = 1;
        cnstrs.fill = GridBagConstraints.REMAINDER;  
        add( component2 , cnstrs );         
    }    
    
    public void setModel(BlockSideDefinition model) 
    {
        this.model = model;
        modelChanged();
    }
    
    private void modelChanged() 
    {
        if ( model == null ) 
        {
            side.setSelectedItem( null );
            flip.setSelected( false );
            rotation.setSelectedItem( BlockSideDefinition.Rotation.NONE );
            inputTexture.setText( "" );
            return;
        }
        
        side.setSelectedItem( model.side );
        flip.setSelected( model.flip );
        rotation.setSelectedItem( model.rotation );
        if ( model.inputTexture != null ) {
            inputTexture.setToolTipText( model.inputTexture );
        } else {
            inputTexture.setToolTipText( "" );
        }
    }
    
    private void saveChanges() 
    {
        this.model.side = (BlockSide) side.getSelectedItem();
        this.model.flip = flip.isSelected();
        this.model.rotation = (Rotation) rotation.getSelectedItem();
        this.model.inputTexture = inputTexture.getText();
        this.changeListener.valueChanged( this.model );
    }
    
    public void setBaseDirectory(Path baseDirectory) {
        this.baseDirectory = baseDirectory;
    }

    public void setValueChangedListener(IValueChangedListener<Object> blockTree) 
    {
        this.changeListener = blockTree; 
    }
}
