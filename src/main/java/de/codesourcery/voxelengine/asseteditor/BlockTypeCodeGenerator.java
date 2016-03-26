package de.codesourcery.voxelengine.asseteditor;

import de.codesourcery.voxelengine.asseteditor.AssetConfig.BlockVisitor;

public class BlockTypeCodeGenerator extends CodeGenerator
{
    // variables to expand
    private static final String VAR_BLOCK_TYPE_IDS = "BLOCK_TYPE_IDS";
    private static final String VAR_MAX_BLOCK_TYPE= "MAX_BLOCK_TYPE";
    private static final String VAR_IS_SOLID_BLOCK_SWITCH = "IS_SOLID_BLOCK_SWITCH";
    private static final String VAR_EMITS_LIGHT_SWITCH = "EMITS_LIGHT_SWITCH";
    private static final String VAR_EMITTED_LIGHT_LEVEL_SWITCH = "EMITTED_LIGHT_LEVEL_SWITCH";
    
    protected String loadDefaultTemplate() 
    {
        return loadDefaultTemplate("/blockeditor/BlockType.template");
    }
    
    public BlockTypeCodeGenerator() 
    {
        this( null );
    }    
    
    public BlockTypeCodeGenerator(String template) 
    {
        super(template);
    }
    
    protected CharSequence expandVariable(CharSequence name,AssetConfig config, String indentString) 
    {
        switch( name.toString() ) 
        {
            case VAR_CLASSNAME:                  return expandClassname(indentString);
            case VAR_PACKAGE:                    return expandPackage(indentString);
            case VAR_BLOCK_TYPE_IDS:             return expandBlockTypeIds(config,indentString);
            case VAR_MAX_BLOCK_TYPE:             return expandMaxBlockType(config,indentString);
            case VAR_UV_COORDINATES:             return expandUVCoordinates(config,indentString);
            case VAR_IS_SOLID_BLOCK_SWITCH:      return expandIsSolidBlockSwitch(config,indentString);
            case VAR_EMITS_LIGHT_SWITCH:         return expandEmitsLightSwitch(config,indentString);
            case VAR_EMITTED_LIGHT_LEVEL_SWITCH: return expandEmittedLightLevelSwitch(config,indentString);
            default:
                throw new RuntimeException("Internal error,unhandled variable "+name);
        }
    }
    
    private CharSequence expandMaxBlockType(AssetConfig config,String indentString) 
    {
        return Integer.toString( config.blocks.stream().mapToInt( bd -> bd.blockType ).max().orElse(0) );
    }

    private CharSequence expandClassname(String indentString) 
    {
        return className;
    }    
    
    private CharSequence expandPackage(String indentString) 
    {
        return packageName;
    }
    
    private CharSequence expandBlockTypeIds(AssetConfig config, String indentString) 
    {
        final StringBuilder buffer = new StringBuilder();
        config.visitBlocksByAscendingTypeId( (bd,isFirst,isLast) -> 
        {
            buffer.append( isFirst ? "" : indentString).append("public static final int ").append( getBlockTypeConstantName( bd ) )
            .append( " = " ).append( bd.blockType );
            buffer.append( ";" );                
            if ( ! isLast ) {
                buffer.append( "\n" );                
            }
        });
        return buffer;
    }
    
    private static String getBlockTypeConstantName(BlockDefinition bd) {
        return bd.name.toUpperCase();
    }
    
    private CharSequence expandUVCoordinates(AssetConfig config, String indentString) 
    {
        final StringBuilder buffer = new StringBuilder();
        
        final BlockVisitor c = new BlockVisitor() 
        {
            private int floatsThisLine = 0;
            
            @Override
            public void visit(BlockDefinition bd,boolean isFirst,boolean isLast) 
            {
                for (int i = 0 ; i < 6 ; i++) 
                {
                    final boolean lastSide = (i+1) == 6;
                    final BlockSideDefinition def = bd.sides[ i ];
                    buffer.append( Float.toString( def.texture.u0 ) ).append("f,");
                    buffer.append( Float.toString( def.texture.v0 ) ).append("f,");
                    buffer.append( Float.toString( def.texture.u1 ) ).append("f,");
                    buffer.append( Float.toString( def.texture.v1 ) ).append("f");
                    floatsThisLine+= 4;
                    if ( floatsThisLine >= 8 ) 
                    {
                        final boolean last = isLast && lastSide;
                        if ( ! last ) {
                            buffer.append(",\n");
                        }
                        if ( ! last ) {
                            buffer.append( indentString );
                        }
                        floatsThisLine = 0;
                    } else if ( ! lastSide ) {
                        buffer.append(",");
                    }
                }
            }
        };
        config.visitBlocksByAscendingTypeId( c );
        return buffer.length() == 0 ? buffer : buffer; 
    }    
    
    private CharSequence expandIsSolidBlockSwitch(AssetConfig config, String indentString) 
    {
        final StringBuilder buffer = new StringBuilder();
        config.visitBlocksByAscendingTypeId( (bd,isFirst,isLast) -> 
        {
            buffer.append( isFirst ? "" : indentString).append("case ").append( getBlockTypeConstantName( bd ) ).append(": ");
            if ( bd.opaque ) {
                buffer.append("return true;");
            } else {
                buffer.append("return false;");
            }
            if ( ! isLast ) {
                buffer.append("\n");
            }
        });
        return buffer;
    }    
    
    private CharSequence expandEmitsLightSwitch(AssetConfig config, String indentString) {
        final StringBuilder buffer = new StringBuilder();
        config.visitBlocksByAscendingTypeId( (bd,isFirst,isLast) -> 
        {
            buffer.append( isFirst ? "" : indentString ).append("case ").append( getBlockTypeConstantName( bd ) ).append(": ");
            if ( bd.emitsLight ) {
                buffer.append("return true;");
            } else {
                buffer.append("return false;");
            }
            if ( ! isLast ) {
                buffer.append("\n");
            }            
        });
        return buffer;
    }     
    
    private CharSequence expandEmittedLightLevelSwitch(AssetConfig config, String indentString) 
    {
        final StringBuilder buffer = new StringBuilder();
        config.visitBlocksByAscendingTypeId( (bd,isFirst,isLast) -> 
        {
            buffer.append( isFirst ? "" : indentString ).append("case ").append( getBlockTypeConstantName( bd ) ).append(": ");
            final int lightLevel = bd.emitsLight ? bd.lightLevel : 0;
            buffer.append("return (byte) ").append( lightLevel ).append(";");
            if ( ! isLast ) {
                buffer.append("\n");
            }            
        });    
        return buffer;
    }      
}