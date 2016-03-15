package de.codesourcery.voxelengine.engine;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import org.apache.commons.lang3.Validate;
import org.apache.log4j.Logger;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.LongMap;
import com.badlogic.gdx.utils.LongMap.Values;

import de.codesourcery.voxelengine.engine.TaskScheduler.Task;
import de.codesourcery.voxelengine.model.BlockType;
import de.codesourcery.voxelengine.model.Chunk;
import de.codesourcery.voxelengine.model.ChunkKey;
import de.codesourcery.voxelengine.model.World;

/**
 * Responsible of loading and unloading (=saving) of chunks.
 *
 * <p>This implementation uses the {@link TaskScheduler} to asynchronously
 * load/unload chunk data. Loading chunks runs as {@link TaskScheduler.Prio high-priority tasks}
 * while unloading (saving chunks) is a low-priority one.</p>
 * 
 * <p>TODO: The current implementation operates on individual chunks, it might be
 * more efficient to group chunks into regions and operate on multiple chunks at once.</p>
 *  
 * @author tobias.gierke@code-sourcery.de
 */
public class ChunkManager implements Disposable
{
    private static final Logger LOG = Logger.getLogger(ChunkManager.class);

    public static final boolean CLEAR_CHUNK_DIR_ON_STARTUP = true;

    private final File chunkDir;

    private final LongMap<Chunk> chunks = new LongMap<>(1000);
    
    private static final int CLEAN_FREQUENCY = 60;
    
    private int cleanCount = CLEAN_FREQUENCY;

    // scheduler used for asynchronous loading/unloading of chunks
    private final TaskScheduler scheduler;
    
    public ChunkManager(File chunkDir,TaskScheduler scheduler) 
    {
        Validate.notNull(chunkDir, "chunkDir must not be NULL");
        Validate.notNull(scheduler, "scheduler must not be NULL");
        this.scheduler = scheduler;
        this.chunkDir = chunkDir;

        if ( CLEAR_CHUNK_DIR_ON_STARTUP ) {
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
            List<Chunk> toRemove = null;
            final Values<Chunk> values = chunks.values();
            while( values.hasNext )
            {
                // hint: cannot remove chunks directly in this loop
                // since will make the Values<> iterator fail
                final Chunk chunk = values.next();
                if ( chunk.isDisposed() ) 
                {
                    if ( toRemove == null ) 
                    {
                        toRemove = new ArrayList<>( 100 );
                    }
                    toRemove.add( chunk );
                }
            }
            if ( toRemove != null ) 
            {
                for ( int i = 0 ; i < toRemove.size() ; i++ ) 
                {
                    final Chunk chunk = toRemove.get(i);
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

    /**
     * Task responsible for asynchronously loading a chunk .
     *
     * @author tobias.gierke@code-sourcery.de
     */
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
    
    // adds chunks to the internal chunk list, linking them
    // with any neighbour chunks if these are already loaded. 
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
                neighbour.setFlags( Chunk.FLAG_NEEDS_REBUILD ); // flag chunk for rebuilding as lighting does not consider not-loaded chunks so this one needs to be re-calculated as well 
            }
            neighbour = chunks.get( key.frontNeighbour() );
            if ( neighbour != null ) {
                chunk.frontNeighbour = neighbour;
                neighbour.backNeighbour = chunk;
                neighbour.setFlags( Chunk.FLAG_NEEDS_REBUILD ); // flag chunk for rebuilding as lighting does not consider not-loaded chunks so this one needs to be re-calculated as well
            }    
            // left+right
            neighbour = chunks.get( key.leftNeighbour() );
            if ( neighbour != null ) {
                chunk.leftNeighbour = neighbour;
                neighbour.rightNeighbour = chunk;
                neighbour.setFlags( Chunk.FLAG_NEEDS_REBUILD ); // flag chunk for rebuilding as lighting does not consider not-loaded chunks so this one needs to be re-calculated as well
            }
            neighbour = chunks.get( key.rightNeighbour() );
            if ( neighbour != null ) {
                chunk.rightNeighbour = neighbour;
                neighbour.leftNeighbour = chunk;
                neighbour.setFlags( Chunk.FLAG_NEEDS_REBUILD ); // flag chunk for rebuilding as lighting does not consider not-loaded chunks so this one needs to be re-calculated as well
            }         
            // top+bottom
            neighbour = chunks.get( key.topNeighbour() );
            if ( neighbour != null ) {
                chunk.topNeighbour = neighbour;
                neighbour.bottomNeighbour = chunk;
                neighbour.setFlags( Chunk.FLAG_NEEDS_REBUILD ); // flag chunk for rebuilding as lighting does not consider not-loaded chunks so this one needs to be re-calculated as well
            }   
            neighbour = chunks.get( key.bottomNeighbour() );
            if ( neighbour != null ) {
                chunk.bottomNeighbour = neighbour;
                neighbour.topNeighbour = chunk;
                neighbour.setFlags( Chunk.FLAG_NEEDS_REBUILD ); // flag chunk for rebuilding as lighting does not consider not-loaded chunks so this one needs to be re-calculated as well
            }      
        }
    }
    
    // removes a chunk from the internal chunk list,unlinking it
    // from any neighbour chunks that are also loaded.
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
        if ( key.equals( -4 , 0 , -1 ) ) { // TODO: Remove debug code !!!
            System.out.println("Loaded watched chunk: "+result);
        }
        if ( result == null ) 
        {
            result = generateChunk( key );
        }
        result.setFlags( Chunk.FLAG_NEEDS_REBUILD );
        return result;
    }

