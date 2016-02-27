package de.codesourcery.voxelengine.engine;

import com.badlogic.gdx.math.Vector3;

import junit.framework.TestCase;

public class WorldTest extends TestCase  {

    
    public void testWorldToChunk() 
    {
        final Vector3 v = new Vector3(2.2555127f,5.7757998f,-16.15538f);
        ChunkKey chunk = new World().getChunkCoordinates(v);
        assertEquals(0,chunk.x);
        assertEquals(0,chunk.y);
        assertEquals(-1,chunk.z);
    }
}
