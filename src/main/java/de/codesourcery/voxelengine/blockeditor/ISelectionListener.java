package de.codesourcery.voxelengine.blockeditor;

@FunctionalInterface
public interface ISelectionListener<T> 
{
    public void selectionChanged(T selected);
}
