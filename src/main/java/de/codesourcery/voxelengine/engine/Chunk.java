package de.codesourcery.voxelengine.engine;

import java.util.Arrays;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.utils.Disposable;

/**
 * A chunk.
 * 
 * Chunks are cubes made up of voxels. The number of voxels along all axis is equal and the outer 'shell' (outmost layer of voxels) of
 * a chunk always holds the voxels of the adjacent chunk (if any). This is to avoid cross-chunk lookups when meshing a chunk but
 * obviously incurs a write penalty whenever boundary voxels are changed (because now two chunks instead of one need to be updated).  
 *
 * @author tobias.gierke@code-sourcery.de
 */
public class Chunk implements Disposable
{
    /**
     * Flag: Chunk contains only empty blocks.
     */
    public static final int FLAG_EMPTY         = 1<<0;
    
    /**
     * Flag: Chunk has been changed and mesh etc. needs to be rebuild.
     */
    public static final int FLAG_NEEDS_REBUILD = 1<<1;
    
    /**
     * Flag: Chunk must not be unloaded
     */
    public static final int FLAG_DONT_UNLOAD   = 1<<2;
    
    /**
     * Flag: In-memory state of chunk differs from on-disk state, chunk must be saved to disk.
     */
    public static final int FLAG_NEEDS_SAVE = 1<<3;
    
    private static String flagsToString(int value) 
    {
        final StringBuilder buffer = new StringBuilder();
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
        if ( ( value & FLAG_DONT_UNLOAD ) != 0 ) 
        {
            if ( buffer.length() > 0 ) { buffer.append(" | "); }
            buffer.append("PINNED");
        }   
        if ( ( value & FLAG_NEEDS_SAVE ) != 0 ) 
        {
            if ( buffer.length() > 0 ) { buffer.append(" | "); }
            buffer.append("NEEDS_SAVE");
        }         
        return buffer.toString();
    }
    
    public final int chunkSize;
    
    public final float blocksize;

    /**
     * Chunk key, <code>null</code> for sub-chunks.
     */
    public final ChunkKey chunkKey;
    
    /**
     * Bitmask holding chunk flags.
     */
    public int flags;
    
    /**
     * Mesh to render this chunk
     */
    public VoxelMesh mesh;
    
    /**
     * World coordinates of this chunk's center 
     */
    public final Vector3 center=new Vector3();
    
    /**
     * Bounding box of this chunk (EXCLUDES outer 'shell' from neighbouring chunks!).
     */
    public final BoundingBox boundingBox;
    
    /**
     * Block type of each voxel.
     */
    public int[] blockTypes;
    
    /**
     * Timestamp when this chunk has last been accessed (used when trying to determine which chunk is a candidate for unloading).
     */
    public long lastAccessTimestamp = System.currentTimeMillis();
    
    @Override
    public String toString() {
        return "Chunk ("+chunkKey+"): center="+center+", last_access="+lastAccessTimestamp+" , flags = "+flagsToString( flags );
    }
    
    /**
     * Create chunk.
     * 
     * @param center Center coordinates of this chunk in world coordinates 
     * @param chunkSize Chunk size 
     * @param blockSize Size of a single voxel in world space
     */
    public Chunk(ChunkKey key,Vector3 center,int chunkSize,float blockSize) 
    {
        this(key,center,chunkSize,blockSize, new int[ chunkSize*chunkSize*chunkSize ] );
        blockTypes = new int[chunkSize*chunkSize*chunkSize];
        Arrays.fill( blockTypes , BlockType.BLOCKTYPE_EMPTY );
        flags |= FLAG_EMPTY;        
    }
    
    /**
     * Create chunk.
     * 
     * @param center Center coordinates of this chunk in world coordinates 
     * @param chunkSize Chunk size
     * @param blockSize Size of a single voxel in world space
     * @param blockTypes array holding the type of each voxel in this chunk (number of array elements needs to be (chunkSize+2)^3 ) 
     */
    public Chunk(ChunkKey key,Vector3 center,int chunkSize,float blockSize,int[] blockTypes) 
    {
        if ( Integer.bitCount( chunkSize ) != 1 ) 
        {
            throw new IllegalArgumentException("Chunk size needs to be a power of 2, was: "+chunkSize);
        }
        if ( blockSize <= 0 ) {
            throw new IllegalArgumentException("Block size needs to be > 0, was: "+blockSize);
        }
        if ( center == null ) {
            throw new IllegalArgumentException("Chunk center must not be NULL");
        }
        
        this.chunkKey = key;
        this.center.set( center );
        this.chunkSize = chunkSize;
        this.blocksize = blockSize;
        this.blockTypes = blockTypes;
        
        final float halfSize = chunkSize*blockSize/2f;
        this.boundingBox = new BoundingBox( center.cpy().sub( halfSize , halfSize , halfSize ) , center.cpy().add( halfSize , halfSize , halfSize ) ); 
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
     * Set whether this chunk can be unloaded.
     * 
     * @param canUnload
     */
    public void setCanUnload(boolean canUnload) 
    {
        if ( canUnload ) {
            clearFlags( FLAG_DONT_UNLOAD );
        } else {
            setFlags( FLAG_DONT_UNLOAD );
        }
    }
    
    /**
     * Returns whether this chunk can be unloaded.
     * @return
     */
    public boolean canUnload() {
        return ! hasFlags( FLAG_DONT_UNLOAD );
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
            if ( blockTypes[i] != BlockType.BLOCKTYPE_EMPTY ) 
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
    
    /**
     * For a given block coordinate, returns the absolute index 
     * into this chunk's internal data arrays.
     * 
     * @param x
     * @param y
     * @param z
     * @return
     */
    public int blockIndex(int x,int y,int z) {
        return x+y*chunkSize + chunkSize * chunkSize * z;
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
        return mesh == null || hasFlags( Chunk.FLAG_NEEDS_REBUILD ) ;
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
        return getBlockType( bx , by , bz ) == BlockType.BLOCKTYPE_EMPTY;
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
        return getBlockType( bx , by , bz ) != BlockType.BLOCKTYPE_EMPTY;
    }    

    @Override
    public void dispose() 
    {
        if ( mesh != null ) 
        {
            mesh.dispose();
            mesh = null;
        }        
    }
    
    /**
     * Translates world coordinates that lie within this chunk into block indices.
     * 
     * @param worldCoords
     * @return
     */
    public BlockKey getBlockKey( Vector3 worldCoords ) 
    {
        final int bx = (int) Math.floor( (worldCoords.x - center.x + World.WORLD_CHUNK_HALF_WIDTH) / World.WORLD_CHUNK_BLOCK_SIZE );
        final int by = (int) Math.floor( (worldCoords.y - center.y + World.WORLD_CHUNK_HALF_WIDTH) / World.WORLD_CHUNK_BLOCK_SIZE );
        final int bz = (int) Math.floor( (worldCoords.z - center.z + World.WORLD_CHUNK_HALF_WIDTH) / World.WORLD_CHUNK_BLOCK_SIZE );
        final BlockKey key = new BlockKey(bx,by,bz);
        if ( bx < 0 || by < 0 || bz < 0 || bx >= World.WORLD_CHUNK_SIZE || by >= World.WORLD_CHUNK_SIZE || bz >= World.WORLD_CHUNK_SIZE ) {
            throw new RuntimeException("Internal error, world coordinates "+worldCoords+" maps to "+key+" in chunk "+chunkKey+" @ center "+center);
        }
        return key;
    }
}