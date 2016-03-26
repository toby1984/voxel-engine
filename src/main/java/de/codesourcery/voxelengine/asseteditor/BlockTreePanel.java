package de.codesourcery.voxelengine.asseteditor;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

import org.apache.commons.lang3.StringUtils;

public class BlockTreePanel extends JPanel implements IValueChangedListener
{
    private final MyTreeModel treeModel = new MyTreeModel();
    private final JTree tree = new JTree( treeModel );

    private ISelectionListener<MyTreeNode> selectionListener = node -> {};
    
    protected final Comparator<MyTreeNode> itemComparator = Comparator.nullsFirst( 
            Comparator.comparing( (MyTreeNode node)-> ((ItemDefinition) node.value).name , String::compareTo ) 
    );
    
    protected Comparator<MyTreeNode>  blockOrdering = Comparator.nullsFirst( 
            Comparator.comparing( (MyTreeNode node)-> ((BlockDefinition) node.value).name , String::compareTo ) 
    );
    
    public static enum NodeCategory 
    {
        ATLASES("Atlases"),
        BLOCKS("Blocks"),
        ITEMS("Items");
        
        public final String title;

        private NodeCategory(String title) {
            this.title = title;
        }
    }
    
    protected static final class MyTreeNode 
    {
        private final List<MyTreeNode> childCollection= new ArrayList<>();
        public MyTreeNode parent;
        public Object value;
        
        public MyTreeNode() {
        }
        
        public List<MyTreeNode> children() {
            return childCollection;
        }
        
        public MyTreeNode(Object value) {
            this.value = value;
        }
        
        public TreePath parentTreePath() {
            return parent.treePath();
        }
        
        public int childCount() {
            return childCollection.size();
        }
        
        private String prettyPrint() {
            return prettyPrint(0);
        }
        
        private String prettyPrint(int depth) 
        {
            final String result = StringUtils.repeat(' ' , depth*2)+this.value+"\n";
            return result + childCollection.stream().map( child -> child.prettyPrint(depth+1 ) ).collect( Collectors.joining("\n"));
        }
        
        @Override
        public String toString() {
            return "MyTreeNode[ "+value+" ]";
        }
        
        public boolean hasValue(Class<?> clazz) 
        {
            return value != null && value.getClass() == clazz;
        }
        
        public <T> void sortChildren(Function<MyTreeNode,T> extractor, Comparator<T> comp) 
        {
            Collections.sort( childCollection , new Comparator<MyTreeNode>() {

                @Override
                public int compare(MyTreeNode o1, MyTreeNode o2) 
                {
                    final T v1 = extractor.apply(o1);
                    final T v2 = extractor.apply(o1);
                    if ( v1 != null && v2 != null ) {
                        return comp.compare( v1 , v2 );
                    }
                    if ( v1 == v2 ) {
                        return 0;
                    }
                    return v1 == null ? -1 : 1;
                }
            });
        }
        
        public TreePath treePath() 
        {
            final List<Object> elements = new ArrayList<>();
            MyTreeNode current = this;
            while ( current != null ) {
                elements.add( current );
                current = current.parent;
            }
            Collections.reverse( elements );
            return new TreePath( elements.toArray(new Object[0]) );
        }
        
        public int indexOf(MyTreeNode child) {
            return childCollection.indexOf( child );
        }
        
        public int childIndex() {
            return parent.indexOf( this );
        }
        
        public MyTreeNode child(int idx) {
            return childCollection.get(idx);
        }
        
        public MyTreeNode firstChild() {
            return child(0);
        }
        
        public boolean hasChildren() { return ! childCollection.isEmpty(); }
        
        public MyTreeNode findValue(Object expected) {
            if ( expected == null ) {
                throw new IllegalArgumentException("Won't search for NULL values");
            }
            if ( this.value == expected ) {
                return this;
            }
            for ( MyTreeNode child : childCollection ) {
                final MyTreeNode result = child.findValue( expected );
                if ( result != null ) {
                    return result;
                }
            }
            return null;
        }
        
