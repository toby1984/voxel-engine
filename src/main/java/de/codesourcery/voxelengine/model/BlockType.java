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
    public static final int AIR = 0;
    
    public static final int SOLID_1 = 1;
    public static final int SOLID_2 = 2;
    public static final int GLOWSTONE = 3;

    public static final int MAX_BLOCK_TYPE = 3;
    
    public static boolean isSolidBlock(int bt) {
        return bt != BlockType.AIR;
    }
    
    public static boolean isNonSolidBlock(int bt) {
        return bt == BlockType.AIR;
    }    
    
    public static boolean emitsLight(int bt) {
        return bt == GLOWSTONE;
    }
    
    public static byte getEmittedLightLevel(int bt) {
        return bt == GLOWSTONE ? Chunk.LIGHTLEVEL_TORCH : 0;
    }
}