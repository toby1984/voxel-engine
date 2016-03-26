package de.codesourcery.voxelengine.asseteditor;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
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

import de.codesourcery.voxelengine.asseteditor.AssetConfig.CodeGenConfig;
import de.codesourcery.voxelengine.asseteditor.AssetConfig.SourceType;
import de.codesourcery.voxelengine.asseteditor.BlockTreePanel.MyTreeNode;

public class EditorMain extends JFrame 
{
    public static final Path APP_BASE_DIR = Paths.get("/home/tobi/mars_workspace/voxel-engine");
    
    public static final Path ASSETS_FOLDER = APP_BASE_DIR.resolve( "assets" );
    public static final Path TEXTURES_FOLDER = ASSETS_FOLDER.resolve( "textures" );
    public static final Path ATLAS_OUTPUT_FILE = TEXTURES_FOLDER.resolve( "blocks_atlas.png" );
    public static final Path BLOCKS_FILE = ASSETS_FOLDER.resolve( "blocks.xml" );
    
    private static final boolean PREVIEW_ATLASES = false;
    
    private final JPanel contentPanel = new JPanel();
    private JPanel content;
    
    private Path currentAtlasFile = ATLAS_OUTPUT_FILE;
    private Path currentFile = BLOCKS_FILE;
    
    private final BlockTreePanel blockTree = new BlockTreePanel();
    
