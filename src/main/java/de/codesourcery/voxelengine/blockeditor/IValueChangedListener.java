package de.codesourcery.voxelengine.blockeditor;

@FunctionalInterface
public interface IValueChangedListener<T> {

    public void valueChanged(T value);
}
