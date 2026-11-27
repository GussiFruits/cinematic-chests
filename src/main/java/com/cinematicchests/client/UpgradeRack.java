package com.cinematicchests.client;

import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.neoforged.fml.ModList;

/**
 * Upgrade slots a storage menu keeps apart from its ordinary slots (Sophisticated Storage's): they are still clicked like any
 * slot, by their index in the menu, but switching them on and off, opening one to work its own slots, and the reasons a change
 * was refused are the mod's own. Only implemented for mods that are installed, so nothing here names their classes.
 */
public interface UpgradeRack {
    /** How many upgrade slots there are. */
    int count();

    /** The menu index of upgrade slot {@code i}: what a slot click names. */
    int menuSlot(int i);

    boolean enabled(int i);

    boolean canSwitch(int i);

    /** Switches upgrade {@code i} on or off (the mod tells the server). */
    void setEnabled(int i, boolean on);

    /** Why the last change to the upgrades was refused, if it was. */
    Optional<Component> error();

    /** Whether a menu slot belongs to an upgrade's own controls (a crafting grid...), not to the storage. */
    boolean belongsToUpgrade(Slot slot);

    /** The menu indices of upgrade {@code i}'s own slots (its crafting grid and result, its filter...); empty when it has none. */
    List<Integer> upgradeSlots(int i);

    /**
     * Where upgrade {@code i}'s slots go, as {menu index: {column, row}}, when the mod knows better than the slots' own positions
     * (which it may only set once its window lays out the tab); null to lay them out in rows.
     */
    @Nullable
    default java.util.Map<Integer, int[]> layout(int i) {
        return null;
    }

    /** What an empty slot of upgrade {@code i} is for (input, fuel, result...), or null. */
    @Nullable
    default Component label(int i, int menuIndex) {
        return null;
    }

    /** Where upgrade {@code i}'s cooking arrow sits, {column, row} in its layout (the flame sits a column before it); null for none. */
    @Nullable
    default int[] gaugeAt(int i) {
        return null;
    }

    /** Upgrade {@code i}'s gauges, {cooking progress, fuel left}, each 0..1; null when it has none. */
    @Nullable
    default float[] gauges(int i) {
        return null;
    }

    /** A button of an upgrade's own (a jukebox's play and stop): a glyph to show, lit when its setting is on. */
    record Control(String id, String glyph, boolean lit, Component name, @Nullable Component group) {
        public Control(String id, String glyph, boolean lit, Component name) {
            this(id, glyph, lit, name, null);
        }
    }

    /** Upgrade {@code i}'s buttons, in order; empty when it has none worked here. */
    default List<Control> controls(int i) {
        return List.of();
    }

    /** Presses one of upgrade {@code i}'s buttons (the mod tells the server). */
    default void press(int i, String id) {}

    /** A button that belongs to one slot (an alchemy filter's condition), shown just right of it. */
    record SlotControl(int menuIndex, Control control) {}

    /** Upgrade {@code i}'s per-slot buttons; empty when it has none. */
    default List<SlotControl> slotControls(int i) {
        return List.of();
    }

    /** The wheel over one of upgrade {@code i}'s buttons (a hurt condition's health value); false when it means nothing there. */
    default boolean scrollControl(int i, String id, double delta) {
        return false;
    }

    /** The menu index of the slot upgrade {@code i} is working from right now (the disc playing), or -1. */
    default int activeSlot(int i) {
        return -1;
    }

    /**
     * A line of text beside an upgrade's slots (a filter's tags when it matches by tag). Lines come in sections, each with a
     * header and scrolled on its own; a line with an id is clickable, with what a click does as its hint.
     */
    record Line(String id, Component text, @Nullable Component hint, int section, boolean header) {
        public boolean clickable() {
            return !header && !id.isEmpty();
        }
    }

    /** Upgrade {@code i}'s lines of text, top to bottom; empty for none. */
    default List<Line> lines(int i) {
        return List.of();
    }

    /** Clicks one of upgrade {@code i}'s lines. */
    default void pressLine(int i, String id) {}

    /** Changes whenever upgrade {@code i}'s slots are laid out differently (a filter switched to matching by tag). */
    default int layoutKey(int i) {
        return 0;
    }

    /** Whether upgrade {@code i} has a tab of its own in the window (slots or options); a stack upgrade has none. */
    default boolean hasControls(int i) {
        return !upgradeSlots(i).isEmpty();
    }

    /** How full upgrade {@code i} is (a tank's fluid, a battery's energy), 0..1, with the colour to show it in; null for none. */
    @Nullable
    default Meter meter(int i) {
        return null;
    }

    record Meter(float fill, int argb, Component amount) {}

    /** What upgrade {@code i} can make of its input, to choose from (a stonecutter's shapes); empty when there is no choice. */
    default List<net.minecraft.world.item.ItemStack> choices(int i) {
        return List.of();
    }

    /** Which of {@link #choices} is chosen, or -1. */
    default int chosen(int i) {
        return -1;
    }

    /** Chooses one of {@link #choices} (the mod tells the server). */
    default void choose(int i, int choice) {}

    /** The upgrade whose controls are open, as in the window's open tab; -1 for none. */
    int open();

    /** Opens upgrade {@code i}'s controls, closing any other, as clicking its tab in the window does; -1 closes them. */
    void setOpen(int i);

    /** The rack for this menu, or null when it keeps no upgrades apart. */
    @Nullable
    static UpgradeRack of(AbstractContainerMenu menu) {
        if (ModList.get().isLoaded("sophisticatedcore") && !GuardedRack.broken()) {
            try {
                UpgradeRack rack = com.cinematicchests.client.compat.SophisticatedRack.of(menu);
                return rack == null ? null : GuardedRack.wrap(rack);
            } catch (LinkageError e) {
                GuardedRack.fail(e);
            }
        }
        return null;
    }
}