    /**
     * Asynchronously unloads a list of chunks.
     * 
     * <p>Only chunks that are not already marked for unloading 
     * will be processed.</p>
     * 
     * @param chunks
     */
    public void unloadChunks(Collection<Chunk> chunks) 
    {
        final boolean debugEnabled = LOG.isDebugEnabled();
        for ( Chunk chunk : chunks ) 
        {
            if ( chunk.chunkKey.equals( -4 , 0 , -1 ) ) { // TODO: Remove debug code !!!
                System.out.println("UNLOADING watched chunk: "+chunk);
            }
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
    
    static Chunk generateChunk(ChunkKey key) 
    {
        if ( key.y <= 0 ) 
        {
            if ( key.y <= -2 ) {
                return generateSolidChunk(key);
            }
            return generateChunkFromNoise(key);
        }
        return generateEmptyChunk(key);
    }

    private static Chunk generateEmptyChunk(ChunkKey key) {
        final Chunk chunk = new Chunk(key);
        chunk.setNeedsSave( true );
        chunk.setFlags(Chunk.FLAG_EMPTY);
        return chunk;
    }
    
    private static Chunk generateSolidChunk(ChunkKey key) 
    {
        final Chunk chunk = new Chunk(key);
        chunk.setNeedsSave( true );
        for ( int i = 0 ; i < World.CHUNK_SIZE*World.CHUNK_SIZE*World.CHUNK_SIZE ; i++) {
            chunk.blockTypes[i] = BlockType.SOLID_1;
        }
        chunk.updateIsEmptyFlag();
        return chunk;
    }    

    static Chunk generateSimpleChunk(ChunkKey key) {

        final Chunk chunk = new Chunk(key);
        chunk.setNeedsSave( true );

        if ( key.y == 0 ) // create ground plane 
        {
            final int middle = World.CHUNK_SIZE/2;
            for ( int x = 0 ; x < World.CHUNK_SIZE ; x++ ) 
            {
                for ( int z = 0 ; z < World.CHUNK_SIZE ; z++ ) 
                {
                    chunk.setBlockType( x ,middle , z , BlockType.SOLID_2 );
                }
            }
            chunk.updateIsEmptyFlag();
        } else {
            chunk.setFlags( Chunk.FLAG_EMPTY );
        }
        if ( LOG.isDebugEnabled() ) {
            LOG.debug("generateChunk(): Generated "+chunk);
        }
        return chunk;
    }
    
    private static class NoiseHelper {
        
        public final Vector3 point=new Vector3();
        public final float[] data = new float[ World.CHUNK_SIZE*World.CHUNK_SIZE*World.CHUNK_SIZE];
        public final SimplexNoise noise = new SimplexNoise();
    }
    
    private static final ThreadLocal<NoiseHelper> noise = new ThreadLocal<NoiseHelper>() 
    {
        protected NoiseHelper initialValue() 
        {
            return new NoiseHelper();
        }
    };

    static Chunk generateChunkFromNoise(ChunkKey key) 
    {
        final NoiseHelper helper = noise.get();
        helper.noise.setSeed( key.toID()  );
        
        ChunkKey.getChunkCenter( key , helper.point );
        helper.point.sub( World.CHUNK_HALF_WIDTH,World.CHUNK_HALF_WIDTH,World.CHUNK_HALF_WIDTH);
        final float tileSize = 0.7f;
        final int octaveCount = 2;
        final float persistance = 32f;
        helper.noise.createNoise3D( helper.point.x*tileSize  , helper.point.y*tileSize, helper.point.z*tileSize , World.CHUNK_SIZE,tileSize,octaveCount,persistance, helper.data );
        
        final Chunk chunk = new Chunk(key);
        chunk.setNeedsSave( true );
        for ( int x = 0 ; x < World.CHUNK_SIZE ; x++ ) 
        {
            for ( int y = 0 ; y < World.CHUNK_SIZE ; y++ ) 
            {
                for ( int z = 0 ; z < World.CHUNK_SIZE ; z++ ) 
                {
                    final float value;
                    if ( key.y == 0 ) {
                        value = helper.data[ x + y * World.CHUNK_SIZE + z*World.CHUNK_SIZE*World.CHUNK_SIZE] / (y*0.20f+0.1f);
                    } else {
                        value = helper.data[ x + y * World.CHUNK_SIZE + z*World.CHUNK_SIZE*World.CHUNK_SIZE] *1.5f;
                    }
                    if ( value > 0.4f ) 
                    {
                        chunk.setBlockType(x,y,z,BlockType.SOLID_2);
                    } 
                }
            }
        }
        chunk.updateIsEmptyFlag();
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