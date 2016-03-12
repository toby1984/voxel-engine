package de.codesourcery.voxelengine.blockeditor;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileFilter;

import de.codesourcery.voxelengine.blockeditor.BlockTreePanel.MyTreeNode;

public class Main extends JFrame {

    private final JPanel contentPanel = new JPanel();
    private JPanel content;
    
    private Path currentAtlasFile;
    private Path currentFile;
    
    private final BlockTreePanel blockTree = new BlockTreePanel();
    
    public static void main(String[] args) throws IOException 
    {
        final Main main = new Main();
        final Runnable r = () -> 
        { 
            try {
                main.run( args.length > 0 ? args[0] : null );
            } catch (Exception e) {
               throw new RuntimeException(e);
            }
        };
        SwingUtilities.invokeLater( r );
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
        setJMenuBar( createMenuBar() );
        getContentPane().add( panel );
        setPreferredSize( new Dimension(800,600 ) );
        pack();
        setLocationRelativeTo( null );
        setVisible(true);
    }
    
    private JMenuBar createMenuBar() 
    {
        final JMenuBar bar = new JMenuBar();
        
        final JMenu menu1 = new JMenu("File");
        menu1.add( menuItem("New" , this::newConfiguration ) );
        menu1.add( menuItem("Load block definitions ..." , () -> loadBlockConfig(true) ) );
        menu1.add( menuItem("Save block definitions" , () -> saveBlockConfig(false) ) );
        menu1.add( menuItem("Save block definitions as ..." , () -> saveBlockConfig(true) ) );
        menu1.add( menuItem("Generate code..." , () -> generateCode() ) );
        menu1.add( menuItem("Save texture atlas..." , () -> 
        { 
            try {
                saveTextureAtlas();
            } catch (Exception e) {
                e.printStackTrace();
            } 
        }));
        menu1.add( menuItem("Quit" , this::quit) );
        bar.add( menu1 );
        return bar;
    }
    
    private void generateCode() 
    {
        final BlockConfig config = blockTree.getConfig();
        final BlockConfigTextureResolver resolver = new BlockConfigTextureResolver( config );
        if ( ! config.isValid( resolver ) ) 
        {
            throw new RuntimeException("Cannot generate code for invalid config");
        }
        
        try {
            new TextureAtlasBuilder().build( blockTree.getConfig() , resolver , true );
        } 
        catch (IOException e) 
        {
            throw new RuntimeException(e);
        }
        
        final CodeGenerator gen = new CodeGenerator();
        final CharSequence text = gen.generateCode( blockTree.getConfig() );
        
        final JTextArea area = new JTextArea();
        final int size = area.getFont().getSize();
        area.setFont(new Font( Font.MONOSPACED , Font.PLAIN, size ) );
        area.setRows( 20 );
        area.setColumns( 80 );
        
        area.setText( text.toString() );
        
        final JFrame frame = new JFrame("Generated code");
        
        frame.getContentPane().setLayout( new BorderLayout() );
        frame.getContentPane().add( new JScrollPane( area ) , BorderLayout.CENTER );
        frame.setDefaultCloseOperation( JFrame.DISPOSE_ON_CLOSE );
        frame.pack();
        frame.setLocationRelativeTo( null );
        frame.setVisible( true );
    }
    
    private void saveTextureAtlas() throws IOException 
    {
        final Path path = selectFile( true , currentAtlasFile );
        if ( path == null ) {
            return;
        }
        
        final BlockConfigTextureResolver resolver = new BlockConfigTextureResolver( blockTree.getConfig() );
        final BufferedImage atlas = new TextureAtlasBuilder().build( blockTree.getConfig() , resolver , false );

        // save atlas
        try ( OutputStream out = new FileOutputStream( path.toFile() ) ) 
        {
            ImageIO.write( atlas , "png" , out );
        }
        
        currentAtlasFile = path;
        
        // show preview
        final JFrame preview = new JFrame("preview");
        preview.setDefaultCloseOperation( JFrame.DISPOSE_ON_CLOSE );        
        final JPanel panel = new JPanel() 
        {
            @Override
            protected void paintComponent(Graphics g) 
            {
                g.drawImage( atlas , 0 , 0 , null );
            }
        };
        panel.setPreferredSize( new Dimension( atlas.getWidth() , atlas.getHeight() ) );
        final JScrollPane pane = new JScrollPane( panel );
        
        preview.getContentPane().setLayout( new BorderLayout() );
        preview.getContentPane().add( pane , BorderLayout.CENTER );
        
        preview.pack();
        preview.setLocationRelativeTo( null );
        preview.setVisible( true );
    }
    
