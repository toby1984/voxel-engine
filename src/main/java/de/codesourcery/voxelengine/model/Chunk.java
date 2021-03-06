package de.codesourcery.voxelengine.model;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.log4j.Logger;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.utils.Disposable;

import de.codesourcery.voxelengine.engine.ChunkRenderer;

/**
 * A chunk.
 * 
 * <p>Chunks are cubes made up of voxels. The number of voxels along all axis is equal (=each chunk represents a cube).
 * Chunks keep six pointers to their neighbouring chunks (not all of them may be set though, this depends on
 * whether neighbouring chunks have been loaded).</p>
 * 
 * <p>Blocks within a chunk are addressed in a right-handed coordinate system with the origin block (0,0,0) being in
 * the bottom left-most corner at the "back" of the cube (=when looking along the -z axis).</p>
 * 
 * <p>Meta-data for each block of this chunk is stored in a 1d array for increased cache hit rate and to avoid multiple array bound checks
 * that would be performed if we were to use a multi-dimensional array instead.</p>
 * 
 * <p>The {@link #blockIndex(int,int,int) index} into the internal array is calculated as <code>x+y*chunkSize + chunkSize * chunkSize * z</code></p>
 *
 * @author tobias.gierke@code-sourcery.de
 */
public class Chunk implements Disposable
{
    private static final Logger LOG = Logger.getLogger(Chunk.class);
    
    /**
     * Flag: Chunk contains only empty blocks.
     */
    public static final int FLAG_EMPTY         = 1<<0;
    
    /**
     * Flag: Chunk has been changed and mesh etc. needs to be rebuild.
     */
    public static final int FLAG_NEEDS_REBUILD = 1<<1;
    
    /**
     * Flag: In-memory state of chunk differs from on-disk state, chunk must be saved to disk.
     */
    public static final int FLAG_NEEDS_SAVE = 1<<3;
    
    public static final byte LIGHTLEVEL_MAX = 15; // !!!! Make sure to adjust phong shader when changing the maximum value !!!!
    
    public static final byte LIGHTLEVEL_SUNLIGHT = 15;
    
    /**
     * Chunk key.
     */
    public final ChunkKey chunkKey;
    
    /**
     * Flag to mark that this chunk for unloading.
     * 
     * Once a flag has been marked, this flag cannot be reset.
     */
    private boolean markedForUnload;
    
    private final AtomicBoolean isDisposed = new AtomicBoolean(false);
    
    /**
     * Flag that marks a chunk as being "in-use". 
     * 
     * Chunks must never be unloaded while they're in-use.
     */
    private boolean isInUse = false;
    
    /**
     * Bitmask holding chunk flags.
     */
    public int flags;
    
    /**
     * Mesh to render this chunk
     */
    public ChunkRenderer renderer;
    
    /**
     * World coordinates of this chunk's center 
     */
    public final Vector3 center=new Vector3();
    
    /**
     * Bounding box of this chunk.
     */
    public final BoundingBox boundingBox;
    
     // Chunk neighbours (front/back refer to right-handed coordinate system when looking along the -z axis).
    public Chunk leftNeighbour;
    public Chunk rightNeighbour;
    public Chunk topNeighbour;
    public Chunk bottomNeighbour;
    public Chunk frontNeighbour;
    public Chunk backNeighbour;
    
    /**
     * Block type of each voxel.
     */
    public final int[] blockTypes;
    
    public final byte[] lightLevels;
    
    @Override
    public String toString() {
        return "Chunk ("+chunkKey+"): center="+center+", flags = "+flagsToString()+" , bounds = "+boundingBox;
    }

    // equality check used in unit-testing
    @Override
    public boolean equals(Object obj) 
    {
        if ( obj instanceof Chunk ) 
        {
            return this.chunkKey.equals( ((Chunk) obj).chunkKey );
        }
        return false;
    }
    
    @Override
    public int hashCode() {
    	return this.chunkKey.hashCode();
    }
    
    /**
     * Create chunk.
     * 
     * @param key the coordinates of this chunk
     * @param center Center coordinates of this chunk in world coordinates 
     * @param chunkSize Chunk size 
     * @param blockSize Size of a single voxel in world space
     */
    public Chunk(ChunkKey key) 
    {
        this(key,new int[ World.BLOCKS_IN_CHUNK ], new byte[ World.BLOCKS_IN_CHUNK ] );
        Arrays.fill( blockTypes , BlockType.AIR );
        flags |= FLAG_EMPTY;        
    }
    
