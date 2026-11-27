package com.cinematicchests.client;

import com.cinematicchests.ChestConfig;
import com.cinematicchests.Storage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.dyslexic.cinematic.api.client.AddonMenuStation;
import dev.dyslexic.cinematic.client.Anim;
import dev.dyslexic.cinematic.client.CameraRig;
import dev.dyslexic.cinematic.client.ItemPlacer;
import dev.dyslexic.cinematic.client.Picking;
import dev.dyslexic.cinematic.client.Scene;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoubleBlockCombiner;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.Vec3;

/**
 * A storage block worked as a Cinematic Interactions scene. The block's real menu is open underneath (its lid opens for
 * everyone, as in vanilla); every touch is an ordinary slot click on that menu, so nothing about storing items changes.
 * <ul>
 * <li>Chest-shaped blocks: the player leans over the open chest and the items lie on its floor. A double chest shows each half's
 * slots in that half.</li>
 * <li>A barrel facing up: the items lie in its open top. Facing sideways: they sit in its open front, upright.</li>
 * <li>A shulker box facing up: the items sit upright in the opening between its base and its lifted lid.</li>
 * <li>Any other block: the items sit upright on its front face.</li>
 * </ul>
 * The slots are laid out as the block's own window lays them out (read from the menu's slot positions), so a 9x3 chest, a
 * 9x6 double chest and a modded 12x9 chest all look like their windows.
 */
public final class StorageScene<M extends AbstractContainerMenu> extends AddonMenuStation<M> {
    enum Style { BOX, TOP, FACE, FLOAT }

    /** Hand height over a surface item, and how far in front of it the fist stays (as at the crafter). */
    private static final double HAND_Y = 0.062, ITEM_REACH = 0.075;
    /**
     * Height over the block's bottom where the items lie inside a chest: on the dark top face of the chest's base (10 px). The
     * base is drawn as one solid box, so anything lower is hidden inside it.
     */
    private static final double BOX_ITEM_Y = 10 / 16.0 + 0.004;
    /** Degrees a lying item is tipped up towards the player. */
    private static final float FLAT_TILT = 20f;
    /**
     * Rows on a surface lie a little more than a slot's width apart (looked at from above at a slant, that reads as the
     * window's square spacing); spread over the whole chest floor they looked like separate shelves.
     */
    private static final double ROW_PITCH = 1.3;
    /** Rows on an upright face, relative to the slot width. */
    private static final double UPRIGHT_ROW_PITCH = 1.35;
    /** Pixels between slots in a container window. */
    private static final int WINDOW_SLOT = 18;

    /**
     * Where slots can be laid: a plane through {@code center}; {@code u} runs along a row (the player's right), {@code v} towards
     * the first row (away from the player on a surface, up on a face), {@code normal} out of the plane towards the camera.
     * {@code width} along u and {@code depth} along v are the room there is; {@code lying}: a surface looked down on (items lie
     * on it) rather than a face looked at (items stand on it).
     */
    record Surface(Vec3 center, Vec3 u, Vec3 v, Vec3 normal, double width, double depth, boolean lying) {}

    /** Slots laid on a surface: each menu slot index at a column and row. */
    record Grid(Surface at, int cols, int rows, double cellU, double cellV, Map<Integer, int[]> cells, int[] lookup) {
        Vec3 u() { return at.u(); }
        Vec3 v() { return at.v(); }
        Vec3 normal() { return at.normal(); }

        Vec3 cell(int slot) {
            int[] cr = cells.get(slot);
            return point(cr[0], cr[1]);
        }

        /** The middle of column {@code col}, row {@code row} (fractions between them too). */
        Vec3 point(double col, double row) {
            return at.center().add(at.u().scale((col - (cols - 1) / 2.0) * cellU)).add(at.v().scale(((rows - 1) / 2.0 - row) * cellV));
        }

        /** The menu slot under a point of the plane, or -1. */
        int pick(Vec3 hit) {
            Vec3 rel = hit.subtract(at.center());
            double a = rel.dot(at.u()) / cellU + cols / 2.0;
            double b = rows / 2.0 - rel.dot(at.v()) / cellV;
            if (a < 0 || b < 0 || a >= cols || b >= rows) {
                return -1;
            }
            return lookup[(int) b * cols + (int) a];
        }
    }

    private final Storage shape;
    private final Style style;
    /** One surface, or two for a double chest (the combined container's first half, then the second). */
    private final List<Surface> surfaces = new ArrayList<>(2);
    /** The other half of a double chest, or null. */
    @Nullable
    private final BlockPos partner;
    /** Middle of the whole work (between the halves of a double chest). */
    private final Vec3 work;
    /** Middle of the block(s) at the floor: the player stands in front of it. */
    private final Vec3 base;

    /** The slots of the open menu, laid out; built when the menu arrives. */
    private List<Grid> grids = List.of();
    /** The block's own slots in the menu (not the player's inventory), in menu order. */
    private List<Integer> slots = List.of();
    private int laidOutFor = Integer.MIN_VALUE;
    /** Upgrade slots the menu keeps apart (Sophisticated Storage's), hung on the block: null without any. */
    @Nullable
    private UpgradeRack rack;
    /** The rack's slots, as menu indices, in rack order. */
    private List<Integer> rackSlots = List.of();
    private int rackCount = -1;
    @Nullable
    private Component lastRackError;
    /** The open upgrade's own slots (its crafting grid, its filter), laid out over the block; null while none is open. */
    @Nullable
    private Grid panel;
    private List<Integer> panelSlots = List.of();
    /** Which upgrade the panel shows (-1: none), and how far the camera has drawn back to show it (0..1). */
    private int panelFor = -1;
    private double panelView;
    /** What the open upgrade can make of its input, to choose from (a stonecutter's shapes); the one under the cursor. */
    private List<ItemStack> choices = List.of();
    private int hoveredChoice = -1;
    /** The open upgrade's buttons (a jukebox's), in a row over its slots; the one under the cursor. */
    private List<UpgradeRack.Control> controls = List.of();
    private int hoveredControl = -1;
    /** Buttons beside single slots (an alchemy filter's condition); the one under the cursor. */
    private List<UpgradeRack.SlotControl> slotControls = List.of();
    private int hoveredSlotControl = -1;
    /** The open upgrade's lines of text (a filter's tags), beside its slots; the one under the cursor. */
    private List<UpgradeRack.Line> lines = List.of();
    private int hoveredLine = -1;
    private int panelKey;
    private static final double LINE_H = 0.032, LINE_W = 0.46;
    /** The lines as shown: each section's header, then a window of its lines scrolled to {@link #lineScroll}. */
    private record Shown(int line, int section, Component text, boolean header) {}
    private List<Shown> shown = List.of();
    private final Map<Integer, Integer> lineScroll = new HashMap<>();
    /** The section under the cursor (for the mouse wheel), or -1. */
    private int hoveredSection = -1;
    private static final int CHOICES_PER_ROW = 10;

    private int hovered = -1;
    /** Scene clock when the menu (and so the contents) first arrived; -1 before. */
    private double shownAt = -1;
    /** What the block held when the player stepped away: it settles back in as the scene closes. */
    private Map<Integer, ItemStack> leaving = Map.of();

    public StorageScene(BlockPos pos, Direction side, BlockState state, Storage shape, Class<M> menuType) {
        super(pos, side, menuType, 0);
        this.shape = shape;
        Vec3 up = new Vec3(0, 1, 0);
        boolean facingUp = !state.hasProperty(BlockStateProperties.FACING) || state.getValue(BlockStateProperties.FACING) == Direction.UP;
        style = switch (shape) {
            case CHEST, ENDER_CHEST -> Style.BOX;
            case BARREL -> facingUp ? Style.TOP : Style.FACE;
            case SHULKER_BOX -> Style.FLOAT;
            case FRONT -> Style.FACE;
        };
        partner = shape == Storage.CHEST ? doubleChestPartner(pos, state) : null;
        switch (style) {
            case BOX -> {
                if (partner == null) {
                    surfaces.add(boxSurface(pos, null, 0.72));
                } else {
                    // the RIGHT-type half is the combined container's first: its slots lie in it, the rest in the other
                    boolean thisFirst = ChestBlock.getBlockType(state) == DoubleBlockCombiner.BlockType.FIRST;
                    BlockPos first = thisFirst ? pos : partner, second = thisFirst ? partner : pos;
                    surfaces.add(boxSurface(first, second, 0.82));
                    surfaces.add(boxSurface(second, first, 0.82));
                }
            }
            case TOP -> surfaces.add(new Surface(Vec3.atBottomCenterOf(pos).add(0, 1.003, 0), right(), forward(), up, 0.78, 0.7, true));
            // at most half a block tall: a big window (a modded 12x9) still fits in view below the inventory drawer at the top
            case FACE -> surfaces.add(new Surface(Vec3.atCenterOf(pos).add(side().scale(0.503)), right(), up, side(), 0.8, 0.5, false));
            // in the gap that opens between the base (8 px) and the lifted lid (whose bottom rises to 12 px), upright, so the
            // contents sit in the opening of the box
            case FLOAT -> surfaces.add(new Surface(Vec3.atBottomCenterOf(pos).add(0, 10 / 16.0, 0).add(side().scale(0.503)), right(), up,
                    side(), 0.9, 0.25, false));
        }
        Vec3 sum = Vec3.ZERO;
        for (Surface surface : surfaces) sum = sum.add(surface.center());
        work = sum.scale(1.0 / surfaces.size());
        base = partner == null ? Vec3.atBottomCenterOf(pos) : Vec3.atBottomCenterOf(pos).add(Vec3.atBottomCenterOf(partner)).scale(0.5);
    }

