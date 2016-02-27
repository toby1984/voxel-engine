package de.codesourcery.voxelengine.engine;

import java.io.File;
import java.io.IOException;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.Validate;
import org.apache.log4j.Logger;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;

public class ChunkManager implements Disposable
{
    private static final Logger LOG = Logger.getLogger(ChunkManager.class);
    
    public static final int MAX_CACHE_SIZE = 32;
    
    public static final boolean CLEAR_CHUNK_DIR = true;

    private final File chunkDir;

    private final ThreadPoolExecutor threadPool;

    public ChunkManager(File chunkDir) 
    {
        Validate.notNull(chunkDir, "chunkDir must not be NULL");
        this.chunkDir = chunkDir;
        
        if ( CLEAR_CHUNK_DIR ) {
            LOG.warn("ChunkManager(): Deleting chunk directory "+chunkDir.getAbsolutePath());
            recursiveDelete( chunkDir );
        }
        
        if ( ! chunkDir.exists() ) {
            LOG.warn("ChunkManager(): Re-creating missing chunk directory "+chunkDir.getAbsolutePath());
            if ( ! chunkDir.mkdirs() ) {
                LOG.error("ChunkManager(): Failed to create chunk dir "+chunkDir.getAbsolutePath());
                throw new RuntimeException("Failed to create chunk dir "+chunkDir.getAbsolutePath());
            }
        }
        
        final BlockingQueue<Runnable> workQueue = new ArrayBlockingQueue<Runnable>(100);
        final ThreadFactory threadFactory = new ThreadFactory() 
        {
            private final AtomicInteger ID = new AtomicInteger(0);
            
            @Override
            public Thread newThread(Runnable r) 
            {
                final Thread t = new Thread(r);
                t.setDaemon( true );
                t.setName( "chunkmanager-"+ID.incrementAndGet());
                return t;
            }
        };
        threadPool = new ThreadPoolExecutor( 2 , 2 , 10 , TimeUnit.MINUTES , workQueue , threadFactory , new ThreadPoolExecutor.CallerRunsPolicy() );
    }
    
    private static void recursiveDelete(File file) 
    {
        if ( ! file.exists() ) {
            return;
        }
        if ( file.isDirectory() ) {
            final File[] files = file.listFiles();
            if ( files != null ) {
                for ( File f : files ) {
                    recursiveDelete( f );
                }
            }
            file.delete();
        } 
        file.delete();
    }

    public Chunk getChunk(ChunkKey key) 
    {
        return getChunk(key,true);
    }

    private Chunk getChunk(ChunkKey key,boolean createMissing) 
    {
        Validate.notNull(key, "key must not be NULL");
        Chunk result = loadChunk(key);
        if ( result == null ) 
        {
            if ( ! createMissing ) {
                return null;
            }
            result = generateChunk( key , createMissing );
        }
        return result; 
    }
    
    public void unloadChunk(Chunk chunk) 
    {
        try 
        {
            if ( chunk.needsSave() ) {
                saveChunk( chunk );
            }
        } catch(Exception e) {
            LOG.error("unloadChunk(): Failed for "+chunk,e);
        } finally {
            chunk.dispose();
        }
    }
    
    private void saveChunk(Chunk chunk) 
    {
        final File file = getFile( chunk.chunkKey );
        if ( LOG.isDebugEnabled() ) {
            LOG.debug("saveChunk(): Saving chunk "+chunk.chunkKey+" to "+file.getAbsolutePath());
        }
        try {
            new ChunkFile( file ).store( chunk );
            chunk.clearFlags( Chunk.FLAG_NEEDS_SAVE );
        } 
        catch (IOException e) 
        {
            LOG.error("saveChunk(): Failed to save chunk "+chunk.chunkKey+" from "+file.getAbsolutePath(),e);
            throw new RuntimeException("Failed to save chunk "+chunk.chunkKey+" from "+file.getAbsolutePath(),e);
        }
    }
    
    private File getFile(ChunkKey key) {
        return new File( chunkDir , key.x+"_"+key.y+"_"+key.z+".chunk" );
    }
    
    private Chunk loadChunk(ChunkKey key) 
    {
        final File file = getFile(key);
        if ( ! file.exists() ) {
            LOG.debug("loadChunk(): Failed to load chunk "+key+" from "+file.getAbsolutePath()+" , file does not exist");
            return null;
        }
        if ( LOG.isDebugEnabled() ) {
            LOG.debug("loadChunk(): Loading chunk "+key+" from "+file.getAbsolutePath());
        }
        try 
        {
            return new ChunkFile( file ).load();
        } 
        catch (IOException e) 
        {
            LOG.error("loadChunk(): Failed to load chunk "+key+" from "+file.getAbsolutePath(),e);
            throw new RuntimeException("Failed to load chunk "+key+" from "+file.getAbsolutePath(),e);
        }
    }
    
    private Chunk generateChunk(ChunkKey key,boolean recurse) {
        
        final float centerX = key.x* World.WORLD_CHUNK_SIZE;
        final float centerY = key.y* World.WORLD_CHUNK_SIZE;
        final float centerZ = key.z* World.WORLD_CHUNK_SIZE;
        
        final Chunk chunk = new Chunk(key,new Vector3(centerX,centerY,centerZ) ,World.WORLD_CHUNK_SIZE ,World.WORLD_CHUNK_BLOCK_SIZE );
        chunk.setNeedsSave( true );
        
        long hash = 31+key.x*31;
        hash = hash*31 + key.y*31;
        hash = hash*31 + key.z*31;
        final Random rnd = new Random( hash );
        
        if ( key.y == 0 ) // create ground plane 
        {
            for ( int x = 0 ; x < World.WORLD_CHUNK_SIZE ; x++ ) 
            {
                for ( int z = 0 ; z < World.WORLD_CHUNK_SIZE ; z++ ) 
                {
                    final int blockType = rnd.nextInt( BlockType.MAX_BLOCK_TYPE+1 );
                    chunk.setBlockType( x , World.WORLD_CHUNK_SIZE/2 , z , blockType ); 
                }
            }
            chunk.clearFlags( Chunk.FLAG_EMPTY );
        }
        return chunk;
    }

    @Override
    public void dispose() 
    {
        threadPool.shutdownNow();
        while ( true ) 
        {
            try 
            {
                threadPool.awaitTermination(10, TimeUnit.HOURS);
                break;
            } catch (InterruptedException e) { /* ok... */ }
        }
    }
}