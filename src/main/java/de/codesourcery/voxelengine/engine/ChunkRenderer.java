package de.codesourcery.voxelengine.engine;

import org.apache.log4j.Logger;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.graphics.glutils.VertexBufferObjectWithVAO;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;

import de.codesourcery.voxelengine.model.BlockType;
import de.codesourcery.voxelengine.model.Chunk;

/**
 * Responsible for rendering a chunk.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public class ChunkRenderer implements Disposable 
{
    private static final Logger LOG = Logger.getLogger(ChunkRenderer.class);

    private static final int SIDE_BACK   = 0;
    private static final int SIDE_FRONT  = 1;
    private static final int SIDE_LEFT   = 2;
    private static final int SIDE_RIGHT  = 3;
    private static final int SIDE_TOP    = 4;
    private static final int SIDE_BOTTOM = 5;

    // TODO: Test code with fixed colors, remove when done
    private static final float[] COLOR_SOLID_1 = new float[] { 1 , 0 , 0 , 1 }; // r,g,b,a
    private static final float[] COLOR_SOLID_2 = new float[] { 0 , 1 , 0 , 1 }; // r,g,b,a

    private static final VertexAttribute ATTR_POSITION = new VertexAttribute( Usage.Position , 3 , "v_position" ); 
    private static final VertexAttribute ATTR_NORMAL = new VertexAttribute( Usage.Normal , 3 , "v_normal" );
    private static final VertexAttribute ATTR_COLOR = new VertexAttribute( Usage.ColorUnpacked , 4 , "v_color" );

    public static final int VERTEX_FLOAT_SIZE = 3 + 3 + 4;
    private static final VertexAttributes VERTEX_ATTRIBUTES = new VertexAttributes( ATTR_POSITION,ATTR_NORMAL,ATTR_COLOR );    

    private static final Vector3 NORMAL_BACK    = new Vector3( 0, 0,-1);
    private static final Vector3 NORMAL_FRONT   = new Vector3( 0, 0, 1);
    private static final Vector3 NORMAL_LEFT    = new Vector3(-1, 0, 0);
    private static final Vector3 NORMAL_RIGHT   = new Vector3( 1, 0, 0);
    private static final Vector3 NORMAL_TOP     = new Vector3( 0, 1, 0);
    private static final Vector3 NORMAL_BOTTOM  = new Vector3( 0,-1, 0);

    // the chunk that should be rendered
    private Chunk chunk;
    
    // VBO to hold vertex data
    private VertexBufferObjectWithVAO vbo;

    // temporary buffer where vertex data will
    // be written to before it gets uploaded to the GPU
    private VertexDataBuffer buffer;

    // Used to hold data associated with the current quad
    private final Quad tmpQuad = new Quad();

    protected static final class Quad
    {
        public float centerX,centerY,centerZ;
        public int side;
        public int blockIndex;

        public void set(int blockIndex,float centerX,float centerY,float centerZ,int side) 
        {
            this.centerX = centerX;
            this.centerY = centerY;
            this.centerZ = centerZ;
            this.blockIndex = blockIndex;
            this.side = side;
        }        
    }

    private void addQuad(int blockIndex,float cx , float cy , float cz,int side, float halfBlockSize) 
    {
        switch( side ) 
        {
            case SIDE_TOP:    tmpQuad.set( blockIndex, cx , cy + halfBlockSize , cz , side ); break;
            case SIDE_BOTTOM: tmpQuad.set( blockIndex, cx , cy - halfBlockSize , cz , side ); break;
            case SIDE_LEFT:   tmpQuad.set( blockIndex, cx - halfBlockSize , cy , cz , side ); break;
            case SIDE_RIGHT:  tmpQuad.set( blockIndex, cx + halfBlockSize , cy , cz , side ); break;
            case SIDE_FRONT:  tmpQuad.set( blockIndex, cx , cy , cz + halfBlockSize , side ); break;
            case SIDE_BACK:   tmpQuad.set( blockIndex, cx , cy , cz - halfBlockSize , side ); break;
            default:
                throw new IllegalArgumentException("Unknown side: "+side);
        }
        addTriangle( tmpQuad , halfBlockSize );
    }

    public void buildMesh(Chunk chunk,VertexDataBuffer buffer)
    {
        this.buffer = buffer;
        this.buffer.vertexPtr = 0;
        this.chunk = chunk;

        // for each block, create quads for all block sides that are adjacent 
        // to an empty neighbour block
        final int chunkSize = chunk.chunkSize;
        final float blockSize = chunk.blocksize;
        final float halfBlockSize = blockSize/2f;
        final float halfWidth = chunkSize*blockSize/2f;

        float bx = chunk.center.x - halfWidth + halfBlockSize;
        for ( int x = 0 ; x < chunkSize ; x++ , bx += blockSize ) 
        {
            float by = chunk.center.y - halfWidth + halfBlockSize;
            for ( int y = 0 ; y < chunkSize ; y++, by += blockSize  ) 
            {
                float bz = chunk.center.z - halfWidth + halfBlockSize;
                for ( int z = 0 ; z < chunkSize ; z++ , bz += blockSize  ) 
                {
                    final int blockType = chunk.getBlockType( x , y , z );
                    if ( blockType != BlockType.BLOCKTYPE_AIR ) // only render non-empty blocks
                    {
                        final int blockIndex = chunk.blockIndex(x,y,z);
                        if ( hasNoBackNeighbour( x , y , z ) ) {
                            addQuad( blockIndex , bx , by , bz , SIDE_BACK , halfBlockSize );
                        }
                        if ( hasNoFrontNeighbour( x , y , z ) ) {
                            addQuad( blockIndex ,bx , by , bz , SIDE_FRONT , halfBlockSize );
                        }
                        if ( hasNoLeftNeighbour( x , y , z ) ) {
                            addQuad( blockIndex ,bx , by , bz , SIDE_LEFT , halfBlockSize );
                        }                    
                        if ( hasNoRightNeighbour( x , y , z ) ) {
                            addQuad( blockIndex ,bx , by , bz , SIDE_RIGHT , halfBlockSize );
                        }  
                        if ( hasNoTopNeighbour( x , y , z ) ) {
                            addQuad( blockIndex ,bx , by , bz , SIDE_TOP , halfBlockSize );
                        }    
                        if ( hasNoBottomNeighbour( x , y , z ) ) {
                            addQuad( blockIndex ,bx , by , bz , SIDE_BOTTOM , halfBlockSize );
                        }                     
                    }
                }
            }
        }

        final int vertexCount = buffer.vertexPtr / VERTEX_FLOAT_SIZE;
        final int triangleCount = vertexCount/3;

        // make sure vertex data fits into VBO
        if ( vbo == null || vbo.getNumMaxVertices() < vertexCount ) 
        {
            if ( vbo != null ) {
                LOG.info("populateVBO(): Re-allocating VBO for "+vertexCount+" vertices");
                vbo.dispose();
            } else {
                LOG.info("populateVBO(): Allocating VBO for "+vertexCount+" vertices");
            }
            vbo = new VertexBufferObjectWithVAO( false , vertexCount , VERTEX_ATTRIBUTES );
        }

        final float sizeInMb = (buffer.vertexPtr*4)/(1024*1024f);
        LOG.info("populateVBO(): Uploading "+buffer.vertexPtr+" floats ("+sizeInMb+" MB, "+triangleCount+" triangles)");
        vbo.setVertices( buffer.vertexData , 0 , buffer.vertexPtr );

        // dispose old mesh
        if ( chunk.renderer != this ) 
        {
            if ( chunk.renderer != null ) {
                chunk.renderer.dispose();
            }
            chunk.renderer = this;
        }
        chunk.clearFlags( Chunk.FLAG_NEEDS_REBUILD );
    }

    private void addTriangle(Quad quad,float halfBlockSize) 
    {
        // vertex data is
        // Position
        // Normal
        // color (r,g,b,a)
        final Vector3 p0 = new Vector3();
        final Vector3 p1 = new Vector3();
        final Vector3 p2 = new Vector3();
        final Vector3 p3 = new Vector3();

        final float[] color;
        switch ( chunk.getBlockType( quad.blockIndex ) ) {
            case BlockType.BLOCKTYPE_SOLID_1: color = COLOR_SOLID_1; break;
            case BlockType.BLOCKTYPE_SOLID_2: color = COLOR_SOLID_2; break;
            default:
                throw new RuntimeException("Unhandled block type: "+chunk.getBlockType( quad.blockIndex ) );
        }

        switch( quad.side ) 
        {
            case SIDE_BACK:  
                p0.x = quad.centerX - halfBlockSize; p0.y = quad.centerY - halfBlockSize; p0.z = quad.centerZ;
                p1.x = p0.x                        ; p1.y = quad.centerY + halfBlockSize; p1.z = quad.centerZ;
                p2.x = quad.centerX + halfBlockSize; p2.y = quad.centerY + halfBlockSize; p2.z = quad.centerZ;
                p3.x = p2.x                        ; p3.y = quad.centerY - halfBlockSize; p3.z = quad.centerZ;
                if ( WorldRenderer.RENDER_WIREFRAME ) {
                    addQuad(p0,p1,p2,p3, NORMAL_BACK , color );
                } else {
                    addTriangle( p0 , p1 , p2 , NORMAL_BACK , color );
                    addTriangle( p0 , p2 , p3 , NORMAL_BACK , color );
                }
                break;
            case SIDE_FRONT:   
                p0.x = quad.centerX - halfBlockSize; p0.y = quad.centerY - halfBlockSize; p0.z = quad.centerZ;
                p1.x = p0.x                        ; p1.y = quad.centerY + halfBlockSize; p1.z = quad.centerZ;
                p2.x = quad.centerX + halfBlockSize; p2.y = quad.centerY + halfBlockSize; p2.z = quad.centerZ;
                p3.x = p2.x                        ; p3.y = quad.centerY - halfBlockSize; p3.z = quad.centerZ;
                
                if ( WorldRenderer.RENDER_WIREFRAME ) {
                    addQuad(p0,p1,p2,p3, NORMAL_FRONT , color );
                } else {
                    addTriangle( p0 , p2 , p1 , NORMAL_FRONT , color );
                    addTriangle( p0 , p3 , p2 , NORMAL_FRONT , color );
                }
                break;
            case SIDE_LEFT:    
                p0.x = quad.centerX ; p0.y = quad.centerY - halfBlockSize; p0.z = quad.centerZ - halfBlockSize;
                p1.x = quad.centerX ; p1.y = quad.centerY + halfBlockSize; p1.z = quad.centerZ - halfBlockSize;
                p2.x = quad.centerX ; p2.y = quad.centerY + halfBlockSize; p2.z = quad.centerZ + halfBlockSize;
                p3.x = quad.centerX ; p3.y = quad.centerY - halfBlockSize; p3.z = quad.centerZ + halfBlockSize;
                
                if ( WorldRenderer.RENDER_WIREFRAME ) {
                    addQuad(p0,p1,p2,p3, NORMAL_LEFT , color );
                } else {
                    addTriangle( p0 , p3 , p1 , NORMAL_LEFT , color );
                    addTriangle( p3 , p2 , p1 , NORMAL_LEFT , color );
                }
                break;
            case SIDE_RIGHT:   
                p0.x = quad.centerX ; p0.y = quad.centerY - halfBlockSize; p0.z = quad.centerZ - halfBlockSize;
                p1.x = quad.centerX ; p1.y = quad.centerY + halfBlockSize; p1.z = quad.centerZ - halfBlockSize;
                p2.x = quad.centerX ; p2.y = quad.centerY + halfBlockSize; p2.z = quad.centerZ + halfBlockSize;
                p3.x = quad.centerX ; p3.y = quad.centerY - halfBlockSize; p3.z = quad.centerZ + halfBlockSize;
                
                if ( WorldRenderer.RENDER_WIREFRAME ) {
                    addQuad(p0,p1,p2,p3, NORMAL_RIGHT , color );
                } else {
                    addTriangle( p0 , p1 , p3 , NORMAL_RIGHT , color );
                    addTriangle( p3 , p1 , p2 , NORMAL_RIGHT , color );
                }
                break;
            case SIDE_TOP:     
                p0.x = quad.centerX - halfBlockSize ; p0.y = quad.centerY ; p0.z = quad.centerZ + halfBlockSize;
                p1.x = quad.centerX + halfBlockSize ; p1.y = quad.centerY ; p1.z = quad.centerZ + halfBlockSize;
                p2.x = quad.centerX + halfBlockSize ; p2.y = quad.centerY ; p2.z = quad.centerZ - halfBlockSize;
                p3.x = quad.centerX - halfBlockSize ; p3.y = quad.centerY ; p3.z = quad.centerZ - halfBlockSize;
                
                if ( WorldRenderer.RENDER_WIREFRAME ) {
                    addQuad(p0,p1,p2,p3, NORMAL_TOP , color );
                } else {
                    addTriangle( p0 , p2 , p3 , NORMAL_TOP , color );
                    addTriangle( p0 , p1 , p2 , NORMAL_TOP , color );
                }
                break;
            case SIDE_BOTTOM:  
                p0.x = quad.centerX - halfBlockSize ; p0.y = quad.centerY ; p0.z = quad.centerZ + halfBlockSize;
                p1.x = quad.centerX + halfBlockSize ; p1.y = quad.centerY ; p1.z = quad.centerZ + halfBlockSize;
                p2.x = quad.centerX + halfBlockSize ; p2.y = quad.centerY ; p2.z = quad.centerZ - halfBlockSize;
                p3.x = quad.centerX - halfBlockSize ; p3.y = quad.centerY ; p3.z = quad.centerZ - halfBlockSize;
                
                if ( WorldRenderer.RENDER_WIREFRAME ) {
                    addQuad(p0,p1,p2,p3, NORMAL_BOTTOM , color );
                } else {               
                    addTriangle( p0 , p3 , p2 , NORMAL_BOTTOM , color );
                    addTriangle( p0 , p2 , p1 , NORMAL_BOTTOM , color );
                }
                break;
            default:
                throw new IllegalArgumentException("Unhandled side: "+quad.side);
        }
    }   

    private void addQuad(Vector3 p0,Vector3 p1,Vector3 p2,Vector3 p3,Vector3 normal,float[] color) 
    {
        final int bytesAvailable = buffer.vertexData.length - buffer.vertexPtr;
        if ( bytesAvailable < 8 * VERTEX_FLOAT_SIZE ) {
            final float[] tmp = new float[ buffer.vertexData.length + buffer.vertexData.length/2 ]; // add space for 100 more quads
            System.arraycopy( buffer.vertexData , 0 , tmp , 0 , buffer.vertexPtr );
            buffer.vertexData = tmp;
        }
        addVertex( p0 , normal , color );
        addVertex( p1 , normal , color );

        addVertex( p1 , normal , color );
        addVertex( p2 , normal , color );

        addVertex( p2 , normal , color );            
        addVertex( p3 , normal , color );

        addVertex( p3 , normal , color );
        addVertex( p0 , normal , color );            
    }     

    private void addTriangle(Vector3 p0,Vector3 p1,Vector3 p2,Vector3 normal,float[] color) 
    {
        final int bytesAvailable = buffer.vertexData.length - buffer.vertexPtr;
        if ( bytesAvailable < 3*VERTEX_FLOAT_SIZE ) {
            final float[] tmp = new float[ buffer.vertexData.length + buffer.vertexData.length/2 ]; 
            System.arraycopy( buffer.vertexData , 0 , tmp , 0 , buffer.vertexPtr );
            buffer.vertexData = tmp;
        }
        addVertex( p0 , normal , color );
        addVertex( p1 , normal , color );
        addVertex( p2 , normal , color );
    }    

    private void addVertex(Vector3 p,Vector3 normal,float[] color) 
    {
        final float[] vertexData = buffer.vertexData;
        int vertexPtr = buffer.vertexPtr;

        vertexData[ vertexPtr    ] = p.x;
        vertexData[ vertexPtr+1  ] = p.y;
        vertexData[ vertexPtr+2  ] = p.z;

        vertexData[ vertexPtr+3 ] = normal.x;
        vertexData[ vertexPtr+4 ] = normal.y;
        vertexData[ vertexPtr+5 ] = normal.z;

        vertexData[ vertexPtr+6 ] = color[0];
        vertexData[ vertexPtr+7 ] = color[1];
        vertexData[ vertexPtr+8 ] = color[2];
        vertexData[ vertexPtr+9 ] = color[3];

        buffer.vertexPtr += VERTEX_FLOAT_SIZE;
    }

    private boolean hasNoFrontNeighbour(int blockX,int blockY,int blockZ) 
    {
        if ( blockZ+1 < chunk.chunkSize ) {
            return chunk.isBlockEmpty( blockX , blockY , blockZ+1 ); 
        }
        return chunk.frontNeighbour.isBlockEmpty( blockX , blockY , 0 );
    }

    private boolean hasNoBackNeighbour(int blockX,int blockY,int blockZ) 
    {
        if ( blockZ-1 >= 0 ) {
            return chunk.isBlockEmpty( blockX , blockY , blockZ-1 ); 
        }
        return chunk.backNeighbour.isBlockEmpty( blockX , blockY , chunk.backNeighbour.chunkSize-1 );
    }    

    private boolean hasNoLeftNeighbour(int blockX,int blockY,int blockZ) 
    {
        if ( blockX-1 >= 0 ) {
            return chunk.isBlockEmpty( blockX-1 , blockY , blockZ ); 
        }
        return chunk.leftNeighbour.isBlockEmpty( chunk.leftNeighbour.chunkSize-1 , blockY , blockZ );
    }    

    private boolean hasNoRightNeighbour(int blockX,int blockY,int blockZ) 
    {
        if ( blockX+1 < chunk.chunkSize ) {
            return chunk.isBlockEmpty( blockX+1 , blockY , blockZ ); 
        }
        return chunk.rightNeighbour.isBlockEmpty( 0 , blockY , blockZ );
    }   

    private boolean hasNoTopNeighbour(int blockX,int blockY,int blockZ) 
    {
        if ( blockY+1 < chunk.chunkSize ) {
            return chunk.isBlockEmpty( blockX , blockY+1 , blockZ ); 
        }
        return chunk.topNeighbour.isBlockEmpty( blockX , 0 , blockZ );
    }  

    private boolean hasNoBottomNeighbour(int blockX,int blockY,int blockZ) 
    {
        if ( blockY-1 >= 0 ) {
            return chunk.isBlockEmpty( blockX , blockY-1 , blockZ ); 
        }
        return chunk.bottomNeighbour.isBlockEmpty( blockX , chunk.bottomNeighbour.chunkSize-1 , blockZ );
    }     

    /**
     * Render this chunk.
     * 
     * @param shader
     * @param camera
     * @param trace
     * @return
     */
    public int render(ShaderProgram shader,boolean trace) 
    {
        final int vertexCount = buffer.vertexPtr / VERTEX_FLOAT_SIZE;
        if ( trace ) {
            LOG.trace("render(): Rendering mesh with "+(vertexCount/3)+" triangles ("+vertexCount+" vertices)");
        }

        vbo.bind( shader );
        if ( WorldRenderer.RENDER_WIREFRAME ) {
            Gdx.gl30.glDrawArrays( GL30.GL_LINES , 0 , vertexCount );
        } else {
            Gdx.gl30.glDrawArrays( GL30.GL_TRIANGLES , 0 , vertexCount );
        }
        vbo.unbind( shader );
        return vertexCount/3;
    }

    @Override
    public void dispose() 
    {
        if ( vbo != null ) {
            vbo.dispose();
            vbo = null;
        }
    }
}