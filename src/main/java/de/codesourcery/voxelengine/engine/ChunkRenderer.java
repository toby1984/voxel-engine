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
import de.codesourcery.voxelengine.model.World;

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
    private static final float[] COLOR_SOLID_1   = new float[] { 0f , 0.5f , 0f , 1 }; // r,g,b,a
    private static final float[] COLOR_SOLID_2   = new float[] { 0 , 1 , 0 , 1 }; // r,g,b,a
    private static final float[] COLOR_GLOWSTONE = new float[] { 1 , 1 , 0 , 1 }; // r,g,b,a

    private static final VertexAttribute ATTR_POSITION = new VertexAttribute( Usage.Position , 3 , "v_position" ); 
    private static final VertexAttribute ATTR_NORMAL = new VertexAttribute( Usage.Normal , 3 , "v_normal" );
    private static final VertexAttribute ATTR_COLOR = new VertexAttribute( Usage.ColorUnpacked , 4 , "v_color" );
    private static final VertexAttribute ATTR_LIGHTLEVEL = new VertexAttribute( Usage.Generic , 1 , "v_lightLevel" );

    public static final int VERTEX_FLOAT_SIZE = 3 + 3 + 4 + 1;
    private static final VertexAttributes VERTEX_ATTRIBUTES = new VertexAttributes( ATTR_POSITION,ATTR_NORMAL,ATTR_COLOR,ATTR_LIGHTLEVEL );    

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
    
    private int vertexPtr = 0;

    protected static final class Quad
    {
        public float centerX,centerY,centerZ;
        public int side;
        public int blockIndex;
        public float lightLevel;

        public void set(int blockIndex,float centerX,float centerY,float centerZ,int side,float lightLevel) 
        {
            this.centerX = centerX;
            this.centerY = centerY;
            this.centerZ = centerZ;
            this.blockIndex = blockIndex;
            this.side = side;
            this.lightLevel = lightLevel;
        }        
    }

    private void addQuad(int blockIndex,float cx , float cy , float cz,int side, float halfBlockSize,float lightLevel) 
    {
        switch( side ) 
        {
            case SIDE_TOP:    tmpQuad.set( blockIndex, cx , cy + halfBlockSize , cz , side , lightLevel); break;
            case SIDE_BOTTOM: tmpQuad.set( blockIndex, cx , cy - halfBlockSize , cz , side , lightLevel); break;
            case SIDE_LEFT:   tmpQuad.set( blockIndex, cx - halfBlockSize , cy , cz , side , lightLevel); break;
            case SIDE_RIGHT:  tmpQuad.set( blockIndex, cx + halfBlockSize , cy , cz , side , lightLevel); break;
            case SIDE_FRONT:  tmpQuad.set( blockIndex, cx , cy , cz + halfBlockSize , side , lightLevel); break;
            case SIDE_BACK:   tmpQuad.set( blockIndex, cx , cy , cz - halfBlockSize , side , lightLevel); break;
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
        final int chunkSize = World.CHUNK_SIZE;
        final float blockSize = World.BLOCK_SIZE;
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
                    final int blockIndex = Chunk.blockIndex(x,y,z);
                    final int bt = chunk.getBlockType( blockIndex );
                    if ( BlockType.isSolidBlock( bt ) ) // only render non-empty blocks
                    {
                        float lightLevel;
                        final boolean isEmittingLight = BlockType.emitsLight( bt );
                        
                        // TODO: Dirty hack... adding LIGHTLEVEL_MAX if the block itself is emitting light... this is a hint
                        // TODO: to the shader to ignore the dot product with the normal to the 'sun' and just use the light level as-is
                        final byte emittedLightLevel = isEmittingLight ? (byte) (Chunk.LIGHTLEVEL_MAX + BlockType.getEmittedLightLevel( bt ) ) : (byte) 0 ;
                        if ( hasNoBackNeighbour( x , y , z ) ) 
                        {
                            if ( isEmittingLight ) {
                                lightLevel = emittedLightLevel;
                            } else {
                                lightLevel = z == 0 ? chunk.backNeighbour.getLightLevel( x , y , World.CHUNK_SIZE-1 ) : chunk.getLightLevel( x , y , z-1 );
                            }
                            addQuad( blockIndex , bx , by , bz , SIDE_BACK , halfBlockSize , lightLevel );
                        }
                        if ( hasNoFrontNeighbour( x , y , z ) ) 
                        {
                            if ( isEmittingLight ) {
                                lightLevel = emittedLightLevel;
                            } else {                            
                                lightLevel = z == World.CHUNK_SIZE-1 ? chunk.frontNeighbour.getLightLevel( x , y , 0 ) : chunk.getLightLevel( x , y , z+1 );
                            }
                            addQuad( blockIndex ,bx , by , bz , SIDE_FRONT , halfBlockSize , lightLevel );
                        }
                        if ( hasNoLeftNeighbour( x , y , z ) ) 
                        {
                            if ( isEmittingLight ) {
                                lightLevel = 1+emittedLightLevel;
                            } else {                            
                                lightLevel = x == 0 ? chunk.leftNeighbour.getLightLevel( World.CHUNK_SIZE-1  , y , z ) : chunk.getLightLevel( x-1 , y , z );
                            }
                            addQuad( blockIndex ,bx , by , bz , SIDE_LEFT , halfBlockSize , lightLevel );
                        }
                        if ( hasNoRightNeighbour( x , y , z ) ) 
                        {
                            if ( isEmittingLight ) {
                                lightLevel = emittedLightLevel;
                            } else {
                                lightLevel = x == World.CHUNK_SIZE-1 ? chunk.rightNeighbour.getLightLevel( 0  , y , z ) : chunk.getLightLevel( x+1 , y , z );
                            }
                            addQuad( blockIndex ,bx , by , bz , SIDE_RIGHT , halfBlockSize , lightLevel );
                        }
                        if ( hasNoTopNeighbour( x , y , z ) ) 
                        {
                            if ( isEmittingLight ) {
                                lightLevel = emittedLightLevel;
                            } else {                            
                                lightLevel = y == World.CHUNK_SIZE-1 ? chunk.topNeighbour.getLightLevel( x  , 0 , z ) : chunk.getLightLevel( x , y+1 , z );
                            }
                            addQuad( blockIndex ,bx , by , bz , SIDE_TOP , halfBlockSize , lightLevel );
                        }
                        if ( hasNoBottomNeighbour( x , y , z ) ) 
                        {
                            if ( isEmittingLight ) {
                                lightLevel = emittedLightLevel;
                            } else {
                                lightLevel = y == 0 ? chunk.bottomNeighbour.getLightLevel( x  , World.CHUNK_SIZE-1 , z ) : chunk.getLightLevel( x , y-1 , z );
                            }
                            addQuad( blockIndex ,bx , by , bz , SIDE_BOTTOM , halfBlockSize , lightLevel );
                        }
                    }
                }
            }
        }
        
        this.vertexPtr = buffer.vertexPtr;

        final int vertexCount = buffer.vertexPtr / VERTEX_FLOAT_SIZE;
        final int triangleCount = vertexCount/3;

        // make sure vertex data fits into VBO
        if ( vbo == null || vbo.getNumMaxVertices() < vertexCount ) 
        {
            if ( vbo != null ) {
                LOG.info("populateVBO(): Re-allocating VBO for "+vertexCount+" vertices of "+chunk);
                vbo.dispose();
            } else {
                LOG.info("populateVBO(): Allocating VBO for "+vertexCount+" vertices of "+chunk);
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
            case BlockType.SOLID_1: color = COLOR_SOLID_1; break;
            case BlockType.SOLID_2: color = COLOR_SOLID_2; break;
            case BlockType.GLOWSTONE: color = COLOR_GLOWSTONE; break;
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
                    addQuad(p0,p1,p2,p3, NORMAL_BACK , color , quad.lightLevel );
                } else {
                    addTriangle( p0 , p1 , p2 , NORMAL_BACK , color , quad.lightLevel);
                    addTriangle( p0 , p2 , p3 , NORMAL_BACK , color , quad.lightLevel);
                }
                break;
            case SIDE_FRONT:   
                p0.x = quad.centerX - halfBlockSize; p0.y = quad.centerY - halfBlockSize; p0.z = quad.centerZ;
                p1.x = p0.x                        ; p1.y = quad.centerY + halfBlockSize; p1.z = quad.centerZ;
                p2.x = quad.centerX + halfBlockSize; p2.y = quad.centerY + halfBlockSize; p2.z = quad.centerZ;
                p3.x = p2.x                        ; p3.y = quad.centerY - halfBlockSize; p3.z = quad.centerZ;
                
                if ( WorldRenderer.RENDER_WIREFRAME ) {
                    addQuad(p0,p1,p2,p3, NORMAL_FRONT , color , quad.lightLevel);
                } else {
                    addTriangle( p0 , p2 , p1 , NORMAL_FRONT , color , quad.lightLevel);
                    addTriangle( p0 , p3 , p2 , NORMAL_FRONT , color , quad.lightLevel);
                }
                break;
            case SIDE_LEFT:    
                p0.x = quad.centerX ; p0.y = quad.centerY - halfBlockSize; p0.z = quad.centerZ - halfBlockSize;
                p1.x = quad.centerX ; p1.y = quad.centerY + halfBlockSize; p1.z = quad.centerZ - halfBlockSize;
                p2.x = quad.centerX ; p2.y = quad.centerY + halfBlockSize; p2.z = quad.centerZ + halfBlockSize;
                p3.x = quad.centerX ; p3.y = quad.centerY - halfBlockSize; p3.z = quad.centerZ + halfBlockSize;
                
                if ( WorldRenderer.RENDER_WIREFRAME ) {
                    addQuad(p0,p1,p2,p3, NORMAL_LEFT , color , quad.lightLevel);
                } else {
                    addTriangle( p0 , p3 , p1 , NORMAL_LEFT , color , quad.lightLevel);
                    addTriangle( p3 , p2 , p1 , NORMAL_LEFT , color , quad.lightLevel);
                }
                break;
            case SIDE_RIGHT:   
                p0.x = quad.centerX ; p0.y = quad.centerY - halfBlockSize; p0.z = quad.centerZ - halfBlockSize;
                p1.x = quad.centerX ; p1.y = quad.centerY + halfBlockSize; p1.z = quad.centerZ - halfBlockSize;
                p2.x = quad.centerX ; p2.y = quad.centerY + halfBlockSize; p2.z = quad.centerZ + halfBlockSize;
                p3.x = quad.centerX ; p3.y = quad.centerY - halfBlockSize; p3.z = quad.centerZ + halfBlockSize;
                
                if ( WorldRenderer.RENDER_WIREFRAME ) {
                    addQuad(p0,p1,p2,p3, NORMAL_RIGHT , color , quad.lightLevel);
                } else {
                    addTriangle( p0 , p1 , p3 , NORMAL_RIGHT , color , quad.lightLevel);
                    addTriangle( p3 , p1 , p2 , NORMAL_RIGHT , color , quad.lightLevel);
                }
                break;
            case SIDE_TOP:     
                p0.x = quad.centerX - halfBlockSize ; p0.y = quad.centerY ; p0.z = quad.centerZ + halfBlockSize;
                p1.x = quad.centerX + halfBlockSize ; p1.y = quad.centerY ; p1.z = quad.centerZ + halfBlockSize;
                p2.x = quad.centerX + halfBlockSize ; p2.y = quad.centerY ; p2.z = quad.centerZ - halfBlockSize;
                p3.x = quad.centerX - halfBlockSize ; p3.y = quad.centerY ; p3.z = quad.centerZ - halfBlockSize;
                
                if ( WorldRenderer.RENDER_WIREFRAME ) {
                    addQuad(p0,p1,p2,p3, NORMAL_TOP , color , quad.lightLevel);
                } else {
                    addTriangle( p0 , p2 , p3 , NORMAL_TOP , color , quad.lightLevel);
                    addTriangle( p0 , p1 , p2 , NORMAL_TOP , color , quad.lightLevel);
                }
                break;
            case SIDE_BOTTOM:  
                p0.x = quad.centerX - halfBlockSize ; p0.y = quad.centerY ; p0.z = quad.centerZ + halfBlockSize;
                p1.x = quad.centerX + halfBlockSize ; p1.y = quad.centerY ; p1.z = quad.centerZ + halfBlockSize;
                p2.x = quad.centerX + halfBlockSize ; p2.y = quad.centerY ; p2.z = quad.centerZ - halfBlockSize;
                p3.x = quad.centerX - halfBlockSize ; p3.y = quad.centerY ; p3.z = quad.centerZ - halfBlockSize;
                
                if ( WorldRenderer.RENDER_WIREFRAME ) {
                    addQuad(p0,p1,p2,p3, NORMAL_BOTTOM , color , quad.lightLevel);
                } else {               
                    addTriangle( p0 , p3 , p2 , NORMAL_BOTTOM , color , quad.lightLevel);
                    addTriangle( p0 , p2 , p1 , NORMAL_BOTTOM , color , quad.lightLevel);
                }
                break;
            default:
                throw new IllegalArgumentException("Unhandled side: "+quad.side);
        }
    }   

    private void addQuad(Vector3 p0,Vector3 p1,Vector3 p2,Vector3 p3,Vector3 normal,float[] color,float lightLevel) 
    {
        final int bytesAvailable = buffer.vertexData.length - buffer.vertexPtr;
        if ( bytesAvailable < 8 * VERTEX_FLOAT_SIZE ) {
            final float[] tmp = new float[ buffer.vertexData.length + buffer.vertexData.length/2 ]; // add space for 100 more quads
            System.arraycopy( buffer.vertexData , 0 , tmp , 0 , buffer.vertexPtr );
            buffer.vertexData = tmp;
        }
        addVertex( p0 , normal , color , lightLevel );
        addVertex( p1 , normal , color , lightLevel );
                                       
        addVertex( p1 , normal , color , lightLevel );
        addVertex( p2 , normal , color , lightLevel );
                                       
        addVertex( p2 , normal , color , lightLevel );            
        addVertex( p3 , normal , color , lightLevel );
                                       
        addVertex( p3 , normal , color , lightLevel );
        addVertex( p0 , normal , color , lightLevel );            
    }     

    private void addTriangle(Vector3 p0,Vector3 p1,Vector3 p2,Vector3 normal,float[] color,float lightLevel) 
    {
        final int maxSize = buffer.vertexData.length;
        final int bytesAvailable = maxSize - buffer.vertexPtr;
        if ( bytesAvailable < 10*VERTEX_FLOAT_SIZE ) {
            final float[] tmp = new float[ maxSize + maxSize/2 ]; 
            System.arraycopy( buffer.vertexData , 0 , tmp , 0 , buffer.vertexPtr );
            buffer.vertexData = tmp;
        }
        addVertex( p0 , normal , color , lightLevel );
        addVertex( p1 , normal , color , lightLevel );
        addVertex( p2 , normal , color , lightLevel );
    }    

    private void addVertex(Vector3 p,Vector3 normal,float[] color,float lightLevel) 
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
        
        vertexData[ vertexPtr+10 ] = lightLevel;

        buffer.vertexPtr += VERTEX_FLOAT_SIZE;
    }

    private boolean hasNoFrontNeighbour(int blockX,int blockY,int blockZ) 
    {
        if ( blockZ+1 < World.CHUNK_SIZE ) {
            return chunk.isBlockEmpty( blockX , blockY , blockZ+1 ); 
        }
        return chunk.frontNeighbour.isBlockEmpty( blockX , blockY , 0 );
    }

    private boolean hasNoBackNeighbour(int blockX,int blockY,int blockZ) 
    {
        if ( blockZ-1 >= 0 ) {
            return chunk.isBlockEmpty( blockX , blockY , blockZ-1 ); 
        }
        return chunk.backNeighbour.isBlockEmpty( blockX , blockY , World.CHUNK_SIZE-1 );
    }    

    private boolean hasNoLeftNeighbour(int blockX,int blockY,int blockZ) 
    {
        if ( blockX-1 >= 0 ) {
            return chunk.isBlockEmpty( blockX-1 , blockY , blockZ ); 
        }
        return chunk.leftNeighbour.isBlockEmpty( World.CHUNK_SIZE-1 , blockY , blockZ );
    }    

    private boolean hasNoRightNeighbour(int blockX,int blockY,int blockZ) 
    {
        if ( blockX+1 < World.CHUNK_SIZE ) {
            return chunk.isBlockEmpty( blockX+1 , blockY , blockZ ); 
        }
        return chunk.rightNeighbour.isBlockEmpty( 0 , blockY , blockZ );
    }   

    private boolean hasNoTopNeighbour(int blockX,int blockY,int blockZ) 
    {
        if ( blockY+1 < World.CHUNK_SIZE ) {
            return chunk.isBlockEmpty( blockX , blockY+1 , blockZ ); 
        }
        return chunk.topNeighbour.isBlockEmpty( blockX , 0 , blockZ );
    }  

    private boolean hasNoBottomNeighbour(int blockX,int blockY,int blockZ) 
    {
        if ( blockY-1 >= 0 ) {
            return chunk.isBlockEmpty( blockX , blockY-1 , blockZ ); 
        }
        return chunk.bottomNeighbour.isBlockEmpty( blockX , World.CHUNK_SIZE-1 , blockZ );
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
        final int vertexCount = this.vertexPtr / VERTEX_FLOAT_SIZE;
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