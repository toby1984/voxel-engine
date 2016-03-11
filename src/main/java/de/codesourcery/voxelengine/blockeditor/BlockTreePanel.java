package de.codesourcery.voxelengine.blockeditor;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

public class BlockTreePanel extends JPanel implements IValueChangedListener<Object>
{
    private final MyTreeModel treeModel = new MyTreeModel();
    private final JTree tree = new JTree( treeModel );

    private ISelectionListener<MyTreeNode> selectionListener = node -> {};
    
    protected Comparator<MyTreeNode> blockOrdering = (nodeA,nodeB) -> 
    {
        final BlockDefinition a = (BlockDefinition) nodeA.value;
        final BlockDefinition b = (BlockDefinition) nodeB.value;
        final String n1 = a.name == null ? "" : a.name;
        final String n2 = b.name == null ? "" : b.name;
        return n1.compareTo(n2);
    };
    
    protected static final class MyTreeNode 
    {
        public final List<MyTreeNode> children = new ArrayList<>();
        public MyTreeNode parent;
        public Object value;
        
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
        
        public MyTreeNode findValue(Object expected) {
            if ( expected == null ) {
                throw new IllegalArgumentException("Won't search for NULL values");
            }
            if ( this.value == expected ) {
                return this;
            }
            for ( MyTreeNode child : children ) {
                final MyTreeNode result = child.findValue( expected );
                if ( result != null ) {
                    return child;
                }
            }
            return null;
        }
        
        public int add(MyTreeNode child) {
            final int idx = children.size();
            return add(child,idx);
        }
        
        public int add(MyTreeNode child,int idx) {
            children.add( idx , child );
            child.parent = this;
            return idx;
        }        
        
        public int remove(MyTreeNode child) 
        {
            final int idx = children.indexOf( child );
            if ( idx == -1 ) {
                throw new IllegalArgumentException("Failed to remove "+child+" from "+this);
            }
            children.remove( idx );
            return idx;
        }
    }
    
    protected final class MyTreeModel implements TreeModel, IValueChangedListener<Object>
    {
        private BlockConfig config = new BlockConfig();
        private MyTreeNode root = new MyTreeNode();
        
        private final List<TreeModelListener> listeners = new ArrayList<>();
        
        @Override
        public MyTreeNode getRoot() {
            return root;
        }
        
        public void populateFrom(BlockConfig config) 
        {
            this.root = createTree( config );
            this.config = config;
            structureChanged();
        }
        
        private MyTreeNode createTree(BlockConfig config) 
        {
            final MyTreeNode root = new MyTreeNode();
            config.blocks.values().forEach( this::createTree ); 
            Collections.sort( root.children , blockOrdering );
            return root;
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
            return ((MyTreeNode) parent).children.get(index);
        }

        @Override
        public int getChildCount(Object parent) {
            return ((MyTreeNode) parent).children.size();
        }

        @Override
        public boolean isLeaf(Object node) {
            return ((MyTreeNode) node).children.isEmpty();
        }

        @Override
        public void valueForPathChanged(TreePath path, Object newValue) 
        {
            treeNodeChanged( path );
        }
        
        public void structureChanged() {
            final TreeModelEvent ev = new TreeModelEvent( this , new Object[] { root } );
            listeners.forEach( l -> l.treeStructureChanged( ev ) );
        }
        
        private void treeNodeChanged(TreePath path) {
            final TreePath parentPath = path.getParentPath();
            final int idx = getIndexOfChild( parentPath.getLastPathComponent() , path.getLastPathComponent() );
            final TreeModelEvent ev = new TreeModelEvent( this , parentPath , new int[] { idx } , new Object[] { path.getLastPathComponent() } );
            listeners.forEach( l -> l.treeNodesChanged( ev ) );
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
            return ((MyTreeNode) parent).children.indexOf( child );
        }

        @Override
        public void addTreeModelListener(TreeModelListener l) {
            listeners.add(l);
        }

