package de.codesourcery.voxelengine.asseteditor;

import java.awt.GridBagConstraints;
import java.awt.Insets;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.SwingConstants;

import de.codesourcery.voxelengine.asseteditor.TextureConfigPanel.ITableLayout;

public interface ThreeColumnLayout 
{
    public default <T extends JComponent> T addInputField(String label,T component,ITableLayout helper) 
    {
        GridBagConstraints cnstrs = new GridBagConstraints();
        cnstrs.insets = new Insets(5,5,5,5);
        cnstrs.weightx = 0.5; cnstrs.weighty = 0;
        cnstrs.gridx = 0;
        cnstrs.gridheight = 1 ; cnstrs.gridwidth = 1;
        cnstrs.fill = GridBagConstraints.HORIZONTAL;  
        cnstrs.anchor = GridBagConstraints.WEST;
        helper.addToRow( new JLabel(label,SwingConstants.LEFT) , cnstrs );
        
        cnstrs = new GridBagConstraints();
        cnstrs.insets = new Insets(5,5,5,5);
        cnstrs.weightx = 0.5; cnstrs.weighty = 0;
        cnstrs.gridx = 1;
        cnstrs.gridheight = 1 ; cnstrs.gridwidth = 2;
        cnstrs.anchor = GridBagConstraints.WEST;
        cnstrs.fill = GridBagConstraints.HORIZONTAL;  
        
        helper.addLast( component , cnstrs );        
        return component;
    }
    
    public default  <T extends JComponent> T addInputField(String label,T component,JComponent component2,ITableLayout helper) 
    {
        GridBagConstraints cnstrs = new GridBagConstraints();
        cnstrs.insets = new Insets(5,5,5,5);
        cnstrs.weightx = 0.33; cnstrs.weighty = 0;
        cnstrs.gridx = 0;
        cnstrs.gridheight = 1 ; cnstrs.gridwidth = 1;
        cnstrs.fill = GridBagConstraints.HORIZONTAL;  
        cnstrs.anchor = GridBagConstraints.WEST;
        helper.addToRow( new JLabel(label,SwingConstants.LEFT) , cnstrs );
        
        cnstrs = new GridBagConstraints();
        cnstrs.insets = new Insets(5,5,5,5);
        cnstrs.weightx = 0.33; cnstrs.weighty = 0;
        cnstrs.gridx = 1;
        cnstrs.gridheight = 1 ; cnstrs.gridwidth = 1;
        cnstrs.anchor = GridBagConstraints.WEST;
        cnstrs.fill = GridBagConstraints.HORIZONTAL;  
        
        helper.addToRow( component , cnstrs );        
        
        cnstrs = new GridBagConstraints();
        cnstrs.insets = new Insets(5,5,5,5);
        cnstrs.weightx = 0.33; cnstrs.weighty = 0;
        cnstrs.gridx = 2;
        cnstrs.gridheight = 1 ; cnstrs.gridwidth = 1;
        cnstrs.anchor = GridBagConstraints.WEST;
        cnstrs.fill = GridBagConstraints.HORIZONTAL;  
        
        helper.addLast( component2 , cnstrs );          
        return component;
    }
}