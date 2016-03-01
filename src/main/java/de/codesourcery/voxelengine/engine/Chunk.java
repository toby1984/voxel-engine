package de.codesourcery.voxelengine.engine;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.log4j.Logger;

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
    
    public final int chunkSize;
    
    public final float blocksize;

    /**
     * Chunk key, <code>null</code> for sub-chunks.
     */
    public final ChunkKey chunkKey;
    
    private boolean markedForUnload;
    private final AtomicBoolean isDisposed = new AtomicBoolean(false);
    private boolean isInUse = false;
    
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
    
    /*
     * Chunk neighbours in right-handed coordinate system (looking along the -z axis).
     * 
     */
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
    
    @Override
    public String toString() {
        return "Chunk ("+chunkKey+"): center="+center+", flags = "+flagsToString()+" , bounds = "+boundingBox;
    }

    @Override
    public boolean equals(Object obj) 
    {
        if ( obj instanceof Chunk ) 
        {
            final Chunk o = (Chunk) obj;
            if ( ! Objects.equals( this.chunkKey , o.chunkKey ) ) {
                return false;
            }
            if ( this.blocksize != o.blocksize ) {
                return false;
            }
            if ( this.chunkSize != o.chunkSize ) {
                return false;
            }
            if ( this.flags != o.flags ) {
                return false;
            }
            if ( ! Objects.equals( this.center , o.center ) ) {
                return false;
            }
            if ( ! Arrays.equals( this.blockTypes , ((Chunk) obj).blockTypes ) ) {
                return false;
            }
            return true;
        }
        return false;
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
        Arrays.fill( blockTypes , BlockType.BLOCKTYPE_AIR );
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
        
        final float halfSize = (chunkSize*blockSize)/2f;
        this.boundingBox = new BoundingBox( center.cpy().sub( halfSize , halfSize , halfSize ) , center.cpy().add( halfSize , halfSize , halfSize ) ); 
        updateIsEmptyFlag();
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
            if ( blockTypes[i] != BlockType.BLOCKTYPE_AIR ) 
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
        return getBlockType( bx , by , bz ) == BlockType.BLOCKTYPE_AIR;
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
        return getBlockType( bx , by , bz ) != BlockType.BLOCKTYPE_AIR;
    }    

    @Override
    public void dispose() 
    {
        if ( mesh != null ) 
        {
            if ( LOG.isDebugEnabled() ) {
                LOG.debug("dispose(): Releasing mesh of chunk "+this);
            }
            mesh.dispose();
            mesh = null;
        }        
    }
    
    /**
     * Returns the block index for a given world coordinate.
     * 
     * @param worldCoords
     * @return
     */
    public int blockIndex(Vector3 worldCoords) 
    {
        final int bx = (int) Math.floor( (worldCoords.x - center.x + World.WORLD_CHUNK_HALF_WIDTH) / World.WORLD_CHUNK_BLOCK_SIZE );
        final int by = (int) Math.floor( (worldCoords.y - center.y + World.WORLD_CHUNK_HALF_WIDTH) / World.WORLD_CHUNK_BLOCK_SIZE );
        final int bz = (int) Math.floor( (worldCoords.z - center.z + World.WORLD_CHUNK_HALF_WIDTH) / World.WORLD_CHUNK_BLOCK_SIZE );
        if ( bx < 0 || by < 0 || bz < 0 || bx >= World.WORLD_CHUNK_SIZE || by >= World.WORLD_CHUNK_SIZE || bz >= World.WORLD_CHUNK_SIZE ) {
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
     * @param key Block key to set
     * 
     * @return <code>key</code> (for chaining)
     */    
    public BlockKey getBlockKey( Vector3 worldCoords , BlockKey key ) 
    {
        final int bx = (int) Math.floor( (worldCoords.x - center.x + World.WORLD_CHUNK_HALF_WIDTH) / World.WORLD_CHUNK_BLOCK_SIZE );
        final int by = (int) Math.floor( (worldCoords.y - center.y + World.WORLD_CHUNK_HALF_WIDTH) / World.WORLD_CHUNK_BLOCK_SIZE );
        final int bz = (int) Math.floor( (worldCoords.z - center.z + World.WORLD_CHUNK_HALF_WIDTH) / World.WORLD_CHUNK_BLOCK_SIZE );
        if ( bx < 0 || by < 0 || bz < 0 || bx >= World.WORLD_CHUNK_SIZE || by >= World.WORLD_CHUNK_SIZE || bz >= World.WORLD_CHUNK_SIZE ) {
            throw new RuntimeException("Internal error, world coordinates "+worldCoords+" maps to "+key+" in chunk "+chunkKey+" @ center "+center);
        }
        key.set( bx , by , bz );
        return key;
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
}