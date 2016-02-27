package de.codesourcery.voxelengine.engine;

/**
 * Interface holding constants of all block types.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public interface BlockType 
{
    /**
     * Block is empty.
     */
    public static final int BLOCKTYPE_AIR = 0;
    
    public static final int BLOCKTYPE_SOLID_1 = 1;
    public static final int BLOCKTYPE_SOLID_2 = 2;

    public static final int MAX_BLOCK_TYPE = BLOCKTYPE_SOLID_2;
}