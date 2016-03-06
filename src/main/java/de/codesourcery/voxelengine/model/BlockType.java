package de.codesourcery.voxelengine.model;

/**
 * Interface holding constants for all block types.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public final class BlockType 
{
    /**
     * Block is empty.
     */
    public static final int BLOCKTYPE_AIR = 0;
    
    public static final int BLOCKTYPE_SOLID_1 = 1;
    public static final int BLOCKTYPE_SOLID_2 = 2;

    public static final int MAX_BLOCK_TYPE = BLOCKTYPE_SOLID_2;
    
    public static boolean isSolidBlock(int bt) {
        return bt != BlockType.BLOCKTYPE_AIR;
    }
    
    public static boolean isNonSolidBlock(int bt) {
        return bt == BlockType.BLOCKTYPE_AIR;
    }    
}