        @Override
        public void removeTreeModelListener(TreeModelListener l) {
            listeners.remove(l);
        }

        public void add(BlockDefinition block) 
        {
            final MyTreeNode newNode = createTree( block );
            
            int newIndex = -1;
            int i = 0;
outer:            
            for ( ; i < root.children.size() ; i++ ) 
            {
                final int cmp = blockOrdering.compare( root.children.get(i) , newNode );
                if ( cmp == 0 ) 
                {
                    i++;
                    while ( i < root.children.size() )
                    {
                        if ( blockOrdering.compare( root.children.get(i) , newNode ) != 0 ) 
                        {
                            newIndex = i-1;
                            break outer;
                        }
                        i++;
                    }
                    newIndex= root.children.size();
                    break;
                } else if ( cmp < 0 ) {
                    newIndex = i-1;
                    break;
                }
            }
            if ( newIndex <= 0 ) { // append
                final int idx = root.add( newNode );
                final TreeModelEvent ev = new TreeModelEvent(this, root.treePath() , new int[] { idx } , new Object[] { newNode } );
                listeners.forEach( l -> l.treeNodesInserted( ev ) );
            } else { // insert
                final int idx = root.add( newNode , newIndex );
                final TreeModelEvent ev = new TreeModelEvent(this, root.treePath() , new int[] { idx } , new Object[] { newNode } );
                listeners.forEach( l -> l.treeNodesInserted( ev ) );
            }
        }
        
        @Override
        public void valueChanged(Object value) 
        {
            final MyTreeNode node = root.findValue( value );
            if ( node != null ) {
                System.err.println("Node value changed: "+node.treePath().getLastPathComponent());
                treeNodeChanged( node.treePath() );
            } else {
                System.err.println("Failed to find changed node");
            }
        }        
    }
    
    public BlockTreePanel() 
    {
        setLayout( new GridBagLayout() );
        
        GridBagConstraints cnstrs = new GridBagConstraints();
        cnstrs.weightx = 1.0; cnstrs.weighty = 1.0;
        cnstrs.gridx = 0 ; cnstrs.gridy = 0;
        cnstrs.gridheight = 1 ; cnstrs.gridwidth = 1;
        cnstrs.fill = GridBagConstraints.BOTH;
        
        tree.setRootVisible(true);
        tree.setCellRenderer( new MyCellRenderer() );
        tree.setModel( treeModel );
        tree.setPreferredSize( new Dimension(150,400 ) );
        tree.addTreeSelectionListener( new TreeSelectionListener() {
            
            @Override
            public void valueChanged(TreeSelectionEvent e) 
            {
                if ( e.getPath() != null ) 
                {
                    selectionListener.selectionChanged( (MyTreeNode) e.getPath().getLastPathComponent() );
                }
            }
        });
        
        add( new JScrollPane( tree ) , cnstrs );
    }
    
    public void setConfig(BlockConfig config) 
    {
        treeModel.populateFrom( config );
    }
    
    public BlockConfig getConfig()
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
                if ( node.value instanceof BlockDefinition ) 
                {
                    final BlockDefinition blockDef = (BlockDefinition ) node.value;
                    setText( "["+blockDef.blockType+"] "+blockDef.name);
                } 
                else if ( node.value instanceof BlockSideDefinition) 
                {
                    final BlockSideDefinition sideDef = (BlockSideDefinition) node.value;
                    setText( sideDef.side.xmlName.toUpperCase()+" - "+sideDef.inputTexture );
                }
            }
            return result;
        }
    }
    
    public void setSelectionListener(ISelectionListener<MyTreeNode> selectionListener) 
    {
        this.selectionListener = selectionListener;
    }
    
    public void add(BlockDefinition block) 
    {
        treeModel.add( block );
    }

    @Override
    public void valueChanged(Object value) 
    {
        treeModel.valueChanged(value);
    }
}