    private void newConfiguration() 
    {
        this.currentFile = null;
        this.currentAtlasFile = null;
        setConfiguration( createNewConfig() );
    }
    
    private void loadBlockConfig(boolean alwaysAsk) 
    {
        final Path path;
        if ( currentFile == null || alwaysAsk ) {
            path = selectFile(false , currentFile );
        } else {
            path = currentFile;
        }
        if ( path != null ) 
        {
            try ( InputStream out = new FileInputStream( path.toFile() ) ) 
            {
                blockTree.setConfig( new BlockConfigReader().read(  out ) );
                currentFile=path;
            } catch (IOException e) {
                e.printStackTrace();
            }            
        }
    }
    
    private void saveBlockConfig(boolean alwaysAsk) 
    {
        final Path path;
        if ( currentFile == null || alwaysAsk ) {
            path = selectFile(true , currentFile );
        } else {
            path = currentFile;
        }
        if ( path != null )
        {
            try ( OutputStream out = new FileOutputStream( path.toFile() ) ) 
            {
                new BlockConfigWriter().write( this.blockTree.getConfig() , out );
                currentFile=path;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }   
    
    private Path selectFile(boolean save,Path currentFile) {
        
        final JFileChooser chooser = new JFileChooser();
        if ( currentFile != null ) {
            chooser.setSelectedFile( currentFile.toFile() );
        }
        chooser.setFileFilter( new FileFilter() {
            
            @Override
            public String getDescription() {
                return ".xml";
            }
            
            @Override
            public boolean accept(File f) {
                return f.isDirectory() || f.getName().toLowerCase().endsWith(".xml");
            }
        });
        final int result;
        if ( save ) {
            result = chooser.showSaveDialog( null );
        } else {
            result = chooser.showOpenDialog( null );
        }
        if ( result == JFileChooser.APPROVE_OPTION ) {
            return  chooser.getSelectedFile().toPath();
        } 
        return null;
    }
    
    private void quit() 
    {
        System.exit(0);
    }        
    
    private JMenuItem menuItem(String label,Runnable action) 
    {
        final JMenuItem item = new JMenuItem(label);
        item.addActionListener( ev -> action.run() );
        return item;
    }
    
    private void treeSelectionChanged(MyTreeNode node) 
    {
        System.out.println("Selected: "+node.value);
        if ( node.value instanceof BlockDefinition ) 
        {
            final BlockDefinitionPanel newContent = new BlockDefinitionPanel();
            newContent.setTextureResolver( new BlockConfigTextureResolver( blockTree.getConfig() ) );
            newContent.setModel( (BlockDefinition) node.value );
            newContent.setValueChangedListener( blockTree );
            changeContent( newContent );
        } 
        else if ( node.value instanceof BlockSideDefinition ) 
        {
            final BlockSideDefinitionPanel newContent = new BlockSideDefinitionPanel();
            newContent.setTextureResolver( new BlockConfigTextureResolver( blockTree.getConfig() ) );
            newContent.setModel( (BlockSideDefinition) node.value );
            newContent.setValueChangedListener( blockTree );
            changeContent( newContent ); 
        } 
        else if ( node.value instanceof BlockConfig) 
        { 
            final BlockConfigPanel newContent = new BlockConfigPanel();
            newContent.setModel( (BlockConfig) node.value );
            changeContent( newContent );
        } 
        else 
        {
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
        def.opaque = true;
        def.emitsLight=false;
        
        blockTree.getConfig().add( def );
        blockTree.add( def );
        blockTree.setSelection( def );
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
    
    private void setConfiguration(BlockConfig config) {
        blockTree.setConfig ( config );
    }
    
    private BlockConfig createNewConfig() 
    {
        final BlockConfig config = new BlockConfig();
        config.baseDirectory = "/home/tobi/mars_workspace/voxel-engine/assets";
        config.textureAtlasSize = 1024;
        config.blockTextureSize = 32;
        return config;
    }

    private void run(String inputFile) throws IOException 
    {
        final BlockConfig config;
        if ( inputFile == null ) 
        {
            config = createNewConfig();
        } 
        else 
        {
            this.currentFile = Paths.get( inputFile );
            config = new BlockConfigReader().read( currentFile );
        }
        setConfiguration( config );
    }
}