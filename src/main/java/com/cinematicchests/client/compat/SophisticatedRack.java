package com.cinematicchests.client.compat;

import com.cinematicchests.client.UpgradeRack;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.p3pp3rf1y.sophisticatedcore.common.gui.StorageContainerMenuBase;
import net.p3pp3rf1y.sophisticatedcore.common.gui.UpgradeContainerBase;
import net.p3pp3rf1y.sophisticatedcore.common.gui.UpgradeSlotChangeResult;
import net.p3pp3rf1y.sophisticatedcore.upgrades.blockconverter.BlockConverterRecipeContainer;
import net.p3pp3rf1y.sophisticatedcore.upgrades.blockconverter.BlockConverterUpgradeContainer;
import net.p3pp3rf1y.sophisticatedcore.upgrades.cooking.AutoCookingUpgradeContainer;
import net.p3pp3rf1y.sophisticatedcore.upgrades.cooking.CookingLogicContainer;
import net.p3pp3rf1y.sophisticatedcore.upgrades.cooking.CookingUpgradeContainer;
import net.p3pp3rf1y.sophisticatedcore.upgrades.alchemy.AlchemyCondition;
import net.p3pp3rf1y.sophisticatedcore.upgrades.alchemy.AlchemyUpgradeContainer;
import net.p3pp3rf1y.sophisticatedcore.upgrades.battery.BatteryUpgradeContainer;
import net.p3pp3rf1y.sophisticatedcore.upgrades.crafting.CraftingUpgradeContainer;
import net.p3pp3rf1y.sophisticatedcore.upgrades.jukebox.JukeboxUpgradeContainer;
import net.p3pp3rf1y.sophisticatedcore.upgrades.jukebox.RepeatMode;
import net.p3pp3rf1y.sophisticatedcore.upgrades.ContentsFilterLogicContainer;
import net.p3pp3rf1y.sophisticatedcore.upgrades.ContentsFilterType;
import net.p3pp3rf1y.sophisticatedcore.upgrades.FilterLogicContainerBase;
import net.p3pp3rf1y.sophisticatedcore.upgrades.PrimaryMatch;
import net.p3pp3rf1y.sophisticatedcore.upgrades.compacting.CompactingUpgradeContainer;
import net.p3pp3rf1y.sophisticatedcore.upgrades.feeding.FeedingUpgradeContainer;
import net.p3pp3rf1y.sophisticatedcore.upgrades.feeding.HungerLevel;
import net.p3pp3rf1y.sophisticatedcore.upgrades.filter.Direction;
import net.p3pp3rf1y.sophisticatedcore.upgrades.filter.FilterUpgradeContainer;
import net.p3pp3rf1y.sophisticatedcore.upgrades.magnet.MagnetUpgradeContainer;
import net.p3pp3rf1y.sophisticatedcore.upgrades.pump.PumpUpgradeContainer;
import net.p3pp3rf1y.sophisticatedcore.upgrades.voiding.VoidType;
import net.p3pp3rf1y.sophisticatedcore.upgrades.voiding.VoidUpgradeContainer;
import net.p3pp3rf1y.sophisticatedcore.upgrades.xppump.AutomationDirection;
import net.p3pp3rf1y.sophisticatedcore.upgrades.xppump.XpPumpUpgradeContainer;
import net.p3pp3rf1y.sophisticatedcore.upgrades.tank.TankUpgradeContainer;

/**
 * Sophisticated Core's upgrades. Its menu numbers the upgrade slots after its ordinary slots (from {@code getFirstUpgradeSlot()}),
 * and its own {@code getSlot} and click handling know them, so they are taken and put like any slot. An upgrade with controls of
 * its own (a crafting grid, a furnace, a stonecutter) has a container whose slots are ordinary menu slots; it is opened as the
 * window's tab opens it. Switching on and off, opening, and choosing a stonecutter's shape go through the menu and the upgrade's
 * own container, which tell the server. Only loaded when Sophisticated Core is installed.
 */
public final class SophisticatedRack implements UpgradeRack {
    private final StorageContainerMenuBase<?> menu;

    private SophisticatedRack(StorageContainerMenuBase<?> menu) {
        this.menu = menu;
    }

    @Nullable
    public static UpgradeRack of(AbstractContainerMenu menu) {
        return menu instanceof StorageContainerMenuBase<?> storage && storage.getNumberOfUpgradeSlots() > 0 ? new SophisticatedRack(storage) : null;
    }

    @Nullable
    private UpgradeContainerBase<?, ?> container(int i) {
        return menu.getUpgradeContainers().get(i);
    }

    @Override
    public int count() {
        return menu.getNumberOfUpgradeSlots();
    }

    @Override
    public int menuSlot(int i) {
        return menu.getFirstUpgradeSlot() + i;
    }

    @Override
    public boolean enabled(int i) {
        return menu.getUpgradeEnabled(i);
    }

    @Override
    public boolean canSwitch(int i) {
        return menu.canDisableUpgrade(i);
    }

