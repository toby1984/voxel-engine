package de.codesourcery.voxelengine.asseteditor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import de.codesourcery.voxelengine.asseteditor.AssetConfig.ItemVisitor;

public class ItemTypeCodeGenerator extends CodeGenerator 
{
    protected static final String VAR_ITEM_TYPE_IDS = "ITEM_TYPE_IDS";
    protected static final String VAR_MAX_ITEM_TYPE = "MAX_ITEM_TYPE";
    protected static final String VAR_CAN_CREATE_BLOCK= "CAN_CREATE_BLOCK";
    protected static final String VAR_CAN_DESTROY_BLOCK = "CAN_DESTROY_BLOCK";
    protected static final String VAR_CREATED_BLOCK_TYPE= "CREATED_BLOCK_TYPE";
    protected static final String VAR_DISPLAY_NAME = "DISPLAY_NAME";
    
    protected String loadDefaultTemplate() 
    {
        return loadDefaultTemplate("/blockeditor/ItemType.template");
    }
    
    @Override
    protected CharSequence expandVariable(CharSequence name, AssetConfig config,String indentString) 
    {
        switch(name.toString()) 
        {
            case VAR_PACKAGE: return packageName;
            case VAR_CLASSNAME: return className;
            case VAR_ITEM_TYPE_IDS: return expandItemTypeIds(config,indentString);
            case VAR_MAX_ITEM_TYPE: return expandMaxItemType(config,indentString);
            case VAR_UV_COORDINATES: return expandUVCoordinates(config,indentString);
            case VAR_CAN_CREATE_BLOCK: return expandCanCreateBlock(config,indentString);
            case VAR_CAN_DESTROY_BLOCK: return expandCanDestroyBlock(config,indentString);
            case VAR_CREATED_BLOCK_TYPE: return expandCreatedBlockType(config,indentString);
            case VAR_DISPLAY_NAME: return expandDisplayName(config,indentString);
            default:
                throw new RuntimeException("Unhandled variable: ${"+name+"}");
        }
    }

    private CharSequence expandCreatedBlockType(AssetConfig config,String indentString) 
    {
        return createSwitchBody( item -> item.canCreateBlock ? Integer.toString( item.createdBlock.blockType ) : null , config , indentString );
    }
    
    private CharSequence expandDisplayName(AssetConfig config,String indentString) 
    {
        return createSwitchBody( item -> '"'+ item.name + '"' , config , indentString );
    }    

    private CharSequence expandCanDestroyBlock(AssetConfig config,String indentString) 
    {
        return createSwitchBody( item -> Boolean.toString( item.canDestroyBlock ) , config , indentString );
    }

    private CharSequence expandCanCreateBlock(AssetConfig config, String indentString) 
    {
        return createSwitchBody( item -> Boolean.toString( item.canCreateBlock ) , config , indentString );
    }
    
    private CharSequence createSwitchBody(Function<ItemDefinition,String> extractor,AssetConfig config,String indentString) 
    {
        final StringBuilder result = new StringBuilder();
        config.visitItemsByAscendingTypeId( (item,isFirst,isLast) -> 
        {
            final String value = extractor.apply( item );
            if ( value != null ) 
            {
                if ( ! isFirst ) {
                    result.append( indentString );
                }
                result.append("case ").append(item.itemId).append(": return ").append( value ).append(";");
                if ( ! isLast ) {
                    result.append( "\n" );
                }
            }
        });
        return result;
    }

    private CharSequence expandUVCoordinates(AssetConfig config,String indentString) 
    {
        final List<TextureConfig> list = new ArrayList<>();
        final ItemVisitor visitor = (item,isFirst,isLast) -> 
        {
            list.add( item.texture );
        };
        config.visitItemsByAscendingTypeId( visitor );
        return super.expandUVCoordinates( list , indentString );
    }

    private CharSequence expandItemTypeIds(AssetConfig config,String indentString) 
    {
        final StringBuilder buffer = new StringBuilder();
        config.visitItemsByAscendingTypeId( (item,isFirst,isLast) -> 
        {
            buffer.append( isFirst ? "" : indentString).append("public static final int ").append( getItemTypeConstantName( item ) )
            .append( " = " ).append( item.itemId );
            buffer.append( ";" );                
            if ( ! isLast ) {
                buffer.append( "\n" );                
            }
        });
        return buffer;        
    }
    
    private String getItemTypeConstantName(ItemDefinition item) {
        return item.name.toUpperCase().replace(' ', '_' );
    }

    private CharSequence expandMaxItemType(AssetConfig config,String indentString) {
        return Integer.toString( config.items.stream().mapToInt( item -> item.itemId ).max().orElse(0) );
    }    
}