    /**
     * Create chunk.
     * 
     * @param key the coordinates of this chunk.
     * @param center Center coordinates of this chunk in world coordinates 
     * @param chunkSize Chunk size
     * @param blockSize Size of a single voxel in world space
     * @param blockTypes array holding the type of each voxel in this chunk (number of array elements needs to be (chunkSize+2)^3 ) 
     */
    public Chunk(ChunkKey key,int[] blockTypes,byte[] lightLevels) 
    {
        if ( center == null ) {
            throw new IllegalArgumentException("Chunk center must not be NULL");
        }
        
        this.chunkKey = key;
        ChunkKey.getChunkCenter( key , this.center );
        this.blockTypes = blockTypes;
        this.lightLevels = lightLevels;
        this.boundingBox = new BoundingBox( 
                center.cpy().sub( World.CHUNK_HALF_WIDTH , World.CHUNK_HALF_WIDTH , World.CHUNK_HALF_WIDTH ) , 
                center.cpy().add( World.CHUNK_HALF_WIDTH , World.CHUNK_HALF_WIDTH , World.CHUNK_HALF_WIDTH ) 
        ); 
        updateIsEmptyFlag();
    }
    
    public boolean emitsLight(int blockIndex) 
    {
        return BlockType.emitsLight( blockTypes[blockIndex] );
    }
    
    /**
     * Sets all blocks to the given light level.
     * 
     * @param level
     */
    public void setLightLevel(byte level) 
    {
       Arrays.fill( this.lightLevels , level ); 
    }
    
    /**
     * Returns the light level for a given block.
     * 
     * @param x
     * @param y
     * @param z
     * @return
     */
    public byte getLightLevel(int x,int y,int z) 
    {
        return lightLevels[ blockIndex(x, y, z ) ];
    }
    
    public byte getLightLevel(int blockIndex) 
    {
        return lightLevels[ blockIndex ];
    }    
    
    /**
     * Calculates the average light level of this block
     * based on the light level of its adjacent blocks.
     * 
     * @param x
     * @param y
     * @param z
     * @return
     */
    public byte calcNeighbourLightLevel(BlockKey original) 
    {
        final BlockKey tmp = new BlockKey();
        
        Chunk chunk = this;
        byte level = 0;
        byte result = 0;
        int neighbourCount = 0;
        int blockIndex;
        // top
        if ( tmp.topOf( original ) ) {
            chunk = this.topNeighbour;
        }
        blockIndex = blockIndex(tmp);
        if ( chunk != null && chunk.isBlockEmpty( blockIndex )) 
        {
            level = chunk.lightLevels[ blockIndex ];
            if ( level > result ) {
                result = level;
            }
            neighbourCount++;
        }
        chunk = this;
        
        // bottom
//        if ( tmp.bottomOf( original ) ) {
//            chunk = this.bottomNeighbour;
//        }
//        blockIndex = blockIndex(tmp);
//        if ( chunk != null && chunk.isBlockEmpty( blockIndex )) 
//        {
//            level = chunk.lightLevels[ blockIndex ];
//            if ( level > result ) {
//                result = level;
//            }
//            neighbourCount++;
//        }
//        chunk = this;        
        
        // left
        if ( tmp.leftOf( original ) ) {
            chunk = this.leftNeighbour;
        }
        blockIndex = blockIndex(tmp);
        if ( chunk != null && chunk.isBlockEmpty( blockIndex ) ) 
        {
            level = chunk.lightLevels[ blockIndex ];
            if ( level > result ) {
                result = level;
            }
            neighbourCount++;
        }         
        chunk = this;         
        
        // right
        if ( tmp.rightOf( original ) ) {
            chunk = this.rightNeighbour;
        }
        blockIndex = blockIndex(tmp);
        if ( chunk != null && chunk.isBlockEmpty( blockIndex )) 
        {
            level = chunk.lightLevels[ blockIndex ];
            if ( level > result ) {
                result = level;
            }
            neighbourCount++;
        }    
        chunk = this;         
        
        // front
        if ( tmp.frontOf( original ) ) {
            chunk = this.frontNeighbour;
        }
        blockIndex = blockIndex(tmp);
        if ( chunk != null && chunk.isBlockEmpty( blockIndex )) 
        {
            level = chunk.lightLevels[ blockIndex ];
            if ( level > result ) {
                result = level;
            }
            neighbourCount++;
        }     
        chunk = this;         
        
        // back
        if ( tmp.backOf( original ) ) {
            chunk = this.backNeighbour;
        }
        blockIndex = blockIndex(tmp);
        if ( chunk != null && chunk.isBlockEmpty( blockIndex ) ) 
        {
            level = chunk.lightLevels[ blockIndex ];
            if ( level > result ) {
                result = level;
            }
            neighbourCount++;
        }          
        return result; // neighbourCount > 0 ? (byte) (sum/neighbourCount) : (byte) 0;
    }
    
