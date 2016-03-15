package de.codesourcery.voxelengine.utils;

public interface IBlockSelection 
{
    @FunctionalInterface
    public interface SelectionVisitor 
    {
        public void visit(long chunkId,int blockId);
    }
    
    public int size();
    
    public void visitSelection(SelectionVisitor visitor);
    
    public boolean hasChanged();
    
    public void resetChanged();
    
    public void set(long chunkId,int blockId); 
    
    public boolean remove(long chunkId,int blockId); 
    
    public void add(long chunkId,int blockId); 
    
    public void clear(); 
    
    public boolean isEmpty();
    
    public boolean isNotEmpty();
    
    public boolean isPartOfSelection(long chunkId,int blockId);
}