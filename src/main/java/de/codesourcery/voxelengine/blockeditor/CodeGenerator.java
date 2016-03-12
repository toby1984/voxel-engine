package de.codesourcery.voxelengine.blockeditor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.lang3.StringUtils;

import de.codesourcery.voxelengine.blockeditor.BlockConfig.BlockVisitor;
import de.codesourcery.voxelengine.engine.BlockSide;

public class CodeGenerator 
{
    // variables to expand
    private static final String VAR_BLOCK_TYPE_IDS = "BLOCK_TYPE_IDS";
    private static final String VAR_UV_COORDINATES = "UV_COORDINATES";
    private static final String VAR_IS_SOLID_BLOCK_SWITCH = "IS_SOLID_BLOCK_SWITCH";
    private static final String VAR_EMITS_LIGHT_SWITCH = "EMITS_LIGHT_SWITCH";
    private static final String VAR_EMITTED_LIGHT_LEVEL_SWITCH = "EMITTED_LIGHT_LEVEL_SWITCH";
    
    private final String codeTemplate;
    
    public int spacesPerTab=4;
    
    public CodeGenerator() 
    {
        this( loadDefaultTemplate() );
    }
    
    private static String loadDefaultTemplate() 
    {
        final byte[] buffer = new byte[1024];
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final InputStream in = CodeGenerator.class.getResourceAsStream("/blockeditor/BlockType.template");
        try 
        {
            int bytesRead=0;
            while(  ( bytesRead = in.read( buffer ) ) > 0 ) 
            { 
                out.write( buffer , 0 , bytesRead );
            }
            
            return new String( out.toByteArray() );
        } 
        catch (IOException e) 
        {
            e.printStackTrace();
            throw new RuntimeException(e);
        } 
        finally 
        {
            if ( in != null ) 
            {
                try { in.close(); } catch(IOException e) { /* can't help it */ }
            }
        }
    }
    
    public CodeGenerator(String template) {
        this.codeTemplate = template;
    }
    
    public CharSequence generateCode(BlockConfig config) 
    {
        final StringBuilder result = new StringBuilder();
        final StringBuilder variable = new StringBuilder();
        
        int column=0;
        for (int i = 0 , len = codeTemplate.length() ; i < len ; i++ ) 
        {
            final char c = codeTemplate.charAt( i );
            if ( c == '$' && (i+1) < len && codeTemplate.charAt(i+1) == '{' ) 
            {
                variable.setLength( 0 );
                final String indent = StringUtils.repeat( ' ' , column );
                final int start = i;
                i+=2;
                while ( i < len ) 
                {
                    final char c2 = codeTemplate.charAt( i );
                    if ( c2 == '}' ) {
                        break;
                    }
                     if ( c2 == '\n' ) {
                         throw new RuntimeException("Unterminated variable name around "+start);
                     }
                    variable.append( c2 );
                    i++;
                }
                if ( i >= len ) 
                {
                    throw new RuntimeException("Unterminated variable name at offset "+start);
                }
                if ( StringUtils.isBlank( variable ) ) {
                    throw new RuntimeException("Blank variable name at offset "+start);
                }
                result.append( expandVariable( variable , config , indent ) );
            } 
            else 
            {
                switch( c ) 
                {
                    case '\n': column =  0; break;
                    case '\t': column += spacesPerTab; break;
                    case ' ':  column += 1; break;
                    default:   column += 1;
                }
                result.append( c );
            }
        }
        return result;
    }
    
    private CharSequence expandVariable(CharSequence name,BlockConfig config, String indentString) 
    {
        switch( name.toString() ) {
            case VAR_BLOCK_TYPE_IDS: return expandBlockTypeIds(config,indentString);
            case VAR_UV_COORDINATES: return expandUVCoordinates(config,indentString);
            case VAR_IS_SOLID_BLOCK_SWITCH: return expandIsSolidBlockSwitch(config,indentString);
            case VAR_EMITS_LIGHT_SWITCH: return expandEmitsLightSwitch(config,indentString);
            case VAR_EMITTED_LIGHT_LEVEL_SWITCH: return expandEmittedLightLevelSwitch(config,indentString);
            default:
                throw new RuntimeException("Internal error,unhandled variable "+name);
        }
    }
    
    private CharSequence expandBlockTypeIds(BlockConfig config, String indentString) 
    {
        final StringBuilder buffer = new StringBuilder();
        config.visitByAscendingTypeId( (bd,isFirst,isLast) -> {
            buffer.append( isFirst ? "" : indentString).append("public static final int ").append( getBlockTypeConstantName( bd ) )
            .append( " = " ).append( bd.blockType );
            buffer.append( ";" );                
            if ( ! isLast ) {
                buffer.append( ";\n" );                
            }
        });
        return buffer;
    }
    
    private static String getBlockTypeConstantName(BlockDefinition bd) {
        return bd.name.toUpperCase();
    }
    
    private CharSequence expandUVCoordinates(BlockConfig config, String indentString) 
    {
        final StringBuilder buffer = new StringBuilder();
        
        final BlockSide[] ordered = new BlockSide[ BlockSide.values().length ];
        for ( int i = 0 , len = BlockSide.values().length ; i < len ; i++ ) {
            ordered[i] = BlockSide.getByOrdinal( i );
        }
        
        final BlockVisitor c = new BlockVisitor() 
        {
            private int floatsThisLine = 0;
            
            @Override
            public void visit(BlockDefinition bd,boolean isFirst,boolean isLast) 
            {
                for (int i = 0 , len = ordered.length ; i < len ; i++) 
                {
                    final boolean lastSide = (i+1) == ordered.length;
                    final BlockSide bs = ordered[i];
                    
                    final BlockSideDefinition def = bd.sides[ bs.ordinal() ];
                    buffer.append( def.u0 ).append("f,");
                    buffer.append( def.v0 ).append("f,");
                    buffer.append( def.u1 ).append("f,");
                    buffer.append( def.v1 ).append("f");
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
        config.visitByAscendingTypeId( c );
        return buffer.length() == 0 ? buffer : buffer; 
    }    
    
    private CharSequence expandIsSolidBlockSwitch(BlockConfig config, String indentString) 
    {
        final StringBuilder buffer = new StringBuilder();
        config.visitByAscendingTypeId( (bd,isFirst,isLast) -> 
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
    
    private CharSequence expandEmitsLightSwitch(BlockConfig config, String indentString) {
        final StringBuilder buffer = new StringBuilder();
        config.visitByAscendingTypeId( (bd,isFirst,isLast) -> 
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
    
    private CharSequence expandEmittedLightLevelSwitch(BlockConfig config, String indentString) 
    {
        final StringBuilder buffer = new StringBuilder();
        config.visitByAscendingTypeId( (bd,isFirst,isLast) -> 
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