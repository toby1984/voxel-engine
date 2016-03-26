package de.codesourcery.voxelengine.asseteditor;

@FunctionalInterface
public interface IValueChangedListener {

    public void valueChanged(Object value, boolean childrenChangedAsWell);
}
