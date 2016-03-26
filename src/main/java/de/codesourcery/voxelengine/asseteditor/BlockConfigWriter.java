package de.codesourcery.voxelengine.asseteditor;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class BlockConfigWriter 
{
    public void write(AssetConfig config,OutputStream out) throws IOException 
    {
        try {
            final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            final DocumentBuilder builder = factory.newDocumentBuilder();
            final Document doc = builder.newDocument();
            populateDocument( config , doc );

            final TransformerFactory transformerFactory = TransformerFactory.newInstance();
            final Transformer transformer = transformerFactory.newTransformer();
            final DOMSource source = new DOMSource(doc);
            final StreamResult result = new StreamResult( out );
            transformer.transform(source, result);
        }
        catch(ParserConfigurationException | TransformerException e) 
        {
            throw new IOException("Failed to create XML document: "+e.getMessage(),e);
        }
    }

    private void populateDocument(AssetConfig config,Document doc) throws IOException 
    {

        final AssetConfigTextureResolver res = new AssetConfigTextureResolver( config );
        if ( ! config.isValid( res ) ) {
            throw new IOException("Refusing to write invalid block config");
        }
        
        /* <assets>
         *   <general>
         *     <baseDirectory>32</baseDirectory>
         *     <codeOutputFile>32</codeOutputFile>
         *   </general>
         *   <codegens>
         *     <codegen name="BLOCKS" directory="somePath" classname="" packagename="" />
         *     <codegen name="ITEMS" directory="somePath" classname="" packagename="" />
         *   </codegens>         
         *   <atlases>
         *     <atlas type="items" textureAtlasSize="1024" textureSize="32" />
         *     <atlas type="blocks" textureAtlasSize="1024" textureSize="32" />
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
         *     <item id="0" name="Block Creator" canCreateBlock="true" canDestroyBlock="false" createdBlockType="0" flip="false" rotate="0" u0="0.0" v0="0.0" u1="1.0" v1="1.0" texture="items/item0"/>
         *   </items>
         * </assets>
         */
        
        final Element root = doc.createElement( "assets" );
        doc.appendChild( root );
        
        // <general> section
        final Element general = doc.createElement( "general" );
        root.appendChild( general );
        
        final Element baseDirectory = doc.createElement( "baseDirectory" );
        general.appendChild( baseDirectory );
        if ( config.baseDirectory != null ) {
            baseDirectory.setTextContent( config.baseDirectory );
        }
        
        // <codegens> section
        final Element codeGens= doc.createElement( "codegens" );
        root.appendChild( codeGens );
        config.getCodeGenConfigs().stream().filter( config2 -> config2.isValid() ).forEach( config2 -> 
        {
                final Element codeGen= doc.createElement( "codegen" );
                codeGens.appendChild( codeGen );
                
                codeGen.setAttribute( "name" , config2.type.name() );
                codeGen.setAttribute( "directory" , config2.outputDirectory );
                codeGen.setAttribute( "classname" , config2.className );
                codeGen.setAttribute( "packagename" , config2.packageName == null ? "" : config2.packageName );
        });

        // <atlases> section
        final Element atlases = doc.createElement( "atlases" );
        root.appendChild( atlases );
        
        for ( TextureAtlasConfig atlasConfig : config.getTextureAtlasConfigs() ) 
        {
            final Element atlas = doc.createElement( "atlas" );
            atlases.appendChild( atlas );

            atlas.setAttribute( "type" , atlasConfig.category.name );
            atlas.setAttribute( "textureAtlasSize" , Integer.toString( atlasConfig.textureAtlasSize )  );
            atlas.setAttribute( "textureSize" , Integer.toString( atlasConfig.textureSize )  );
            if ( atlasConfig.outputName != null ) {
                atlas.setAttribute( "outputName" , atlasConfig.outputName );
            }
        }
        
        // <blocks> section
        final Element blocks = doc.createElement( "blocks" );
        root.appendChild( blocks );
        
        config.blocks.stream().sorted( (a,b) -> Integer.compare( a.blockType, b.blockType) ).filter( bd -> ! bd.isAutoGenerated() ).forEach( blockDef -> 
        {
            final Element block = doc.createElement( "block" );
            blocks.appendChild( block );
            block.setAttribute( "type" , Integer.toString( blockDef.blockType ) );
            block.setAttribute( "name" , blockDef.name );
            block.setAttribute( "emitsLight" , Boolean.toString( blockDef.emitsLight ) );
            block.setAttribute( "opaque" , Boolean.toString( blockDef.opaque) );
            block.setAttribute( "lightLevel" , Integer.toString( blockDef.lightLevel ) );
            
            Arrays.stream( blockDef.sides ).forEach( sideDef -> 
            {
                final Element side = doc.createElement( "side" );
                block.appendChild( side );
                
                side.setAttribute( "type" , sideDef.side.xmlName );
                storeTextureConfig( side , sideDef.texture );
            });
        });
        
        // <items> section
        final Element items = doc.createElement( "items" );
        root.appendChild( items );
        
        for ( ItemDefinition itemDef : config.items ) 
        {
            final Element item = doc.createElement( "item" );
            items.appendChild( item );
            // <item id="0" name="Block Creator" canCreateBlock="true" canDestroyBlock="false" createdBlockType="0"
            
            item.setAttribute( "id" , Integer.toString( itemDef.itemId ) );
            item.setAttribute( "name" , itemDef.name );
            item.setAttribute( "canCreateBlock" , Boolean.toString( itemDef.canCreateBlock ) );
            item.setAttribute( "canDestroyBlock" , Boolean.toString( itemDef.canDestroyBlock ) );
            if ( itemDef.createdBlock != null ) {
                item.setAttribute( "createdBlockType" , Integer.toString( itemDef.createdBlock.blockType ) );
            }
            storeTextureConfig( item , itemDef.texture );
        }
    }
    
    private void storeTextureConfig(Element side,TextureConfig texture) 
    {
        side.setAttribute( "flip" , Boolean.toString( texture.flip ) );
        side.setAttribute( "rotate" , Integer.toString( texture.rotation.degrees ) );
        side.setAttribute( "u0" , Float.toString( texture.u0 ) );
        side.setAttribute( "v0" , Float.toString( texture.v0 ) );
        side.setAttribute( "u1" , Float.toString( texture.u1 ) );
        side.setAttribute( "v1" , Float.toString( texture.v1 ) );
        if ( texture.isTextureAssigned() ) {
            side.setAttribute( "texture" , texture.getInputTexture() );
        }
    }
}
