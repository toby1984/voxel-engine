package de.codesourcery.voxelengine.utils;

import de.codesourcery.voxelengine.model.BlockKey;
import de.codesourcery.voxelengine.model.ChunkKey;

public class SelectedBlock {

    public long chunkID;
    public int blockID;
    private boolean isValid;
    
    public void set(long chunkID,int blockID) {
        this.chunkID = chunkID;
        this.blockID = blockID;
        this.isValid = true;
    }
      
    public boolean isValid() {
        return isValid;
    }
    
    public void invalidate() 
    {
        chunkID = ChunkKey.INVALID;
        blockID = BlockKey.INVALID;
        isValid = false;
    }
}