    private Surface boxSurface(BlockPos block, @Nullable BlockPos toward, double width) {
        Vec3 center = Vec3.atBottomCenterOf(block).add(0, BOX_ITEM_Y, 0);
        if (toward != null) {
            // a double chest has no wall at the seam: each half's room is shifted a pixel towards it
            center = center.add(Vec3.atLowerCornerOf(toward.subtract(block)).scale(1 / 16.0));
        }
        return new Surface(center, right(), forward(), new Vec3(0, 1, 0), width, 0.7, true);
    }

    /**
     * The other half of a double chest. Read from the standard chest properties (type and facing) rather than the vanilla chest
     * class, so modded chests that pair up the same way (Sophisticated Storage's) are found too.
     */
    @Nullable
    private static BlockPos doubleChestPartner(BlockPos pos, BlockState state) {
        Level level = Minecraft.getInstance().level;
        if (level == null || !state.hasProperty(BlockStateProperties.CHEST_TYPE) || !state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                || state.getValue(BlockStateProperties.CHEST_TYPE) == ChestType.SINGLE) {
            return null;
        }
        Direction toward = ChestBlock.getConnectedDirection(state);
        BlockPos other = pos.relative(toward);
        BlockState there = level.getBlockState(other);
        boolean joined = there.is(state.getBlock()) && there.getValue(BlockStateProperties.CHEST_TYPE) != ChestType.SINGLE
                && ChestBlock.getConnectedDirection(there) == toward.getOpposite();
        return joined ? other.immutable() : null;
    }

    /** The other half of the double chest worked here, or null. */
    @Nullable
    public BlockPos partner() {
        return partner;
    }

    private boolean flat() {
        return style == Style.BOX || style == Style.TOP;
    }

    /** The block's menu; never the player's own inventory (a modded block's scene accepts any menu class). */
    @Nullable
    @Override
    public M menu() {
        M menu = super.menu();
        return menu instanceof InventoryMenu ? null : menu;
    }

    private ItemStack carried() {
        M menu = menu();
        return menu == null ? ItemStack.EMPTY : menu.getCarried();
    }

    // ---- laying out the slots ------------------------------------------------------------------------