    /**
     * Sets the light level for a given block.
     * 
     * @param x
     * @param y
     * @param z
     * @param level
     */
    public void setLightLevel(int x,int y,int z,byte level) 
    {
        lightLevels[ blockIndex(x, y, z ) ] = level;
    }    
    
    public void setLightLevel(int blockIndex,byte level) 
    {
        lightLevels[ blockIndex ] = level;
    }     
    
    /**
     * Returns whether a point is contained in this chunk.
     * 
     * @param point
     * @return
     */
    public boolean contains(Vector3 point) 
    {
        return boundingBox.contains( point );
    }
    
    /**
     * Returns whether this chunk
     * is empty (has no set voxels).
     * 
     * @return
     */
    public boolean isEmpty() {
        return (flags & FLAG_EMPTY) != 0;
    }
    
    /**
     * Returns whether this chunk 
     * is not empty (= has at least one voxel set).
     * 
     * @return
     */
    public boolean isNotEmpty() {
        return (flags & FLAG_EMPTY) == 0;
    }
    
    /**
     * Returns whether the given flag bits are set.
     * 
     * @param bitMask
     * @return
     */
    public boolean hasFlags(int bitMask) {
        return (this.flags & bitMask ) != 0;
    }
    
    /**
     * Clears the given flag bits.
     * @param bitMask
     */
    public void clearFlags(int bitMask) {
        this.flags &= ~bitMask;
    }
    
    /**
     * Sets the given flag bits.
     * 
     * @param bitMask
     */
    public void setFlags(int bitMask) {
        this.flags |= bitMask;
    }
    
    /**
     * Marks this chunk as being 'dirty' (=changed).
     */
    public void markDirty() 
    {
        setFlags( FLAG_NEEDS_REBUILD );
    }
    
    /**
     * Re-calculates the 'isEmpty' flag of
     * this chunk any all of its sub-chunks.
     * 
     * @return <code>true</code> 
     */
    public boolean updateIsEmptyFlag() 
    {
        for ( int i = 0 , len = blockTypes.length ; i < len ; i++ ) 
        {
            if ( blockTypes[i] != BlockType.AIR ) 
            {
                clearFlags( FLAG_EMPTY );
                return false;
            }
        }
        setFlags( FLAG_EMPTY );
        return true;
    }
    
    /**
     * Sets the block type for a given voxel.
     * 
     * See {@link #getBlockType(int, int, int)} for a 
     * @param x
     * @param y
     * @param z
     * @param blockType
     */
    public void setBlockType(int x,int y,int z,int blockType) 
    {
        blockTypes[ blockIndex(x,y,z) ] = blockType;
    }
    
    public void setBlockType(int blockIdx,int blockType) 
    {
        blockTypes[ blockIdx ] = blockType;
    }    
    
    /**
     * Changes the type of a given block and if this block's 
     * light level is different it will mark neighbouring chunks for
     * re-meshing/re-building.
     * 
     * @param blockIdx
     * @param newBlockType
     */
    public void setBlockTypeAndInvalidate(int blockIdx ,int newBlockType) 
    {
        final int oldLightLevel = getLightLevel( blockIdx );
        final int newLightLevel = BlockType.emitsLight( newBlockType ) ? BlockType.getEmittedLightLevel( newBlockType ) : 0;
        setBlockType( blockIdx  , newBlockType );
        if ( oldLightLevel != newLightLevel ) 
        {
        	markNeighboursForRebuild();
        }    	
    }
    
    /**
     * Returns the block type for a given voxel.
     * 
     * <pre>
     * Voxel coordinates use a right-handed coordinate system.
     *  When looking at a chunk from the 'front' , the origin
     *  of the internal coordinate system is at the bottom-leftmost corner
     *  at the back of the chunk like this:
     *   
     *          +y
     *          +---------------+
     *         /|             / |
     *        / |            /  |
     *       /  |           /   |
     *      +--------------+    |
     *      |   |          |    |
     *      |   O----------|----+ +x
     *      |  /           |   /
     *      | /            |  /
     *      |/ +z          | / 
     *      +--------------+
     *                 
     * </pre>    
     * @param x
     * @param y
     * @param z
     * @return
     */
    public int getBlockType(int x,int y,int z) 
    {
        return blockTypes[ blockIndex(x,y,z) ];
    }    
    
