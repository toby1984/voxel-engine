package de.codesourcery.voxelengine.engine;

import de.codesourcery.voxelengine.model.Item;
import de.codesourcery.voxelengine.model.ItemType;

public class ItemFactory 
{
    public Item createItem(int itemType) 
    {
        final Item result = new Item( itemType  , ItemType.getDisplayName( itemType ) );
        result.setCanDestroyBlock( ItemType.canDestroyBlock( itemType ) );
        if ( ItemType.canCreateBlock( itemType ) ) {
            result.setCreatedBlockType( ItemType.getCreatedBlockType( itemType ) );
        }
        return result;
    }
}
