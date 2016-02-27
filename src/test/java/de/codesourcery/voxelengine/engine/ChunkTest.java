package de.codesourcery.voxelengine.engine;

import java.util.Random;

import com.badlogic.gdx.math.Vector3;

import junit.framework.TestCase;

public class ChunkTest extends TestCase {
    
    public void testChunkIsEmpty() {
        final Chunk chunk = new Chunk( null, new Vector3(0,0,0) , 32 , 1f );
        assertTrue( chunk.isEmpty() );
    }
    
    public void testSetBlockTypes() {
        
        final Chunk chunk = new Chunk( null , new Vector3(0,0,0) , 32 , 1f );
        
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
        final Chunk chunk = new Chunk( null, new Vector3(0,0,0) , 32 , 1f );
        assertEquals( new Vector3(-16f,-16f,-16f) , chunk.boundingBox.min);
        assertEquals( new Vector3(16f,16f,16f) , chunk.boundingBox.max);
    }
}