    /** Lays the open menu's slots out on the surfaces, once per menu. */
    private void layout() {
        M menu = menu();
        LocalPlayer player = Minecraft.getInstance().player;
        if (menu == null || player == null) {
            return;
        }
        if (menu.containerId != laidOutFor) {
            rack = UpgradeRack.of(menu);
        }
        int upgrades = rack == null ? 0 : rack.count();
        if (menu.containerId == laidOutFor && upgrades == rackCount) {
            return;
        }
        laidOutFor = menu.containerId;
        rackCount = upgrades;
        List<Integer> own = new ArrayList<>();
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot candidate = menu.slots.get(i);
            // an upgrade's own controls (a crafting grid...) are menu slots too, but not storage
            if (candidate.container != player.getInventory() && (rack == null || !rack.belongsToUpgrade(candidate))) own.add(i);
        }
        slots = own;
        int n = own.size();
        int[] col = new int[n], row = new int[n];
        int cols = 0, rows = 0;
        boolean fromWindow = n > 0;
        if (fromWindow) {
            // as the window shows them: columns and rows from the slots' pixel positions
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
            for (int index : own) {
                Slot slot = menu.slots.get(index);
                minX = Math.min(minX, slot.x);
                minY = Math.min(minY, slot.y);
            }
            Set<Integer> taken = new HashSet<>();
            for (int k = 0; k < n; k++) {
                Slot slot = menu.slots.get(own.get(k));
                col[k] = Math.round((slot.x - minX) / (float) WINDOW_SLOT);
                row[k] = Math.round((slot.y - minY) / (float) WINDOW_SLOT);
                cols = Math.max(cols, col[k] + 1);
                rows = Math.max(rows, row[k] + 1);
                fromWindow &= taken.add(row[k] * 1000 + col[k]);
            }
            // slots hidden off screen or laid out irregularly: not a usable grid (a tall scrolling window is fine)
            fromWindow &= cols <= 27 && rows <= 60 && cols * rows <= Math.max(n * 2, 9);
        }
        if (!fromWindow) {
            // in order, in rows as wide as suits the room there is: at least 9 (as a chest's window), wider for a big container
            double room = 0;
            for (Surface surface : surfaces) room += surface.width() / surface.depth();
            int perSurface = (n + surfaces.size() - 1) / surfaces.size();
            cols = Math.max(Math.min(9, Math.max(1, n)), (int) Math.ceil(Math.sqrt(perSurface * room / surfaces.size())));
            rows = Math.max(1, (n + cols - 1) / cols);
            if (surfaces.size() == 2 && rows % 2 == 1) rows++;
            for (int k = 0; k < n; k++) {
                col[k] = k % cols;
                row[k] = k / cols;
            }
        }
        List<Grid> out = new ArrayList<>(2);
        if (surfaces.size() == 2 && rows % 2 == 0) {
            // a double chest: the window's top half lies in the first chest, the bottom half in the second
            int half = rows / 2;
            out.add(grid(surfaces.get(0), cols, half, own, col, row, 0, half));
            out.add(grid(surfaces.get(1), cols, half, own, col, row, half, rows));
        } else {
            Surface one = surfaces.get(0);
            if (surfaces.size() == 2) {
                // can't be split between the halves: one grid over both
                one = new Surface(work, one.u(), one.v(), one.normal(), 1.7, one.depth(), one.lying());
            }
            out.add(grid(one, cols, rows, own, col, row, 0, rows));
        }
        List<Integer> hung = new ArrayList<>(upgrades);
        if (upgrades > 0) {
            // the upgrades in one row, each a slot of its own
            Surface at = rackSurface();
            double cell = Math.min(at.width() / upgrades, 0.1);
            Map<Integer, int[]> cells = new HashMap<>();
            int[] lookup = new int[upgrades];
            for (int i = 0; i < upgrades; i++) {
                int index = rack.menuSlot(i);
                hung.add(index);
                cells.put(index, new int[]{i, 0});
                lookup[i] = index;
            }
            out.add(new Grid(at, upgrades, 1, cell, cell, cells, lookup));
        }
        rackSlots = hung;
        grids = out;
    }

    /**
     * Where the upgrades hang: inside a chest's open lid (it stands upright at the back, its inside towards the player), behind
     * the open top of a barrel, below the slots on a front, on a shulker box's base below the opening.
     */
    private Surface rackSurface() {
        Vec3 up = new Vec3(0, 1, 0);
        return switch (style) {
            case BOX -> new Surface(new Vec3(base.x, base.y + 0.84, base.z).subtract(side().scale(7 / 16.0 - 0.004)), right(), up, side(),
                    partner == null ? 0.8 : 1.6, 0.12, false);
            case TOP -> new Surface(Vec3.atBottomCenterOf(pos).add(0, 1.07, 0).subtract(side().scale(0.47)), right(), up, side(), 0.8, 0.12, false);
            case FACE -> new Surface(surfaces.get(0).center().add(0, -0.32, 0), right(), up, side(), 0.8, 0.12, false);
            case FLOAT -> new Surface(Vec3.atBottomCenterOf(pos).add(0, 0.3, 0).add(side().scale(0.503)), right(), up, side(), 0.8, 0.12, false);
        };
    }

    /** The block's own slots, then the rack's. */
    private List<Integer> allSlots() {
        if (rackSlots.isEmpty() && panelSlots.isEmpty()) return slots;
        List<Integer> all = new ArrayList<>(slots.size() + rackSlots.size() + panelSlots.size());
        all.addAll(slots);
        all.addAll(rackSlots);
        all.addAll(panelSlots);
        return all;
    }

    /**
     * Keeps the panel in step with the upgrade that is open (opened here, or still open from the window): its slots laid out
     * as its tab lays them out, over the block.
     */
    private void updatePanel() {
        try {
            updatePanelNow();
        } catch (RuntimeException e) {
            // another mod's menu not being what was expected must not take the game down: no panel, and say so once
            if (panelFor != Integer.MIN_VALUE) {
                com.cinematicchests.CinematicChests.LOGGER.warn("[Cinematic Chests] could not lay out an upgrade's slots", e);
            }
            panel = null;
            panelSlots = List.of();
            panelFor = Integer.MIN_VALUE;
        }
    }

    private void updatePanelNow() {
        M menu = menu();
        int open = rack == null || menu == null ? -1 : rack.open();
        choices = open >= 0 && rack != null ? rack.choices(open) : List.of();
        controls = open >= 0 && rack != null ? rack.controls(open) : List.of();
        slotControls = open >= 0 && rack != null ? rack.slotControls(open) : List.of();
        lines = open >= 0 && rack != null ? rack.lines(open) : List.of();
        buildShown();
        int key = open >= 0 && rack != null ? rack.layoutKey(open) : 0;
        if (open == panelFor && key == panelKey) {
            return;
        }
        panelFor = open;
        panelKey = key;
        List<Integer> indices = open < 0 ? List.of() : rack.upgradeSlots(open);
        Map<Integer, int[]> known = open < 0 ? null : rack.layout(open);
        if (known != null) {
            // the mod's arrangement also says which slots to show (a filter's tag-selection slot is not one of them)
            indices = indices.stream().filter(known::containsKey).toList();
        }
        if (indices.isEmpty() || menu == null) {
            panel = null;
            panelSlots = List.of();
            return;
        }
        int n = indices.size();
        int[] col = new int[n], row = new int[n];
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, cols = 0, rows = 0;
        for (int index : indices) {
            // getSlot, not the slot list: the mod keeps upgrade slots past the end of the list, and its getSlot knows them
            minX = Math.min(minX, menu.getSlot(index).x);
            minY = Math.min(minY, menu.getSlot(index).y);
        }
        Set<Integer> taken = new HashSet<>();
        boolean fromWindow = true;
        for (int k = 0; k < n; k++) {
            Slot slot = menu.getSlot(indices.get(k));
            col[k] = Math.round((slot.x - minX) / (float) WINDOW_SLOT);
            row[k] = Math.round((slot.y - minY) / (float) WINDOW_SLOT);
            cols = Math.max(cols, col[k] + 1);
            rows = Math.max(rows, row[k] + 1);
            fromWindow &= taken.add(row[k] * 1000 + col[k]);
        }
        if (known != null) {
            // the mod's own arrangement (its slots wait off screen until its window lays out the tab)
            cols = 0;
            rows = 0;
            for (int k = 0; k < n; k++) {
                int[] cr = known.getOrDefault(indices.get(k), new int[]{k, 0});
                col[k] = cr[0];
                row[k] = cr[1];
                cols = Math.max(cols, col[k] + 1);
                rows = Math.max(rows, row[k] + 1);
            }
        } else if (!fromWindow || cols > 12 || rows > 8) {
            cols = Math.min(9, n);
            rows = (n + cols - 1) / cols;
            for (int k = 0; k < n; k++) {
                col[k] = k % cols;
                row[k] = k / cols;
            }
        }
        Surface at = panelSurface();
        double cell = Math.min(0.1, Math.min(at.width() / cols, at.depth() / rows));
        Map<Integer, int[]> cells = new HashMap<>();
        int[] lookup = new int[cols * rows];
        Arrays.fill(lookup, -1);
        for (int k = 0; k < n; k++) {
            cells.put(indices.get(k), new int[]{col[k], row[k]});
            lookup[row[k] * cols + col[k]] = indices.get(k);
        }
        if (!lines.isEmpty()) {
            // a list beside the slots: the slots move left, the list takes the right
            at = new Surface(at.center().subtract(at.u().scale(at.width() * 0.3)), at.u(), at.v(), at.normal(), at.width(), at.depth(),
                    at.lying());
        }
        panel = new Grid(at, cols, rows, cell, cell, cells, lookup);
        panelSlots = indices;
    }

    /** Where choice {@code k} hangs: in rows over the panel's top, the first row lowest. */
    private Vec3 choicePoint(int k) {
        int perRow = Math.min(CHOICES_PER_ROW, choices.size());
        double pitch = choicePitch();
        int row = k / CHOICES_PER_ROW + controlRows(), col = k % CHOICES_PER_ROW;
        Vec3 top = panel.at().center().add(panel.v().scale(panel.rows() * panel.cellV() / 2 + pitch * 0.9));
        return top.add(panel.u().scale((col - (perRow - 1) / 2.0) * pitch)).add(panel.v().scale(row * pitch));
    }

    private static final int CONTROLS_PER_ROW = 8;

    /** Each button's {row, column, buttons in its row}: a new row for each labelled group (a filter of several), and every 8. */
    private int[][] controlCells() {
        int[][] cells = new int[controls.size()][];
        int row = 0, col = 0;
        for (int k = 0; k < controls.size(); k++) {
            boolean newGroup = k > 0 && !java.util.Objects.equals(controls.get(k).group(), controls.get(k - 1).group());
            if (k > 0 && (col >= CONTROLS_PER_ROW || newGroup)) {
                row++;
                col = 0;
            }
            cells[k] = new int[]{row, col, 0};
            col++;
        }
        for (int[] cell : cells) {
            int inRow = 0;
            for (int[] other : cells) if (other[0] == cell[0]) inRow++;
            cell[2] = inRow;
        }
        return cells;
    }

    /** Where button {@code k} sits: over the panel's top, each row centred, the first row lowest. */
    private Vec3 controlPoint(int k) {
        double pitch = controlPitch();
        int[] cell = controlCells()[k];
        Vec3 top = panel.at().center().add(panel.v().scale(panel.rows() * panel.cellV() / 2 + pitch * 0.85));
        return top.add(panel.u().scale((cell[1] - (cell[2] - 1) / 2.0) * pitch)).add(panel.v().scale(cell[0] * pitch));
    }

    /** Rows the buttons take, for the choices to sit over them. */
    private int controlRows() {
        int rows = 0;
        for (int[] cell : controlCells()) rows = Math.max(rows, cell[0] + 1);
        return rows;
    }

    private double controlPitch() {
        return panel == null ? 0.08 : Math.min(0.09, Math.max(0.07, panel.cellU()));
    }

    /** How many lines of each section show at once: three, two when there are more than two sections (a hopper's two filters). */
    private int visibleLines() {
        int sections = 0;
        for (UpgradeRack.Line line : lines) sections = Math.max(sections, line.section() + 1);
        return sections > 2 ? 2 : 3;
    }

    /** Each section's header, with where the window stands when it scrolls, and the lines in its window. */
    private void buildShown() {
        List<Shown> out = new ArrayList<>();
        int visible = visibleLines();
        for (int j = 0; j < lines.size(); j++) {
            UpgradeRack.Line header = lines.get(j);
            if (!header.header()) continue;
            List<Integer> body = new ArrayList<>();
            for (int k = j + 1; k < lines.size() && !lines.get(k).header() && lines.get(k).section() == header.section(); k++) body.add(k);
            int n = body.size();
            int offset = Math.max(0, Math.min(lineScroll.getOrDefault(header.section(), 0), n - visible));
            lineScroll.put(header.section(), offset);
            Component title = header.text();
            if (n > visible) {
                title = title.copy().append(" " + (offset + 1) + "\u2013" + Math.min(n, offset + visible) + "/" + n + " \u2195");
            }
            out.add(new Shown(j, header.section(), title, true));
            for (int k = offset; k < Math.min(n, offset + visible); k++) {
                out.add(new Shown(body.get(k), header.section(), lines.get(body.get(k)).text(), false));
            }
        }
        shown = out;
    }

    /** The wheel over a list scrolls it. */
    @Override
    public void scroll(double delta) {
        if (hoveredSlotControl >= 0 && hoveredSlotControl < slotControls.size() && rack != null && panelFor >= 0 && delta != 0
                && rack.scrollControl(panelFor, slotControls.get(hoveredSlotControl).control().id(), delta)) {
            sound(SoundEvents.UI_BUTTON_CLICK.value(), 0.2f, delta > 0 ? 1.4f : 1.0f);
            return;
        }
        if (hoveredSection >= 0 && delta != 0) {
            lineScroll.merge(hoveredSection, delta > 0 ? -1 : 1, Integer::sum);
            buildShown();
        }
    }

    /** The left end of line {@code j}, in the middle of its height: to the right of the panel's slots, top down. */
    private Vec3 linePoint(int j) {
        Vec3 topRight = panel.point(panel.cols() - 0.5, 0).add(panel.v().scale(panel.cellV() * 0.5));
        return topRight.add(panel.u().scale(0.04)).subtract(panel.v().scale(LINE_H * (j + 0.5)));
    }

    /** Where a slot's own button sits: one column to its right. */
    @Nullable
    private Vec3 slotControlPoint(UpgradeRack.SlotControl control) {
        return panel != null && panel.cells().containsKey(control.menuIndex())
                ? panel.cell(control.menuIndex()).add(panel.u().scale(panel.cellU())) : null;
    }

    private double choicePitch() {
        return panel == null ? 0.07 : Math.min(0.07, panel.cellU() * 0.75);
    }

    /**
     * Where an open upgrade's slots are laid: on a chest's open lid above the rack, standing just over the block otherwise
     * (over a shulker box, in front of its lifted lid).
     */
    private Surface panelSurface() {
        Vec3 up = new Vec3(0, 1, 0);
        return switch (style) {
            case BOX -> new Surface(new Vec3(base.x, base.y + 1.13, base.z).subtract(side().scale(7 / 16.0 - 0.005)), right(), up, side(),
                    partner == null ? 0.8 : 1.6, 0.36, false);
            case TOP -> new Surface(Vec3.atBottomCenterOf(pos).add(0, 1.36, 0).subtract(side().scale(0.47)), right(), up, side(), 0.8, 0.36, false);
            // on the top, towards its front edge: the buttons over the slots then fall in the top's middle, not past its far edge
            case FACE -> new Surface(Vec3.atBottomCenterOf(pos).add(0, 1.004, 0).add(side().scale(0.2)), right(), forward(), up, 0.8, 0.5, true);
            case FLOAT -> new Surface(Vec3.atBottomCenterOf(pos).add(0, 1.17, 0).add(side().scale(0.53)), right(), up, side(), 0.8, 0.36, false);
        };
    }

    /** Which upgrade a menu index is (0..), or -1 for an ordinary slot. */
    private int upgradeAt(int index) {
        return rackSlots.indexOf(index);
    }

    /** Whether a menu slot is a filter slot (Sophisticated's), which holds a pattern rather than an item. */
    private boolean isFilterSlot(int index) {
        M menu = menu();
        if (menu == null || !net.neoforged.fml.ModList.get().isLoaded("sophisticatedcore") || GuardedRack.broken()) {
            return false;
        }
        try {
            return com.cinematicchests.compat.SophisticatedFilters.isFilterSlot(menu, index);
        } catch (LinkageError e) {
            GuardedRack.fail(e);
            return false;
        }
    }

    /** Whether the menu would let this stack into the slot. */
    private boolean mayPlace(int index, ItemStack stack) {
        M menu = menu();
        return menu != null && menu.getSlot(index).mayPlace(stack);
    }

    /** The slots whose window row is in [fromRow, toRow), laid on {@code at}. */
    private Grid grid(Surface at, int cols, int rows, List<Integer> own, int[] col, int[] row, int fromRow, int toRow) {
        double cellU = at.width() / cols;
        double cellV = Math.min(cellU * (flat() ? ROW_PITCH : UPRIGHT_ROW_PITCH), at.depth() / rows);
        // no wider than deep: few columns over a wide surface (a 12-wide window across a double chest) close up in the middle
        // instead of standing apart in stripes
        cellU = Math.min(cellU, cellV * 1.15);
        Map<Integer, int[]> cells = new HashMap<>();
        int[] lookup = new int[cols * rows];
        Arrays.fill(lookup, -1);
        for (int k = 0; k < own.size(); k++) {
            if (row[k] < fromRow || row[k] >= toRow) continue;
            int r = row[k] - fromRow;
            cells.put(own.get(k), new int[]{col[k], r});
            lookup[r * cols + col[k]] = own.get(k);
        }
        return new Grid(at, cols, rows, cellU, cellV, cells, lookup);
    }

    @Nullable
    private Grid gridFor(int slot) {
        if (panel != null && panel.cells().containsKey(slot)) return panel;
        for (Grid grid : grids) {
            if (grid.cells().containsKey(slot)) return grid;
        }
        return null;
    }

    /** Edge of a slot's square, half its side: as wide as the slot, whatever the row depth. */
    private static double half(Grid grid) {
        return Math.min(grid.cellU(), grid.cellV()) * 0.5;
    }

    // ---- staging -------------------------------------------------------------------------------------

    @Override
    public Vec3 center() {
        return work;
    }

    @Override
    public boolean workOnTop() {
        return flat();
    }

    /**
     * Inside a chest the work is hidden by the chest's own lid until it opens: the points that must be in view are kept within
     * the block that was used, which the reach check lets through.
     */
    @Override
    public List<Vec3> workSamples() {
        if (style != Style.BOX) {
            return super.workSamples();
        }
        Surface own = surfaces.get(0);
        for (Surface surface : surfaces) {
            if (Math.abs(surface.center().x - (pos.getX() + 0.5)) < 0.5 && Math.abs(surface.center().z - (pos.getZ() + 0.5)) < 0.5) own = surface;
        }
        Vec3 c = own.center().add(0, 0.04, 0), a = right().scale(0.2), b = side().scale(0.2);
        return List.of(c, c.add(a), c.subtract(a), c.add(b), c.subtract(b));
    }

    @Override
    public boolean worksAtAnAngle() {
        return style != Style.FLOAT;
    }

    @Override
    public boolean crouches() {
        return style == Style.FACE || style == Style.FLOAT;
    }

    @Override
    public double leanFov() {
        // a chest's slots are small: lean in closer than for a block-sized surface
        if (style == Style.BOX) return partner != null ? 58 : 48;
        return 56;
    }

    @Override
    public float bodyPitch() {
        return flat() ? 50f : 15f;
    }

    @Override
    public List<Vec3> approach(LocalPlayer player) {
        Vec3 stand = new Vec3(base.x + side.getStepX() * 0.9, player.getY(), base.z + side.getStepZ() * 0.9);
        boolean walk = Math.hypot(stand.x - player.getX(), stand.z - player.getZ()) > 0.06;
        return walk ? List.of(stand) : List.of();
    }

    @Override
    public CameraRig.Pose lean() {
        Vec3 eye, look;
        switch (style) {
            case BOX -> {
                eye = partner == null ? work.add(side().scale(0.7)).add(0, 0.95, 0) : work.add(side().scale(0.95)).add(0, 1.2, 0);
                look = work.subtract(side().scale(0.04));
            }
            case TOP -> {
                eye = work.add(side().scale(0.62)).add(0, 0.6, 0);
                look = work.subtract(side().scale(0.04));
            }
            case FACE -> {
                eye = work.add(side().scale(0.8)).add(0, 0.12, 0);
                // aimed a little above the middle, so the slots sit a little low on screen, clear of the drawer
                look = work.add(0, 0.05, 0);
            }
            default -> {
                // crouched in front of the opening, a little above it
                eye = work.add(side().scale(0.75)).add(0, 0.14, 0);
                look = work;
            }
        }
        if (panelView > 0.001 && style == Style.FACE) {
            // standing up over the block: its top (the open upgrade) and its front (the storage) both in view
            double k = Anim.smoother(panelView);
            Vec3 openEye = work.add(side().scale(0.55)).add(0, 0.95, 0);
            Vec3 openLook = panelSurface().center().add(work).scale(0.5);
            eye = Anim.lerp(eye, openEye, k);
            look = Anim.lerp(look, openLook, k);
        } else if (panelView > 0.001) {
            // half way towards the open upgrade's slots and back a little, the same way round: both in view
            Vec3 toPanel = panelSurface().center().subtract(work).scale(0.5 * Anim.smoother(panelView));
            Vec3 back = side().scale(0.18 * Anim.smoother(panelView));
            eye = eye.add(toPanel).add(back);
            look = look.add(toPanel);
        }
        Vec3 dir = look.subtract(eye);
        float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(dir.y, Math.hypot(dir.x, dir.z)));
        return new CameraRig.Pose(eye, yaw, pitch, 0);
    }

    @Override
    public Vec3 rest(boolean rightHand) {
        double sign = rightHand ? 1 : -1;
        return switch (style) {
            // on the front rim of the chest
            case BOX -> base.add(side().scale(0.44)).add(0, 0.66, 0).add(right().scale(sign * (partner == null ? 0.26 : 0.5)));
            case TOP -> work.add(side().scale(0.38)).add(right().scale(sign * 0.26)).add(0, 0.06, 0);
            case FACE -> work.add(side().scale(0.18)).add(right().scale(sign * 0.38)).add(0, -0.28, 0);
            // on the box's front, either side below the opening
            case FLOAT -> work.add(side().scale(0.16)).add(right().scale(sign * 0.4)).add(0, -0.22, 0);
        };
    }

    @Override
    public BlockPos lightPos() {
        return style == Style.FACE ? pos.relative(side) : pos.above();
    }

    @Override
    public SoundEvent touchSound() {
        return shape == Storage.ENDER_CHEST || shape == Storage.SHULKER_BOX ? SoundEvents.STONE_STEP : SoundEvents.WOOD_STEP;
    }

    // ---- cursor and hands ----------------------------------------------------------------------------

    @Override
    public void updateCursor(boolean overPanel) {
        clearCursor();
        if (overPanel || menu() == null) {
            return;
        }
        layout();
        updatePanel();
        if (panel != null && !controls.isEmpty()) {
            Vec3 hit = Picking.cursorOnPlane(panel.at().center(), panel.normal());
            double half = controlPitch() * 0.45;
            for (int k = 0; hit != null && k < controls.size(); k++) {
                Vec3 rel = hit.subtract(controlPoint(k));
                if (Math.abs(rel.dot(panel.u())) <= half && Math.abs(rel.dot(panel.v())) <= half) {
                    hoveredControl = k;
                    aim = controlPoint(k);
                    return;
                }
            }
        }
        if (panel != null && !slotControls.isEmpty()) {
            Vec3 hit = Picking.cursorOnPlane(panel.at().center(), panel.normal());
            double half = panel.cellU() * 0.42;
            for (int k = 0; hit != null && k < slotControls.size(); k++) {
                Vec3 at = slotControlPoint(slotControls.get(k));
                if (at == null) continue;
                Vec3 rel = hit.subtract(at);
                if (Math.abs(rel.dot(panel.u())) <= half && Math.abs(rel.dot(panel.v())) <= half) {
                    hoveredSlotControl = k;
                    aim = at;
                    return;
                }
            }
        }
        hoveredSection = -1;
        if (panel != null && !shown.isEmpty()) {
            Vec3 hit = Picking.cursorOnPlane(panel.at().center(), panel.normal());
            for (int j = 0; hit != null && j < shown.size(); j++) {
                Vec3 rel = hit.subtract(linePoint(j));
                double along = rel.dot(panel.u()), across = rel.dot(panel.v());
                if (along < -0.02 || along > LINE_W || Math.abs(across) > LINE_H / 2) continue;
                hoveredSection = shown.get(j).section();
                if (!shown.get(j).header() && lines.get(shown.get(j).line()).clickable()) {
                    hoveredLine = shown.get(j).line();
                    aim = linePoint(j).add(panel.u().scale(0.05));
                    return;
                }
            }
        }
        if (panel != null && !choices.isEmpty()) {
            Vec3 hit = Picking.cursorOnPlane(panel.at().center(), panel.normal());
            double half = choicePitch() * 0.5;
            for (int k = 0; hit != null && k < choices.size(); k++) {
                Vec3 rel = hit.subtract(choicePoint(k));
                if (Math.abs(rel.dot(panel.u())) <= half && Math.abs(rel.dot(panel.v())) <= half) {
                    hoveredChoice = k;
                    aim = choicePoint(k);
                    return;
                }
            }
        }
        List<Grid> under = new ArrayList<>(grids.size() + 1);
        if (panel != null) under.add(panel);
        under.addAll(grids);
        for (Grid grid : under) {
            Vec3 hit = Picking.cursorOnPlane(grid.at().center(), grid.normal());
            if (hit == null) continue;
            int slot = grid.pick(hit);
            if (slot >= 0) {
                hovered = slot;
                aim = grid.cell(slot);
                return;
            }
        }
    }

    @Override
    public void clearCursor() {
        super.clearCursor();
        hovered = -1;
        hoveredChoice = -1;
        hoveredControl = -1;
        hoveredLine = -1;
        hoveredSlotControl = -1;
    }

    @Override
    public Vec3 handTarget(Vec3 aim, Vec3 shoulder, boolean carrying, double tap) {
        Surface at = hoveredSurface();
        if (!at.lying()) {
            return aim.add(at.normal().scale(0.1 - 0.05 * tap)).add(0, -0.04, 0);
        }
        Vec3 reach = new Vec3(aim.x - shoulder.x, 0, aim.z - shoulder.z).normalize();
        double hover = carrying ? 0.075 : 0.045;
        return aim.add(0, HAND_Y + hover - 0.06 * tap, 0).subtract(reach.scale(ITEM_REACH));
    }

    @Override
    public Vec3 heldItemPoint(Vec3 hand, Vec3 shoulder) {
        Surface at = hoveredSurface();
        if (!at.lying()) {
            return hand.subtract(at.normal().scale(0.06)).add(0, 0.03, 0);
        }
        Vec3 reach = new Vec3(hand.x - shoulder.x, 0, hand.z - shoulder.z).normalize();
        return hand.add(reach.scale(ITEM_REACH)).add(0, -HAND_Y + 0.012, 0);
    }

    /** Once a frame: the camera draws back while an upgrade's slots are open, and comes in again after. */
    @Override
    public void beforeFrame() {
        panelView = Anim.approach(panelView, panel != null ? 1 : 0, 5, Math.min(0.1, Scene.frameDt));
    }

    /** The surface of the slot under the cursor (the main one when none is). */
    private Surface hoveredSurface() {
        if ((hoveredChoice >= 0 || hoveredControl >= 0 || hoveredLine >= 0 || hoveredSlotControl >= 0) && panel != null) return panel.at();
        Grid grid = hovered >= 0 ? gridFor(hovered) : null;
        return grid != null ? grid.at() : surfaces.get(0);
    }

    // ---- actions -------------------------------------------------------------------------------------

    /** Left click takes or puts down the whole stack, right click half or one, shift-click sends it to the other side. */
    @Override
    protected void act(int button, boolean shift) {
        if (hoveredSlotControl >= 0 && hoveredSlotControl < slotControls.size() && rack != null && panelFor >= 0) {
            rack.press(panelFor, slotControls.get(hoveredSlotControl).control().id());
            sound(SoundEvents.UI_BUTTON_CLICK.value(), 0.35f, 1.2f);
            return;
        }
        if (hoveredLine >= 0 && hoveredLine < lines.size() && rack != null && panelFor >= 0) {
            rack.pressLine(panelFor, lines.get(hoveredLine).id());
            sound(SoundEvents.UI_BUTTON_CLICK.value(), 0.3f, 1.3f);
            return;
        }
        if (hoveredControl >= 0 && hoveredControl < controls.size() && rack != null && panelFor >= 0) {
            rack.press(panelFor, controls.get(hoveredControl).id());
            sound(SoundEvents.UI_BUTTON_CLICK.value(), 0.35f, 1.1f);
            return;
        }
        if (hoveredChoice >= 0 && rack != null && panelFor >= 0) {
            // the shape to cut, as a stonecutter's button
            rack.choose(panelFor, hoveredChoice);
            sound(SoundEvents.UI_STONECUTTER_SELECT_RECIPE, 0.6f, 1f);
            return;
        }
        if (hovered < 0) {
            return;
        }
        boolean carrying = !carried().isEmpty();
        int upgrade = upgradeAt(hovered);
        if (upgrade >= 0 && rack != null) {
            if (carrying && !mayPlace(hovered, carried())) {
                notice(Component.translatable("cinematicchests.rack.not_upgrade"));
                deny();
                return;
            }
            if (!carrying && button == 0 && !shift && !slot(hovered).isEmpty()) {
                // opens its own slots over the block, as its tab does in the window; again closes them
                if (rack.upgradeSlots(upgrade).isEmpty()) {
                    notice(Component.translatable(rack.hasControls(upgrade) ? "cinematicchests.rack.no_panel" : "cinematicchests.rack.nothing"));
                    deny();
                } else {
                    boolean opening = rack.open() != upgrade;
                    rack.setOpen(opening ? upgrade : -1);
                    sound(opening ? SoundEvents.BUNDLE_DROP_CONTENTS : SoundEvents.BUNDLE_INSERT, 0.4f, opening ? 1.1f : 0.9f);
                }
                return;
            }
            if (!carrying && button == 1 && !shift && !slot(hovered).isEmpty()) {
                // right-click switches it on or off, as the switch beside it in the window
                if (rack.canSwitch(upgrade)) {
                    boolean on = !rack.enabled(upgrade);
                    rack.setEnabled(upgrade, on);
                    sound(SoundEvents.LEVER_CLICK, 0.4f, on ? 1.0f : 0.8f);
                } else {
                    notice(Component.translatable("cinematicchests.rack.always_on"));
                    deny();
                }
                return;
            }
        }
        if (panel != null && panel.cells().containsKey(hovered) && isFilterSlot(hovered)) {
            // a filter slot takes a pattern of the item in hand (the item stays in hand); an empty hand clears it
            ItemStack pattern = carrying ? carried().copyWithCount(1) : ItemStack.EMPTY;
            if (!carrying && slot(hovered).isEmpty()) {
                return;
            }
            M menu = menu();
            try {
                com.cinematicchests.compat.SophisticatedFilters.set(menu, hovered, pattern);
            } catch (LinkageError e) {
                GuardedRack.fail(e);
                return;
            }
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                    new com.cinematicchests.SetFilterSlotPayload(menu.containerId, hovered, pattern));
            sound(SoundEvents.ITEM_FRAME_ADD_ITEM, 0.35f, carrying ? 1.2f : 0.9f);
            return;
        }
        if (!carrying && slot(hovered).isEmpty()) {
            return;
        }
        slotClick(hovered, button, shift);
        sound(carrying && !shift ? SoundEvents.BUNDLE_INSERT : SoundEvents.BUNDLE_REMOVE_ONE, 0.35f, 1.05f + (float) Math.random() * 0.15f);
    }

    /** The drop key over a slot throws one item from it (with Ctrl, the stack), as over a slot in the window. */
    @Override
    public boolean keyPressed(int key) {
        Minecraft mc = Minecraft.getInstance();
        M menu = menu();
        if (hovered < 0 || menu == null || mc.gameMode == null || mc.player == null || key != mc.options.keyDrop.getKey().getValue()
                || !carried().isEmpty() || slot(hovered).isEmpty()) {
            return false;
        }
        mc.gameMode.handleInventoryMouseClick(menu.containerId, hovered, Screen.hasControlDown() ? 1 : 0, ClickType.THROW, mc.player);
        return true;
    }

    /** A line over the hotbar, as the game shows short notices. */
    private static void notice(Component text) {
        Minecraft.getInstance().gui.setOverlayMessage(text, false);
    }

    /** The mod's reason when it refused a change to the upgrades (a second upgrade of a kind, one another needs...). */
    @Override
    public void tick() {
        Component error = rack == null || menu() == null ? null : rack.error().orElse(null);
        if (error != null && !error.equals(lastRackError)) {
            notice(error.copy().withStyle(net.minecraft.ChatFormatting.RED));
        }
        lastRackError = error;
    }

    private void sound(SoundEvent sound, float volume, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && aim != null) {
            mc.level.playLocalSound(aim.x, aim.y, aim.z, sound, SoundSource.PLAYERS, volume, pitch, false);
        }
    }

    @Override
    public void onEnd() {
        layout();
        Map<Integer, ItemStack> left = new LinkedHashMap<>();
        if (menu() != null) {
            for (int index : allSlots()) {
                left.put(index, slot(index).copy());
            }
        }
        leaving = left;
        super.onEnd();
    }

    @Override
    protected Vec3 restPoint(int slot) {
        Grid grid = gridFor(slot);
        return grid == null ? work : grid.cell(slot);
    }

    // ---- drawing -------------------------------------------------------------------------------------

    @Override
    public void renderScene(PoseStack pose, MultiBufferSource buffers, Camera camera, int light) {
        if (outro()) {
            // the lid closes over them: they sink back in and are gone
            float stay = (float) (1 - Anim.smoother(Anim.window(Scene.time / Scene.OUTRO_LENGTH, 0.0, 0.45)));
            if (stay > 0.01f) {
                drawItems(pose, buffers, camera, leaving, stay, -1, light);
            }
            return;
        }
        if (menu() == null) {
            return;
        }
        layout();
        updatePanel();
        if (shownAt < 0) {
            shownAt = Scene.clock();
        }
        float appear = (float) Anim.smoother(Anim.clamp01((Scene.clock() - shownAt) / 0.35));
        Map<Integer, ItemStack> items = new LinkedHashMap<>();
        for (int index : allSlots()) {
            items.put(index, slot(index));
        }
        drawItems(pose, buffers, camera, items, appear, hovered, light);
        if (panel != null && !choices.isEmpty()) {
            Vec3 cam = camera.getPosition();
            for (int k = 0; k < choices.size(); k++) {
                ItemStack stack = choices.get(k);
                boolean block = ItemPlacer.isBlock(stack);
                float size = (float) (choicePitch() * (block ? 0.6 : 0.85) * (k == hoveredChoice ? 1.15 : 1));
                ItemPlacer.drawOriented(pose, buffers, cam, stack, choicePoint(k).add(panel.normal().scale(block ? size / 2 : 0.006)),
                        ItemPlacer.lying(ItemPlacer.awayYaw(side), 90), size, light);
            }
        }
    }

    /** The contents in their slots; {@code k} 0..1 how far they have come out (the scene opening or closing). */
    private void drawItems(PoseStack pose, MultiBufferSource buffers, Camera camera, Map<Integer, ItemStack> items, float k, int hover,
            int light) {
        Vec3 cam = camera.getPosition();
        Vec3 inside = base.add(0, 0.5, 0);
        for (Map.Entry<Integer, ItemStack> entry : items.entrySet()) {
            int index = entry.getKey();
            ItemStack stack = entry.getValue();
            Grid grid = gridFor(index);
            if (stack.isEmpty() || grid == null) {
                continue;
            }
            Vec3 cell = grid.cell(index);
            Vec3 at = switch (style) {
                // a little over the slot squares, so flat items never sink into them
                case BOX, TOP -> cell.add(0, 0.004 - 0.12 * (1 - k), 0);
                case FACE -> cell.subtract(grid.normal().scale(0.06 * (1 - k)));
                case FLOAT -> Anim.lerp(inside, cell, k);
            };
            float grow = k * (index == hover ? 1.15f : 1f);
            double side2 = half(grid) * 2;
            boolean block = ItemPlacer.isBlock(stack);
            if (grid.at().lying()) {
                float scale = (float) (side2 * 1.05 / ItemPlacer.FLAT_SIZE) * grow;
                ItemPlacer.render(pose, buffers, cam, stack, at, side, 977L + index * 31L, scale, FLAT_TILT, light);
            } else {
                float size = (float) (side2 * (block ? 0.7 : 0.95)) * grow;
                ItemPlacer.drawOriented(pose, buffers, cam, stack, at.add(grid.normal().scale(block ? size / 2 : 0.006)),
                        ItemPlacer.lying(ItemPlacer.awayYaw(side), 90), size, light);
            }
        }
    }

    /**
     * Stack sizes, drawn on the screen like the numbers in the window's slots: at the bottom right of each item, white with a
     * shadow, sized to the slot as it appears. Drawn in the world they were hidden behind the items' own blocks.
     */
    @Override
    public void renderScreen(GuiGraphics graphics, Font font, float alpha) {
        if (outro() || menu() == null || shownAt < 0 || Scene.clock() - shownAt < 0.35) {
            return;
        }
        int color = (int) Math.round(Anim.clamp01(alpha) * 255) << 24 | 0xFFFFFF;
        for (int j = 0; panel != null && j < shown.size(); j++) {
            Shown line = shown.get(j);
            Vec3 left = linePoint(j).add(panel.normal().scale(0.01));
            double[] at = Picking.toGui(left);
            double[] below = Picking.toGui(left.subtract(panel.v().scale(LINE_H)));
            if (at == null || below == null) continue;
            float scale = (float) Math.max(0.4, Math.min(1.2, Math.hypot(below[0] - at[0], below[1] - at[1]) / 10.0));
            boolean clickable = !line.header() && lines.get(line.line()).clickable();
            int rgb = line.header() ? 0xB8A27A : !clickable ? 0x9A9A9A : line.line() == hoveredLine ? 0xFFF080 : 0xFFFFFF;
            graphics.pose().pushPose();
            graphics.pose().translate(at[0], at[1], 200);
            graphics.pose().scale(scale, scale, 1);
            graphics.drawString(font, line.text(), 0, -font.lineHeight / 2, (color & 0xFF000000) | rgb, true);
            graphics.pose().popPose();
        }
        for (int k = 0; panel != null && k < slotControls.size(); k++) {
            Vec3 point = slotControlPoint(slotControls.get(k));
            if (point == null) continue;
            UpgradeRack.Control control = slotControls.get(k).control();
            double[] at = Picking.toGui(point.add(panel.normal().scale(0.01)));
            double[] edge = Picking.toGui(point.add(panel.u().scale(panel.cellU() * 0.4)));
            if (at == null || edge == null) continue;
            float scale = (float) Math.max(0.5, Math.min(1.4, (edge[0] - at[0]) * 2 / 14.0));
            graphics.pose().pushPose();
            graphics.pose().translate(at[0], at[1], 200);
            graphics.pose().scale(scale, scale, 1);
            int glyphColor = control.lit() ? (color & 0xFF000000) | 0xA8F070 : color;
            graphics.drawString(font, control.glyph(), -font.width(control.glyph()) / 2, -font.lineHeight / 2 + 1, glyphColor, true);
            graphics.pose().popPose();
        }
        for (int k = 0; panel != null && k < controls.size(); k++) {
            UpgradeRack.Control control = controls.get(k);
            if (control.group() == null || (k > 0 && control.group().equals(controls.get(k - 1).group()))) continue;
            Vec3 start = controlPoint(k).subtract(panel.u().scale(controlPitch() * 0.6)).add(panel.normal().scale(0.01));
            double[] at = Picking.toGui(start);
            double[] below = Picking.toGui(start.subtract(panel.v().scale(LINE_H)));
            if (at == null || below == null) continue;
            float scale = (float) Math.max(0.4, Math.min(1.2, Math.hypot(below[0] - at[0], below[1] - at[1]) / 10.0));
            graphics.pose().pushPose();
            graphics.pose().translate(at[0], at[1], 200);
            graphics.pose().scale(scale, scale, 1);
            graphics.drawString(font, control.group(), -font.width(control.group()), -font.lineHeight / 2, (color & 0xFF000000) | 0xB8A27A, true);
            graphics.pose().popPose();
        }
        for (int k = 0; panel != null && k < controls.size(); k++) {
            UpgradeRack.Control control = controls.get(k);
            Vec3 middle = controlPoint(k).add(panel.normal().scale(0.01));
            double[] at = Picking.toGui(middle);
            double[] edge = Picking.toGui(middle.add(panel.u().scale(controlPitch() * 0.45)));
            if (at == null || edge == null) continue;
            float scale = (float) Math.max(0.5, Math.min(1.4, (edge[0] - at[0]) * 2 / 14.0));
            graphics.pose().pushPose();
            graphics.pose().translate(at[0], at[1], 200);
            graphics.pose().scale(scale, scale, 1);
            int glyphColor = control.lit() ? (color & 0xFF000000) | 0xA8F070 : color;
            graphics.drawString(font, control.glyph(), -font.width(control.glyph()) / 2, -font.lineHeight / 2 + 1, glyphColor, true);
            graphics.pose().popPose();
        }
        for (int index : ChestConfig.showCounts() ? allSlots() : List.<Integer>of()) {
            ItemStack stack = slot(index);
            Grid grid = gridFor(index);
            if (stack.getCount() <= 1 || grid == null) {
                continue;
            }
            double half = half(grid);
            Vec3 cell = grid.cell(index).add(grid.normal().scale(grid.at().lying() ? 0.02 : 0.01));
            double[] corner = Picking.toGui(cell.add(grid.u().scale(half)).subtract(grid.v().scale(half)));
            double[] left = Picking.toGui(cell.subtract(grid.u().scale(half)).subtract(grid.v().scale(half)));
            if (corner == null || left == null) {
                continue;
            }
            // a window slot is 16 px across with a 9 px number: keep that proportion, within reason
            float scale = (float) Math.max(0.5, Math.min(1.0, (corner[0] - left[0]) / 16.0));
            String text = String.valueOf(stack.getCount());
            graphics.pose().pushPose();
            graphics.pose().translate(corner[0], corner[1], 200);
            graphics.pose().scale(scale, scale, 1);
            graphics.drawString(font, text, -font.width(text), -font.lineHeight + 1, color, true);
            graphics.pose().popPose();
        }
    }

    /**
     * Working: every slot drawn as a square like the window's, the one under the cursor filled warm, or red when the carried
     * stack won't go there.
     */
    @Override
    public void renderWorld(PoseStack pose, MultiBufferSource buffers, Vec3 camera, int light) {
        if (menu() == null) {
            return;
        }
        layout();
        boolean showGrid = ChestConfig.showGrid();
        ItemStack carried = carried();
        VertexConsumer quads = buffers.getBuffer(RenderType.debugQuads());
        for (int index : allSlots()) {
            Grid g = gridFor(index);
            boolean hover = index == hovered;
            if (g == null || !hover && !showGrid) {
                continue;
            }
            ItemStack there = slot(index);
            boolean ok = carried.isEmpty() || (there.isEmpty() ? mayPlace(index, carried) : ItemStack.isSameItemSameComponents(carried, there));
            int upgrade = upgradeAt(index);
            // a switched-off upgrade sits in a red slot
            boolean off = upgrade >= 0 && rack != null && !there.isEmpty() && !rack.enabled(upgrade);
            Vec3 at = g.cell(index).add(g.normal().scale(0.001)).subtract(camera);
            // a square the size of the item, with a small gap to its neighbours as between the window's slots; a chest's rows
            // can be deeper than its slots are wide, and the rest of the row's depth stays bare floor
            double half = half(g) * 0.9;
            Vec3 u = g.u().scale(half), v = g.v().scale(half);
            int fill, frame;
            if (style == Style.BOX) {
                // A chest is drawn after the scene: whatever a see-through square lets through is what lay behind the chest
                // (the ground), not its floor. So inside chests the squares are solid, in the colours of the chest's wood.
                float pulse = (float) (0.5 + 0.5 * Math.sin(Scene.clock() * 5));
                fill = !hover ? (off ? 0xFF4A1E1A : 0xFF2E241A) : ok ? lerpColor(0xFF6B5432, 0xFF7F6640, pulse) : 0xFF6A2620;
                frame = hover ? 0xFFF2E6C8 : 0xFF9C8460;
            } else if (hover) {
                int a = (int) (110 + 30 * Math.sin(Scene.clock() * 5));
                fill = ok ? a << 24 | 0xFFE6A0 : a << 24 | 0xFF4030;
                frame = 0xE0FFFFFF;
            } else {
                fill = off ? 0x70A02020 : 0x50000000;
                frame = 0x70FFFFFF;
            }
            quad(quads, pose, at, u, v, fill);
            double t = half * 0.16;
            Vec3 lift = g.normal().scale(0.0005);
            Vec3 un = g.u().scale(t), vn = g.v().scale(t);
            // the frame: four thin strips along the edges
            quad(quads, pose, at.add(v).subtract(vn.scale(0.5)).add(lift), u, vn.scale(0.5), frame);
            quad(quads, pose, at.subtract(v).add(vn.scale(0.5)).add(lift), u, vn.scale(0.5), frame);
            quad(quads, pose, at.add(u).subtract(un.scale(0.5)).add(lift), un.scale(0.5), v, frame);
            quad(quads, pose, at.subtract(u).add(un.scale(0.5)).add(lift), un.scale(0.5), v, frame);
        }
        drawPanelExtras(quads, pose, camera);
    }

    /** The open upgrade's gauges (a furnace's arrow and flame) and its choices' frames, the chosen one lit. */
    private void drawPanelExtras(VertexConsumer quads, PoseStack pose, Vec3 camera) {
        if (panel == null || rack == null || panelFor < 0) {
            return;
        }
        Vec3 lift = panel.normal().scale(0.0015);
        float[] gauges = rack.gauges(panelFor);
        if (gauges != null) {
            double cell = panel.cellU();
            // the arrow from the input towards the result, filling as it cooks; the flame a column before it
            int[] gauge = rack.gaugeAt(panelFor);
            double gc = gauge == null ? 1 : gauge[0], gr = gauge == null ? 1 : gauge[1];
            Vec3 arrow = panel.point(gc, gr).add(lift).subtract(camera);
            Vec3 du = panel.u().scale(cell * 0.4), dv = panel.v().scale(cell * 0.12);
            quad(quads, pose, arrow, du, dv, 0xFF3A3028);
            if (gauges[0] > 0) {
                Vec3 filled = arrow.subtract(du).add(du.scale(gauges[0])).add(lift);
                quad(quads, pose, filled, du.scale(gauges[0]), dv, 0xFFF2F2F2);
            }
            // the flame between input and fuel, shrinking as the fuel burns away
            Vec3 flame = panel.point(gc - 1, gr).add(lift).subtract(camera);
            Vec3 fu = panel.u().scale(cell * 0.14), fv = panel.v().scale(cell * 0.3);
            quad(quads, pose, flame, fu, fv, 0xFF3A3028);
            if (gauges[1] > 0) {
                Vec3 lit = flame.subtract(fv).add(fv.scale(gauges[1])).add(lift);
                quad(quads, pose, lit, fu, fv.scale(gauges[1]), 0xFFFF9A20);
            }
        }
        UpgradeRack.Meter meter = rack.meter(panelFor);
        if (meter != null) {
            Vec3 middle = meterPoint().add(lift).subtract(camera);
            double height = meterHeight();
            Vec3 mu = panel.u().scale(panel.cellU() * 0.18), mv = panel.v().scale(height / 2);
            quad(quads, pose, middle, mu, mv, 0xFF2A2420);
            float fill = Math.max(0f, Math.min(1f, meter.fill()));
            if (fill > 0) {
                quad(quads, pose, middle.subtract(mv).add(mv.scale(fill)).add(lift), mu, mv.scale(fill), meter.argb());
            }
        }
        double ch = controlPitch() * 0.44;
        for (int k = 0; k < controls.size(); k++) {
            UpgradeRack.Control control = controls.get(k);
            Vec3 at = controlPoint(k).add(panel.normal().scale(0.001)).subtract(camera);
            int fill = k == hoveredControl ? 0xFF8A7048 : control.lit() ? 0xFF4F6B2E : 0xFF2E241A;
            quad(quads, pose, at, panel.u().scale(ch), panel.v().scale(ch), fill);
            Vec3 edge = panel.normal().scale(0.0005);
            quad(quads, pose, at.add(panel.v().scale(ch * 0.92)).add(edge), panel.u().scale(ch), panel.v().scale(ch * 0.08), 0xFF9C8460);
            quad(quads, pose, at.subtract(panel.v().scale(ch * 0.92)).add(edge), panel.u().scale(ch), panel.v().scale(ch * 0.08), 0xFF9C8460);
        }
        double sh = panel.cellU() * 0.4;
        for (int k = 0; k < slotControls.size(); k++) {
            Vec3 point = slotControlPoint(slotControls.get(k));
            if (point == null) continue;
            Vec3 at = point.add(panel.normal().scale(0.001)).subtract(camera);
            boolean lit = slotControls.get(k).control().lit();
            quad(quads, pose, at, panel.u().scale(sh), panel.v().scale(sh),
                    k == hoveredSlotControl ? 0xFF8A7048 : lit ? 0xFF4F6B2E : 0xFF2E241A);
        }
        int active = rack.activeSlot(panelFor);
        if (active >= 0 && panel.cells().containsKey(active)) {
            double h = Math.min(panel.cellU(), panel.cellV()) * 0.5;
            Vec3 at = panel.cell(active).add(panel.normal().scale(0.0012)).subtract(camera);
            Vec3 u = panel.u().scale(h), v = panel.v().scale(h);
            Vec3 un = panel.u().scale(h * 0.12), vn = panel.v().scale(h * 0.12);
            int green = 0xFF5FD03A;
            quad(quads, pose, at.add(v).subtract(vn), u, vn, green);
            quad(quads, pose, at.subtract(v).add(vn), u, vn, green);
            quad(quads, pose, at.add(u).subtract(un), un, v, green);
            quad(quads, pose, at.subtract(u).add(un), un, v, green);
        }
        if (!choices.isEmpty()) {
            int chosen = rack.chosen(panelFor);
            double half = choicePitch() * 0.46;
            for (int k = 0; k < choices.size(); k++) {
                if (k != chosen && k != hoveredChoice) continue;
                Vec3 at = choicePoint(k).add(panel.normal().scale(0.001)).subtract(camera);
                quad(quads, pose, at, panel.u().scale(half), panel.v().scale(half), k == chosen ? 0xFF7F6640 : 0x80FFE6A0);
            }
        }
    }

    /** A tank's or battery's level stands beside the upgrade's slots, as tall as them. */
    private Vec3 meterPoint() {
        return panel.point(panel.cols() - 0.5 + 0.55, (panel.rows() - 1) / 2.0);
    }

    private double meterHeight() {
        return Math.max(panel.rows() * panel.cellV() * 0.9, 0.14);
    }

    /** Whether the cursor is on the meter, for its tooltip. */
    private boolean overMeter() {
        if (panel == null || rack == null || panelFor < 0 || rack.meter(panelFor) == null) return false;
        Vec3 hit = Picking.cursorOnPlane(panel.at().center(), panel.normal());
        if (hit == null) return false;
        Vec3 rel = hit.subtract(meterPoint());
        return Math.abs(rel.dot(panel.u())) <= panel.cellU() * 0.25 && Math.abs(rel.dot(panel.v())) <= meterHeight() / 2;
    }

    private static int lerpColor(int a, int b, float t) {
        int out = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            int ca = a >>> shift & 0xFF, cb = b >>> shift & 0xFF;
            out |= Math.round(ca + (cb - ca) * t) << shift;
        }
        return out;
    }

    /** A flat quad round {@code c} (camera-relative) spanning ±u, ±v, in one ARGB colour. */
    private static void quad(VertexConsumer vc, PoseStack pose, Vec3 c, Vec3 u, Vec3 v, int argb) {
        int a = argb >>> 24, r = argb >> 16 & 0xFF, g = argb >> 8 & 0xFF, b = argb & 0xFF;
        Vec3[] corners = {c.subtract(u).subtract(v), c.add(u).subtract(v), c.add(u).add(v), c.subtract(u).add(v)};
        for (Vec3 p : corners) {
            vc.addVertex(pose.last(), (float) p.x, (float) p.y, (float) p.z).setColor(r, g, b, a);
        }
    }

    // ---- words ---------------------------------------------------------------------------------------

    @Nullable
    @Override
    public List<Component> tooltip(ItemStack carried) {
        if (hoveredControl >= 0 && hoveredControl < controls.size()) {
            return List.of(controls.get(hoveredControl).name());
        }
        if (hoveredSlotControl >= 0 && hoveredSlotControl < slotControls.size()) {
            return List.of(slotControls.get(hoveredSlotControl).control().name(),
                    Component.translatable("cinematicchests.opt.click_condition").withStyle(net.minecraft.ChatFormatting.GRAY));
        }
        if (hoveredLine >= 0 && hoveredLine < lines.size() && lines.get(hoveredLine).hint() != null) {
            return List.of(lines.get(hoveredLine).hint().copy().withStyle(net.minecraft.ChatFormatting.GRAY));
        }
        if (hovered < 0 && hoveredChoice < 0 && overMeter()) {
            return List.of(rack.meter(panelFor).amount());
        }
        if (hoveredChoice >= 0 && hoveredChoice < choices.size()) {
            List<Component> lines = new ArrayList<>(Screen.getTooltipFromItem(Minecraft.getInstance(), choices.get(hoveredChoice)));
            lines.add(Component.translatable("cinematicchests.panel.choose").withStyle(net.minecraft.ChatFormatting.GRAY));
            return lines;
        }
        if (hovered < 0) {
            return null;
        }
        if (panel != null && panel.cells().containsKey(hovered) && slot(hovered).isEmpty() && rack != null) {
            Component label = rack.label(panelFor, hovered);
            return label == null ? null : List.of(label.copy().withStyle(net.minecraft.ChatFormatting.GRAY));
        }
        if (!carried.isEmpty()) {
            return null;
        }
        ItemStack there = slot(hovered);
        int upgrade = upgradeAt(hovered);
        if (upgrade >= 0 && rack != null) {
            if (there.isEmpty()) {
                return List.of(Component.translatable("cinematicchests.rack.empty").withStyle(net.minecraft.ChatFormatting.GRAY));
            }
            List<Component> lines = new ArrayList<>(Screen.getTooltipFromItem(Minecraft.getInstance(), there));
            if (!rack.enabled(upgrade)) {
                lines.add(Component.translatable("cinematicchests.rack.off").withStyle(net.minecraft.ChatFormatting.RED));
            }
            lines.add(Component.translatable(rack.canSwitch(upgrade) ? "cinematicchests.rack.toggle" : "cinematicchests.rack.always_on")
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
            lines.add(Component.translatable("cinematicchests.rack.settings").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
            return lines;
        }
        return there.isEmpty() ? null : Screen.getTooltipFromItem(Minecraft.getInstance(), there);
    }

    @Override
    public Component hint() {
        return Component.translatable("cinematicchests.hint");
    }
}