    @Override
    public void setEnabled(int i, boolean on) {
        menu.setUpgradeEnabled(i, on);
    }

    @Override
    public Optional<Component> error() {
        return menu.getErrorUpgradeSlotChangeResult().flatMap(UpgradeSlotChangeResult::getErrorMessage);
    }

    @Override
    public boolean belongsToUpgrade(Slot slot) {
        return menu.getSlotUpgradeContainer(slot).isPresent();
    }

    @Override
    public List<Integer> upgradeSlots(int i) {
        UpgradeContainerBase<?, ?> container = container(i);
        if (container == null) {
            return List.of();
        }
        List<Integer> indices = new ArrayList<>();
        for (Slot slot : container.getSlots()) {
            indices.add(slot.index);
        }
        return indices;
    }

    // ---- layouts: the upgrades' slots wait off screen until the window lays out their tab, so they are placed here ----------

    @Nullable
    private static CookingLogicContainer<?> cooking(@Nullable UpgradeContainerBase<?, ?> container) {
        if (container instanceof CookingUpgradeContainer<?, ?> cooking) return cooking.getSmeltingLogicContainer();
        if (container instanceof AutoCookingUpgradeContainer<?, ?> auto) return auto.getCookingLogicContainer();
        return null;
    }

    @Nullable
    private static BlockConverterRecipeContainer<?, ?, ?, ?> converter(@Nullable UpgradeContainerBase<?, ?> container) {
        return container instanceof BlockConverterUpgradeContainer<?, ?, ?, ?> converter ? converter.getRecipeContainer() : null;
    }

    /**
     * A crafting grid as a crafting table's with the result a column to the right; a furnace's input over its fuel with the
     * result to the right between them; a stonecutter's input and result side by side. Any other slots of the upgrade (an
     * auto-smelting upgrade's filters) in rows below.
     */
    @Nullable
    @Override
    public Map<Integer, int[]> layout(int i) {
        UpgradeContainerBase<?, ?> container = container(i);
        Map<Integer, int[]> cells = new HashMap<>();
        int below;
        if (container instanceof CraftingUpgradeContainer crafting) {
            List<Slot> recipe = crafting.getRecipeSlots();
            for (int k = 0; k < recipe.size(); k++) {
                cells.put(recipe.get(k).index, new int[]{k % 3, k / 3});
            }
            for (Slot slot : crafting.getSlots()) {
                if (!cells.containsKey(slot.index)) cells.put(slot.index, new int[]{4, 1});
            }
            return cells;
        }
        if (container == null) {
            return null;
        }
        if (container instanceof AlchemyUpgradeContainer alchemy) {
            // as its tab: two columns of a filter and its condition (the condition button sits in the column after each)
            List<Slot> slots = alchemy.getSlots();
            for (int k = 0; k < slots.size(); k++) {
                cells.put(slots.get(k).index, new int[]{(k % 2) * 3, k / 2});
            }
            return cells;
        }
        CookingLogicContainer<?> cooking = cooking(container);
        BlockConverterRecipeContainer<?, ?, ?, ?> converter = converter(container);
        List<Map.Entry<String, FilterLogicContainerBase<?, ?>>> filters = filters(container);
        if (cooking != null && cooking.getCookingSlots().size() >= 3 && container instanceof AutoCookingUpgradeContainer<?, ?> auto) {
            // as its tab: the input filter in rows of four, the furnace under it, the fuel filter under that
            int row = 0;
            java.util.Set<Integer> hiddenTags = new java.util.HashSet<>();
            FilterLogicContainerBase<?, ?> input = auto.getInputFilterLogicContainer(), fuel = auto.getFuelFilterLogicContainer();
            row = filterRows(cells, input, row, hiddenTags);
            List<Slot> slots = cooking.getCookingSlots();
            cells.put(slots.get(0).index, new int[]{0, row});
            cells.put(slots.get(2).index, new int[]{2, row + 1});
            cells.put(slots.get(1).index, new int[]{0, row + 2});
            row += 3;
            filterRows(cells, fuel, row, hiddenTags);
            return cells;
        }
        if (cooking != null && cooking.getCookingSlots().size() >= 3) {
            List<Slot> slots = cooking.getCookingSlots();
            cells.put(slots.get(0).index, new int[]{0, 0});
            cells.put(slots.get(1).index, new int[]{0, 2});
            cells.put(slots.get(2).index, new int[]{2, 1});
            below = 3;
        } else if (converter != null) {
            cells.put(converter.getInputSlot().index, new int[]{0, 0});
            cells.put(converter.getOutputSlot().index, new int[]{2, 0});
            below = 1;
        } else if (!filters.isEmpty()) {
            below = 0;
        } else {
            return null;
        }
        // A filter's slots as its tab shows them: a square (3x3, 4x4), several filters side by side. Its tag-selection slot is left
        // out: the window only shows it when matching by tags, and an item put there picks tags, it does not filter.
        java.util.Set<Integer> hidden = new java.util.HashSet<>();
        int x = 0, filterRows = 0;
        for (Map.Entry<String, FilterLogicContainerBase<?, ?>> entry : filters) {
            FilterLogicContainerBase<?, ?> logic = entry.getValue();
            boolean byTag = logic.getPrimaryMatch() == PrimaryMatch.TAGS;
            if (byTag) {
                // matching by tag: the window swaps the filter slots for the tag slot and the lists of tags (see lines())
                for (Slot slot : logic.getFilterSlots()) hidden.add(slot.index);
                if (logic.getTagSelectionSlot() != null) {
                    cells.put(logic.getTagSelectionSlot().index, new int[]{x, below});
                }
                filterRows = Math.max(filterRows, 1);
                x += 2;
                continue;
            }
            if (logic.getTagSelectionSlot() != null) hidden.add(logic.getTagSelectionSlot().index);
            List<? extends Slot> slots = logic.getFilterSlots();
            int cols = Math.max(1, (int) Math.ceil(Math.sqrt(slots.size())));
            for (int k = 0; k < slots.size(); k++) {
                cells.put(slots.get(k).index, new int[]{x + k % cols, below + k / cols});
            }
            filterRows = Math.max(filterRows, (slots.size() + cols - 1) / cols);
            x += cols + 1;
        }
        int k = 0, start = below + filterRows;
        for (Slot slot : container.getSlots()) {
            if (!cells.containsKey(slot.index) && !hidden.contains(slot.index)) {
                cells.put(slot.index, new int[]{k % 9, start + k / 9});
                k++;
            }
        }
        return cells;
    }

