package de.codesourcery.voxelengine.model;

import de.codesourcery.voxelengine.engine.BlockSide;

/**
 * AUTO-GENERATED FILE, DO NOT EDIT.
 *
 * Edit BlockType.template file instead !!
 *
 * @author tobias.gierke@code-sourcery.de
 */
public final class BlockType 
{
    private static final int UV_SIZE_PER_BLOCK_IN_FLOATS = 6*4;
    
    public static final int AIR = 0;
    public static final int SOLID_1 = 1;
    public static final int SOLID_2 = 2;
    public static final int GLOWSTONE = 3;
    
    public static final int MAX_BLOCK_TYPE = 3;
    
    private static final float[] uv = new float[] 
    {
      0.0f,0.0f,0.0f,0.0f,0.0f,0.0f,0.0f,0.0f,
      0.0f,0.0f,0.0f,0.0f,0.0f,0.0f,0.0f,0.0f,
      0.0f,0.0f,0.0f,0.0f,0.0f,0.0f,0.0f,0.0f,
      9.765625E-4f,9.765625E-4f,0.12597656f,0.12597656f,0.12695312f,9.765625E-4f,0.25195312f,0.12597656f,
      0.2529297f,9.765625E-4f,0.3779297f,0.12597656f,0.37890625f,9.765625E-4f,0.50390625f,0.12597656f,
      0.5048828f,9.765625E-4f,0.6298828f,0.12597656f,0.6308594f,9.765625E-4f,0.7558594f,0.12597656f,
      0.75683594f,9.765625E-4f,0.88183594f,0.12597656f,9.765625E-4f,0.12695312f,0.12597656f,0.25195312f,
      0.12695312f,0.12695312f,0.25195312f,0.25195312f,0.2529297f,0.12695312f,0.3779297f,0.25195312f,
      0.37890625f,0.12695312f,0.50390625f,0.25195312f,0.5048828f,0.12695312f,0.6298828f,0.25195312f,
      0.6308594f,0.12695312f,0.7558594f,0.25195312f,0.75683594f,0.12695312f,0.88183594f,0.25195312f,
      9.765625E-4f,0.2529297f,0.12597656f,0.3779297f,0.12695312f,0.2529297f,0.25195312f,0.3779297f,
      0.2529297f,0.2529297f,0.3779297f,0.3779297f,0.37890625f,0.2529297f,0.50390625f,0.3779297f 
    };
    
    public static void getTextureCoordinates(int bt,BlockSide side,float[] destination,int destinationOffset) 
    {
        final int ptr = bt *  UV_SIZE_PER_BLOCK_IN_FLOATS + side.ordinal()*4;
        destination[ destinationOffset   ] = uv[ptr];
        destination[ destinationOffset+1 ] = uv[ptr+1];
        destination[ destinationOffset+2 ] = uv[ptr+2];
        destination[ destinationOffset+3 ] = uv[ptr+3];
    }
    
    public static boolean isSolidBlock(int bt) 
    {
        switch(bt) {
            case AIR: return false;
            case SOLID_1: return true;
            case SOLID_2: return true;
            case GLOWSTONE: return true;
            default:
                throw new RuntimeException("Unhandled case: "+bt);
        }
    }
    
    public static boolean isNonSolidBlock(int bt) {
        return ! isSolidBlock( bt );
    }    
    
    public static boolean emitsLight(int bt) 
    {
        switch(bt) {
            case AIR: return false;
            case SOLID_1: return false;
            case SOLID_2: return false;
            case GLOWSTONE: return true;
            default:
                throw new RuntimeException("Unhandled case: "+bt);
        }            
    }
    
    public static float getU0(int bt,BlockSide side) {
        return uv[ bt * UV_SIZE_PER_BLOCK_IN_FLOATS + side.ordinal()*4 ];
    }
    
    public static float getV0(int bt,BlockSide side) {
        return uv[ bt * UV_SIZE_PER_BLOCK_IN_FLOATS + side.ordinal()*4 + 1];
    }
    
    public static float getU1(int bt,BlockSide side) {
        return uv[ bt * UV_SIZE_PER_BLOCK_IN_FLOATS + side.ordinal()*4 + 2 ];    
    }
    
    public static float getV1(int bt,BlockSide side) {
        return uv[ bt * UV_SIZE_PER_BLOCK_IN_FLOATS + side.ordinal()*4 + 3 ];     
    }    
    
    public static byte getEmittedLightLevel(int bt) 
    {
        switch(bt) 
        {
            case AIR: return (byte) 0;
            case SOLID_1: return (byte) 0;
            case SOLID_2: return (byte) 0;
            case GLOWSTONE: return (byte) 15;
            default:
                throw new RuntimeException("Unhandled case: "+bt);
        }    
    }
}