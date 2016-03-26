package de.codesourcery.voxelengine.asseteditor;

@FunctionalInterface
public interface ISelectionListener<T> 
{
    public void selectionChanged(T selected);
}
