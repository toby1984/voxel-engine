package de.codesourcery.voxelengine.blockeditor;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

public class BlockDefinitionPanel extends JPanel 
{
    public BlockDefinition model;
    
    private final JTextField blockIdField = new JTextField();
    private final JTextField blockNameField = new JTextField();
    
    private final JCheckBox emitsLight = new JCheckBox();
    private final JTextField lightLevel = new JTextField();
    private final JCheckBox opaque = new JCheckBox();
    
    private IValueChangedListener<Object> listener = (obj, childrenChangedAsWell) -> {};
    
    private BlockPreviewPanel blockPreview = new BlockPreviewPanel();
    
    public BlockDefinitionPanel() 
    {
        setLayout( new GridBagLayout() );
        
        int y = 0;
        
        // preview
        GridBagConstraints cnstrs = new GridBagConstraints();
        cnstrs.insets = new Insets(5,5,5,5);
        cnstrs.weightx = 1; cnstrs.weighty = 0;
        cnstrs.gridx = 0 ; cnstrs.gridy = y;
        cnstrs.gridheight = 1 ; cnstrs.gridwidth = 2;
        cnstrs.fill = GridBagConstraints.NONE;
        add( blockPreview , cnstrs );
        y++;
        
        // input fields
        addInputField( "Block ID:" , blockIdField , y ).setColumns( 20 );
        y++;
        
        addInputField( "Name:" , blockNameField , y ).setColumns( 20 );
        y++;
        
        addInputField( "Opaque:" , opaque , y );
        y++;

        emitsLight.addActionListener( ev -> 
        {
            lightLevel.setEditable( emitsLight.isSelected() );
        });
        addInputField( "Emits light:" , emitsLight , y );
        y++;        
        
        addInputField( "Light level:" , lightLevel , y ).setColumns( 3 );
        y++;    
        
        // "select same texture for all sides" button
        final JButton textureButton = new JButton("Choose...");
        textureButton.addActionListener( ev -> selectTexture() );
        addInputField( "Texture:" , textureButton , y );
        y++;   
        
        
        // add save button
        cnstrs = new GridBagConstraints();
        cnstrs.insets = new Insets(5,5,5,5);
        cnstrs.weightx = 0.5; cnstrs.weighty = 0;
        cnstrs.gridx = 0 ; cnstrs.gridy = y;
        cnstrs.gridheight = 1 ; cnstrs.gridwidth = 1;
        cnstrs.fill = GridBagConstraints.BOTH;
        
        final JButton save = new JButton("Save");
        save.addActionListener( ev -> saveChanges() );
        add( save , cnstrs );
        
        // add cancel button
        cnstrs = new GridBagConstraints();
        cnstrs.insets = new Insets(5,5,5,5);
        cnstrs.weightx = 0.5; cnstrs.weighty = 0;
        cnstrs.gridx = 1 ; cnstrs.gridy = y;
        cnstrs.gridheight = 1 ; cnstrs.gridwidth = 1;
        cnstrs.fill = GridBagConstraints.BOTH;
        
        final JButton cancel = new JButton("Cancel");
        cancel.addActionListener( ev -> modelChanged() );
        add( cancel , cnstrs );
        y++;        
    }
    
    private void selectTexture() 
    {
        final String path = blockPreview.getTextureResolver().selectTextureFile();
        final String relPath = blockPreview.getTextureResolver().toRelativePath( path );
        for ( BlockSideDefinition sideDef : this.model.sides ) {
            sideDef.inputTexture = relPath;
        }
        this.blockPreview.setModel( this.model );        
        this.listener.valueChanged( this.model, true );
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
        add( new JLabel(label,SwingConstants.LEFT) , cnstrs );
        
        cnstrs = new GridBagConstraints();
        cnstrs.insets = new Insets(5,5,5,5);
        cnstrs.weightx = 0.5; cnstrs.weighty = 0;
        cnstrs.gridx = 1 ; cnstrs.gridy = y;
        cnstrs.gridheight = 1 ; cnstrs.gridwidth = 1;
        cnstrs.anchor = GridBagConstraints.WEST;
        cnstrs.fill = GridBagConstraints.HORIZONTAL;  
        
        add( component , cnstrs );        
        return component;
    }
    
    public void setModel(BlockDefinition model) 
    {
        this.model = model;
        modelChanged();
    }
    
    private void modelChanged() 
    {
        final boolean editable = model == null ? false : model.editable;
        if ( model == null ) {
            blockIdField.setText("");
            blockNameField.setText("");
            emitsLight.setSelected( false );
            lightLevel.setText("0");
            opaque.setSelected( false );
        } else {
            blockIdField.setText( Integer.toString( model.blockType) );
            if ( model.name != null ) 
            {
                blockNameField.setText( model.name );
            } else {
                blockNameField.setText( "" );
            }          
            emitsLight.setSelected( model.emitsLight );
            lightLevel.setText( Byte.toString( model.lightLevel ) );
            opaque.setSelected( model.opaque );            
        }
        
        emitsLight.setEnabled( editable );
        lightLevel.setEditable( editable );
        opaque.setEnabled( editable );
        blockIdField.setEditable( editable );
        blockNameField.setEditable( editable );
        
        lightLevel.setEditable( editable && emitsLight.isSelected() );
        
        this.blockPreview.setModel( this.model );
    }
    
    private void saveChanges() 
    {
        this.model.blockType = Integer.parseInt( blockIdField.getText() );
        this.model.name = blockNameField.getText();
        this.model.emitsLight = emitsLight.isSelected();
        this.model.lightLevel = this.model.emitsLight ? Byte.parseByte( lightLevel.getText() ) : 0;
        this.model.opaque = opaque.isSelected();
        
        this.listener.valueChanged( this.model, true );
    }
    
    public void setValueChangedListener(IValueChangedListener<Object> listener) {
        this.listener = listener;
    }
    
    public void setTextureResolver(TextureResolver resolver) 
    {
        this.blockPreview.setTextureResolver( resolver );
    }
}