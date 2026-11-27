package com.cinematicchests;

import com.cinematicchests.mixin.ShulkerBoxBlockInvoker;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Server side: opening a storage block's menu, under the same rules as a right-click on it. */
public final class StorageMenus {
    private StorageMenus() {}

    private static final Component ENDER_CHEST_TITLE = Component.translatable("container.enderchest");

    /**
     * A modded block: runs its own right-click, as if the player had used its {@code face} with an empty hand, so its mod opens
     * the menu its own way (with whatever extra data its screen needs) and applies its own rules and statistics.
     */
    public static void useBlock(ServerPlayer player, BlockPos pos, Direction face) {
        Level level = player.level();
        BlockState state = level.getBlockState(pos);
        Vec3 at = Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(face.getNormal()).scale(0.5));
        state.useWithoutItem(level, player, new BlockHitResult(at, face, pos, false));
    }

    /** The horizontal face of the block that looks at the player. */
    public static Direction faceToward(ServerPlayer player, BlockPos pos) {
        Vec3 off = player.position().subtract(Vec3.atCenterOf(pos));
        return Direction.getNearest(off.x, 0, off.z);
    }

    /** A vanilla block's menu, or null when vanilla would not open it now (a cat on the chest, no room for a lid). */
    @Nullable
    public static MenuProvider provider(ServerPlayer player, BlockPos pos) {
        Level level = player.level();
        BlockState state = level.getBlockState(pos);
        StorageBlocks.Entry entry = StorageBlocks.get(state);
        if (entry == null || !entry.vanilla()) {
            return null;
        }
        MenuProvider inner;
        switch (entry.shape()) {
            case ENDER_CHEST -> {
                PlayerEnderChestContainer ender = player.getEnderChestInventory();
                BlockPos above = pos.above();
                if (ender == null || !(level.getBlockEntity(pos) instanceof EnderChestBlockEntity chest)
                        || level.getBlockState(above).isRedstoneConductor(level, above)) {
                    return null;
                }
                // makes the lid open for onlookers and ties the menu's reach to this chest
                ender.setActiveChest(chest);
                inner = new net.minecraft.world.SimpleMenuProvider((id, inventory, p) -> ChestMenu.threeRows(id, inventory, ender), ENDER_CHEST_TITLE);
            }
            case SHULKER_BOX -> {
                if (!(level.getBlockEntity(pos) instanceof ShulkerBoxBlockEntity box)
                        || !ShulkerBoxBlockInvoker.cinematicchests$canOpen(state, level, pos, box)) {
                    return null;
                }
                inner = box;
            }
            default -> inner = state.getMenuProvider(level, pos);
        }
        return inner == null ? null : new Counted(inner, stat(state, entry.shape()));
    }

    private static ResourceLocation stat(BlockState state, Storage shape) {
        return switch (shape) {
            case ENDER_CHEST -> Stats.OPEN_ENDERCHEST;
            case BARREL -> Stats.OPEN_BARREL;
            case SHULKER_BOX -> Stats.OPEN_SHULKER_BOX;
            default -> state.is(Blocks.TRAPPED_CHEST) ? Stats.TRIGGER_TRAPPED_CHEST : Stats.OPEN_CHEST;
        };
    }

    /** Opens the real menu; once it is actually open, counts the statistic and angers nearby piglins, as vanilla does. */
    private record Counted(MenuProvider inner, ResourceLocation stat) implements MenuProvider {
        @Override
        public Component getDisplayName() {
            return inner.getDisplayName();
        }

        @Nullable
        @Override
        public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
            AbstractContainerMenu menu = inner.createMenu(id, inventory, player);
            if (menu != null) {
                player.awardStat(stat);
                PiglinAi.angerNearbyPiglins(player, true);
            }
            return menu;
        }

        @Override
        public boolean shouldTriggerClientSideContainerClosingOnOpen() {
            return inner.shouldTriggerClientSideContainerClosingOnOpen();
        }
    }
}