    /**
     * Returns the type for a given block index.
     * 
     * @param blockIndex
     * @return
     * 
     * @see #getBlockType(int, int, int)
     * @see #blockIndex(int, int, int)
     */
    public int getBlockType(int blockIndex) 
    {
        return blockTypes[ blockIndex ];
    }     
    
    public int getBlockType(Vector3 worldCoords) {
        return getBlockType( blockIndex( worldCoords ) );
    }
   
    public boolean isBlockEmpty(Vector3 worldCoords) 
    {
        return getBlockType( worldCoords ) == BlockType.AIR;
    }
    
    public boolean isBlockNotEmpty(Vector3 worldCoords) 
    {
        return getBlockType( worldCoords ) != BlockType.AIR;
    }    
    
    /**
     * For a given block coordinate, returns the absolute index 
     * into this chunk's internal data arrays.
     * 
     * @param x
     * @param y
     * @param z
     * @return
     */
    public static int blockIndex(int x,int y,int z) {
        return x+y*World.CHUNK_SIZE + World.CHUNK_SIZE * World.CHUNK_SIZE * z;
    }
    
    /**
     * Extracts the X coordinate from a given block index.
     * 
     * @param blockIndex
     * @return
     * @see #blockIndex(int, int, int)
     */
    public static int blockIndexX(int blockIndex) {
        /*
         *           blockIndex = x + z* World.CHUNK_SIZE * World.CHUNK_SIZE  + y*World.CHUNK_SIZE;                      
         * 
         * HE uses: short index = x + y * World.CHUNK_SIZE * World.CHUNK_SIZE + z * World.CHUNK_SIZE ;
         * 
       int x = index % World.CHUNK_SIZE; 
       int z = index / (World.CHUNK_SIZE*World.CHUNK_SIZE); 
       int y = (index % (World.CHUNK_SIZE*World.CHUNK_SIZE) ) / World.CHUNK_SIZE;         
         */
        return blockIndex % World.CHUNK_SIZE;
    }
    
    /**
     * Extracts the Y coordinate from a given block index.
     * 
     * @param blockIndex
     * @return
     * @see #blockIndex(int, int, int)
     */
    public static int blockIndexY(int blockIndex) {
       return (blockIndex % (World.CHUNK_SIZE*World.CHUNK_SIZE) ) / World.CHUNK_SIZE;         
    }
    
    /**
     * Extracts the Z coordinate from a given block index.
     * 
     * @param blockIndex
     * @return
     * @see #blockIndex(int, int, int)
     */
    public static int blockIndexZ(int blockIndex) {
       return blockIndex / (World.CHUNK_SIZE*World.CHUNK_SIZE); 
    }    
    
    
    public static int blockIndex(BlockKey key) {
        return key.x+key.y*World.CHUNK_SIZE + World.CHUNK_SIZE * World.CHUNK_SIZE * key.z;
    }
    
    public boolean needsSave() 
    {
        return hasFlags( FLAG_NEEDS_SAVE );
    }
    
    public void setNeedsSave(boolean needsSave) 
    {
        if ( needsSave ) {
            setFlags( FLAG_NEEDS_SAVE );
        } else {
            clearFlags( FLAG_NEEDS_SAVE );
        }
    }
    
    /**
     * Returns the distance between this chunk's center and a given point (world coordinates).
     * 
     * @param point
     * @return
     */
    public float distanceSquared(Vector3 point) {
        return center.dst2( point );
    }

    /**
     * Returns whether data associated with this chunk (mesh,lighting,etc.) needs
     * to be rebuild (either because the chunk was just loaded from disk or
     * chunk data has changed in a way that requires a rebuild).
     *  
     * @return
     */
    public boolean needsRebuild() {
        return hasFlags( Chunk.FLAG_NEEDS_REBUILD ) ;
    }
    
    /**
     * Returns whether a given block in this chunk is empty.
     * 
     * @param bx
     * @param by
     * @param bz
     * @return
     */
    public boolean isBlockEmpty(int bx,int by,int bz) {
        return getBlockType( bx , by , bz ) == BlockType.AIR;
    }
    
    public boolean hasTransparency(int bx,int by,int bz) 
    {
        final int bt = getBlockType(bx,by,bz);
        return bt == BlockType.AIR || BlockType.isNonSolidBlock(bt);
    }
    
