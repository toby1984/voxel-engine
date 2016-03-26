package de.codesourcery.voxelengine.asseteditor;

import java.util.Arrays;

public class TextureAtlasConfig 
{
    public static enum Category 
    {
        BLOCKS("blocks"),
        ITEMS("items");
        
        public final String name;
        
        private Category(String name) {
            this.name= name;
        }
        
        public static Category fromName(String name) {
            return Arrays.stream(values()).filter( v -> name.equals(v.name ) ).findFirst().orElseThrow( () -> new IllegalArgumentException("Unknown atlas category '"+name+"'"));
        }
    }
    
    public Category category;
    
    // size of the (square) texture atlas texture in pixels
    public int textureAtlasSize = 1024;

    // size of a (square) block texture in pixels
    public int textureSize = 64;
    
    public String outputName;

    public TextureAtlasConfig() {
    }
    
    public TextureAtlasConfig(Category category) {
        this.category = category;
    }
    
    @Override
    public String toString() {
        return "TextureAtlasConfig [category="+category+", textureAtlasSize=" + textureAtlasSize+ ", textureSize=" + textureSize + "]";
    }
}
