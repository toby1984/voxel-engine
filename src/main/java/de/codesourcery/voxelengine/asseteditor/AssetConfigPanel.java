package de.codesourcery.voxelengine.asseteditor;

import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.util.HashMap;
import java.util.Map;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTextField;

import de.codesourcery.voxelengine.asseteditor.AssetConfig.CodeGenConfig;
import de.codesourcery.voxelengine.asseteditor.TextureConfigPanel.PanelTableLayout;

public class AssetConfigPanel extends FormPanel<AssetConfig> implements ThreeColumnLayout
{
    private final JTextField baseDirectory = new JTextField();
    private final Map<AssetConfig.SourceType,CodeGenConfigPanel> codeOutputFiles = new HashMap<>();
    
    public AssetConfigPanel() 
    {
        PanelTableLayout helper = new PanelTableLayout(this);
        
        // input fields
        addInputField( "Input texture base directory:" , baseDirectory , helper ).setColumns( 20 );
        
        for ( AssetConfig.SourceType type : AssetConfig.SourceType.values() ) 
        {
            final CodeGenConfigPanel tf = new CodeGenConfigPanel( type, helper );
            codeOutputFiles.put( type , tf );
        }
        
        final JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout( new FlowLayout() );
        
        // add save button
        final JButton save = new JButton("Save");
        save.addActionListener( ev -> saveChanges() );
        buttonPanel.add( save );
        
        // add cancel button
        final JButton cancel = new JButton("Cancel");
        cancel.addActionListener( ev -> modelChanged() );
        buttonPanel.add( cancel );
        
        // add button panel
        final GridBagConstraints cnstrs = new GridBagConstraints();
        cnstrs.insets = new Insets(5,5,5,5);
        cnstrs.weightx = 0.5; cnstrs.weighty = 0;
        cnstrs.gridx = 0;
        cnstrs.gridheight = 1 ; cnstrs.gridwidth = 3;
        cnstrs.fill = GridBagConstraints.HORIZONTAL;
        
        helper.addLast( buttonPanel , cnstrs );
    }
    
    protected void modelChanged() 
    {
        if ( model == null ) {
            baseDirectory.setText( "" );
            for ( AssetConfig.SourceType type : AssetConfig.SourceType.values() ) 
            {
                codeOutputFiles.get(type).setModel( null );
            }
        } 
        else 
        {
            for ( AssetConfig.SourceType type : AssetConfig.SourceType.values() ) 
            {
                final CodeGenConfig config = model.getCodeGenConfig( type );
                codeOutputFiles.get(type).setModel( config );
            }
            
            if ( model.baseDirectory != null ) {
                baseDirectory.setText( model.baseDirectory );
            } else {
                baseDirectory.setText( "" );
            }
        }
    }
    
    @Override
    public void saveChanges() 
    {
        codeOutputFiles.values().forEach( v -> v.saveChanges() );
        model.baseDirectory = baseDirectory.getText();
        notifyChangeListener( false );
    }
}