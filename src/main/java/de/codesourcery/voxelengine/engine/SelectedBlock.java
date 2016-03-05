package de.codesourcery.voxelengine.engine;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.graphics.glutils.VertexBufferObjectWithVAO;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;

import de.codesourcery.voxelengine.model.BlockKey;
import de.codesourcery.voxelengine.model.ChunkKey;
import de.codesourcery.voxelengine.model.World;

/**
 * Used to track and highlight the currently selected block (if any).
 *
 * @author tobias.gierke@code-sourcery.de
 */
public class SelectedBlock implements Disposable 
{
    private static final VertexAttribute ATTR_POSITION = new VertexAttribute( Usage.Position , 3 , "v_position" ); 

    public static final int VERTEX_FLOAT_SIZE = 3;
    
    private static final VertexAttributes VERTEX_ATTRIBUTES = new VertexAttributes( ATTR_POSITION);  
    
    private VertexBufferObjectWithVAO vbo;
    
    public long chunkID=ChunkKey.INVALID;
    public int blockID=BlockKey.INVALID;
    
    private final ShaderProgram shader;
    
    private int vertexPtr;
    private final float[] vertexData = new float[6 * 8 * VERTEX_FLOAT_SIZE];
    
    private final Camera camera;
    
    private final Vector3 p0 = new Vector3();
    private final Vector3 p1 = new Vector3();
    private final Vector3 p2 = new Vector3();
    private final Vector3 p3 = new Vector3();
    private final Vector3 p4 = new Vector3();
    private final Vector3 p5 = new Vector3();
    private final Vector3 p6 = new Vector3();
    private final Vector3 p7 = new Vector3();
    
    public SelectedBlock(World world,ShaderManager shaderManager) 
    {
        this.shader = shaderManager.getShader( ShaderManager.SELECTED_BLOCK_SHADER );
        this.camera = world.camera;
    }
    
    /**
     * Sets the currently selected block.
     * @param chunk Chunk the block is in
     * @param block Block within the chunk
     */
    public void setSelected(long chunkID,int blockID) 
    {
        this.chunkID = chunkID;
        this.blockID = blockID;
        
        vertexPtr = 0;
        
        final Vector3 center = BlockKey.getBlockCenter( chunkID , blockID , new Vector3() );
        
        p0.set(center.x-World.HALF_BLOCK_SIZE,center.y-World.HALF_BLOCK_SIZE,center.z+World.HALF_BLOCK_SIZE);
        p1.set(center.x+World.HALF_BLOCK_SIZE,center.y-World.HALF_BLOCK_SIZE,center.z+World.HALF_BLOCK_SIZE);
        p2.set(center.x+World.HALF_BLOCK_SIZE,center.y+World.HALF_BLOCK_SIZE,center.z+World.HALF_BLOCK_SIZE);
        p3.set(center.x-World.HALF_BLOCK_SIZE,center.y+World.HALF_BLOCK_SIZE,center.z+World.HALF_BLOCK_SIZE);
        
        p4.set(center.x-World.HALF_BLOCK_SIZE,center.y-World.HALF_BLOCK_SIZE,center.z-World.HALF_BLOCK_SIZE);
        p5.set(center.x+World.HALF_BLOCK_SIZE,center.y-World.HALF_BLOCK_SIZE,center.z-World.HALF_BLOCK_SIZE);
        p6.set(center.x+World.HALF_BLOCK_SIZE,center.y+World.HALF_BLOCK_SIZE,center.z-World.HALF_BLOCK_SIZE);
        p7.set(center.x-World.HALF_BLOCK_SIZE,center.y+World.HALF_BLOCK_SIZE,center.z-World.HALF_BLOCK_SIZE);        
        
        addQuad(p0,p1,p2,p3); // front
        addQuad(p1,p5,p6,p2); // right
        addQuad(p3,p2,p6,p7); // top
        addQuad(p4,p0,p3,p7); // left
        addQuad(p4,p5,p1,p0); // bottom
        addQuad( p5,p4,p7,p6); // back
        
        if ( vbo == null ) 
        {
            vbo = new VertexBufferObjectWithVAO(false,vertexPtr/VERTEX_FLOAT_SIZE,VERTEX_ATTRIBUTES);
        }
        vbo.setVertices( vertexData , 0 , vertexPtr );
    }
    
    private void addQuad(Vector3 p0,Vector3 p1,Vector3 p2,Vector3 p3) 
    {
        addLine( p0 , p1 );
        addLine( p1 , p2 );
        addLine( p2 , p3 );
        addLine( p3 , p0 );
    }
    
    private void addLine(Vector3 p0,Vector3 p1) 
    {
        addVertex( p0 );
        addVertex( p1 );
    }
    
    private void addVertex(Vector3 p) 
    {
        int ptr=vertexPtr;
        
        vertexData[ptr]   = p.x;
        vertexData[ptr+1] = p.y;
        vertexData[ptr+2] = p.z;
        
        this.vertexPtr += 3;
    }
    
    /**
     * Clears the selection.
     */
    public void clearSelection() {
        chunkID=ChunkKey.INVALID;
        blockID=BlockKey.INVALID;
    }
    
    /**
     * Returns whether a block is currently selected.
     * @return
     */
    public boolean hasSelection() {
        return chunkID != ChunkKey.INVALID;
    }
    
    /**
     * Highlights the currently selected block (if the selection is active).
     * 
     * @see #hasSelection()
     */
    public void render() 
    {
        if ( hasSelection() ) 
        {
            Gdx.gl30.glDisable( GL20.GL_CULL_FACE );
            Gdx.gl30.glDisable(GL20.GL_DEPTH_TEST);
            
            shader.begin();

            shader.setUniformMatrix("u_modelViewProjection", camera.combined );
            
            vbo.bind( shader );
            Gdx.gl30.glDrawArrays( GL30.GL_LINES , 0 , vertexPtr/VERTEX_FLOAT_SIZE ); 
            vbo.unbind( shader );
            shader.end();
        }
    }
    
    @Override
    public void dispose() 
    {
        if ( vbo != null ) 
        {
            vbo.dispose();
            vbo = null;
        }
    }
}