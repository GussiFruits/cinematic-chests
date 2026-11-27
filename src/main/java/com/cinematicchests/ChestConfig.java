package com.cinematicchests;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client settings: which storage blocks play a scene, and what the scene shows. */
public final class ChestConfig {
    private ChestConfig() {}

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static {
        BUILDER.comment("Which storage blocks open as a cinematic scene. Off: the block keeps its normal window.").push("scenes");
    }

    public static final ModConfigSpec.BooleanValue CHESTS = BUILDER.comment("Chests and trapped chests, single and double")
            .define("chests", true);
    public static final ModConfigSpec.BooleanValue ENDER_CHESTS = BUILDER.define("enderChests", true);
    public static final ModConfigSpec.BooleanValue BARRELS = BUILDER.comment("Barrels facing up or sideways (facing down always uses the window)")
            .define("barrels", true);
    public static final ModConfigSpec.BooleanValue SHULKER_BOXES = BUILDER.comment("Shulker boxes facing up (other directions always use the window)")
            .define("shulkerBoxes", true);
    public static final ModConfigSpec.BooleanValue MODDED = BUILDER
            .comment("Storage blocks from other mods (which ones is set in cinematicchests-startup.toml)")
            .define("moddedContainers", true);

    static {
        BUILDER.pop().push("display");
    }

    public static final ModConfigSpec.BooleanValue SHOW_COUNTS = BUILDER.comment("Show stack sizes next to the items")
            .define("showCounts", true);
    public static final ModConfigSpec.BooleanValue SHOW_GRID = BUILDER.comment("Faintly mark every slot, so empty ones can be found")
            .define("showGrid", true);

    static {
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    public static boolean enabled(StorageBlocks.Entry entry) {
        if (!SPEC.isLoaded()) return true;
        if (!entry.vanilla()) return MODDED.get();
        return switch (entry.shape()) {
            case CHEST -> CHESTS.get();
            case ENDER_CHEST -> ENDER_CHESTS.get();
            case BARREL -> BARRELS.get();
            case SHULKER_BOX -> SHULKER_BOXES.get();
            case FRONT -> true;
        };
    }

    public static boolean showCounts() {
        return !SPEC.isLoaded() || SHOW_COUNTS.get();
    }

    public static boolean showGrid() {
        return !SPEC.isLoaded() || SHOW_GRID.get();
    }
}