    @Override
    public int layoutKey(int i) {
        UpgradeContainerBase<?, ?> container = container(i);
        int key = 1;
        if (container != null) {
            for (Map.Entry<String, FilterLogicContainerBase<?, ?>> entry : filters(container)) {
                key = key * 31 + entry.getValue().getPrimaryMatch().ordinal();
            }
        }
        return key;
    }

    /**
     * A filter matching by tag, as its tab shows it: the tags of the item in the tag slot (click one to add it), then the tags the
     * filter has (click one to take it off), each list its own section (the scene scrolls them). The selection is stepped to the
     * clicked tag, then the filter's own add or remove runs, which tells the server.
     */
    @Override
    public List<Line> lines(int i) {
        UpgradeContainerBase<?, ?> container = container(i);
        if (container == null) {
            return List.of();
        }
        List<Map.Entry<String, FilterLogicContainerBase<?, ?>>> filters = filters(container);
        List<Line> out = new ArrayList<>();
        int section = 0;
        for (int f = 0; f < filters.size(); f++) {
            FilterLogicContainerBase<?, ?> logic = filters.get(f).getValue();
            if (logic.getPrimaryMatch() != PrimaryMatch.TAGS) continue;
            Component which = filters.size() > 1
                    ? Component.translatable("cinematicchests.opt.filter_" + filters.get(f).getKey()).append(" \u00B7 ") : Component.empty();
            List<net.minecraft.tags.TagKey<net.minecraft.world.item.Item>> toAdd = new ArrayList<>(logic.getTagsToAdd());
            out.add(new Line("", which.copy().append(Component.translatable("cinematicchests.tags.item")), null, section, true));
            if (toAdd.isEmpty()) {
                out.add(new Line("", Component.translatable("cinematicchests.tags.put_item"), null, section, false));
            }
            for (int k = 0; k < toAdd.size(); k++) {
                out.add(new Line(f + ":add:" + k, Component.literal("+ #" + toAdd.get(k).location()),
                        Component.translatable("cinematicchests.tags.add"), section, false));
            }
            section++;
            List<net.minecraft.tags.TagKey<net.minecraft.world.item.Item>> have = new ArrayList<>(logic.getTagNames());
            out.add(new Line("", which.copy().append(Component.translatable("cinematicchests.tags.filter")), null, section, true));
            if (have.isEmpty()) {
                out.add(new Line("", Component.translatable("cinematicchests.tags.none"), null, section, false));
            }
            for (int k = 0; k < have.size(); k++) {
                out.add(new Line(f + ":remove:" + k, Component.literal("\u2212 #" + have.get(k).location()),
                        Component.translatable("cinematicchests.tags.remove"), section, false));
            }
            section++;
        }
        return out;
    }

    @Override
    public void pressLine(int i, String id) {
        UpgradeContainerBase<?, ?> container = container(i);
        String[] parts = id.split(":");
        if (container == null || parts.length != 3) {
            return;
        }
        List<Map.Entry<String, FilterLogicContainerBase<?, ?>>> filters = filters(container);
        int f = Integer.parseInt(parts[0]), k = Integer.parseInt(parts[2]);
        if (f < 0 || f >= filters.size()) {
            return;
        }
        FilterLogicContainerBase<?, ?> logic = filters.get(f).getValue();
        if (parts[1].equals("add")) {
            int size = logic.getTagsToAdd().size();
            for (int step = 0; step < size && logic.getSelectedTagToAdd() != k; step++) logic.selectNextTagToAdd();
            if (logic.getSelectedTagToAdd() == k) logic.addSelectedTag();
        } else {
            int size = logic.getTagNames().size();
            for (int step = 0; step < size && logic.getSelectedTagToRemove() != k; step++) logic.selectNextTagToRemove();
            if (logic.getSelectedTagToRemove() == k) logic.removeSelectedTag();
        }
    }

