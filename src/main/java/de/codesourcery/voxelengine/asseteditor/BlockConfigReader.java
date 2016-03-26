package de.codesourcery.voxelengine.asseteditor;

import java.io.File;
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

import de.codesourcery.voxelengine.asseteditor.TextureAtlasConfig.Category;
import de.codesourcery.voxelengine.engine.BlockSide;

public class BlockConfigReader 
{
    /*
     * <assets>
     *   <general>
     *     <baseDirectory>32</baseDirectory>
     *     <codeOutputFile>32</codeOutputFile>
     *   </general>
     *   <codegens>
     *     <codegen name="BLOCKS" directory="somePath" classname="" packagename="" />
     *     <codegen name="ITEMS" directory="somePath" classname="" packagename="" />
     *   </codegens>            
     *   <atlases>
     *     <atlas type="items" outputName="items" textureAtlasSize="1024" textureSize="32" />
     *     <atlas type="blocks" outputName="blocks" textureAtlasSize="1024" textureSize="32" />
     *   </atlases>
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
     *   <items>
     *     <item id="0" name="Block Creator" canCreateBlock="true" canDestroyBlock="false" createdBlockType="0" />
     *   </items>
     * </assets>
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


    private static final String BASEDIRECTORY_XPATH = "/assets/general/baseDirectory";
    private static final String CODEGEN_XPATH = "/assets/codegens/codegen";

    private static final String TEXTURE_ATLAS_XPATH = "/assets/atlases/atlas";
    private static final String BLOCKS_XPATH = "/assets/blocks/block";
    private static final String ITEM_XPATH = "/assets/items/item";

    private static final String SIDE_XPATH = "side";

    private static final XPathExpression itemExpr;
    private static final XPathExpression textureAtlasExpr;
    private static final XPathExpression baseDirectoryExpr;
    private static final XPathExpression codeOutputFileExpr;

    private static final XPathExpression blocksExpr;
    private static final XPathExpression sideExpr;

    public AssetConfig read(Path path) throws IOException 
    {
        try ( InputStream in = new FileInputStream( path.toFile() ) ) {
            return read( in );
        }
    }

    static {

        final XPath factory = XPathFactory.newInstance().newXPath();

        try {
            itemExpr = factory.compile( ITEM_XPATH );
            textureAtlasExpr = factory.compile( TEXTURE_ATLAS_XPATH );
            baseDirectoryExpr = factory.compile( BASEDIRECTORY_XPATH );
            codeOutputFileExpr = factory.compile( CODEGEN_XPATH );

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
            if ( node == null ) {
                throw new RuntimeException("XPath expression not matched: "+expr.toString());
            }
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

    private TextureConfig populateTextureConfig(Element sideElement,TextureConfig def) 
    {
        def.flip = attr( sideElement , "flip" , "false" , BOOLEAN_MAPPER );
        def.rotation = attr( sideElement , "rotate" , "0" , ROTATION_MAPPER );
        def.u0 = attr( sideElement , "u0" , FLOAT_MAPPER );
        def.v0 = attr( sideElement , "v0" , FLOAT_MAPPER );
        def.u1 = attr( sideElement , "u1" , FLOAT_MAPPER );
        def.v1 = attr( sideElement , "v1" , FLOAT_MAPPER );

        def.setInputTexture( attr( sideElement , "texture" , null , STRING_MAPPER ));
        return def;
    }

    public AssetConfig read(InputStream in) throws IOException 
    {
        final Document doc = readXml( in );

        final AssetConfig result = new AssetConfig();

        result.baseDirectory = parseNodeValue( baseDirectoryExpr , doc , STRING_MAPPER );

        // code gens
        parseElements( codeOutputFileExpr , doc ).forEach( codeGen -> 
        {
            final AssetConfig.SourceType type = attr( codeGen, "name" , s -> AssetConfig.SourceType.valueOf( s ) );
            result.getCodeGenConfig( type ).outputDirectory = attr( codeGen, "directory" , STRING_MAPPER);
            result.getCodeGenConfig( type ).className = attr( codeGen, "classname" , STRING_MAPPER);
            result.getCodeGenConfig( type ).packageName = attr( codeGen, "packagename" , "" , STRING_MAPPER);
        });

        // read atlases
        parseElements( textureAtlasExpr , doc ).forEach( atlas -> 
        {
            String name = attr( atlas , "type" , STRING_MAPPER ); 
            final TextureAtlasConfig config = result.textureAtlasConfig( Category.fromName( name ) );
            config.textureAtlasSize = attr( atlas , "textureAtlasSize" , INT_MAPPER );
            config.textureSize = attr( atlas , "textureSize" , INT_MAPPER );
            config.outputName = attr( atlas , "outputName" , null , STRING_MAPPER );
        });

        // read blocks
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

                populateTextureConfig( sideElement , def.texture );

                System.out.println("Side "+def.side+" of "+name+" has (u0,v0) = ("+def.texture.u0+","+def.texture.v0+")");
                System.out.println("Side "+def.side+" of "+name+" has (u1,v1) = ("+def.texture.u1+","+def.texture.v1+")");
                System.out.println("Side "+def.side+" has TEXTURE: "+def.getInputTexture());

                block.sides[ def.side.ordinal() ] = def;
            });
            result.blocks.add( block );
        });

        // read items
        parseElements( itemExpr , doc ).forEach( itemElement -> 
        {
            final ItemDefinition item = new ItemDefinition();

            item.itemId = attr( itemElement , "id" , INT_MAPPER );
            item.name = attr( itemElement , "name" , STRING_MAPPER );
            item.canCreateBlock = attr( itemElement , "canCreateBlock" , BOOLEAN_MAPPER );
            item.canDestroyBlock = attr( itemElement , "canDestroyBlock" , BOOLEAN_MAPPER );
            
            populateTextureConfig( itemElement , item.texture );
            
            if ( item.canCreateBlock && itemElement.hasAttribute( "createdBlockType" ) ) 
            {
                final int blockType = attr( itemElement , "createdBlockType" , INT_MAPPER );
                final BlockDefinition bd = result.getBlockDefinition( blockType );
                if ( bd != null ) {
                    item.createdBlock = bd;
                } else {
                    System.err.println("Item #"+item.itemId+" refers to missing block type #"+blockType);
                }
            }
            result.add( item );
        });

        final AssetConfigTextureResolver res = new AssetConfigTextureResolver( result );
        if ( ! result.isValid( res ) ) {
            throw new IOException("File contains invalid configuration");
        }
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