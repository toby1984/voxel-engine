package de.codesourcery.voxelengine.engine;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import de.codesourcery.voxelengine.model.Chunk;
import de.codesourcery.voxelengine.model.ChunkKey;
import junit.framework.TestCase;

public class ChunkFileTest extends TestCase 
{
    private File tempFile;
    
    @Override
    protected void setUp() throws Exception 
    {
        tempFile = File.createTempFile("test", "test");
    }
    
    @Override
    protected void tearDown() throws Exception 
    {
        tempFile.delete();
    }
    
    public void testSaveLoad() throws IOException {

        final ChunkKey key = new ChunkKey(-2,0,3 );
        final Chunk chunk = ChunkManager.generateChunk( key );
        assertNotNull( chunk );
        assertEquals( key , chunk.chunkKey );
        
        try ( OutputStream out = new FileOutputStream( tempFile ) ) {
            ChunkFile.store( chunk , out );
        }
        
        Chunk loaded;
        try ( InputStream in = new FileInputStream( tempFile ) ) {
            loaded = ChunkFile.load( in );
        }
        
        assertEquals( chunk , loaded );
    }
}