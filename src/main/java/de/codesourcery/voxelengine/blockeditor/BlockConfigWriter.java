package de.codesourcery.voxelengine.blockeditor;

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

import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class BlockConfigWriter 
{
    public void write(BlockConfig config,OutputStream out) throws IOException 
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

    private void populateDocument(BlockConfig config,Document doc) 
    {
        /* <blockDefinitions>
        *   <general>
        *     <textureAtlasSize>1024</textureAtlasSize>
        *     <blockTextureSize>32</blockTextureSize>
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
        
        final Element root = doc.createElement( "blockDefinitions" );
        doc.appendChild( root );
        
        final Element general = doc.createElement( "general" );
        root.appendChild( general );
        
        final Element textureAtlasSize = doc.createElement( "textureAtlasSize" );
        general.appendChild( textureAtlasSize );
        textureAtlasSize.setTextContent( Integer.toString( config.textureAtlasSize ) );
        
        final Element blockTextureSize = doc.createElement( "blockTextureSize" );
        general.appendChild( blockTextureSize );
        textureAtlasSize.setTextContent( Integer.toString( config.blockTextureSize ) );
        
        final Element blocks = doc.createElement( "blocks" );
        root.appendChild( blocks );
        
        config.blocks.forEach( (typeId,blockDef) -> 
        {
            final Element block = doc.createElement( "block" );
            blocks.appendChild( block );
            block.setAttribute( "type" , Integer.toString( blockDef.blockType ) );
            block.setAttribute( "emitsLight" , Boolean.toString( blockDef.emitsLight ) );
            block.setAttribute( "opaque" , Boolean.toString( blockDef.opaque) );
            block.setAttribute( "lightLevel" , Integer.toString( blockDef.lightLevel ) );
            
            Arrays.stream( blockDef.sides ).forEach( sideDef -> 
            {
                final Element side = doc.createElement( "side" );
                block.appendChild( side );
                
                side.setAttribute( "type" , sideDef.side.xmlName );
                side.setAttribute( "flip" , Boolean.toString( sideDef.flip ) );
                side.setAttribute( "rotate" , Integer.toString( sideDef.rotation.degrees ) );
                side.setAttribute( "u0" , Float.toString( sideDef.u0 ) );
                side.setAttribute( "v0" , Float.toString( sideDef.v0 ) );
                side.setAttribute( "u1" , Float.toString( sideDef.u1 ) );
                side.setAttribute( "v1" , Float.toString( sideDef.v1 ) );
                if ( sideDef.inputTexture != null ) {
                    side.setAttribute( "texture" , sideDef.inputTexture );
                }
            });
        });
    }
    
    
}