    /**
     * A filter's slots in rows of four from {@code row} (its tag slot alone when it matches by tag); returns the row after them.
     */
    private static int filterRows(Map<Integer, int[]> cells, FilterLogicContainerBase<?, ?> logic, int row, java.util.Set<Integer> hidden) {
        if (logic.getPrimaryMatch() == PrimaryMatch.TAGS) {
            if (logic.getTagSelectionSlot() != null) cells.put(logic.getTagSelectionSlot().index, new int[]{0, row});
            return row + 1;
        }
        List<? extends Slot> slots = logic.getFilterSlots();
        for (int k = 0; k < slots.size(); k++) {
            cells.put(slots.get(k).index, new int[]{k % 4, row + k / 4});
        }
        return row + (slots.size() + 3) / 4;
    }

    /** The cooking arrow: just before the furnace's result slot. */
    @Nullable
    @Override
    public int[] gaugeAt(int i) {
        CookingLogicContainer<?> cooking = cooking(container(i));
        Map<Integer, int[]> cells = layout(i);
        if (cooking == null || cells == null || cooking.getCookingSlots().size() < 3) {
            return null;
        }
        int[] result = cells.get(cooking.getCookingSlots().get(2).index);
        return result == null ? null : new int[]{result[0] - 1, result[1]};
    }

    @Nullable
    @Override
    public Component label(int i, int menuIndex) {
        UpgradeContainerBase<?, ?> container = container(i);
        CookingLogicContainer<?> cooking = cooking(container);
        if (cooking != null && cooking.getCookingSlots().size() >= 3) {
            List<Slot> slots = cooking.getCookingSlots();
            if (slots.get(0).index == menuIndex) return Component.translatable("cinematicchests.panel.input");
            if (slots.get(1).index == menuIndex) return Component.translatable("cinematicchests.panel.fuel");
            if (slots.get(2).index == menuIndex) return Component.translatable("cinematicchests.panel.result");
        }
        BlockConverterRecipeContainer<?, ?, ?, ?> converter = converter(container);
        if (converter != null) {
            if (converter.getInputSlot().index == menuIndex) return Component.translatable("cinematicchests.panel.input");
            if (converter.getOutputSlot().index == menuIndex) return Component.translatable("cinematicchests.panel.result");
        }
        if (container instanceof CraftingUpgradeContainer crafting) {
            return crafting.getRecipeSlots().stream().anyMatch(slot -> slot.index == menuIndex) ? null
                    : Component.translatable("cinematicchests.panel.result");
        }
        return null;
    }

    /** A furnace's arrow and flame: how far the item in it is cooked, and how much of the burning fuel is left. */
    @Nullable
    @Override
    public float[] gauges(int i) {
        CookingLogicContainer<?> cooking = cooking(container(i));
        var level = Minecraft.getInstance().level;
        if (cooking == null || level == null) {
            return null;
        }
        long now = level.getGameTime();
        float cook = cooking.isCooking() && cooking.getCookTimeTotal() > 0
                ? 1f - (cooking.getCookTimeFinish() - now) / (float) cooking.getCookTimeTotal() : 0f;
        float burn = cooking.isBurning(level) && cooking.getBurnTimeTotal() > 0
                ? (cooking.getBurnTimeFinish() - now) / (float) cooking.getBurnTimeTotal() : 0f;
        return new float[]{Math.max(0f, Math.min(1f, cook)), Math.max(0f, Math.min(1f, burn))};
    }

    // ---- buttons: each upgrade's own options, through the setters its tab's buttons call (they tell the server) ------------

    /** One button: what it shows and the change it makes. */
    private record Option(Control control, Runnable action) {}

    private static final String[] FILTER_GETTERS = {"getFilterLogicContainer", "getInputFilterLogicContainer", "getOutputFilterLogicContainer",
            "getFuelFilterLogicContainer"};
    private static final Map<Class<?>, List<java.lang.reflect.Method>> FILTER_METHODS = new java.util.concurrent.ConcurrentHashMap<>();

