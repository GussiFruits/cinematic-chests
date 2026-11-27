package com.cinematicchests;

import com.cinematicchests.mixin.AddonStationsAccessor;
import dev.dyslexic.cinematic.api.AddonStations;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Which blocks get a storage scene, and how. Vanilla's chests, ender chest, barrel and shulker boxes are always in; blocks from
 * other mods come in by detection (they extend those vanilla classes) or by name in the startup config.
 * <p>
 * Built once in common setup, identically on server and client (the same mods and the same startup config give the same list):
 * Cinematic Interactions needs both sides to agree on which blocks are stations.
 */
public final class StorageBlocks {
    private StorageBlocks() {}

    /**
     * @param vanilla whether the server opens it with this add-on's own copy of vanilla's rules ({@link StorageMenus}); a modded
     *                block is opened by running its own right-click, so its mod's rules, extra data and statistics all apply
     */
    public record Entry(Block block, Storage shape, boolean vanilla, ResourceLocation sceneId) {
        /** The menu class the scene expects. A modded block may open any menu, so it is accepted whatever it is. */
        public Class<? extends AbstractContainerMenu> menuClass() {
            if (!vanilla) return AbstractContainerMenu.class;
            return shape == Storage.SHULKER_BOX ? ShulkerBoxMenu.class : ChestMenu.class;
        }
    }

    private static final Map<Block, Entry> ENTRIES = new LinkedHashMap<>();

    @Nullable
    public static Entry get(BlockState state) {
        return ENTRIES.get(state.getBlock());
    }

    public static Collection<Entry> entries() {
        return ENTRIES.values();
    }

    /** Whether this scene id is one of ours. */
    public static boolean owns(@Nullable ResourceLocation sceneId) {
        return sceneId != null && CinematicChests.MODID.equals(sceneId.getNamespace());
    }

    // ---- building the list ---------------------------------------------------------------------------

