package de.codesourcery.voxelengine.blockeditor;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import de.codesourcery.voxelengine.engine.BlockSide;
import de.codesourcery.voxelengine.model.BlockType;

public class BlockConfigReader 
{
    /*
     * <blockDefinitions>
     *   <general>
     *     <textureAtlasSize>1024</textureAtlasSize>
     *     <blockTextureSize>32</blockTextureSize>
     *     <baseDirectory>32</baseDirectory>
     *     <codeOutputFile>32</codeOutputFile>
     *   </general>
     *   <blocks>
     *     <block type="0" name="air" emitsLight="false" opaque="false" lightLevel="0">
     *       <side type="top" flip="false" rotate="0" u0="0.0" v0="0.0" u1="1.0" v1="1.0" texture="blocks/0/top" />
     *       <side type="bottom" flip="false" rotate="0" u0="0.0" v0="0.0" u1="1.0" v1="1.0" texture="blocks/0/top" />
     *       <side type="front" flip="false" rotate="0" u0="0.0" v0="0.0" u1="1.0" v1="1.0" texture="blocks/0/top" />
     *       <side type="back" flip="false" rotate="0" u0="0.0" v0="0.0" u1="1.0" v1="1.0" texture="blocks/0/top" />
     *       <side type="left" flip="false" rotate="0" u0="0.0" v0="0.0" u1="1.0" v1="1.0" texture="blocks/0/top" />
     *       <side type="right" flip="false" rotate="0" u0="0.0" v0="0.0" u1="1.0" v1="1.0" texture="blocks/0/top" />
     *    </block>
     *   </blocks>
     * </blockDefinitions>
     */
    
    @FunctionalInterface
    private interface StringMapper<T> 
    {
        public T map(String s);
    }
    
    private static final StringMapper<String> STRING_MAPPER = string -> string;
    private static final StringMapper<Integer> INT_MAPPER = string -> Integer.parseInt( string );
    private static final StringMapper<Float> FLOAT_MAPPER = string -> Float.valueOf( string );
    private static final StringMapper<Boolean> BOOLEAN_MAPPER = string -> Boolean.parseBoolean( string );
    private static final StringMapper<Byte> BYTE_MAPPER = string -> Byte.parseByte( string );
    private static final StringMapper<BlockSide> BLOCKSIDE_MAPPER = string -> BlockSide.fromXmlName( string );
    
    private static final StringMapper<BlockSideDefinition.Rotation> ROTATION_MAPPER = string -> 
    {
        switch( string ) {
            case "0":   return BlockSideDefinition.Rotation.NONE;
            case "90":  return BlockSideDefinition.Rotation.CW_90;
            case "180": return BlockSideDefinition.Rotation.ONE_HUNDRED_EIGHTY;
            case "270": return BlockSideDefinition.Rotation.CCW_90;
            default: throw new RuntimeException("Unsupported rotation: '"+string+"'");
        }
    };
    
    private static final String TEXTURE_ATLAS_SIZE_XPATH = "/blockDefinitions/general/textureAtlasSize";
    private static final String BLOCK_TEXTURE_SIZE_XPATH = "/blockDefinitions/general/blockTextureSize";
    private static final String BASEDIRECTORY_XPATH = "/blockDefinitions/general/baseDirectory";
    private static final String CODE_OUTPUT_FILE_XPATH = "/blockDefinitions/general/codeOutputFile";
    
    private static final String BLOCKS_XPATH = "/blockDefinitions/blocks/block";
    private static final String SIDE_XPATH = "side";
    
    private static final XPathExpression textureAtlasSizeExpr;
    private static final XPathExpression blockTextureSizeExpr;
    private static final XPathExpression baseDirectoryExpr;
    private static final XPathExpression codeOutputFileExpr;
    
    private static final XPathExpression blocksExpr;
    private static final XPathExpression sideExpr;
    
    public BlockConfig read(Path path) throws IOException 
    {
        try ( InputStream in = new FileInputStream( path.toFile() ) ) {
            return read( in );
        }
    }
    
    static {
        
        final XPath factory = XPathFactory.newInstance().newXPath();
        
        try {
            textureAtlasSizeExpr = factory.compile( TEXTURE_ATLAS_SIZE_XPATH );
            blockTextureSizeExpr = factory.compile( BLOCK_TEXTURE_SIZE_XPATH );
            baseDirectoryExpr = factory.compile( BASEDIRECTORY_XPATH );
            codeOutputFileExpr = factory.compile( CODE_OUTPUT_FILE_XPATH );
            
            blocksExpr = factory.compile( BLOCKS_XPATH );
            sideExpr = factory.compile( SIDE_XPATH );
        } 
        catch (XPathExpressionException e) {
            throw new RuntimeException(e);
        }
    }
    
    private <T> T parseNodeValue(XPathExpression expr,Node doc,StringMapper<T> mapper) 
    {
        return mapper.map( parseNodeValue( expr , doc ) );
    }
    
    public String parseNodeValue(XPathExpression expr,Node doc) 
    {
        try {
            final Node node = (Node) expr.evaluate(doc, XPathConstants.NODE);
            return node.getTextContent();
        } catch (XPathExpressionException e) {
            throw new RuntimeException( e );
        }
    }
    