    public boolean isBlockEmpty(int blockIndex) 
    {
        return blockTypes[ blockIndex ] == BlockType.AIR;
    }    
    
    /**
     * Returns whether a given block in this chunk is empty.
     * 
     * @param key
     * @return
     */
    public boolean isBlockEmpty(BlockKey key) {
        return getBlockType( key.x , key.y , key.z ) == BlockType.AIR;
    }    
    
    /**
     * Returns whether a given block in this chunk is not empty.
     * 
     * @param bx
     * @param by
     * @param bz
     * @return
     */
    public boolean isBlockNotEmpty(int bx,int by,int bz) {
        return getBlockType( bx , by , bz ) != BlockType.AIR;
    }  
    
    /**
     * Returns whether a given block in this chunk is not empty.
     * 
     * @param blockIndex
     * @return
     */
    public boolean isBlockNotEmpty(int blockIndex) {
        return getBlockType( blockIndex ) != BlockType.AIR;
    }     
    
    public boolean isBlockNotEmpty(BlockKey key) {
        return getBlockType( key.x , key.y , key.z ) != BlockType.AIR;
    }     
    
    /**
     * Returns whether this chunk holds onto objects that need
     * to be disposed while on the OpenGL rendering thread.
     * 
     * @return
     * @see #disposeVBO()
     */
    public boolean needsDisposeOnRenderingThread() {
        return renderer != null; // mesh allocates VBOs and thus needs to be discarded on OpenGL thread
    }
    
    /**
     * Dispose of any OpenGL objects this chunk is holding on to.
     * @see #needsDisposeOnRenderingThread()
     */
    public void disposeVBO() {
        if ( renderer != null ) 
        {
            if ( LOG.isDebugEnabled() ) {
                LOG.debug("dispose(): Releasing mesh of chunk "+this);
            }
            renderer.dispose();
            renderer = null;
        }  
    }

    /**
     * Dispose this chunk.
     * 
     * <p>Make sure to call this method on the OpenGL rendering thread
     * when {@link #needsDisposeOnRenderingThread()} returns <code>true</code>.</p>
     */
    @Override
    public void dispose() 
    {
        disposeVBO();
    }
    
    /**
     * Returns the block index for a given world coordinate.
     * 
     * @param worldCoords
     * @return
     */
    public int blockIndex(Vector3 worldCoords) 
    {
        final int bx = (int) Math.floor( (worldCoords.x - center.x + World.CHUNK_HALF_WIDTH) / World.BLOCK_SIZE );
        final int by = (int) Math.floor( (worldCoords.y - center.y + World.CHUNK_HALF_WIDTH) / World.BLOCK_SIZE );
        final int bz = (int) Math.floor( (worldCoords.z - center.z + World.CHUNK_HALF_WIDTH) / World.BLOCK_SIZE );
        if ( bx < 0 || by < 0 || bz < 0 || bx >= World.CHUNK_SIZE || by >= World.CHUNK_SIZE || bz >= World.CHUNK_SIZE ) {
            throw new RuntimeException("Internal error, world coordinates "+worldCoords+" maps to ("+bx+","+by+","+bz+") in chunk "+chunkKey+" @ center "+center);
        }
        return blockIndex(bx,by,bz);
    }
    
    /**
     * Translates world coordinates that lie within this chunk into block indices.
     * 
     * @param worldCoords
     * @return
     */
    public BlockKey getBlockKey( Vector3 worldCoords ) 
    {
        return getBlockKey( worldCoords , new BlockKey() );
    }
    
    /**
     * Populates a {@link BlockKey} instance using world coordinates.
     * 
     * @param worldCoords
     * @param result Block key to set
     * 
     * @return <code>result</code> (for chaining)
     */    
    public BlockKey getBlockKey( Vector3 worldCoords , BlockKey result ) 
    {
        final int bx = (int) Math.floor( (worldCoords.x - center.x + World.CHUNK_HALF_WIDTH) / World.BLOCK_SIZE );
        final int by = (int) Math.floor( (worldCoords.y - center.y + World.CHUNK_HALF_WIDTH) / World.BLOCK_SIZE );
        final int bz = (int) Math.floor( (worldCoords.z - center.z + World.CHUNK_HALF_WIDTH) / World.BLOCK_SIZE );
        if ( bx < 0 || by < 0 || bz < 0 || bx >= World.CHUNK_SIZE || by >= World.CHUNK_SIZE || bz >= World.CHUNK_SIZE ) {
            throw new RuntimeException("Internal error, world coordinates "+worldCoords+" maps to ("+bx+","+by+","+bz+") in chunk "+chunkKey+" @ center "+center);
        }
        result.set( bx , by , bz );
        return result;
    }
    
