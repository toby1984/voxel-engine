package de.codesourcery.voxelengine.model;

public class PlayerToolbar {

    private static final int NONE = -1;

    public static final int SLOTS = 10;
    
    private final Item[] items = new Item[SLOTS];
    
    private int selectedSlot = NONE;
    
    public PlayerToolbar() {
        items[0] = new Item(ItemType.BLOCK_CREATOR,"Block-Creator #1").setCreatedBlockType( BlockType.BLOCKTYPE_SOLID_1 );
        items[1] = new Item(ItemType.BLOCK_DESTROYER,"Block-Destroyer").setCanDestroyBlock( true );
        items[2] = new Item(ItemType.BLOCK_CREATOR,"Block-Creator #2").setCreatedBlockType( BlockType.BLOCKTYPE_SOLID_2 );
    }
    
    public boolean isItemSelected() {
        return selectedSlot != NONE && items[selectedSlot] != null;
    }
    
    public void setSelectedSlot(int selectedSlot) {
        this.selectedSlot = selectedSlot;
    }
    
    public int getSelectedSlot() {
        return selectedSlot;
    }
    
    public Item getSelectedItem() {
        return selectedSlot == NONE ? null : items[selectedSlot];
    }
    
    public Item getItemInSlot(int slotNo) {
        return items[slotNo];
    }
    
    public void setItemInSlot(int slotNo,Item item) {
        items[slotNo] = item;
    }
}