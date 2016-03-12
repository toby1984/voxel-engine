package de.codesourcery.voxelengine.blockeditor;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

public class BlockConfigPanel extends JPanel 
{
    public BlockConfig model;
    
    private final JTextField blockTextureSize = new JTextField();
    private final JTextField textureAtlasSize = new JTextField();
    private final JTextField baseDirectory = new JTextField();
    private final JTextField codeOutputFile = new JTextField();
    
    public BlockConfigPanel() 
    {
        setLayout( new GridBagLayout() );
        
        int y = 0;
        
        // input fields
        addInputField( "Input texture base directory:" , baseDirectory , y ).setColumns( 20 );
        y++;
        
        codeOutputFile.setEditable( false );
        addInputField( "Code output file:" , codeOutputFile , y ).setColumns( 20 );
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
    
    public void setModel(BlockConfig model) 
    {
        this.model = model;
        modelChanged();
    }
    
    private void modelChanged() 
    {
        if ( model == null ) {
            baseDirectory.setText( "" );
            textureAtlasSize.setText( "" );
            blockTextureSize.setText("");
            codeOutputFile.setText("");
        } else {
            if ( model.codeOutputFile != null ) {
                codeOutputFile.setText( model.codeOutputFile );
            } else {
                codeOutputFile.setText("");
            }
            if ( model.baseDirectory != null ) {
                baseDirectory.setText( model.baseDirectory );
            } else {
                baseDirectory.setText( "" );
            }
            textureAtlasSize.setText( Integer.toString( model.textureAtlasSize ) );
            blockTextureSize.setText( Integer.toString( model.blockTextureSize ) );
        }
    }
    
    private void saveChanges() 
    {
        model.baseDirectory = baseDirectory.getText();
        model.blockTextureSize = Integer.parseInt( blockTextureSize.getText() );
        model.textureAtlasSize = Integer.parseInt( textureAtlasSize.getText() );
    }    
}