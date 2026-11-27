package com.cinematicchests;

import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Which modded blocks get a storage scene. Read once at startup; it must be the same on the server and on every client, since
 * both sides have to agree on which blocks are stations (the same holds for the mods installed).
 */
public final class StartupConfig {
    private StartupConfig() {}

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static {
        BUILDER.comment("Modded storage blocks. Must match on the server and all clients. Changes apply after a restart.")
                .push("modded");
    }

    public static final ModConfigSpec.BooleanValue AUTO_DETECT = BUILDER
            .comment("Give a scene to modded blocks that extend vanilla's chest, ender chest, barrel or shulker box")
            .define("autoDetect", true);

    public static final ModConfigSpec.BooleanValue BUILT_IN = BUILDER
            .comment("Give a scene to the storage of mods known to work with this add-on, when they are installed:",
                    "Sophisticated Storage's chests, barrels and shulker boxes (all tiers)")
            .define("builtInSupport", true);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> EXTRA = BUILDER
            .comment("More blocks, as \"modid:block=shape\". * matches any characters, so \"somemod:*_chest=chest\" adds a whole family.",
                    "A shape listed here overrides a detected one. Shapes:",
                    "  chest   - chest-shaped with a lid: the items lie inside",
                    "  barrel  - the items lie on its top (facing up) or sit on its front (facing sideways)",
                    "  shulker - a box whose lid lifts: the items sit in the opening",
                    "  front   - anything else: the items sit upright on its front face",
                    "The block must open a container when right-clicked. Example: " + StorageBlocks.EXAMPLE_EXTRA)
            .defineListAllowEmpty("extraContainers", List.of(), () -> "modid:block=front", o -> o instanceof String);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> EXCLUDED = BUILDER
            .comment("Block ids that keep their normal window, even if detected (vanilla ones too, e.g. \"minecraft:barrel\"); * works here too")
            .defineListAllowEmpty("excluded", List.of(), () -> "modid:block", o -> o instanceof String);

    static {
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();
}