    public static void main(String[] args) throws IOException 
    {
        final EditorMain main = new EditorMain();
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
    
    public EditorMain() 
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
        menu1.add( menuItem("Load asset configuration ..." , () -> loadAssetConfig(true) ) );
        menu1.add( menuItem("Save asset configuration" , () -> saveAssetConfig(false) ) );
        menu1.add( menuItem("Save asset configuration as ..." , () -> saveAssetConfig(true) ) );
        menu1.add( menuItem("Generate code..." , () -> generateCode() ) );
        menu1.add( menuItem("Generate texture atlases..." , () -> 
        { 
            try {
                generateTextureAtlas();
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
        final AssetConfig config = blockTree.getConfig();
        final AssetConfigTextureResolver resolver = new AssetConfigTextureResolver( config );
        if ( ! config.isValid( resolver ) ) 
        {
            throw new RuntimeException("Cannot generate code for invalid config");
        }
        
        // make sure we have valid texture coordinates
        // TODO: This is a redundant step if the user already re-generated the texture atlases 
        try {
            new TextureAtlasBuilder().assignTextureCoordinates( blockTree.getConfig() , resolver );
        } 
        catch (IOException e) 
        {
            throw new RuntimeException(e);
        }
        
        generateCode( config , SourceType.BLOCKS , new BlockTypeCodeGenerator() );
        generateCode( config , SourceType.ITEMS , new ItemTypeCodeGenerator() );
    }
    
    private void generateCode(AssetConfig config,AssetConfig.SourceType type , CodeGenerator gen) 
    {
        final CodeGenConfig codeGenConfig = config.getCodeGenConfig( type );
        if ( ! codeGenConfig.isValid() ) {
            System.err.println("generateCode() failed for "+type+" , configuration is invalid");
            return;
        }
        
        gen.configure( codeGenConfig );

        final File file = codeGenConfig.getFullPath();
        final CharSequence text = gen.generateCode( config );
        try ( PrintWriter out = new PrintWriter( new FileOutputStream( file ) ) ) 
        {
            out.write( text.toString() );
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        System.out.println("Code written to "+file.getAbsolutePath());
        
        final JTextArea area = new JTextArea();
        final int size = area.getFont().getSize();
        area.setFont(new Font( Font.MONOSPACED , Font.PLAIN, size ) );
        area.setRows( 20 );
        area.setColumns( 80 );
        
        area.setText( text.toString() );
        
        final JFrame frame = new JFrame("Generated "+type+" code");
        
        frame.getContentPane().setLayout( new BorderLayout() );
        frame.getContentPane().add( new JScrollPane( area ) , BorderLayout.CENTER );
        
        final JButton closeButton = new JButton("Close");
        closeButton.addActionListener( ev -> frame.dispose() );
        frame.getContentPane().add( closeButton , BorderLayout.SOUTH);
        
        frame.setDefaultCloseOperation( JFrame.DISPOSE_ON_CLOSE );
        frame.pack();
        frame.setLocationRelativeTo( null );
        frame.setVisible( true );
    }
    
    private void generateTextureAtlas() throws IOException 
    {
        final Path path = selectAtlasFile( true , currentAtlasFile );
        if ( path == null ) {
            return;
        }
        
        final AssetConfigTextureResolver resolver = new AssetConfigTextureResolver( blockTree.getConfig() );
        final BufferedImage atlas = new TextureAtlasBuilder().buildBlockTextureAtlas( blockTree.getConfig() , resolver , null );

        // save atlas
        try ( OutputStream out = new FileOutputStream( path.toFile() ) ) 
        {
            ImageIO.write( atlas , "png" , out );
        }
        
        System.out.println("Texture atlas written to "+path.getFileName());
        
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
        
        final JButton closeButton = new JButton("Close");
        closeButton.addActionListener( ev -> preview.dispose() );
        preview.getContentPane().add( closeButton , BorderLayout.SOUTH);
        
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
    
    private void loadAssetConfig(boolean alwaysAsk) 
    {
        final Path path;
        if ( currentFile == null || alwaysAsk ) {
            path = selectXmlFile(false , currentFile );
        } else {
            path = currentFile;
        }
        if ( path != null ) 
        {
            try ( InputStream out = new FileInputStream( path.toFile() ) ) 
            {
                blockTree.setConfig( new BlockConfigReader().read(  out ) );
                currentFile=path;
                System.out.println("Block definitions loaded from "+path.getFileName());
            } catch (IOException e) {
                e.printStackTrace();
            }            
        }
    }
    
    private void saveAssetConfig(boolean alwaysAsk) 
    {
        final Path path;
        if ( currentFile == null || alwaysAsk ) {
            path = selectXmlFile(true , currentFile );
        } else {
            path = currentFile;
        }
        if ( path != null )
        {
            try ( OutputStream out = new FileOutputStream( path.toFile() ) ) 
            {
                new BlockConfigWriter().write( this.blockTree.getConfig() , out );
                currentFile=path;
                System.out.println("Block definitions saved to "+path.getFileName());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }   
    
    private Path selectAtlasFile(boolean save,Path currentFile) {
        
        final FileFilter filter =  new FileFilter() {
            
            @Override
            public String getDescription() {
                return ".png";
            }
            
            @Override
            public boolean accept(File f) {
                return f.isDirectory() || f.getName().endsWith(".png");
            }
        };
        return selectFile(save,currentFile,filter );
    }    
    
    private Path selectXmlFile(boolean save,Path currentFile) {
     
        final FileFilter filter =  new FileFilter() {
            
            @Override
            public String getDescription() {
                return ".xml";
            }
            
            @Override
            public boolean accept(File f) {
                return f.isDirectory() || f.getName().toLowerCase().endsWith(".xml");
            }
        };
        return selectFile(save,currentFile,filter );
    }
    
    private Path selectJavaFile(boolean save,Path currentFile) {
        
        final FileFilter filter =  new FileFilter() {
            
            @Override
            public String getDescription() {
                return ".java";
            }
            
            @Override
            public boolean accept(File f) {
                return f.isDirectory() || f.getName().endsWith(".java");
            }
        };
        return selectFile(save,currentFile,filter );
    }
    
    private Path selectFile(boolean save,Path currentFile,FileFilter filter) {
        
        final JFileChooser chooser = new JFileChooser();
        if ( currentFile != null ) {
            chooser.setCurrentDirectory( currentFile.getParent().toFile() );
            chooser.setSelectedFile( currentFile.toFile() );
        }
        chooser.setFileFilter( filter );
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
        if ( node == null || node.value == null ) {
            changeContent( new JPanel() );
            return;
        }
        
        final FormPanel<?> formPanel;
        if ( node.hasValue(BlockDefinition.class) ) 
        {
            final BlockDefinitionPanel newContent = new BlockDefinitionPanel();
            newContent.setTextureResolver( new AssetConfigTextureResolver( blockTree.getConfig() ) );
            newContent.setModel( (BlockDefinition) node.value );
            formPanel = newContent;
        } 
        else if ( node.hasValue(BlockSideDefinition.class) ) 
        {
            final BlockSideDefinitionPanel newContent = new BlockSideDefinitionPanel();
            newContent.setTextureResolver( new AssetConfigTextureResolver( blockTree.getConfig() ) );
            newContent.setModel( (BlockSideDefinition) node.value );
            formPanel = newContent;
        } 
        else if ( node.hasValue(TextureAtlasConfig.class) ) 
        { 
            final TextureAtlasConfigPanel newContent = new TextureAtlasConfigPanel();
            newContent.setModel( (TextureAtlasConfig) node.value );
            formPanel = newContent;            
        }         
        else if ( node.hasValue(AssetConfig.class) ) 
        { 
            final AssetConfigPanel newContent = new AssetConfigPanel();
            newContent.setModel( (AssetConfig) node.value );
            formPanel = newContent;
        } 
        else if ( node.hasValue(ItemDefinition.class) ) 
        { 
            final ItemDefinitionPanel newContent = new ItemDefinitionPanel( blockTree.getConfig() );
            newContent.setTextureResolver( new AssetConfigTextureResolver( blockTree.getConfig() ) );
            newContent.setModel( (ItemDefinition) node.value );
            formPanel = newContent;
        } else {
            // do nothing
            return;
        }
        formPanel.setValueChangedListener( blockTree );
        changeContent( formPanel );
    }
    
    private JToolBar createToolbar() 
    {
        final JToolBar result = new JToolBar(JToolBar.HORIZONTAL);
        result.add( button("Add block" , this::addBlock ) );
        result.add( button("Add item" , this::addItem ) );
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
    
    private void addItem() 
    {
        final ItemDefinition def = new ItemDefinition();
        def.itemId = blockTree.getConfig().nextAvailableItemId();
        def.name = "";
        def.canCreateBlock=false;
        def.canDestroyBlock=false;
        
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
    
    private void setConfiguration(AssetConfig config) {
        blockTree.setConfig ( config );
    }
    
    private AssetConfig createNewConfig() 
    {
        final AssetConfig config = new AssetConfig();
        
        config.baseDirectory = EditorMain.TEXTURES_FOLDER.toFile().getAbsolutePath();
        
        config.blockAtlas().textureAtlasSize = 1024;
        config.blockAtlas().textureSize = 32;
        
        config.itemAtlas().textureAtlasSize = 1024;
        config.itemAtlas().textureSize = 32;
        
        return config;
    }

    private void run(String inputFile) throws IOException 
    {
        final AssetConfig config;
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