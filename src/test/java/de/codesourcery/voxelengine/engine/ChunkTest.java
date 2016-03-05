package de.codesourcery.voxelengine.engine;

import java.util.Random;

import com.badlogic.gdx.math.Vector3;

import de.codesourcery.voxelengine.model.BlockKey;
import de.codesourcery.voxelengine.model.Chunk;
import de.codesourcery.voxelengine.model.ChunkKey;
import de.codesourcery.voxelengine.model.World;
import junit.framework.TestCase;

public class ChunkTest extends TestCase {
    
    public void testChunkIsEmpty() {
        final Chunk chunk = new Chunk( new ChunkKey(0,0,0) );
        assertTrue( chunk.isEmpty() );
    }
    
    public void testSetBlockTypes() {
        
        final Chunk chunk = new Chunk( new ChunkKey(0,0,0) );
        
        final Random rnd = new Random(0xdeadbeef);
        
        for ( int x = 0 ; x < 32 ; x++ ) 
        {
            for ( int y = 0 ; y < 32 ; y++ ) 
            {
                for ( int z = 0 ; z < 32 ; z++ ) 
                {
                    final int type = rnd.nextInt();
                    try {
                        chunk.setBlockType( x , y , z , type );
                        assertEquals(type, chunk.getBlockType( x ,y , z ) );
                    } 
                    catch(ArrayIndexOutOfBoundsException e) 
                    {
                        System.err.println("x="+x+",y="+y+",z="+z);
                        throw e;
                    }
                }
            }
        }
        chunk.updateIsEmptyFlag();
        assertFalse( chunk.isEmpty() );
    }
    
    public void testChunkBoundaries() 
    {
        final Chunk chunk = new Chunk( new ChunkKey(0,0,0) );
        assertEquals( new Vector3(-16f,-16f,-16f) , chunk.boundingBox.min);
        assertEquals( new Vector3(16f,16f,16f) , chunk.boundingBox.max);
    }
    
    public void testWorldToBlock1() 
    {
        final Chunk chunk = new Chunk( new ChunkKey(0,0,0) );
        final BlockKey blockKey = chunk.getBlockKey( new Vector3(0,0,0) );
        assertEquals( 16 , blockKey.x );
        assertEquals( 16 , blockKey.y );
        assertEquals( 16 , blockKey.z );
        final Vector3 blockCenter = chunk.getBlockCenter( blockKey );
        assertEquals( new Vector3(0,0,0) , blockCenter );
    }
    
    public void testWorldToBlock2() 
    {
        final Chunk chunk = new Chunk( new ChunkKey(0,0,0) );
        final BlockKey blockKey = chunk.getBlockKey( new Vector3(World.BLOCK_SIZE,0,0) );
        assertEquals( 17 , blockKey.x );
        assertEquals( 16 , blockKey.y );
        assertEquals( 16 , blockKey.z );
        final Vector3 blockCenter = chunk.getBlockCenter( blockKey );
        assertEquals( new Vector3(World.BLOCK_SIZE,0,0) , blockCenter );
    }   
    
    public void testWorldToBlock3() 
    {
        final Chunk chunk = new Chunk( new ChunkKey(0,0,0) );
        final BlockKey blockKey = chunk.getBlockKey( new Vector3(-World.CHUNK_HALF_WIDTH,-World.CHUNK_HALF_WIDTH,-World.CHUNK_HALF_WIDTH) );
        assertEquals( 0 , blockKey.x );
        assertEquals( 0 , blockKey.y );
        assertEquals( 0 , blockKey.z );
        final Vector3 blockCenter = chunk.getBlockCenter( blockKey );
        assertEquals( new Vector3(-World.CHUNK_HALF_WIDTH,-World.CHUNK_HALF_WIDTH,-World.CHUNK_HALF_WIDTH) , blockCenter );
    }     
    
    public void testWorldToBlock4() 
    {
        final Chunk chunk = new Chunk( new ChunkKey(0,0,0) );
        final BlockKey blockKey = chunk.getBlockKey( new Vector3(World.CHUNK_HALF_WIDTH-0.01f,World.CHUNK_HALF_WIDTH-0.01f,World.CHUNK_HALF_WIDTH-0.01f) );
        assertEquals( World.CHUNK_SIZE-1 , blockKey.x );
        assertEquals( World.CHUNK_SIZE-1 , blockKey.y );
        assertEquals( World.CHUNK_SIZE-1 , blockKey.z );
        final Vector3 blockCenter = chunk.getBlockCenter( blockKey );
        assertEquals( new Vector3(15,15,15) , blockCenter );
    }  
    
    public void testWorldToBlock5() 
    {
        final Chunk chunk = new Chunk( new ChunkKey(0,0,0) );
        final BlockKey blockKey = chunk.getBlockKey( new Vector3(0,0,0) );
        assertEquals( 16 , blockKey.x );
        assertEquals( 16 , blockKey.y );
        assertEquals( 16 , blockKey.z );
        final Vector3 blockCenter = chunk.getBlockCenter( blockKey );
        assertEquals( new Vector3(0,0,0) , blockCenter );
    } 
    
    public void testWorldToBlock6() 
    {
        final Chunk chunk = new Chunk( new ChunkKey(0,0, 0) );
        final Vector3 worldCoords = new Vector3(0,0, -World.BLOCK_SIZE );
        final BlockKey blockKey = chunk.getBlockKey( worldCoords );
        assertEquals( 16 , blockKey.x );
        assertEquals( 16 , blockKey.y );
        assertEquals( 15 , blockKey.z );
        final Vector3 blockCenter = chunk.getBlockCenter( blockKey );
        assertEquals( worldCoords , blockCenter );
        assertEquals( worldCoords , BlockKey.getBlockCenter( chunk.chunkKey , blockKey.x , blockKey.y , blockKey.z , new Vector3() ) );
    }  
    
    public void testWorldToBlock7() 
    {
        final Vector3 v = new Vector3();
        final ChunkKey chunk = new ChunkKey(0, 0, 0);
        
        for ( int x = 0 ; x < World.CHUNK_SIZE ; x++ ) {
            for ( int y = 0 ; y < World.CHUNK_SIZE ; y++ ) {
                for ( int z = 0 ; z < World.CHUNK_SIZE ; z++ ) 
                {
                    System.out.println( "("+x+","+y+","+z+") maps to center coordinates "+BlockKey.getBlockCenter( chunk , x , y , z , v ) ); 
                }                
            }
        }
    }    
}
