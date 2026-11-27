package com.cinematicchests.compat;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.p3pp3rf1y.sophisticatedcore.common.gui.IFilterSlot;
import net.p3pp3rf1y.sophisticatedcore.common.gui.StorageContainerMenuBase;

/**
 * Sophisticated Core's filter slots, on either side. Setting one goes through the menu's own {@code setGhostSlot} (what its
 * recipe-viewer support uses), which checks the slot takes the pattern and stores it with the upgrade. Only loaded when
 * Sophisticated Core is installed.
 */
public final class SophisticatedFilters {
    private SophisticatedFilters() {}

    public static boolean isFilterSlot(AbstractContainerMenu menu, int index) {
        return menu instanceof StorageContainerMenuBase<?> storage && index >= 0 && index < storage.getTotalSlotsNumber()
                && storage.getSlot(index) instanceof IFilterSlot;
    }

    /** Sets the pattern (one of the stack), or clears the slot when the stack is empty. */
    public static void set(AbstractContainerMenu menu, int index, ItemStack pattern) {
        if (!isFilterSlot(menu, index)) {
            return;
        }
        StorageContainerMenuBase<?> storage = (StorageContainerMenuBase<?>) menu;
        Slot slot = storage.getSlot(index);
        if (pattern.isEmpty()) {
            slot.set(ItemStack.EMPTY);
            ((IFilterSlot) slot).onItemSet(0);
        } else if (storage.canSetGhostSlot(index, pattern)) {
            storage.setGhostSlot(index, pattern.copyWithCount(1));
        }
    }
}