        public MyTreeNode findNode(MyTreeNode expected) 
        {
            if ( expected == null ) {
                throw new IllegalArgumentException("Won't search for NULL values");
            }
            if ( this == expected ) {
                return this;
            }
            for ( MyTreeNode child : childCollection ) {
                final MyTreeNode result = child.findNode( expected );
                if ( result != null ) {
                    return result;
                }
            }
            return null;
        }        
        
        public int add(MyTreeNode child) {
            final int idx = childCollection.size();
            return add(child,idx);
        }
        
        public int add(MyTreeNode child,int idx) {
            childCollection.add( idx , child );
            child.parent = this;
            return idx;
        }        
        
        public int remove(MyTreeNode child) 
        {
            final int idx = childCollection.indexOf( child );
            if ( idx == -1 ) {
                throw new IllegalArgumentException("Failed to remove "+child+" from "+this);
            }
            childCollection.remove( idx );
            return idx;
        }
    }
    
    protected final class MyTreeModel implements TreeModel, IValueChangedListener
    {
        private AssetConfig config = new AssetConfig();
        private MyTreeNode root = new MyTreeNode();
        
        private final List<TreeModelListener> listeners = new ArrayList<>();
        
        @Override
        public MyTreeNode getRoot() {
            return root;
        }
        
        public void populateFrom(AssetConfig config) 
        {
            this.root = createTree( config );
            this.config = config;
            structureChanged();
        }
        
        private MyTreeNode createTree(AssetConfig config) 
        {
            final MyTreeNode root = new MyTreeNode();
            root.value = config;
            
            // add atlases
            final MyTreeNode atlases = new MyTreeNode( NodeCategory.ATLASES );
            root.add( atlases );
            config.getTextureAtlasConfigs().forEach( atlasConfig -> 
            {
                atlases.add( new MyTreeNode( atlasConfig ) );
            });
            atlases.sortChildren( node -> ((TextureAtlasConfig) node.value).category.name , String::compareTo );
            
            // add blocks
            final MyTreeNode blocks = new MyTreeNode( NodeCategory.BLOCKS );
            root.add( blocks );
            add( config.blocks , blocks );
            
            // add items
            final MyTreeNode items = new MyTreeNode( NodeCategory.ITEMS );
            root.add( items );
            addItems( config.items , items );
            
            return root;
        }
        
        public MyTreeNode findValue(Object expected ) {
            return root.findValue( expected );
        }
        
        public void remove(MyTreeNode node) 
        {
            if ( node == root ) {
                throw new IllegalArgumentException("Cannot remove root node");
            }
            MyTreeNode toRemove = root.findNode( node );
            if ( toRemove == null ) {
                throw new IllegalStateException("Trying to remove node "+node+" which is not part of this tree");
            }
            removeNode( toRemove.parent , toRemove );
        }
        
        public void add( Collection<BlockDefinition> defs , MyTreeNode parent ) 
        {
            defs.forEach( def -> parent.add( createTree( def ) ) );
            Collections.sort( parent.children() , blockOrdering );
        }
        
        public void addItems( Collection<ItemDefinition> defs , MyTreeNode parent ) 
        {
            defs.forEach( def -> parent.add( new MyTreeNode(def) ) );
            Collections.sort( parent.children() , itemComparator );
        }        
        
        private MyTreeNode createTree(BlockDefinition blockDef) 
        {
            final MyTreeNode block = new MyTreeNode();
            block.value = blockDef;
            Arrays.stream( blockDef.sides ).forEach( sideDef -> 
            {
                final MyTreeNode side = new MyTreeNode();
                side.value = sideDef;
                block.add( side );
            });
            return block;
        }

        @Override
        public MyTreeNode getChild(Object parent, int index) {
            return ((MyTreeNode) parent).child(index);
        }

        @Override
        public int getChildCount(Object parent) {
            return ((MyTreeNode) parent).children().size();
        }

        @Override
        public boolean isLeaf(Object node) {
            return ((MyTreeNode) node).children().isEmpty();
        }

