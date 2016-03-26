package de.codesourcery.voxelengine.engine;

import java.util.HashMap;
import java.util.Map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.Texture.TextureWrap;
import com.badlogic.gdx.utils.Disposable;

public class TextureManager implements Disposable {

    public static final String BLOCKS_TEXTUREATLAS = "blocks_atlas";
    
    private final Map<String,Texture> textures = new HashMap<>();
    
    public Texture getTexture(String name) {

        synchronized ( textures ) 
        {
            Texture result = textures.get(name);
            if ( result == null ) {
                result = loadTexture(name);
                textures.put(name, result );
            }
            return result;
        }
    }
    
    private Texture loadTexture(String name) 
    {
        final String cp = "textures/"+name+".png"; // Note that crappy libgdx always prefixes the path with '/' so we must not do this here....
        final Texture t = new Texture( Gdx.files.classpath( cp ) , false );
        t.setFilter(TextureFilter.Nearest,TextureFilter.Nearest);
        t.setWrap( TextureWrap.Repeat , TextureWrap.Repeat );
        return t;
    }
    
    @Override
    public void dispose() 
    {
        synchronized(textures) 
        {
            textures.values().forEach( t -> t.dispose() );
            textures.clear();
        }
    }

}