    /** The filters an upgrade has (most have one; a hopper an input and an output one; auto-smelting an input and a fuel one). */
    private static List<Map.Entry<String, FilterLogicContainerBase<?, ?>>> filters(UpgradeContainerBase<?, ?> container) {
        List<java.lang.reflect.Method> methods = FILTER_METHODS.computeIfAbsent(container.getClass(), type -> {
            List<java.lang.reflect.Method> found = new ArrayList<>();
            for (String name : FILTER_GETTERS) {
                try {
                    java.lang.reflect.Method method = type.getMethod(name);
                    if (FilterLogicContainerBase.class.isAssignableFrom(method.getReturnType())) found.add(method);
                } catch (NoSuchMethodException ignored) {
                    // this upgrade has no such filter
                }
            }
            return found;
        });
        List<Map.Entry<String, FilterLogicContainerBase<?, ?>>> out = new ArrayList<>();
        for (java.lang.reflect.Method method : methods) {
            try {
                if (method.invoke(container) instanceof FilterLogicContainerBase<?, ?> logic) {
                    String which = method.getName().replace("get", "").replace("FilterLogicContainer", "").toLowerCase(java.util.Locale.ROOT);
                    out.add(Map.entry(which, logic));
                }
            } catch (ReflectiveOperationException ignored) {
                // a filter that can't be read is left out
            }
        }
        return out;
    }

    private static <E extends Enum<E>> E next(E value) {
        E[] all = value.getDeclaringClass().getEnumConstants();
        return all[(value.ordinal() + 1) % all.length];
    }

    /** "Name: Value" for a tooltip, the value in title case. */
    private static Component named(String key, Object value) {
        String text = value instanceof Boolean on ? (on ? "on" : "off") : value.toString().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        Component shown = value instanceof Boolean ? Component.translatable("cinematicchests.opt." + text)
                : Component.literal(Character.toUpperCase(text.charAt(0)) + text.substring(1));
        return Component.translatable("cinematicchests.opt." + key).append(": ").append(shown);
    }

    private static Component prefixed(String filter, Component name) {
        return filter.isEmpty() ? name : Component.translatable("cinematicchests.opt.filter_" + filter).append(" · ").append(name);
    }

