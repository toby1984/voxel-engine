package de.codesourcery.voxelengine.blockeditor;

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JToolBar;

import de.codesourcery.voxelengine.blockeditor.BlockTreePanel.MyTreeNode;

public class Main extends JFrame {

    private final JPanel contentPanel = new JPanel();
    private JPanel content;
    
    private final BlockTreePanel blockTree = new BlockTreePanel();
    
    public static void main(String[] args) throws IOException 
    {
    
        final Main main = new Main();
        main.run( args.length > 0 ? args[0] : null );
    }
    
    public Main() 
    {
        super("Block-Editor v0.0");
        setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );
        
        final JPanel panel = new JPanel();
        panel.setLayout( new GridBagLayout() );
        
        // add toolbar
        GridBagConstraints cnstrs = new GridBagConstraints();
        cnstrs.weightx = 1.0; cnstrs.weighty = 0.0;
        cnstrs.gridx = 0 ; cnstrs.gridy = 0;
        cnstrs.gridheight = 1 ; cnstrs.gridwidth = 2;
        cnstrs.fill = GridBagConstraints.BOTH;        
        panel.add( createToolbar() , cnstrs );
        
        // add block tree panel
        cnstrs = new GridBagConstraints();
        cnstrs.weightx = 1.0; cnstrs.weighty = 1.0;
        cnstrs.gridx = 0 ; cnstrs.gridy = 1;
        cnstrs.gridheight = 1 ; cnstrs.gridwidth = 1;
        cnstrs.fill = GridBagConstraints.BOTH;
        
        blockTree.setSelectionListener( this::treeSelectionChanged );
        
        panel.add( blockTree , cnstrs );
        
        // add content area
        contentPanel.setLayout( new GridBagLayout() );
        cnstrs = new GridBagConstraints();
        cnstrs.weightx = 1.0; cnstrs.weighty = 1.0;
        cnstrs.gridx = 1 ; cnstrs.gridy = 1;
        cnstrs.gridheight = 1 ; cnstrs.gridwidth = 1;
        cnstrs.fill = GridBagConstraints.BOTH;
        
        contentPanel.setPreferredSize( new Dimension(200,400 ) );
        
        panel.add( contentPanel , cnstrs );
        
        // setup frame
        getContentPane().add( panel );
        setPreferredSize( new Dimension(800,600 ) );
        pack();
        setLocationRelativeTo( null );
        setVisible(true);
    }
    
    private void treeSelectionChanged(MyTreeNode node) 
    {
        System.out.println("Selected: "+node.value);
        if ( node.value instanceof BlockDefinition ) 
        {
            final BlockDefinitionPanel newContent = new BlockDefinitionPanel();
            newContent.setModel( (BlockDefinition) node.value );
            newContent.setValueChangedListener( blockTree );
            changeContent( newContent );
        } 
        else if ( node.value instanceof BlockSideDefinition ) 
        {
            final BlockSideDefinitionPanel newContent = new BlockSideDefinitionPanel();
            newContent.setModel( (BlockSideDefinition) node.value );
            newContent.setValueChangedListener( blockTree );
            changeContent( newContent ); 
        } else {
            changeContent( new JPanel() );
        }
    }
    
    private JToolBar createToolbar() 
    {
        final JToolBar result = new JToolBar(JToolBar.HORIZONTAL);
        result.add( button("Add block" , this::addBlock ) );
        return result;
    }
    
    private void addBlock() 
    {
        final BlockDefinition def = new BlockDefinition();
        def.blockType = blockTree.getConfig().nextAvailableBlockTypeId(); 
        def.name = "";
        blockTree.getConfig().add( def );
        
        blockTree.add( def );
    }
    
    private void changeContent(JPanel newContent) 
    {
        if ( this.content != null ){
            contentPanel.remove( this.content );
        }
        final GridBagConstraints cnstrs = new GridBagConstraints();
        cnstrs.weightx = 1.0; cnstrs.weighty = 1.0;
        cnstrs.gridx = 0 ; cnstrs.gridy = 0;
        cnstrs.gridheight = 1 ; cnstrs.gridwidth = 1;
        cnstrs.fill = GridBagConstraints.BOTH;    
        
        this.content = newContent;
        this.contentPanel.add( newContent , cnstrs );
        this.contentPanel.revalidate();
        this.contentPanel.repaint();
    }
    
    private JButton button(String s,Runnable action) 
    {
        final JButton result = new JButton(s);
        result.addActionListener( ev -> action.run() );
        return result;
    }

    private void run(String inputFile) throws IOException 
    {
        final BlockConfig config;
        if ( inputFile == null ) {
            config = new BlockConfig();
        } else {
            config = new BlockConfigReader().read( Paths.get( inputFile ) );
        }
        blockTree.setConfig ( config );
    }
}