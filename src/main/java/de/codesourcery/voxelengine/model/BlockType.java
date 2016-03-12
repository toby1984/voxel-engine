package de.codesourcery.voxelengine.model;

import de.codesourcery.voxelengine.engine.BlockSide;

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
    
    private static final int UV_SIZE_PER_BLOCK_IN_FLOATS = 6*4;
    
    private static final float[] uv = new float[ MAX_BLOCK_TYPE * UV_SIZE_PER_BLOCK_IN_FLOATS ]; // (u0,v0) , (u1,v1) for all six sides of each block
    
    public static void getTextureCoordinates(int bt,BlockSide side,float[] destination,int destinationOffset) 
    {
        final int ptr = bt *  UV_SIZE_PER_BLOCK_IN_FLOATS;
        destination[ destinationOffset   ] = uv[ptr];
        destination[ destinationOffset+1 ] = uv[ptr+1];
        destination[ destinationOffset+2 ] = uv[ptr+2];
        destination[ destinationOffset+3 ] = uv[ptr+3];
    }
    
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