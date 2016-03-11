package de.codesourcery.voxelengine.engine;

public enum BlockSide 
{
    /* !!!!!!!!!!!!!!!!!!
     * Do NOT change the constant(s) or constant
     * ordering here, ordinal indices are used as
     * indices into various arrays
     * !!!!!!!!!!!!!!!!!!
     */
    SIDE_BACK("back"),
    SIDE_FRONT("front"),
    SIDE_LEFT("left"),
    SIDE_RIGHT("right"),
    SIDE_TOP("top"),
    SIDE_BOTTOM("bottom");
    
    public final String xmlName;
    
    private BlockSide(String xmlName) {
        this.xmlName = xmlName;
    }
    
    public static BlockSide fromXmlName(String name) {
        switch( name ) {
            case "top": return BlockSide.SIDE_TOP;
            case "bottom": return BlockSide.SIDE_BOTTOM;
            case "left": return BlockSide.SIDE_LEFT;
            case "right": return BlockSide.SIDE_RIGHT;
            case "front": return BlockSide.SIDE_FRONT;
            case "back": return BlockSide.SIDE_BACK;
            default: throw new RuntimeException("Not a valid block side string: '"+name+"'");
        }        
    }
}
