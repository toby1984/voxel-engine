package de.codesourcery.voxelengine.engine;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
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

    private final ThreadPoolExecutor chunkLoaderPool;
    private final ThreadPoolExecutor chunkUnloaderPool;

    private final Map<ChunkKey,Chunk> chunks = new HashMap<>();
    
    private static final int CLEAN_FREQUENCY = 60;
    
    private int cleanCount = CLEAN_FREQUENCY;

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

        final ThreadFactory threadFactory1 = new ThreadFactory() 
        {
            private final AtomicInteger ID = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable r) 
            {
                final Thread t = new Thread(r);
                t.setDaemon( true );
                t.setName( "chunkloader-"+ID.incrementAndGet());
                return t;
            }
        };

        final ThreadFactory threadFactory2 = new ThreadFactory() 
        {
            private final AtomicInteger ID = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable r) 
            {
                final Thread t = new Thread(r);
                t.setDaemon( true );
                t.setName( "chunkunloader-"+ID.incrementAndGet());
                return t;
            }
        };

        chunkLoaderPool = new ThreadPoolExecutor( 2 , 2 , 10 , TimeUnit.MINUTES , new ArrayBlockingQueue<Runnable>(100) , threadFactory1 , new ThreadPoolExecutor.CallerRunsPolicy() );
        chunkUnloaderPool = new ThreadPoolExecutor( 2 , 2 , 10 , TimeUnit.MINUTES , new ArrayBlockingQueue<Runnable>(100) , threadFactory2 , new ThreadPoolExecutor.CallerRunsPolicy() );
    }

    public static void recursiveDelete(File file) 
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
        Validate.notNull(key, "key must not be NULL");
        while( true ) 
        {
            Chunk result = chunks.get( key );
            if ( result == null || result.isDisposed() ) 
            {
                result = loadOrCreateChunk(key);
                chunks.put( key , result );
                result.setIsInUse( true );
                return result;
            }
            if ( result.isMarkedForUnloading() ) 
            {
                while ( ! result.isDisposed() );
                continue;
            }
            result.setIsInUse( true );
            removeDisposedChunks();            
            return result; 
        }
    }    
    
    private void removeDisposedChunks() 
    {
        if ( --cleanCount < 0 ) 
        {
            int removalCount=0;
            for (Iterator<Entry<ChunkKey, Chunk>> it = chunks.entrySet().iterator(); it.hasNext();) 
            {
                final Entry<ChunkKey, Chunk> entry = it.next();
                if ( entry.getValue().isDisposed() ) {
                    it.remove();
                    removalCount++;
                }
            }
            if ( LOG.isDebugEnabled() ) {
                LOG.debug("removeDisposedChunks(): Removed "+removalCount+" disposed chunks");
            }
            cleanCount = CLEAN_FREQUENCY;
        }
    }

    public List<Chunk> getChunks(Collection<ChunkKey> toLoad) 
    {
        final Set<ChunkKey> missingChunks = new HashSet<>();
        final List<Chunk> result = new ArrayList<>();
        for (ChunkKey key : toLoad ) 
        {
            while( true )
            {
                final Chunk existing = chunks.get( key );
                if ( existing == null || existing.isDisposed() ) {
                    missingChunks.add(key);
                } 
                else 
                {
                    if ( existing.isMarkedForUnloading() ) 
                    {
                        while( ! existing.isDisposed() );
                        continue;
                    }
                    result.add( existing );
                }
                break;
            }
        }

        if ( ! missingChunks.isEmpty() ) 
        {
            final List<Chunk> loaded = new ArrayList<>();
            final CountDownLatch latch = new CountDownLatch( missingChunks.size() );
            for ( final ChunkKey key : missingChunks ) {
                chunkLoaderPool.submit( new ChunkLoader( key , loaded, latch ) ); 
            }
            while( true ) 
            {
                try {
                    latch.await();
                    break;
                } catch (InterruptedException e) {
                    LOG.error("getChunks(): Caught ",e);
                }
            }
            synchronized(loaded) 
            {
                for ( Chunk chunk : loaded ) {
                    chunks.put( chunk.chunkKey , chunk );
                }
            }
            result.addAll( loaded );
        }
        removeDisposedChunks();        
        return result;
    }

    protected final class ChunkLoader implements Runnable 
    {
        private final ChunkKey toLoad;
        private final List<Chunk> chunkList;
        private final CountDownLatch latch;

        public ChunkLoader(ChunkKey toLoad,List<Chunk> chunkList,CountDownLatch latch) {
            this.toLoad = toLoad;
            this.chunkList = chunkList;
            this.latch = latch;
        }

        @Override
        public void run() 
        {
            try 
            {
                final Chunk chunk = loadOrCreateChunk( toLoad );
                synchronized( chunkList ) 
                {
                    chunkList.add( chunk );
                }
            } 
            finally 
            {
                latch.countDown();
            }
        }
    }

    private Chunk loadOrCreateChunk(ChunkKey key) 
    {
        Chunk result = loadChunk(key);
        if ( result == null ) 
        {
            result = generateChunk( key );
        }
        result.setFlags( Chunk.FLAG_NEEDS_REBUILD );
        return result;
    }

    public void unloadChunks(Collection<Chunk> chunks) 
    {
        final boolean debugEnabled = LOG.isDebugEnabled();
        for ( Chunk chunk : chunks ) 
        {
            if ( ! chunk.isMarkedForUnloading() ) 
            {
                chunk.markForUnloading();
                if ( debugEnabled ) {
                    LOG.debug("unloadChunks(): Marked for unload: "+chunk);
                }
                chunkUnloaderPool.submit( new ChunkUnloader( chunk ) );
            }
        }
    }

    protected final class ChunkUnloader implements Runnable 
    {
        private final Chunk chunk;

        public ChunkUnloader(Chunk chunk) {
            this.chunk = chunk;
        }

        @Override
        public void run() 
        {
            try 
            {
                if ( chunk.needsSave() ) 
                {
                    saveChunk( chunk );
                }
            } 
            catch(Exception e) 
            {
                LOG.error("unloadChunk(): Failed for "+chunk,e);
            } 
            finally 
            {
                try {
                    chunk.dispose();
                } finally {
                    chunk.markDisposed();
                }
            }
        }
    }

    private void saveChunk(Chunk chunk) 
    {
        final File file = getFile( chunk.chunkKey );
        if ( LOG.isDebugEnabled() ) {
            LOG.debug("saveChunk(): Saving chunk to "+file.getAbsolutePath()+": "+chunk);
        }
        try 
        {
            // clear flag BEFORE saving chunk, otherwise flag gets saved as well
            new ChunkFile( file ).store( chunk );
        } 
        catch (IOException e) 
        {
            LOG.error("saveChunk(): Failed to save chunk to "+file.getAbsolutePath()+": "+chunk,e);
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
            final Chunk result = new ChunkFile( file ).load();
            if ( LOG.isDebugEnabled() ) {
                LOG.debug("loadChunk(): Loaded from disk: "+result);
            }            
            if ( ! result.chunkKey.equals( key ) ) {
                LOG.error("loadChunk(): Trying to load chunk "+key+" from "+file.getAbsolutePath()+" yielded different chunk: "+result);
                throw new RuntimeException("Trying to load chunk "+key+" from "+file.getAbsolutePath()+" yielded different chunk: "+result);
            }
            return result;
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

    static Chunk generateChunk(ChunkKey key) {

        final float centerX = key.x* World.WORLD_CHUNK_WIDTH;
        final float centerY = key.y* World.WORLD_CHUNK_WIDTH;
        final float centerZ = key.z* World.WORLD_CHUNK_WIDTH;

        final Vector3 center = new Vector3(centerX,centerY,centerZ);

        final Chunk chunk = new Chunk(key, center ,World.WORLD_CHUNK_SIZE ,World.WORLD_CHUNK_BLOCK_SIZE );
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
            chunk.updateIsEmptyFlag();
        } else {
            chunk.setFlags( Chunk.FLAG_EMPTY );
        }
        chunk.setFlags( Chunk.FLAG_NEEDS_REBUILD );
        if ( LOG.isDebugEnabled() ) {
            LOG.debug("generateChunk(): Generated "+chunk);
        }
        return chunk;
    }

    @Override
    public void dispose() 
    {
        chunks.values().stream().filter( c -> ! c.isMarkedForUnloading() ).forEach( c -> c.setIsInUse( false ) );
        
        unloadChunks( chunks.values() );
        
        int disposedCount = 0;
        do {
            disposedCount = 0;
            for ( Chunk chunk : chunks.values() ) 
            {
                if ( chunk.isDisposed() ) {
                    disposedCount++;
                }
            }
        } while ( disposedCount != chunks.size() );
        
        chunkLoaderPool.shutdownNow();
        chunkUnloaderPool.shutdownNow();
        while ( true ) 
        {
            try 
            {
                chunkLoaderPool.awaitTermination(10, TimeUnit.HOURS);
                break;
            } catch (InterruptedException e) { /* ok... */ }
        }
    }
}