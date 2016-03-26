package de.codesourcery.voxelengine.asseteditor;

import javax.swing.JPanel;

import org.apache.commons.lang3.Validate;

public abstract class FormPanel<T> extends JPanel 
{
    protected T model;

    private IValueChangedListener changeListener = (m,childrenChanged) -> {};
    
    public abstract void saveChanges();
    
    public final void setValueChangedListener(IValueChangedListener listener) {
        Validate.notNull(listener, "listener must not be NULL");
        this.changeListener = listener;
    }
    
    public final void setModel(T model) {
        this.model = model;
        modelChanged();
    }
    
    protected abstract void modelChanged();
    
    protected final void notifyChangeListener(boolean childrenChanged) {
        changeListener.valueChanged( model , childrenChanged );
    }
}
