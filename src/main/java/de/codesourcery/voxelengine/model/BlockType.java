package de.codesourcery.voxelengine.model;

import com.badlogic.gdx.math.Vector2;

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
    
    public static boolean isSolidBlock(int bt) 
    {
        switch(bt) {
            case AIR: return false;
            case SOLID_1: return true;
            case SOLID_2: return true;
            case GLOWSTONE: return false;
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
    
    /**
     * Returns the min/max texture UV coordinates for a given block type and block side.
     * 
     * @param bt
     * @param side
     * @param min
     * @param max
     */
    public static void getUVMinMax(int bt,BlockSide side,Vector2 min,Vector2 max) 
    {
        final int ptr = bt * UV_SIZE_PER_BLOCK_IN_FLOATS + side.ordinal()*4;
        min.x = uv[ ptr   ];
        min.y = uv[ ptr+1 ];
        max.x = uv[ ptr+2 ];
        max.y = uv[ ptr+3 ];
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