        @Override
        public void valueForPathChanged(TreePath path, Object newValue) 
        {
            treeNodeChanged( path , false );
        }
        
        public void structureChanged() {
            structureChanged( new TreeModelEvent( this , new Object[] { root } ) );
        }
        
        public void structureChanged(TreeModelEvent ev) {
            listeners.forEach( l -> l.treeStructureChanged( ev ) );
        }        
        
        private void treeNodeChanged(TreePath path,boolean includingChildren) 
        {
            final MyTreeNode node = (MyTreeNode) path.getLastPathComponent();
            
            if ( includingChildren ) 
            {
                // signal parent changed
                final TreeModelEvent ev1 = new TreeModelEvent( this , node.parentTreePath() , new int[] { node.childIndex() } , new Object[] { node } );
                listeners.forEach( l -> l.treeNodesChanged( ev1 ) );
                
                // signal children changed
                final int len = node.childCount();
                final int[] childIndices = new int[ len ];
                final Object[] changedChildren = new Object[ len ];
                for ( int i = 0  ; i < len ; i++ ) {
                    childIndices[i] = i;
                    changedChildren[i] = node.child(i);
                }                
                final TreeModelEvent ev2 = new TreeModelEvent( this , node.treePath() , childIndices , changedChildren );
                listeners.forEach( l -> l.treeNodesChanged( ev2 ) );
            } else 
            {
                final TreeModelEvent ev;
                if ( node == root ) {
                    ev = new TreeModelEvent( this , node.treePath() );
                } else {
                    ev = new TreeModelEvent( this , node.parentTreePath() , new int[] { node.childIndex() } , new Object[] { node } );
                }
                listeners.forEach( l -> l.treeNodesChanged( ev ) );
            }
        }
        
        public void addNode(MyTreeNode parent,MyTreeNode child) 
        {
            final int idx = parent.add( child );
            final TreeModelEvent ev = new TreeModelEvent( this , parent.treePath() , new int[] { idx } , new Object[] { child } );
            listeners.forEach( l -> l.treeNodesInserted( ev ) );
        }
        
        public void removeNode(MyTreeNode parent,MyTreeNode child) 
        {
            final int idx = parent.remove( child );
            final TreeModelEvent ev = new TreeModelEvent( this , parent.treePath() , new int[] { idx } , new Object[] { child } );
            listeners.forEach( l -> l.treeNodesRemoved( ev ) );
        }        

        @Override
        public int getIndexOfChild(Object parent, Object child) {
            return ((MyTreeNode) parent).children().indexOf( child );
        }

        @Override
        public void addTreeModelListener(TreeModelListener l) {
            listeners.add(l);
        }

        @Override
        public void removeTreeModelListener(TreeModelListener l) {
            listeners.remove(l);
        }
        
        public void add(ItemDefinition item) 
        {
            final MyTreeNode newNode = new MyTreeNode(item);
            final MyTreeNode parent = findValue( NodeCategory.ITEMS );

            addOrdered(parent,newNode, itemComparator );
        }

        public void add(BlockDefinition block) 
        {
            final MyTreeNode newNode = createTree( block );
            final MyTreeNode parent = findValue( NodeCategory.BLOCKS );

            addOrdered(parent,newNode,blockOrdering);
        }
        
        private void addOrdered(MyTreeNode parent,MyTreeNode newNode,Comparator<MyTreeNode> comp) 
        {
            int newIndex = -1;
            int i = 0;
outer:            
            for ( final int len = parent.childCount() ; i < len ; i++ ) 
            {
                final int cmp = comp.compare( parent.child(i) , newNode );
                if ( cmp == 0 ) 
                {
                    i++;
                    while ( i < len )
                    {
                        if ( comp.compare( parent.child(i) , newNode ) != 0 ) 
                        {
                            newIndex = i-1;
                            break outer;
                        }
                        i++;
                    }
                    newIndex= parent.children().size();
                    break;
                } else if ( cmp < 0 ) {
                    newIndex = i-1;
                    break;
                }
            }
            if ( newIndex <= 0 ) { // append
                final int idx = parent.add( newNode );
                final TreeModelEvent ev = new TreeModelEvent(this, parent.treePath() , new int[] { idx } , new Object[] { newNode } );
                listeners.forEach( l -> l.treeNodesInserted( ev ) );
            } else { // insert
                final int idx = parent.add( newNode , newIndex );
                final TreeModelEvent ev = new TreeModelEvent(this, parent.treePath() , new int[] { idx } , new Object[] { newNode } );
                listeners.forEach( l -> l.treeNodesInserted( ev ) );
            }
        }        
        
