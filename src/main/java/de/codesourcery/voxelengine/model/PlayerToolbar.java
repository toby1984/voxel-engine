package de.codesourcery.voxelengine.model;

public class PlayerToolbar {

    private final Item[] items;
    
    private int selectedSlot;
    
    public PlayerToolbar(int size) {
        items = new Item[size];
    }
    
    public boolean isItemSelected() {
        return items[selectedSlot] != null;
    }
    
    public void setSelectedSlot(int selectedSlot) {
        if ( selectedSlot < 0 || selectedSlot >= items.length ) {
            throw new IllegalArgumentException("Invalid slot no. "+selectedSlot);
        }
        this.selectedSlot = selectedSlot;
    }
    
    public int getSlotCount() {
        return items.length;
    }
    
    public int getSelectedSlot() {
        return selectedSlot;
    }
    
    public Item getSelectedItem() {
        return items[selectedSlot];
    }
    
    public Item getItemInSlot(int slotNo) {
        return items[slotNo];
    }
    
    public void setItemInSlot(int slotNo,Item item) {
        items[slotNo] = item;
    }
}