package de.codesourcery.voxelengine.utils;

/**
 * Keeps track of an arbitrary set of blocks (chunkId+blockId)
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class IndividualBlockSelection implements IBlockSelection
{
	public int size;
	
	private boolean selectionChanged;
	
	public long[] chunkIds = new long[50];
	public int[] blockIds = new int[50];
	
	public void visitSelection(SelectionVisitor visitor) {
		
		for ( int i = 0 ,len = size ; i < len ; i++ ) 
		{
			visitor.visit( chunkIds[i] , blockIds[i] );  
		}
	}
	
	public int size() {
	    return size;
	}
	
	public boolean hasChanged() {
		return selectionChanged;
	}
	
	public void resetChanged() {
		selectionChanged = false;
	}
	
	public void set(long chunkId,int blockId) 
	{
	    final boolean changed = size != 1 || chunkIds[0] != chunkId || blockIds[0] != blockId;
	    if ( changed ) {
	        chunkIds[0] = chunkId;
	        blockIds[0] = blockId;
	        size=1;
	        selectionChanged = true;
	    }
	}
	
	public boolean remove(long chunkId,int blockId) 
	{
		for ( int i = 0 ; i < size ; i++ ) 
		{
			if ( chunkIds[i] == chunkId && blockIds[i] == blockId ) 
			{
				final int toCopy = size - i - 1;
				for ( int j = 0 ; j < toCopy ; j++ ) {
					chunkIds[i+j] = chunkIds[i+j+1];
				}
				size--;
				selectionChanged = true;
				return true;
			}
		}
		return false;
	}
	
	public void add(long chunkId,int blockId) 
	{
	    if ( isPartOfSelection( chunkId ,blockId ) ) {
	        return;
	    }
		if ( size == chunkIds.length ) {
			final int newLen = chunkIds.length + chunkIds.length/2;
			long[] tmp1 = new long[ newLen ];
			System.arraycopy(  chunkIds , 0 , tmp1 , 0 , size );
			chunkIds = tmp1;
			int[] tmp2 = new int[ newLen ];
			System.arraycopy(  blockIds , 0 , tmp2 , 0 , size );
			blockIds = tmp2;			
		}
		chunkIds[size] = chunkId;
		blockIds[size] = blockId;
		size++;
		selectionChanged = true;		
	}
	
	public void clear() 
	{
	    if ( size != 0 ) 
	    {
	        size = 0;
	        selectionChanged = true;
	    }
	}
	
	public boolean isEmpty() {
		return size == 0 ;
	}
	
	public boolean isNotEmpty() {
		return size != 0 ;
	}

    @Override
    public boolean isPartOfSelection(long chunkId, int blockId) 
    {
        for ( int i = 0 , len = size ; i < len ; i++ ) 
        {
            if ( chunkIds[i] == chunkId && blockIds[i] == blockId ) {
                return true;
            }
        }
        return false;
    }    
}