        @Override
        public void valueChanged(Object value, boolean childrenChangedAsWell) 
        {
            final MyTreeNode node = root.findValue( value );
            if ( node != null ) {
                
                final boolean expanded = tree.isExpanded( node.treePath() );
                treeNodeChanged( node.treePath() , childrenChangedAsWell );
                
                if ( expanded ) 
                {
                    if ( node.hasChildren() ) {
                        tree.expandPath( node.firstChild().treePath() );
                    } else {
                        tree.expandPath( node.treePath() );
                    }
                }
            } else {
                System.err.println("Failed to find changed node");
            }
        }        
    }
    
    public BlockTreePanel() 
    {
        setLayout( new GridBagLayout() );
        
        tree.setRootVisible(true);
        tree.setCellRenderer( new MyCellRenderer() );
        tree.setModel( treeModel );
        tree.setExpandsSelectedPaths( true );
        tree.setVisibleRowCount( 25 );
        tree.setPreferredSize( new Dimension(150,400 ) );
        
        tree.addTreeSelectionListener( new TreeSelectionListener() {
            
            @Override
            public void valueChanged(TreeSelectionEvent e) 
            {
                if ( e.getPath() != null ) 
                {
                    selectionListener.selectionChanged( (MyTreeNode) e.getPath().getLastPathComponent() );
                } else {
                    selectionListener.selectionChanged( null );
                }
            }
        });
        
        tree.addKeyListener( new KeyAdapter() 
        {
            @Override
            public void keyReleased(KeyEvent e) 
            {
                final MyTreeNode selection = tree.getSelectionPath() == null ? null : (MyTreeNode) tree.getSelectionPath().getLastPathComponent();
                if ( e.getKeyCode() == KeyEvent.VK_DELETE ) 
                {
                    if ( selection != null && canDelete( selection ) ) {
                        delete( selection );
                    }
                }
            }
        });
        
        tree.addMouseListener( new MouseAdapter() 
        {
            public void mousePressed(MouseEvent e) {
                maybeShowPopup(e);
            }

            public void mouseReleased(MouseEvent e) {
                maybeShowPopup(e);
            }

            private void maybeShowPopup(MouseEvent e) 
            {
                if (e.isPopupTrigger()) 
                {
                    TreePath path = tree.getClosestPathForLocation( e.getX(), e.getY() );
                    if ( path != null ) 
                    {
                        final JPopupMenu menu = createPopupMenu( (MyTreeNode) path.getLastPathComponent() );
                        if ( menu != null ) {
                            menu.show(e.getComponent(),e.getX(), e.getY());
                        }
                    }
                }
            }            
        });
        
        GridBagConstraints cnstrs = new GridBagConstraints();
        cnstrs.weightx = 1.0; cnstrs.weighty = 1.0;
        cnstrs.gridx = 0 ; cnstrs.gridy = 0;
        cnstrs.gridheight = 1 ; cnstrs.gridwidth = 1;
        cnstrs.fill = GridBagConstraints.HORIZONTAL;        
        add( new JScrollPane( tree ) , cnstrs );
    }
    
    public void setConfig(AssetConfig config) 
    {
        treeModel.populateFrom( config );
        tree.clearSelection();
        selectionListener.selectionChanged( null );
    }
    
    public AssetConfig getConfig()
    {
        return treeModel.config;
    }
    
