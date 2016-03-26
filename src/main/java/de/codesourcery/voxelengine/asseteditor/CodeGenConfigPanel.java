package de.codesourcery.voxelengine.asseteditor;

import java.io.File;

import javax.swing.JButton;
import javax.swing.JTextField;

import de.codesourcery.voxelengine.asseteditor.AssetConfig.CodeGenConfig;
import de.codesourcery.voxelengine.asseteditor.TextureConfigPanel.ITableLayout;
import de.codesourcery.voxelengine.asseteditor.TextureConfigPanel.PanelTableLayout;

public class CodeGenConfigPanel extends FormPanel<CodeGenConfig> implements ThreeColumnLayout
{
    private final JTextField outputDir = new JTextField();
    private final JTextField className = new JTextField();
    private final JTextField packageName = new JTextField();
    
    public CodeGenConfigPanel(AssetConfig.SourceType sourceType) 
    {
        this(sourceType,null);
    }
    
    public CodeGenConfigPanel(AssetConfig.SourceType sourceType,ITableLayout helper) 
    {
        if ( helper == null ) {
            helper = new PanelTableLayout(this);
        }
        
        final JButton button = new JButton("Choose...");
        button.addActionListener( ev -> 
        {
            final File dir = EditorUtils.selectDirToSave( model.outputDirectory );
            if ( dir != null ) {
                outputDir.setText( dir.getAbsolutePath() );
            }
        });
        addInputField( sourceType.name()+" output directory:" , outputDir , button , helper );
        addInputField( sourceType.name()+" package name:" , packageName , helper );
        addInputField( sourceType.name()+" class name:" , className , helper );
    }
    
    @Override
    public void saveChanges() 
    {
        model.outputDirectory = outputDir.getText();
        model.className = className.getText();
        model.packageName = packageName.getText();
        
        notifyChangeListener( false );
    }

    @Override
    protected void modelChanged() {
        if ( model == null ) {
            outputDir.setText("");
            className.setText("");
            packageName.setText("");
        } else {
            outputDir.setText( model.outputDirectory );
            className.setText( model.className );
            packageName.setText( model.packageName );            
        }
    }
}