    private List<Option> options(int i) {
        UpgradeContainerBase<?, ?> container = container(i);
        if (container == null) {
            return List.of();
        }
        List<Option> out = new ArrayList<>();
        if (container instanceof JukeboxUpgradeContainer jukebox) {
            boolean playing = jukebox.getDiscSlotActive().isPresent();
            boolean advanced = jukebox.getSlots().size() > 1;
            if (advanced) out.add(new Option(new Control("previous", "◀◀", false,
                    Component.translatable("cinematicchests.jukebox.previous")), jukebox::previous));
            out.add(new Option(new Control("play", "▶", playing, Component.translatable("cinematicchests.jukebox.play")), jukebox::play));
            out.add(new Option(new Control("stop", "■", false, Component.translatable("cinematicchests.jukebox.stop")), jukebox::stop));
            if (advanced) {
                out.add(new Option(new Control("next", "▶▶", false, Component.translatable("cinematicchests.jukebox.next")), jukebox::next));
                boolean shuffle = jukebox.isShuffleEnabled();
                out.add(new Option(new Control("shuffle", "⇄", shuffle,
                        Component.translatable(shuffle ? "cinematicchests.jukebox.shuffle_on" : "cinematicchests.jukebox.shuffle_off")),
                        jukebox::toggleShuffle));
                RepeatMode repeat = jukebox.getRepeatMode();
                out.add(new Option(new Control("repeat", repeat == RepeatMode.ONE ? "↻1" : "↻", repeat != RepeatMode.NO,
                        Component.translatable("cinematicchests.jukebox.repeat_" + repeat.name().toLowerCase(java.util.Locale.ROOT))),
                        jukebox::toggleRepeat));
            }
            return out;
        }

        if (container instanceof AlchemyUpgradeContainer alchemy) {
            boolean all = Boolean.TRUE.equals(alchemy.shouldMatchAll()), duration = alchemy.shouldMatchDuration(),
                    amplifier = alchemy.shouldMatchAmplifier();
            out.add(new Option(new Control("alchemy_all", "\u2237", all, named("alchemy_all", all)), alchemy::toggleMatchAll));
            out.add(new Option(new Control("alchemy_duration", "\u231A", duration, named("alchemy_duration", duration)),
                    alchemy::toggleMatchDuration));
            out.add(new Option(new Control("alchemy_amplifier", "II", amplifier, named("alchemy_amplifier", amplifier)),
                    alchemy::toggleMatchAmplifier));
            if (alchemy.hasEntityMatchOption()) {
                var match = alchemy.getEntityMatch();
                String glyph = switch (match) {
                    case PLAYERS -> "P";
                    case ENTITIES -> "E";
                    default -> "P+E";
                };
                out.add(new Option(new Control("alchemy_entity", glyph, false, Component.translatable(
                        "gui.sophisticatedcore.upgrades.buttons.entity_match_" + match.name().toLowerCase(java.util.Locale.ROOT))),
                        alchemy::toggleEntityMatch));
            }
            return out;
        }

        // what the upgrade does, before its filters
        if (container instanceof FilterUpgradeContainer filter) {
            Direction direction = filter.getDirection();
            String glyph = direction == Direction.INPUT ? "↓" : direction == Direction.OUTPUT ? "↑" : "⇅";
            out.add(new Option(new Control("direction", glyph, false, named("direction", direction)), () -> filter.setDirection(next(direction))));
        }
        if (container instanceof MagnetUpgradeContainer magnet) {
            boolean items = magnet.shouldPickupItems(), xp = magnet.shouldPickupXp();
            out.add(new Option(new Control("items", "◆", items, named("pickup_items", items)), () -> magnet.setPickupItems(!items)));
            out.add(new Option(new Control("xp", "XP", xp, named("pickup_xp", xp)), () -> magnet.setPickupXp(!xp)));
        }
        if (container instanceof FeedingUpgradeContainer feeding) {
            HungerLevel hunger = feeding.getFeedAtHungerLevel();
            boolean hurt = feeding.shouldFeedImmediatelyWhenHurt();
            String glyph = hunger == HungerLevel.HALF ? "½" : hunger == HungerLevel.FULL ? "F" : "∗";
            out.add(new Option(new Control("hunger", glyph, false, named("hunger", hunger)), () -> feeding.setFeedAtHungerLevel(next(hunger))));
            out.add(new Option(new Control("hurt", "♥", hurt, named("hurt", hurt)), () -> feeding.setFeedImmediatelyWhenHurt(!hurt)));
        }
        if (container instanceof CompactingUpgradeContainer compacting) {
            boolean any = compacting.shouldCompactNonUncraftable(), gui = compacting.shouldWorkInGUI();
            out.add(new Option(new Control("uncraftable", "▦", any, named("uncraftable", any)), () -> compacting.setCompactNonUncraftable(!any)));
            out.add(new Option(new Control("gui", "G", gui, named("gui", gui)), () -> compacting.setShouldWorkdInGUI(!gui)));
        }
        if (container instanceof VoidUpgradeContainer voiding) {
            VoidType type = voiding.getVoidType();
            boolean gui = voiding.shouldWorkInGUI();
            String glyph = type == VoidType.ALWAYS ? "∅" : type == VoidType.SLOT_OVERFLOW ? "∅s" : "∅▣";
            out.add(new Option(new Control("void", glyph, false, named("void", type)), () -> voiding.setVoidType(next(type))));
            out.add(new Option(new Control("gui", "G", gui, named("gui", gui)), () -> voiding.setShouldWorkdInGUI(!gui)));
        }
        if (container instanceof CraftingUpgradeContainer crafting) {
            boolean shift = crafting.shouldShiftClickIntoStorage(), refill = crafting.shouldRefillCraftingGrid();
            out.add(new Option(new Control("shift", "⇩", shift, named("shift", shift)), () -> crafting.setShiftClickIntoStorage(!shift)));
            out.add(new Option(new Control("refill", "↺", refill, named("refill", refill)), () -> crafting.setRefillCraftingGrid(!refill)));
        }
        if (container instanceof BlockConverterUpgradeContainer<?, ?, ?, ?> converter) {
            boolean shift = converter.shouldShiftClickIntoStorage(), refill = converter.shouldRefillInput();
            out.add(new Option(new Control("shift", "⇩", shift, named("shift", shift)), () -> converter.setShiftClickIntoStorage(!shift)));
            out.add(new Option(new Control("refill", "↺", refill, named("refill_input", refill)), () -> converter.setRefillInput(!refill)));
        }
        if (container instanceof PumpUpgradeContainer pump) {
            boolean input = pump.isInput(), hand = pump.shouldInteractWithHand(), world = pump.shouldInteractWithWorld(),
                    tanks = pump.shouldInteractWithFluidHandlers();
            out.add(new Option(new Control("pump", input ? "↓" : "↑", false,
                    Component.translatable(input ? "cinematicchests.opt.pump_in" : "cinematicchests.opt.pump_out")), () -> pump.setIsInput(!input)));
            out.add(new Option(new Control("hand", "H", hand, named("pump_hand", hand)), () -> pump.setInteractWithHand(!hand)));
            out.add(new Option(new Control("world", "W", world, named("pump_world", world)), () -> pump.setInteractWithWorld(!world)));
            out.add(new Option(new Control("tanks", "T", tanks, named("pump_tanks", tanks)), () -> pump.setInteractWithFluidHandlers(!tanks)));
        }
        if (container instanceof XpPumpUpgradeContainer xp) {
            AutomationDirection direction = xp.getDirection();
            boolean mend = xp.shouldMendItems();
            int level = xp.getLevel();
            String glyph = switch (direction) {
                case INPUT -> "↓";
                case OUTPUT -> "↑";
                case KEEP -> "=";
                default -> "–";
            };
            out.add(new Option(new Control("xp_direction", glyph, direction != AutomationDirection.OFF, named("xp_direction", direction)),
                    () -> xp.setDirection(next(direction))));
            out.add(new Option(new Control("level_down", "−", false, Component.translatable("cinematicchests.opt.level", level)),
                    () -> xp.setLevel(Math.max(0, level - 1))));
            out.add(new Option(new Control("level_up", "+", false, Component.translatable("cinematicchests.opt.level", level)),
                    () -> xp.setLevel(level + 1)));
            out.add(new Option(new Control("mend", "⚒", mend, named("mend", mend)), () -> xp.setMendItems(!mend)));
        }

        // the filters, as their tabs show them; an auto-cooking tab gives buttons to its input filter only (its fuel filter is
        // just slots, at the default settings)
        List<Map.Entry<String, FilterLogicContainerBase<?, ?>>> filters = filters(container);
        if (container instanceof AutoCookingUpgradeContainer<?, ?>) {
            filters = filters.stream().filter(entry -> entry.getKey().equals("input")).toList();
        }
        for (Map.Entry<String, FilterLogicContainerBase<?, ?>> entry : filters) {
            String which = filters.size() > 1 ? entry.getKey() : "";
            FilterLogicContainerBase<?, ?> logic = entry.getValue();
            String id = "filter_" + entry.getKey() + "_";
            int first = out.size();
            if (logic instanceof ContentsFilterLogicContainer contents) {
                ContentsFilterType type = contents.getFilterType();
                String glyph = type == ContentsFilterType.ALLOW ? "✓" : type == ContentsFilterType.BLOCK ? "✗" : "▣";
                out.add(new Option(new Control(id + "type", glyph, type == ContentsFilterType.ALLOW,
                        prefixed(which, named("filter_type", type))), () -> contents.setFilterType(next(type))));
            } else {
                boolean allow = logic.isAllowList();
                out.add(new Option(new Control(id + "allow", allow ? "✓" : "✗", allow,
                        prefixed(which, Component.translatable(allow ? "cinematicchests.opt.allow" : "cinematicchests.opt.block"))),
                        () -> logic.setAllowList(!allow)));
            }
            PrimaryMatch match = logic.getPrimaryMatch();
            String matchGlyph = match == PrimaryMatch.MOD ? "M" : match == PrimaryMatch.TAGS ? "#" : "I";
            out.add(new Option(new Control(id + "match", matchGlyph, false, prefixed(which, named("match", match))),
                    () -> logic.setPrimaryMatch(next(match))));
            if (match != PrimaryMatch.TAGS) {
                boolean durability = logic.shouldMatchDurability(), nbt = logic.shouldMatchNbt();
                out.add(new Option(new Control(id + "durability", "⚒", durability, prefixed(which, named("durability", durability))),
                        () -> logic.setMatchDurability(!durability)));
                out.add(new Option(new Control(id + "nbt", "{}", nbt, prefixed(which, named("components", nbt))), () -> logic.setMatchNbt(!nbt)));
            } else {
                boolean anyTag = logic.shouldMatchAnyTag();
                out.add(new Option(new Control(id + "any_tag", anyTag ? "∨" : "∧", false, prefixed(which, named("any_tag", anyTag))),
                        () -> logic.setMatchAnyTag(!anyTag)));
            }
            if (!which.isEmpty()) {
                // a row of its own, labelled, for each of several filters
                Component group = Component.translatable("cinematicchests.opt.filter_" + which);
                for (int o = first; o < out.size(); o++) {
                    Control c = out.get(o).control();
                    out.set(o, new Option(new Control(c.id(), c.glyph(), c.lit(), c.name(), group), out.get(o).action()));
                }
            }
        }
        return out;
    }

