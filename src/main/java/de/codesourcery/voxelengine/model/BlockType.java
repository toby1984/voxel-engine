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
    public static final int WOOD = 4;
    
    public static final int MAX_BLOCK_TYPE = 4;
    
    private static final float[] uv = new float[] 
    {
      0.0f,0.0f,0.0f,0.0f,0.0f,0.0f,0.0f,0.0f,
      0.0f,0.0f,0.0f,0.0f,0.0f,0.0f,0.0f,0.0f,
      0.0f,0.0f,0.0f,0.0f,0.0f,0.0f,0.0f,0.0f,
      9.765625E-4f,9.765625E-4f,0.032226562f,0.032226562f,0.033203125f,9.765625E-4f,0.064453125f,0.032226562f,
      0.06542969f,9.765625E-4f,0.09667969f,0.032226562f,0.09765625f,9.765625E-4f,0.12890625f,0.032226562f,
      0.12988281f,9.765625E-4f,0.16113281f,0.032226562f,0.16210938f,9.765625E-4f,0.19335938f,0.032226562f,
      0.19433594f,9.765625E-4f,0.22558594f,0.032226562f,0.2265625f,9.765625E-4f,0.2578125f,0.032226562f,
      0.25878906f,9.765625E-4f,0.29003906f,0.032226562f,0.29101562f,9.765625E-4f,0.32226562f,0.032226562f,
      0.3232422f,9.765625E-4f,0.3544922f,0.032226562f,0.35546875f,9.765625E-4f,0.38671875f,0.032226562f,
      0.3876953f,9.765625E-4f,0.4189453f,0.032226562f,0.41992188f,9.765625E-4f,0.45117188f,0.032226562f,
      0.45214844f,9.765625E-4f,0.48339844f,0.032226562f,0.484375f,9.765625E-4f,0.515625f,0.032226562f,
      0.51660156f,9.765625E-4f,0.54785156f,0.032226562f,0.5488281f,9.765625E-4f,0.5800781f,0.032226562f,
      0.5810547f,9.765625E-4f,0.6123047f,0.032226562f,0.61328125f,9.765625E-4f,0.64453125f,0.032226562f,
      0.6455078f,9.765625E-4f,0.6767578f,0.032226562f,0.6777344f,9.765625E-4f,0.7089844f,0.032226562f,
      0.70996094f,9.765625E-4f,0.74121094f,0.032226562f,0.7421875f,9.765625E-4f,0.7734375f,0.032226562f 
    };
    
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
    
    public static boolean isSolidBlock(int bt) 
    {
        switch(bt) {
            case AIR: return false;
            case SOLID_1: return true;
            case SOLID_2: return true;
            case GLOWSTONE: return true;
            case WOOD: return true;
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
            case WOOD: return false;
            default:
                throw new RuntimeException("Unhandled case: "+bt);
        }            
    }
    
    public static byte getEmittedLightLevel(int bt) 
    {
        switch(bt) 
        {
            case AIR: return (byte) 0;
            case SOLID_1: return (byte) 0;
            case SOLID_2: return (byte) 0;
            case GLOWSTONE: return (byte) 10;
            case WOOD: return (byte) 0;
            default:
                throw new RuntimeException("Unhandled case: "+bt);
        }    
    }
}