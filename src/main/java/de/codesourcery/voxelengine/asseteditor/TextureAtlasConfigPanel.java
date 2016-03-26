package de.codesourcery.voxelengine.asseteditor;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

public class TextureAtlasConfigPanel extends FormPanel<TextureAtlasConfig> 
{
    private final JTextField blockTextureSize = new JTextField();
    private final JTextField textureAtlasSize = new JTextField();
    private final JTextField outputName = new JTextField();
    
    public TextureAtlasConfigPanel() 
    {
        setLayout( new GridBagLayout() );
        
        int y = 0;
        
        // input fields
        addInputField( "Output name:" , outputName , y ).setColumns( 20 );
        y++;
        
        addInputField( "Texture atlas size:" , textureAtlasSize , y ).setColumns( 5 );
        y++;
        
        addInputField( "Block texture size:" , blockTextureSize , y ).setColumns( 5 );
        y++;

        // add save button
        GridBagConstraints cnstrs = new GridBagConstraints();
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
    
    protected void modelChanged() 
    {
        if ( model == null ) {
            outputName.setText( "" );
            textureAtlasSize.setText( "" );
            blockTextureSize.setText("");
        } else {
            if ( model.outputName != null ) {
                outputName.setText( model.outputName );
            } else {
                outputName.setText("");
            }
            textureAtlasSize.setText( Integer.toString( model.textureAtlasSize ) );
            blockTextureSize.setText( Integer.toString( model.textureSize ) );
        }
    }
    
    @Override
    public void saveChanges() 
    {
        model.outputName = outputName.getText();
        model.textureSize = Integer.parseInt( blockTextureSize.getText() );
        model.textureAtlasSize = Integer.parseInt( textureAtlasSize.getText() );
        notifyChangeListener(false);
    }    
}