    @Override
    public List<Control> controls(int i) {
        return options(i).stream().map(Option::control).toList();
    }

    @Override
    public void press(int i, String id) {
        if (id.startsWith("condition:") && container(i) instanceof AlchemyUpgradeContainer alchemy) {
            alchemy.toggleCondition(Integer.parseInt(id.substring("condition:".length())));
            return;
        }
        for (Option option : options(i)) {
            if (option.control().id().equals(id)) {
                option.action().run();
                return;
            }
        }
    }

    /** An alchemy filter's condition: when the potion in it is used. Its names are Sophisticated's own. */
    @Override
    public List<SlotControl> slotControls(int i) {
        if (!(container(i) instanceof AlchemyUpgradeContainer alchemy)) {
            return List.of();
        }
        List<SlotControl> out = new ArrayList<>();
        List<Slot> slots = alchemy.getSlots();
        for (int k = 0; k < slots.size(); k++) {
            AlchemyCondition condition = alchemy.getCondition(k);
            String glyph = switch (condition) {
                case NEVER -> "\u2715";
                case ALWAYS -> "\u221E";
                case UNDER_WATER -> "\u2248";
                case ON_FIRE -> "\u2668";
                case FALLING -> "\u2193";
                case MINING -> "\u2692";
                case SPRINTING -> "\u00BB";
                case HURT -> "\u2665";
                case NEGATIVE_EFFECT -> "\u2620";
                // a condition added by a later version of the mod
                default -> "?";
            };
            String key = "gui.sophisticatedcore.upgrades.buttons.alchemy_condition_" + condition.name().toLowerCase(java.util.Locale.ROOT);
            Component name = condition == AlchemyCondition.HURT
                    ? Component.translatable(key, String.valueOf(Math.round(alchemy.getValue(k))))
                    : Component.translatable(key);
            if (condition == AlchemyCondition.HURT) {
                name = name.copy().append(Component.literal(" \u00B7 ")).append(Component.translatable("cinematicchests.opt.scroll_value"));
            }
            out.add(new SlotControl(slots.get(k).index, new Control("condition:" + k, glyph, condition != AlchemyCondition.NEVER, name)));
        }
        return out;
    }

