package de.codesourcery.voxelengine.asseteditor;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.util.Arrays;
import java.util.Comparator;

import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.SwingConstants;
import javax.swing.plaf.basic.BasicComboBoxRenderer;

import de.codesourcery.voxelengine.asseteditor.TextureConfigPanel.ITableLayout;
import de.codesourcery.voxelengine.asseteditor.TextureConfigPanel.PanelTableLayout;

public class ItemDefinitionPanel extends FormPanel<ItemDefinition> {

    private final JTextField itemName = new JTextField();
    private final JCheckBox canCreateBlock = new JCheckBox();
    private final JCheckBox canDestroyBlock = new JCheckBox();
    
    private final TextureConfigPanel texturePanel;
    
    private final JComboBox<BlockDefinition> createdBlock = new JComboBox<>();
    
    public ItemDefinitionPanel(AssetConfig config) 
    {
        final PanelTableLayout helper = new PanelTableLayout(this);
        
        final BlockDefinition[] items = new BlockDefinition[ config.blocks.size() ];
        for ( int i = 0 ; i < items.length ; i++ ) {
            items[i] = config.blocks.get(i);
        }
        Arrays.sort( items , Comparator.comparing( bd -> bd.name , Comparator.nullsFirst( String::compareTo ) ) );
        
        final ComboBoxModel<BlockDefinition> comboModel = new DefaultComboBoxModel<>( items );
        createdBlock.setModel( comboModel );
        
        createdBlock.setEditable( false );
        
        @SuppressWarnings("unchecked")
        final ListCellRenderer<BlockDefinition> renderer = new BasicComboBoxRenderer() 
        {
            public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) 
            {
              final Component result = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
              if ( value instanceof BlockDefinition) 
              {
                  setText( ((BlockDefinition) value).name );
              } else {
                  setText("---");
              }
              return result;
            }
        };
        createdBlock.setRenderer( renderer);
        
        // add icon
        texturePanel = new TextureConfigPanel( helper );
        
        // Item name
        addInputField( "Item name:" , itemName , helper );
        
        // canCreateBlock
        canCreateBlock.addActionListener( ev -> 
        {
            createdBlock.setEnabled( canCreateBlock.isSelected() );
        });
        addInputField( "Can create block?:" , canCreateBlock , helper );
        
        // createdBlock
        addInputField( "Created block:" , createdBlock , helper );
        
        // canDestroyBlock
        addInputField( "Can destroy block?:" , canDestroyBlock , helper );
        
        // add save button
        final JButton save = new JButton("Save");
        save.addActionListener( ev -> saveChanges() );
        
        GridBagConstraints cnstrs = new GridBagConstraints();
        cnstrs.insets = new Insets(5,5,5,5);
        cnstrs.weightx = 0.5; cnstrs.weighty = 0;
        cnstrs.gridx = 0;
        cnstrs.gridheight = 1 ; cnstrs.gridwidth = 1;
        cnstrs.fill = GridBagConstraints.BOTH;        
        helper.addToRow( save , cnstrs );
        
        // add cancel button
        final JButton cancel = new JButton("Cancel");
        cancel.addActionListener( ev -> modelChanged() );
        
        cnstrs = new GridBagConstraints();
        cnstrs.insets = new Insets(5,5,5,5);
        cnstrs.weightx = 0.5; cnstrs.weighty = 0;
        cnstrs.gridx = 1 ;
        cnstrs.gridheight = 1 ; cnstrs.gridwidth = 1;
        cnstrs.fill = GridBagConstraints.REMAINDER;        
        helper.addLast( cancel , cnstrs );
    }
    
    public void saveChanges() 
    {
        texturePanel.saveChanges();
        model.name = itemName.getText();
        model.canCreateBlock = canCreateBlock.isSelected();
        model.canDestroyBlock = canDestroyBlock.isSelected();
        model.createdBlock = (BlockDefinition) createdBlock.getSelectedItem();
        notifyChangeListener( false );
    }

    private <T extends JComponent> T addInputField(String label,T component,ITableLayout helper) 
    {
        component.setPreferredSize( new Dimension(100,20 ) );
        GridBagConstraints cnstrs = new GridBagConstraints();
        cnstrs.insets = new Insets(5,5,5,5);
        cnstrs.weightx = 0.5; cnstrs.weighty = 0;
        cnstrs.gridx = 0;
        cnstrs.anchor = GridBagConstraints.WEST;
        cnstrs.gridheight = 1 ; cnstrs.gridwidth = 1;
        cnstrs.fill = GridBagConstraints.HORIZONTAL;  
        helper.addToRow( new JLabel(label,SwingConstants.LEFT) , cnstrs );
        
        cnstrs = new GridBagConstraints();
        cnstrs.insets = new Insets(5,5,5,5);
        cnstrs.weightx = 0.5; cnstrs.weighty = 0;
        cnstrs.gridx = 1;
        cnstrs.anchor = GridBagConstraints.WEST;
        cnstrs.gridheight = 1 ; cnstrs.gridwidth = GridBagConstraints.REMAINDER;
        cnstrs.fill = GridBagConstraints.HORIZONTAL;  
        
        helper.addLast( component , cnstrs );        
        return component;
    }    
    
    protected void modelChanged() 
    {
        if ( model != null ) 
        {
            itemName.setText( model.name );
            canCreateBlock.setSelected( model.canCreateBlock );
            canDestroyBlock.setSelected( model.canDestroyBlock );
            createdBlock.setSelectedItem( model.createdBlock );      
            createdBlock.setEnabled( canCreateBlock.isSelected() );
            texturePanel.setModel( model.texture );
        } 
        else 
        {
            itemName.setText("");
            canCreateBlock.setSelected(false);
            canDestroyBlock.setSelected(false);
            createdBlock.setSelectedItem( null );
            createdBlock.setEnabled( false );            
            texturePanel.setModel( model.texture );
        }
    }
    
    public void setTextureResolver(TextureResolver resolver) {
        texturePanel.setTextureResolver( resolver );
    }
}