    private static void vanilla(Block block, Storage shape) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        ENTRIES.put(block, new Entry(block, shape, true, ResourceLocation.fromNamespaceAndPath(CinematicChests.MODID, id.getPath())));
    }

    private static void modded(Block block, Storage shape) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        ENTRIES.put(block, new Entry(block, shape, false,
                ResourceLocation.fromNamespaceAndPath(CinematicChests.MODID, id.getNamespace() + "/" + id.getPath())));
    }

    private static void build() {
        ENTRIES.clear();
        vanilla(Blocks.CHEST, Storage.CHEST);
        vanilla(Blocks.TRAPPED_CHEST, Storage.CHEST);
        vanilla(Blocks.ENDER_CHEST, Storage.ENDER_CHEST);
        vanilla(Blocks.BARREL, Storage.BARREL);
        vanilla(Blocks.SHULKER_BOX, Storage.SHULKER_BOX);
        for (DyeColor color : DyeColor.values()) {
            vanilla(ShulkerBoxBlock.getBlockByColor(color), Storage.SHULKER_BOX);
        }

        List<Pattern> excluded = new ArrayList<>();
        for (String line : StartupConfig.EXCLUDED.get()) {
            Pattern pattern = idPattern(line);
            if (pattern != null) excluded.add(pattern);
            else CinematicChests.LOGGER.warn("[Cinematic Chests] excluded: '{}' is not a block id", line);
        }

        if (StartupConfig.AUTO_DETECT.get()) {
            int found = 0;
            for (Block block : BuiltInRegistries.BLOCK) {
                ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
                if ("minecraft".equals(id.getNamespace()) || matches(excluded, id) || ENTRIES.containsKey(block)) continue;
                Storage shape = block instanceof ShulkerBoxBlock ? Storage.SHULKER_BOX
                        : block instanceof EnderChestBlock ? Storage.ENDER_CHEST
                        : block instanceof ChestBlock ? Storage.CHEST
                        : block instanceof BarrelBlock ? Storage.BARREL
                        : null;
                if (shape != null) {
                    modded(block, shape);
                    found++;
                }
            }
            if (found > 0) {
                CinematicChests.LOGGER.info("[Cinematic Chests] detected {} modded storage blocks: {}", found, describe());
            }
        }

        int listed = 0;
        // the built-in entries for mods known to work (only when the mod is installed), then the player's own, which win
        List<String> lines = new ArrayList<>();
        if (StartupConfig.BUILT_IN.get()) {
            for (Map.Entry<String, List<String>> mod : BUILT_IN.entrySet()) {
                if (net.neoforged.fml.ModList.get().isLoaded(mod.getKey())) lines.addAll(mod.getValue());
            }
        }
        lines.addAll(StartupConfig.EXTRA.get());
        for (String line : lines) {
            int eq = line.indexOf('=');
            Pattern pattern = idPattern(eq < 0 ? line : line.substring(0, eq));
            Storage shape = eq < 0 ? Storage.FRONT : Storage.parse(line.substring(eq + 1));
            if (pattern == null || shape == null) {
                CinematicChests.LOGGER.warn("[Cinematic Chests] extraContainers: can't read '{}' (expected modid:block=chest|barrel|shulker|front)", line);
                continue;
            }
            int matched = 0;
            for (Block block : BuiltInRegistries.BLOCK) {
                ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
                if (!pattern.matcher(id.toString()).matches()) continue;
                matched++;
                if ("minecraft".equals(id.getNamespace())) {
                    CinematicChests.LOGGER.warn("[Cinematic Chests] extraContainers: '{}' is a vanilla block; only modded blocks can be added", id);
                } else if (!matches(excluded, id)) {
                    // a listed shape overrides a detected one
                    modded(block, shape);
                    listed++;
                }
            }
            if (matched == 0) {
                // a line for a mod that isn't installed is normal (one config for several modpacks): only say so when debugging
                String namespace = line.contains(":") ? line.substring(0, line.indexOf(':')).trim() : "";
                if (net.neoforged.fml.ModList.get().isLoaded(namespace)) {
                    CinematicChests.LOGGER.warn("[Cinematic Chests] extraContainers: no block matches '{}'", line);
                } else {
                    CinematicChests.LOGGER.debug("[Cinematic Chests] extraContainers: '{}' skipped, {} is not installed", line, namespace);
                }
            }
        }
        if (listed > 0) {
            CinematicChests.LOGGER.info("[Cinematic Chests] {} blocks from built-in support and extraContainers; all modded storage: {}", listed, describe());
        }
        ENTRIES.values().removeIf(entry -> entry.vanilla() && matches(excluded, BuiltInRegistries.BLOCK.getKey(entry.block())));
    }

    private static List<String> describe() {
        return ENTRIES.values().stream().filter(e -> !e.vanilla())
                .map(e -> BuiltInRegistries.BLOCK.getKey(e.block()) + "=" + e.shape().name().toLowerCase(java.util.Locale.ROOT)).toList();
    }

    /**
     * A block id from the config, where {@code *} stands for any run of characters ({@code sophisticatedstorage:*barrel},
     * {@code somemod:*}); null when it can't be a block id.
     */
    @Nullable
    static Pattern idPattern(String text) {
        String trimmed = text.trim().toLowerCase(java.util.Locale.ROOT);
        if (trimmed.isEmpty() || !trimmed.matches("[a-z0-9_.\\-]+:[a-z0-9_./\\-*]+")) {
            return null;
        }
        StringBuilder regex = new StringBuilder();
        for (String part : trimmed.split("\\*", -1)) {
            if (!regex.isEmpty()) regex.append(".*");
            regex.append(Pattern.quote(part));
        }
        return Pattern.compile(regex.toString());
    }

    private static boolean matches(List<Pattern> patterns, ResourceLocation id) {
        String text = id.toString();
        for (Pattern pattern : patterns) {
            if (pattern.matcher(text).matches()) return true;
        }
        return false;
    }

    /**
     * Registers every entry as a Cinematic Interactions add-on station. The public {@link AddonStations#register} refuses vanilla
     * blocks, so the definitions go straight into its tables, under the same lock its own methods take. Other add-ons keep
     * the normal checks, and a block another add-on already gave a scene is left to it.
     */
    static void registerStations() {
        build();
        int added = 0;
        synchronized (AddonStations.class) {
            Map<ResourceLocation, AddonStations.Definition> byId = AddonStationsAccessor.cinematicchests$byId();
            Map<ResourceLocation, AddonStations.Definition> byBlock = AddonStationsAccessor.cinematicchests$byBlock();
            for (var it = ENTRIES.values().iterator(); it.hasNext(); ) {
                Entry entry = it.next();
                ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(entry.block());
                if (byBlock.containsKey(blockId) || byId.containsKey(entry.sceneId())) {
                    CinematicChests.LOGGER.warn("[Cinematic Chests] {} already has a cinematic scene; leaving it alone", blockId);
                    it.remove();
                    continue;
                }
                Storage shape = entry.shape();
                AddonStations.Definition definition = new AddonStations.Definition(entry.sceneId(), blockId, entry.menuClass(), shape::sides);
                byId.put(entry.sceneId(), definition);
                byBlock.put(blockId, definition);
                added++;
            }
        }
        CinematicChests.LOGGER.info("[Cinematic Chests] registered {} storage stations", added);
    }

    /**
     * Mods whose storage is known to work, added whenever the mod is installed (turned off with builtInSupport): mod id to
     * config lines, in the same form as extraContainers.
     */
    static final Map<String, List<String>> BUILT_IN = Map.of(
            "sophisticatedstorage", List.of("sophisticatedstorage:*chest=chest", "sophisticatedstorage:*barrel=barrel",
                    "sophisticatedstorage:*shulker_box=shulker"));

    /** For the startup config's comments. */
    static final List<String> EXAMPLE_EXTRA = List.of("sophisticatedstorage:*chest=chest", "sophisticatedstorage:*barrel=barrel", "somemod:crate=front");
}
