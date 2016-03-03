package de.codesourcery.voxelengine.engine;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import org.apache.commons.lang3.Validate;
import org.apache.log4j.Logger;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.LongMap;
import com.badlogic.gdx.utils.LongMap.Values;

import de.codesourcery.voxelengine.engine.TaskScheduler.Task;

public class ChunkManager implements Disposable
{
    private static final Logger LOG = Logger.getLogger(ChunkManager.class);

    public static final int MAX_CACHE_SIZE = 32;

    public static final boolean CLEAR_CHUNK_DIR = true;

    private final File chunkDir;

    private final LongMap<Chunk> chunks = new LongMap<>(1000);
    
    private static final int CLEAN_FREQUENCY = 60;
    
    private int cleanCount = CLEAN_FREQUENCY;

    private final TaskScheduler scheduler;
    
    public ChunkManager(File chunkDir,TaskScheduler scheduler) 
    {
        Validate.notNull(chunkDir, "chunkDir must not be NULL");
        Validate.notNull(scheduler, "scheduler must not be NULL");
        this.scheduler = scheduler;
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
        return getChunk( key.toID() );
    }
    
    public Chunk getChunk(long chunkID) 
    {
        while( true ) 
        {
            Chunk result = chunks.get( chunkID );
            if ( result == null || result.isDisposed() ) 
            {
                result = loadOrCreateChunk( ChunkKey.fromID( chunkID ) );
                addChunks( Arrays.asList((result) ) );
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
            final boolean debug = LOG.isDebugEnabled();
            final Values<Chunk> values = chunks.values();
            while( values.hasNext )
            {
                final Chunk chunk = values.next();
                if ( chunk.isDisposed() ) 
                {
                    if ( debug ) {
                        LOG.info("removeDisposedChunks(): Removing disposed chunk: "+chunk);
                    }                    
                    removeChunk(chunk);
                }
            }
            cleanCount = CLEAN_FREQUENCY;
        }
    }

    public List<Chunk> getChunks(Collection<Long> toLoad) 
    {
        final Set<Long> missingChunks = new HashSet<>();
        final List<Chunk> result = new ArrayList<>();
        for (Long key : toLoad ) 
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
            for ( final Long key : missingChunks ) {
                scheduler.add( new ChunkLoader( ChunkKey.fromID( key ) , loaded, latch ) ); 
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
                addChunks(loaded);
            }
            result.addAll( loaded );
        }
        removeDisposedChunks();        
        return result;
    }

    protected final class ChunkLoader extends TaskScheduler.Task
    {
        private final ChunkKey toLoad;
        private final List<Chunk> chunkList;
        private final CountDownLatch latch;

        public ChunkLoader(ChunkKey toLoad,List<Chunk> chunkList,CountDownLatch latch) 
        {
            super(TaskScheduler.Prio.HI);
            this.toLoad = toLoad;
            this.chunkList = chunkList;
            this.latch = latch;
        }