    /**
     * Returns the center coordinates of a given block within this chunk.
     *      
     * @param key
     * @return
     */
    public Vector3 getBlockCenter(BlockKey key) {
        return getBlockCenter(key.x,key.y,key.z , new Vector3() );
    }
    
    /**
     * Returns the center coordinates of a given block within this chunk.
     *      
     * @param blockX
     * @param blockY
     * @param blockZ
     * @return
     */
    public Vector3 getBlockCenter(int blockX,int blockY,int blockZ) {
        return getBlockCenter(blockX,blockY,blockZ,new Vector3() );
    }
            
    /**
     * Returns the center coordinates of a given block within this chunk.
     * 
     * @param blockX
     * @param blockY
     * @param blockZ
     * @param result
     * @return
     */
    public Vector3 getBlockCenter(int blockX,int blockY,int blockZ,Vector3 result) 
    {
        result.x = (blockX*World.BLOCK_SIZE ) + center.x - World.CHUNK_HALF_WIDTH;
        result.y = (blockY*World.BLOCK_SIZE ) + center.y - World.CHUNK_HALF_WIDTH;
        result.z = (blockZ*World.BLOCK_SIZE ) + center.z - World.CHUNK_HALF_WIDTH;
        return result;
    }
    
    private String flagsToString() 
    {
        final StringBuilder buffer = new StringBuilder();
        final int value = flags;
        if ( ( value & FLAG_EMPTY) != 0 ) 
        {
            if ( buffer.length() > 0 ) { buffer.append(" | "); }
            buffer.append("EMPTY");
        }
        if ( ( value & FLAG_NEEDS_REBUILD) != 0 ) 
        {
            if ( buffer.length() > 0 ) { buffer.append(" | "); }
            buffer.append("NEEDS_REBUILD");
        }   
        if ( isInUse() )
        {
            if ( buffer.length() > 0 ) { buffer.append(" | "); }
            buffer.append("IN_USE");
        }   
        if ( isMarkedForUnloading() )
        {
            if ( buffer.length() > 0 ) { buffer.append(" | "); }
            buffer.append("MARKED_FOR_UNLOAD");
        } 
        if ( isDisposed() )
        {
            if ( buffer.length() > 0 ) { buffer.append(" | "); }
            buffer.append("DISPOSED");
        }  
        if ( ( value & FLAG_NEEDS_SAVE ) != 0 ) 
        {
            if ( buffer.length() > 0 ) { buffer.append(" | "); }
            buffer.append("NEEDS_SAVE");
        }         
        return buffer.toString();
    }
    
    /**
     * Returns whether this chunk is currently actively used by the system.
     * 
     * @return
     */
    public boolean isInUse() {
        return isInUse;
    }
    
    public void setIsInUse(boolean yesNo) 
    {
        if ( yesNo && markedForUnload )
        {
            LOG.error("Cannot mark chunk as in-use, already marked for unloading or disposed: "+this);
            throw new IllegalStateException("Cannot mark chunk as in-use, already marked for unloading or disposed: "+this);
        }
        this.isInUse = yesNo;
    }
    
    public boolean isMarkedForUnloading() {
        return this.markedForUnload;
    }
    
    public void markForUnloading() 
    {
        if ( isInUse() ) 
        {
            LOG.error("markForUnloading(): Cannot mark chunk that is in-use for unloading: "+this);            
            throw new IllegalStateException("Cannot mark chunk that is in-use for unloading: "+this);
        }        
        this.markedForUnload = true;
    }
    
    public boolean isDisposed() {
        return this.isDisposed.get();
    }
    
    public void markDisposed() {
        this.isDisposed.set(true);
    }
    
    public void markNeighboursForRebuild() 
    {
    	if ( topNeighbour != null ) {
    		topNeighbour.markDirty();
    	}
       	if ( bottomNeighbour != null ) {
    		bottomNeighbour.markDirty();
    	}   
       	if ( leftNeighbour != null ) {
    		leftNeighbour.markDirty();
    	}       
       	if ( rightNeighbour != null ) {
    		rightNeighbour.markDirty();
    	} 
       	if ( frontNeighbour != null ) {
       		frontNeighbour.markDirty();
    	}        
       	if ( backNeighbour != null ) {
       		backNeighbour.markDirty();
    	}        	
    }
}