    @Override
    public boolean scrollControl(int i, String id, double delta) {
        if (!(container(i) instanceof AlchemyUpgradeContainer alchemy) || !id.startsWith("condition:")) {
            return false;
        }
        int k = Integer.parseInt(id.substring("condition:".length()));
        if (alchemy.getCondition(k) != AlchemyCondition.HURT) {
            return false;
        }
        alchemy.setValue(k, Math.max(1, Math.min(20, Math.round(alchemy.getValue(k)) + (delta > 0 ? 1 : -1))));
        return true;
    }

    @Override
    public int activeSlot(int i) {
        return container(i) instanceof JukeboxUpgradeContainer jukebox ? jukebox.getDiscSlotActive().map(slot -> slot.index).orElse(-1) : -1;
    }

    @Override
    public boolean hasControls(int i) {
        return container(i) != null;
    }

    /** A tank's fluid in the fluid's own colour; a battery's charge in red. */
    @Nullable
    @Override
    public Meter meter(int i) {
        UpgradeContainerBase<?, ?> container = container(i);
        if (container instanceof TankUpgradeContainer tank && tank.getTankCapacity() > 0) {
            var fluid = tank.getContents();
            int tint = fluid.isEmpty() ? 0xFF3F76E4
                    : net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions.of(fluid.getFluid()).getTintColor(fluid);
            Component amount = Component.literal(fluid.getAmount() + " / " + tank.getTankCapacity() + " mB")
                    .append(fluid.isEmpty() ? Component.empty() : Component.literal(" ").append(fluid.getHoverName()));
            return new Meter(fluid.getAmount() / (float) tank.getTankCapacity(), 0xFF000000 | tint, amount);
        }
        if (container instanceof BatteryUpgradeContainer battery && battery.getMaxEnergyStored() > 0) {
            return new Meter(battery.getEnergyStored() / (float) battery.getMaxEnergyStored(), 0xFFD8342C,
                    Component.literal(battery.getEnergyStored() + " / " + battery.getMaxEnergyStored() + " FE"));
        }
        return null;
    }

    /** A crafting grid that matches more than one recipe offers them all, as the window's arrows step through them. */
    @Override
    public List<ItemStack> choices(int i) {
        if (container(i) instanceof CraftingUpgradeContainer crafting) {
            List<ItemStack> matched = crafting.getMatchedCraftingResults();
            return matched.size() > 1 ? matched : List.of();
        }
        BlockConverterRecipeContainer<?, ?, ?, ?> converter = converter(container(i));
        var level = Minecraft.getInstance().level;
        if (converter == null || level == null || !converter.hasItemsInInputSlot()) {
            return List.of();
        }
        List<ItemStack> out = new ArrayList<>();
        for (var holder : converter.getRecipeList()) {
            out.add(holder.value().getResultItem(level.registryAccess()));
        }
        return out;
    }

    @Override
    public int chosen(int i) {
        if (container(i) instanceof CraftingUpgradeContainer crafting) {
            ItemStack result = ItemStack.EMPTY;
            for (Slot slot : crafting.getSlots()) {
                if (!crafting.getRecipeSlots().contains(slot)) result = slot.getItem();
            }
            List<ItemStack> matched = crafting.getMatchedCraftingResults();
            for (int k = 0; k < matched.size(); k++) {
                if (ItemStack.isSameItemSameComponents(matched.get(k), result)) return k;
            }
            return -1;
        }
        BlockConverterRecipeContainer<?, ?, ?, ?> converter = converter(container(i));
        return converter == null ? -1 : converter.getSelectedRecipe();
    }

    @Override
    public void choose(int i, int choice) {
        if (container(i) instanceof CraftingUpgradeContainer crafting) {
            crafting.selectCraftingResult(choice);
            return;
        }
        BlockConverterRecipeContainer<?, ?, ?, ?> converter = converter(container(i));
        if (converter != null) {
            converter.selectRecipe(choice);
        }
    }

    @Override
    public int open() {
        UpgradeContainerBase<?, ?> open = menu.getOpenContainer().orElse(null);
        if (open == null) {
            return -1;
        }
        for (Map.Entry<Integer, UpgradeContainerBase<?, ?>> entry : menu.getUpgradeContainers().entrySet()) {
            if (entry.getValue() == open) return entry.getKey();
        }
        return -1;
    }

    @Override
    public void setOpen(int i) {
        menu.getOpenContainer().ifPresent(container -> container.setIsOpen(false));
        if (i < 0) {
            menu.removeOpenTabId();
            return;
        }
        UpgradeContainerBase<?, ?> container = container(i);
        if (container != null) {
            container.setIsOpen(true);
            menu.setOpenTabId(i);
        }
    }
}
