package de.codesourcery.voxelengine.asseteditor;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import org.apache.commons.lang3.StringUtils;

import de.codesourcery.voxelengine.asseteditor.BlockSideDefinition.Rotation;

public class TextureConfigPanel extends FormPanel<TextureConfig>
{
    public TransformingTextureResolver resolver;
    
    private final JCheckBox flip = new JCheckBox();
    private final JComboBox<BlockSideDefinition.Rotation> rotation = new JComboBox<>( BlockSideDefinition.Rotation.values() );
    private final JTextField inputTexture = new JTextField();
    private final JButton fileSelector = new JButton("Choose...");
    private final ImagePanel preview = new ImagePanel();
    
    public interface ITableLayout 
    {
        public void addToRow(JComponent component,GridBagConstraints cnstrs);
        
        public void addLast(JComponent component,GridBagConstraints cnstrs);
    }
    
    public static final class PanelTableLayout implements ITableLayout 
    {
        private final JPanel panel;
        public int y = 0;

        public PanelTableLayout(JPanel panel) {
            this.panel = panel;
            panel.setLayout( new GridBagLayout() );
        }
        
        public void addToRow(JComponent component,GridBagConstraints cnstrs) {
            cnstrs.gridy = y;
            panel.add( component , cnstrs );
        }
        
        public void addLast(JComponent component,GridBagConstraints cnstrs) {
            cnstrs.gridy = y;
            panel.add( component , cnstrs );
            y++;
        }        
    }
    
    public TextureConfigPanel() 
    {
        this( null );
    }
    
    public TextureConfigPanel(ITableLayout layout) 
    {
        if ( layout == null ) {
            layout = new PanelTableLayout( this );
        }
        
        rotation.setEditable( false );
        
        // add preview
        GridBagConstraints cnstrs = new GridBagConstraints();
        cnstrs.insets = new Insets(5, 5, 5, 5);
        cnstrs.weightx = 0; cnstrs.weighty = 0;
        cnstrs.gridx = 0;
        cnstrs.gridheight = 1 ; cnstrs.gridwidth = 3;
        cnstrs.fill = GridBagConstraints.NONE;
        preview.setMinimumSize( new Dimension(50,50 ) );
        preview.setPreferredSize( new Dimension(50,50 ) );
        preview.setBorder( BorderFactory.createLineBorder( Color.BLACK ) );
        layout.addLast( preview , cnstrs );
        
        // add fields
        fileSelector.addActionListener( ev -> selectFile() );
        inputTexture.setEditable( false );
        inputTexture.setEnabled( false );
        addInputField( "Texture:" , inputTexture , fileSelector , layout ).setColumns(20);
        addInputField( "Flip:" , flip , layout  ).addActionListener( ev -> updatePreview() );
        addInputField( "Rotation:" , rotation , layout  ).addActionListener( ev -> updatePreview() );
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
    
    private <T extends JComponent> T addInputField(String label,T component,ITableLayout layout ) 
    {
        GridBagConstraints cnstrs = new GridBagConstraints();
        cnstrs.insets = new Insets(5,5,5,5);
        cnstrs.weightx = 0.5; cnstrs.weighty = 0;
        cnstrs.gridx = 0;
        cnstrs.gridheight = 1 ; cnstrs.gridwidth = 1;
        cnstrs.fill = GridBagConstraints.HORIZONTAL;  
        cnstrs.anchor = GridBagConstraints.WEST;
        layout.addToRow( new JLabel(label) , cnstrs );
        
        cnstrs = new GridBagConstraints();
        cnstrs.insets = new Insets(5,5,5,5);
        cnstrs.weightx = 0.5; cnstrs.weighty = 0;
        cnstrs.gridx = 1;
        cnstrs.gridheight = 1 ; cnstrs.gridwidth = 2;
        cnstrs.fill = GridBagConstraints.HORIZONTAL;  
        cnstrs.anchor = GridBagConstraints.WEST;
        layout.addLast( component , cnstrs );     
        return component;
    }
    
    private <T extends JComponent> T addInputField(String label,T component1,JComponent component2,ITableLayout layout) 
    {
        GridBagConstraints cnstrs = new GridBagConstraints();
        cnstrs.insets = new Insets(5, 5, 5, 5);
        cnstrs.weightx = 0.5; cnstrs.weighty = 0;
        cnstrs.gridx = 0;
        cnstrs.gridheight = 1 ; cnstrs.gridwidth = 1;
        cnstrs.fill = GridBagConstraints.HORIZONTAL;  
        layout.addToRow( new JLabel(label) , cnstrs );
        
        cnstrs = new GridBagConstraints();
        cnstrs.insets = new Insets(5, 5, 5, 5);
        cnstrs.weightx = 0.5; cnstrs.weighty = 0;
        cnstrs.gridx = 1;
        cnstrs.gridheight = 1 ; cnstrs.gridwidth = 1;
        cnstrs.fill = GridBagConstraints.HORIZONTAL;  
        layout.addToRow( component1 , cnstrs );
        
        cnstrs = new GridBagConstraints();
        cnstrs.insets = new Insets(5, 5, 5, 5);
        cnstrs.weightx = 0.5; cnstrs.weighty = 0;
        cnstrs.gridx = 2 ;
        cnstrs.gridheight = 1 ; cnstrs.gridwidth = 1;
        cnstrs.fill = GridBagConstraints.HORIZONTAL;  
        layout.addLast( component2 , cnstrs );     
        return component1;
    }    
    
    protected void modelChanged() 
    {
        final boolean editable = model != null;
        if ( model == null ) 
        {
            flip.setSelected( false );
            rotation.setSelectedItem( BlockSideDefinition.Rotation.NONE );
            inputTexture.setText( "" );
        } else {
            flip.setSelected( model.flip );
            rotation.setSelectedItem( model.rotation );
            if ( model.getInputTexture() != null ) {
                inputTexture.setText( model.getInputTexture() );
            } else {
                inputTexture.setText( "" );
            }            
        }
            
        fileSelector.setEnabled( editable );
        flip.setEnabled( editable );
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
    
    public void saveChanges() 
    {
        this.model.flip = flip.isSelected();
        this.model.rotation = (Rotation) rotation.getSelectedItem();
        this.model.setInputTexture(inputTexture.getText());
        updatePreview();
        notifyChangeListener(false);
    }
    
    public void setTextureResolver(TextureResolver resolver) 
    {
        this.resolver = new TransformingTextureResolver( resolver );
        updatePreview();
    }
}