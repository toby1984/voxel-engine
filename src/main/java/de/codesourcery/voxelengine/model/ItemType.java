package de.codesourcery.voxelengine.model;

import com.badlogic.gdx.math.Vector2;

/**
 * AUTO-GENERATED FILE, DO NOT EDIT.
 *
 * Edit BlockType.template file instead !!
 *
 * @author tobias.gierke@code-sourcery.de
 */
public final class ItemType 
{
    public static final int SOLID1 = 0;
    public static final int ERASER = 1;
    public static final int GLOWSTONE = 2;
    public static final int SOLID2 = 3;
    public static final int WOOD = 4;
    
    public static final int MAX_ITEM_TYPE = 4;
    
    private static final float[] uv = new float[] 
    {
      9.765625E-4f,9.765625E-4f,0.032226562f,0.032226562f,0.033203125f,9.765625E-4f,0.064453125f,0.032226562f,
      0.06542969f,9.765625E-4f,0.09667969f,0.032226562f,0.09765625f,9.765625E-4f,0.12890625f,0.032226562f,
      0.12988281f,9.765625E-4f,0.16113281f,0.032226562f 
    };
    
    /**
     * Returns the min/max texture UV coordinates for a given item type.
     * 
     * @param itemType
     * @param min where min(uv) should be stored
     * @param max where max(uv) should be stored
     */        
    public static void getUVMinMax(int itemType,Vector2 min,Vector2 max) 
    {
        final int ptr = itemType * 4;
        min.x = uv[ ptr   ];
        min.y = uv[ ptr+1 ];
        max.x = uv[ ptr+2 ];
        max.y = uv[ ptr+3 ];
    }    
    
    public static boolean canCreateBlock(int itemType) 
    {
        switch( itemType ) 
        {
          case 0: return true;
          case 1: return false;
          case 2: return true;
          case 3: return true;
          case 4: return true;
          default:
            throw new IllegalArgumentException("Invalid item type id #"+itemType);
        }
    }    
    
    public static boolean canDestroyBlock(int itemType) 
    {
        switch( itemType ) 
        {
          case 0: return false;
          case 1: return true;
          case 2: return false;
          case 3: return false;
          case 4: return false;
          default:
            throw new IllegalArgumentException("Invalid item type id #"+itemType);
        }
    }    
    
    public static int getCreatedBlockType(int itemType) 
    {
        switch( itemType ) 
        {
          case 0: return 1;
          case 2: return 3;
          case 3: return 2;
          case 4: return 4;
          default:
            throw new IllegalArgumentException("Invalid item type id #"+itemType);
        }
    }     
    
    public static String getDisplayName(int itemType) 
    {
        switch( itemType ) 
        {
          case 0: return "Solid1";
          case 1: return "Eraser";
          case 2: return "Glowstone";
          case 3: return "Solid2";
          case 4: return "Wood";
          default:
            throw new IllegalArgumentException("Invalid item type id #"+itemType);
        }    
    }
}