        @Override
        public boolean perform() 
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
            return true;
        }
    }
    
    private void addChunks(List<Chunk> chunksToAdd) 
    {
        for ( int i = 0 , len = chunksToAdd.size() ; i < len ; i++ ) 
        {
            final Chunk chunk = chunksToAdd.get(i);
            final ChunkKey key = chunk.chunkKey;
            chunks.put( key.toID() , chunk );

            // front+back
            Chunk neighbour = chunks.get( key.backNeighbour() );
            if ( neighbour != null ) {
                chunk.backNeighbour = neighbour;
                neighbour.frontNeighbour = chunk;
            }
            neighbour = chunks.get( key.frontNeighbour() );
            if ( neighbour != null ) {
                chunk.frontNeighbour = neighbour;
                neighbour.backNeighbour = chunk;
            }    
            // left+right
            neighbour = chunks.get( key.leftNeighbour() );
            if ( neighbour != null ) {
                chunk.leftNeighbour = neighbour;
                neighbour.rightNeighbour = chunk;
            }
            neighbour = chunks.get( key.rightNeighbour() );
            if ( neighbour != null ) {
                chunk.rightNeighbour = neighbour;
                neighbour.leftNeighbour = chunk;
            }         
            // top+bottom
            neighbour = chunks.get( key.topNeighbour() );
            if ( neighbour != null ) {
                chunk.topNeighbour = neighbour;
                neighbour.bottomNeighbour = chunk;
            }   
            neighbour = chunks.get( key.bottomNeighbour() );
            if ( neighbour != null ) {
                chunk.bottomNeighbour = neighbour;
                neighbour.topNeighbour = chunk;
            }      
        }
    }
    
    private void removeChunk(Chunk current) 
    {
        final ChunkKey key = current.chunkKey;
        chunks.remove( current.chunkKey.toID() );
        
        // front+back
        Chunk neighbour = chunks.get( key.backNeighbour() );
        if ( neighbour != null ) {
            neighbour.frontNeighbour = null;
        }
        neighbour = chunks.get( key.frontNeighbour() );
        if ( neighbour != null ) {
            neighbour.backNeighbour = null;
        }    
        // left+right
        neighbour = chunks.get( key.leftNeighbour() );
        if ( neighbour != null ) {
            neighbour.rightNeighbour = null;
        }
        neighbour = chunks.get( key.rightNeighbour() );
        if ( neighbour != null ) {
            neighbour.leftNeighbour = null;
        }         
        // top+bottom
        neighbour = chunks.get( key.topNeighbour() );
        if ( neighbour != null ) {
            neighbour.bottomNeighbour = null;
        }   
        neighbour = chunks.get( key.bottomNeighbour() );
        if ( neighbour != null ) {
            neighbour.topNeighbour = null;
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
                scheduler.add( new ChunkUnloader( chunk ) );
            }
        }
    }

    protected final class ChunkUnloader extends TaskScheduler.Task
    {
        private final Chunk chunk;

        public ChunkUnloader(Chunk chunk) 
        {
            super(TaskScheduler.Prio.LO);
            this.chunk = chunk;
        }

        @Override
        public boolean perform() 
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
                if ( chunk.needsDisposeOnRenderingThread() ) 
                {
                    scheduler.add( new Task(TaskScheduler.Prio.RENDER) 
                    {
                        @Override
                        public boolean perform() 
                        {
                            try 
                            {
                                chunk.dispose();
                            } finally {
                                chunk.markDisposed();
                            }
                            return true;
                        }
                    });
                } 
                else 
                {
                    try 
                    {
                        chunk.dispose();
                    } finally {
                        chunk.markDisposed();
                    }
                }
            }
            return true;
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
        return chunks.size;
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
            final int yMin = middle-1;
            final int yMax = middle+1;
            
            final float rSquared = (World.WORLD_CHUNK_HALF_WIDTH/4)*(World.WORLD_CHUNK_HALF_WIDTH/4);
            float by = centerY - World.WORLD_CHUNK_HALF_WIDTH + World.WORLD_CHUNK_HALF_BLOCK_SIZE;
            for ( int y = yMin ; y <=  yMax ; y++ , by += World.WORLD_CHUNK_BLOCK_SIZE ) 
            {
                float bx = centerX - World.WORLD_CHUNK_HALF_WIDTH + World.WORLD_CHUNK_HALF_BLOCK_SIZE;
                for ( int x = 0 ; x < World.WORLD_CHUNK_SIZE ; x++ , bx += World.WORLD_CHUNK_BLOCK_SIZE ) 
                {
                    float bz = centerZ - World.WORLD_CHUNK_HALF_WIDTH + World.WORLD_CHUNK_HALF_BLOCK_SIZE;
                    for ( int z = 0 ; z < World.WORLD_CHUNK_SIZE ; z++ , bz += World.WORLD_CHUNK_BLOCK_SIZE ) 
                    {
//                        float dstSquared = (bx-centerX)*(bx-centerX)+(by-centerY)*(by-centerY)+(bz-centerZ)*(bz-centerZ);
//                        if ( dstSquared < rSquared ) {
//                            chunk.setBlockType( x , y , z , BlockType.BLOCKTYPE_SOLID_1 );
//                        }
                        chunk.setBlockType( x ,y , z , rnd.nextInt(BlockType.MAX_BLOCK_TYPE+1));
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
        for ( Chunk chunk : chunks.values() ) 
        {
            if ( chunk.needsSave() ) 
            {
                try {
                    saveChunk( chunk );
                } catch(Exception e) {
                    LOG.error("dispose(): Failed to save chunk "+chunk,e);
                } finally {
                    chunk.dispose();
                }
            }
        }
    }
}