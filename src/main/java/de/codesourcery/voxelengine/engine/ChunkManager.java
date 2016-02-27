package de.codesourcery.voxelengine.engine;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
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
    
    private final Map<ChunkKey,Chunk> chunks = new HashMap<>();

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
        Chunk result = chunks.get( key );
        if ( result == null ) 
        {
            result = loadChunk(key);
            if ( result == null ) 
            {
                if ( ! createMissing ) {
                    return null;
                }
                result = generateChunk( key , createMissing );
            }
            chunks.put( key , result );
        }
        return result; 
    }
    
    public void unloadChunk(Chunk chunk) 
    {
        if ( LOG.isDebugEnabled() ) {
            LOG.debug("unloadChunk(): Unloading chunk "+chunk);
        }
        try 
        {
            if ( chunk.needsSave() ) {
                saveChunk( chunk );
            }
        } 
        catch(Exception e) 
        {
            LOG.error("unloadChunk(): Failed for "+chunk,e);
        } 
        finally {
            chunks.remove( chunk.chunkKey );
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
        catch (Exception e) 
        {
            LOG.error("loadChunk(): Failed to load chunk "+key+" from "+file.getAbsolutePath(),e);
            return null;
        }
    }
    
    public int getLoadedChunkCount() {
        return chunks.size();
    }
    
    private Chunk generateChunk(ChunkKey key,boolean recurse) {
        
        if ( LOG.isDebugEnabled() ) {
            LOG.debug("generateChunk(): Generating "+key);
        }
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
            final int middle = World.WORLD_CHUNK_SIZE/2;
//            chunk.setBlockType( middle , middle , middle , BlockType.BLOCKTYPE_SOLID_1 ); 
            final int yMin = middle -2;
            final int yMax = middle +2;
            for ( int y = yMin ; y <= yMax ; y++ ) {
                for ( int x = 0 ; x < World.WORLD_CHUNK_SIZE ; x++ ) 
                {
                    for ( int z = 0 ; z < World.WORLD_CHUNK_SIZE ; z++ ) 
                    {
                        final int blockType = rnd.nextInt( BlockType.MAX_BLOCK_TYPE+1 );
                        chunk.setBlockType( x , y , z , blockType ); 
                    }
                }
            }
            chunk.clearFlags( Chunk.FLAG_EMPTY );
        }
        return chunk;
    }

    @Override
    public void dispose() 
    {
        for ( ChunkKey key : new ArrayList<>( chunks.keySet() ) ) 
        {
            unloadChunk( chunks.get( key ) );
        }
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