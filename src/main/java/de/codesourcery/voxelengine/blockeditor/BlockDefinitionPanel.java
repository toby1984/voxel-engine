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

public class BlockDefinitionPanel extends JPanel 
{
    public BlockDefinition model;
    
    private final JTextField blockIdField = new JTextField();
    private final JTextField blockNameField = new JTextField();
    
    private IValueChangedListener<Object> listener = obj -> {};
    
    public BlockDefinitionPanel() 
    {
        setLayout( new GridBagLayout() );
        
        int y = 0;
        blockIdField.setEditable(false);
        addInputField( "Block ID:" , blockIdField , y ).setColumns( 20 );
        y++;
        
        addInputField( "Name:" , blockNameField , y ).setColumns( 20 );
        y++;
        
        // add save button
        GridBagConstraints cnstrs = new GridBagConstraints();
        cnstrs.insets = new Insets(5,5,5,5);
        cnstrs.weightx = 0.5; cnstrs.weighty = 0;
        cnstrs.gridx = 0 ; cnstrs.gridy = y;
        cnstrs.gridheight = 1 ; cnstrs.gridwidth = 1;
        cnstrs.fill = GridBagConstraints.RELATIVE;
        
        final JButton save = new JButton("Save");
        save.addActionListener( ev -> saveChanges() );
        add( save , cnstrs );
        
        // add cancel button
        cnstrs = new GridBagConstraints();
        cnstrs.insets = new Insets(5,5,5,5);
        cnstrs.weightx = 0.5; cnstrs.weighty = 0;
        cnstrs.gridx = 1 ; cnstrs.gridy = y;
        cnstrs.gridheight = 1 ; cnstrs.gridwidth = 1;
        cnstrs.fill = GridBagConstraints.REMAINDER;
        
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
        cnstrs.fill = GridBagConstraints.RELATIVE;  
        cnstrs.anchor = GridBagConstraints.WEST;
        add( new JLabel(label,SwingConstants.LEFT) , cnstrs );
        
        cnstrs = new GridBagConstraints();
        cnstrs.insets = new Insets(5,5,5,5);
        cnstrs.weightx = 0.5; cnstrs.weighty = 0;
        cnstrs.gridx = 1 ; cnstrs.gridy = y;
        cnstrs.gridheight = 1 ; cnstrs.gridwidth = 1;
        cnstrs.fill = GridBagConstraints.REMAINDER;  
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
        if ( model == null ) {
            blockIdField.setText("");
            blockNameField.setText("");
            return;
        }
        
        blockIdField.setText( Integer.toString( model.blockType) );
        if ( model.name != null ) 
        {
            blockNameField.setText( model.name );
        } else {
            blockNameField.setText( "" );
        }
    }
    
    private void saveChanges() 
    {
        this.model.blockType = Integer.parseInt( blockIdField.getText() );
        this.model.name = blockNameField.getText();
        this.listener.valueChanged( this.model );
    }
    
    public void setValueChangedListener(IValueChangedListener<Object> listener) {
        this.listener = listener;
    }
}
