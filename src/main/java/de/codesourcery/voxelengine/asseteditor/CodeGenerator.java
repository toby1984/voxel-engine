package de.codesourcery.voxelengine.asseteditor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import de.codesourcery.voxelengine.asseteditor.AssetConfig.CodeGenConfig;
import de.codesourcery.voxelengine.asseteditor.CodeGenerator.TextureConfigVisitor;

public abstract class CodeGenerator 
{
    protected static final String VAR_PACKAGE = "PACKAGE";
    protected static final String VAR_CLASSNAME = "CLASSNAME";
    protected static final String VAR_UV_COORDINATES = "UV_COORDINATES";
    
    private final String codeTemplate;
    
    protected String className;
    protected String packageName;
    
    public int spacesPerTab=4;
    
    @FunctionalInterface
    public interface TextureConfigVisitor 
    {
        public void visit(TextureConfig config,boolean isFirst,boolean isLast);
    }
    
    public CodeGenerator() 
    {
        this( null );
    }
    
    public void configure(CodeGenConfig config)
    {
        this.className = config.className;
        this.packageName = config.packageName;
    }
    
    protected String loadDefaultTemplate() 
    {
        throw new RuntimeException("loadDefaultTemplate() is not implemented");
    }
    
    protected static String loadDefaultTemplate(String classPath) 
    {
        final byte[] buffer = new byte[1024];
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final InputStream in = CodeGenerator.class.getResourceAsStream( classPath );
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
        if ( template == null ) {
            template = loadDefaultTemplate();
        }
        this.codeTemplate = template;
    }
    
    public final CharSequence generateCode(AssetConfig config) 
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
    
    protected abstract CharSequence expandVariable(CharSequence name,AssetConfig config, String indentString);
    
    /**
     * 
     * @param configs Texture configs sorted ascending by (block/item) type ID
     * @param indentString
     * @return
     */
    protected final CharSequence expandUVCoordinates(List<TextureConfig> configs, String indentString) 
    {
        /* !!!!!
         * This method assumes that the input list is already sorted
         * so that the first texture config belongs to the first item/block,
         * the second belongs to the second item/block etc.
         * 
         * Failing to sort the list correctly will yield bogus results on array lookups.
         * !!!!!
         */
        final StringBuilder buffer = new StringBuilder();
        
        final TextureConfigVisitor c = new TextureConfigVisitor() 
        {
            private int floatsThisLine = 0;
            
            @Override
            public void visit(TextureConfig def,boolean isFirst,boolean isLast) 
            {
                    buffer.append( Float.toString( def.u0 ) ).append("f,");
                    buffer.append( Float.toString( def.v0 ) ).append("f,");
                    buffer.append( Float.toString( def.u1 ) ).append("f,");
                    buffer.append( Float.toString( def.v1 ) ).append("f");
                    floatsThisLine+= 4;
                    if ( floatsThisLine >= 8 ) 
                    {
                        final boolean last = isLast;
                        if ( ! last ) {
                            buffer.append(",\n");
                        }
                        if ( ! last ) {
                            buffer.append( indentString );
                        }
                        floatsThisLine = 0;
                    } else if ( ! isLast ) {
                        buffer.append(",");
                    }
            }
        };
        for ( int i = 0,len=configs.size(); i < len ; i++ ) {
            c.visit( configs.get(i) , (i==0) , (i == (len-1) ) );
        }
        return buffer.length() == 0 ? buffer : buffer; 
    }      
}