    public Stream<Element> parseElements(XPathExpression expr,Node doc) 
    {
        try {
            final NodeList nodeList = (NodeList) expr.evaluate(doc , XPathConstants.NODESET);
            if ( nodeList.getLength() == 0 ) {
                return Collections.<Element>emptyList().stream();
            }
            final List<Element> elements = new ArrayList<>( nodeList.getLength() );
            for ( int i = 0 , len = nodeList.getLength() ; i < len ; i++ ) {
                elements.add( (Element) nodeList.item( i ) );
            }
            return elements.stream();
        } 
        catch (XPathExpressionException e) 
        {
            throw new RuntimeException(e);
        }
    }

    public BlockConfig read(InputStream in) throws IOException 
    {
        final Document doc = readXml( in );
        
        final BlockConfig result = new BlockConfig();
        
        result.textureAtlasSize = parseNodeValue( textureAtlasSizeExpr , doc , INT_MAPPER );
        result.blockTextureSize = parseNodeValue( blockTextureSizeExpr , doc , INT_MAPPER );
        result.baseDirectory = parseNodeValue( baseDirectoryExpr , doc , STRING_MAPPER );
        result.codeOutputFile = parseNodeValue( codeOutputFileExpr , doc , STRING_MAPPER );
        
        parseElements( blocksExpr , doc ).forEach( blockElement -> 
        {
            final String name = blockElement.getAttribute( "name" );
            final int blockType = Integer.parseInt( blockElement.getAttribute( "type" ) );
            final BlockDefinition block = new BlockDefinition( blockType , name );
            block.opaque = attr( blockElement , "opaque" , BOOLEAN_MAPPER );
            block.emitsLight = attr( blockElement , "emitsLight" , BOOLEAN_MAPPER ); 
            block.lightLevel = attr( blockElement , "lightLevel" , BYTE_MAPPER );
                    
            parseElements( sideExpr , blockElement ).forEach( sideElement -> 
            {
                final BlockSideDefinition def = new BlockSideDefinition( BLOCKSIDE_MAPPER.map( sideElement.getAttribute("type" ) ) );
                def.flip = attr( sideElement , "flip" , "false" , BOOLEAN_MAPPER );
                def.rotation = attr( sideElement , "rotate" , "0" , ROTATION_MAPPER );
                def.u0 = attr( sideElement , "u0" , FLOAT_MAPPER );
                def.v0 = attr( sideElement , "v0" , FLOAT_MAPPER );
                def.u1 = attr( sideElement , "u1" , FLOAT_MAPPER );
                def.v1 = attr( sideElement , "v1" , FLOAT_MAPPER );
                
                System.out.println("Side "+def.side+" of "+name+" has (u0,v0) = ("+def.u0+","+def.v0+")");
                System.out.println("Side "+def.side+" of "+name+" has (u1,v1) = ("+def.u1+","+def.v1+")");
                def.setInputTexture( attr( sideElement , "texture" , null , STRING_MAPPER ));
                block.sides[ def.side.ordinal() ] = def;
                System.out.println("Side "+def.side+" has TEXTURE: "+def.getInputTexture());
            });
            result.blocks.add( block );
        });
        
        final BlockConfigTextureResolver res = new BlockConfigTextureResolver( result );
        if ( ! result.isValid( res ) ) {
            throw new IOException("File contains invalid configuration");
        }
        
        // TODO: Remove debug code
//        for ( BlockDefinition def : result.blocks ) 
//        {
//            for ( BlockSideDefinition s : def.sides ) 
//            {
//                float u = BlockType.getU0( def.blockType , s.side );
//                float v = BlockType.getV0( def.blockType , s.side );
//                if ( u != s.u0 || v != s.v0 ) {
//                    throw new RuntimeException("ERROR: Got ("+u+","+v+") but expected ("+s.u0+","+s.v0+") for side "+s.side);
//                }
//                u = BlockType.getU1( def.blockType , s.side );
//                v = BlockType.getV1( def.blockType , s.side );
//                if ( u != s.u1 || v != s.v1 ) {
//                    throw new RuntimeException("ERROR: Got ("+u+","+v+") but expected ("+s.u0+","+s.v0+") for side "+s.side);
//                }                
//            }
//        }
        return result;
    }
    
    private static <T> T attr(Element node,String attrName,String defaultValue,StringMapper<T> stringMapper) 
    {
        return stringMapper.map( attr( node , attrName , defaultValue ) );
    }
    
    private static String attr(Element node,String attrName,String defaultValue) 
    {
        final String result = node.getAttribute( attrName );
        return result == null ? defaultValue : result;
    }
    
    private static <T> T attr(Element node,String attrName,StringMapper<T> mapper) 
    {    
        return mapper.map( attr( node , attrName ) );
    }
    
    private static String attr(Element node,String attrName) 
    {
        final String result = node.getAttribute( attrName );
        if ( StringUtils.isBlank( result ) ) {
            throw new RuntimeException("XML syntax error, missing value for attribute '"+attrName+"'");
        }
        return result;
    }    
    
    private static Document readXml(InputStream in) throws IOException 
    {
        final DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
        final DocumentBuilder builder;
        try {
            builder = builderFactory.newDocumentBuilder();
            return builder.parse( in );
        } 
        catch (ParserConfigurationException | SAXException e) 
        {
            throw new IOException("Failed to parse XML: "+e.getMessage(),e);
        }
    }
}