    protected static final class MyCellRenderer extends DefaultTreeCellRenderer 
    {
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value,
                boolean sel, boolean expanded, boolean leaf, int row,
                boolean hasFocus) 
        {
            final Component result = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
            if ( value instanceof MyTreeNode ) 
            {
                final MyTreeNode node = (MyTreeNode) value;
                if ( node.hasValue( NodeCategory.class ) ) 
                {
                    setText( ((NodeCategory) node.value).title);
                } 
                else if ( node.hasValue( BlockDefinition.class ) ) 
                {
                    final BlockDefinition blockDef = (BlockDefinition ) node.value;
                    setText( "["+blockDef.blockType+"] "+blockDef.name);
                } 
                else if ( node.hasValue( AssetConfig.class ) ) 
                {
                  setText("Assets");  
                } 
                else if ( node.hasValue( BlockSideDefinition.class ) ) 
                {
                    final BlockSideDefinition sideDef = (BlockSideDefinition) node.value;
                    setText( sideDef.side.xmlName.toUpperCase()+" - "+sideDef.getInputTexture() );
                }                 
                else if ( node.hasValue( ItemDefinition.class ) ) 
                {
                    final ItemDefinition def = (ItemDefinition) node.value;
                    setText( "["+def.itemId+"] "+def.name );
                } 
                else if ( node.hasValue( TextureAtlasConfig.class ) ) 
                {
                    final TextureAtlasConfig config = (TextureAtlasConfig) node.value;
                    if ( StringUtils.isNotBlank( config.outputName ) ) {
                        setText( config.category.name+ " - "+config.outputName );
                    } else {
                        setText( config.category.name );
                    }
                }
            }
            return result;
        }
    }
    
    private JPopupMenu createPopupMenu(MyTreeNode node) 
    {
        final List<JMenuItem> items = new ArrayList<>();
        if ( canDelete( node ) ) 
        {
            items.add( menuItem("Delete" , () -> delete(node) ) );
        }
        if ( items.isEmpty() ) {
            return null;
        }
        final JPopupMenu  result = new JPopupMenu ();
        items.forEach( result::add );
        return result;
    }
    
    private static JMenuItem menuItem(String label,Runnable action) 
    {
        final JMenuItem item = new JMenuItem(label);
        item.addActionListener( ev -> action.run() ); 
        return item;
    }
    
    public void setSelectionListener(ISelectionListener<MyTreeNode> selectionListener) 
    {
        this.selectionListener = selectionListener;
    }
    
    private void delete(MyTreeNode node) 
    {
        if ( ! canDelete( node ) ) {
            throw new RuntimeException("Internal error, deleting "+node+" is not allowed");
        }
        final boolean selectionDeleted = tree.getSelectionPath() != null && tree.getSelectionPath().getLastPathComponent() == node;
        treeModel.remove( node );
        if ( selectionDeleted ) {
            selectionListener.selectionChanged( null );
        }
    }
    
    private boolean canDelete(MyTreeNode node) {
        return node.hasValue( BlockDefinition.class ) || node.hasValue( ItemDefinition.class );
    }
    
    public void add(BlockDefinition block) 
    {
        treeModel.add( block );
    }
    
    public void add(ItemDefinition item) 
    {
        treeModel.add( item );
    }    

    @Override
    public void valueChanged(Object value, boolean childrenChangedAsWell) 
    {
        treeModel.valueChanged(value, childrenChangedAsWell);
    }
    
    public void clearSelection() {
        tree.clearSelection();
        selectionListener.selectionChanged( null );
    }

    public void setSelection(Object def) 
    {
        final MyTreeNode node = treeModel.findValue( def );
        if ( node != null ) 
        {
            final MyTreeNode toSelect = node.children().isEmpty() ? node : node.firstChild();
            tree.setSelectionPath( toSelect.treePath() );
            tree.scrollPathToVisible( toSelect.treePath() );
        } else {
            System.err.println("setSelection("+def+") failed to find node holding this value");
        }
    }
}