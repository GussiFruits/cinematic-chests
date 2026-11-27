package com.cinematicchests.client;

import com.cinematicchests.StorageBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Some mods' storage blocks have names that are templates filled in from the item (Sophisticated Storage: "%s%s Diamond Barrel",
 * completed with the wood and tier), so the bare block name shows the template. A storage block is named as pick-block would
 * name it instead: by the item it gives.
 */
public final class StorageNames {
    private StorageNames() {
    }

    public static MutableComponent of(Block block, @Nullable BlockPos pos) {
        MutableComponent name = block.getName();
        Minecraft mc = Minecraft.getInstance();
        if (pos == null || mc.level == null || mc.player == null) {
            return name;
        }
        BlockState state = mc.level.getBlockState(pos);
        if (state.getBlock() != block || StorageBlocks.get(state) == null) {
            return name;
        }
        try {
            ItemStack stack = state.getCloneItemStack(new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false), mc.level, pos, mc.player);
            return stack.isEmpty() ? name : stack.getHoverName().copy();
        } catch (RuntimeException e) {
            // a mod's pick-block failing must not break the overlay
            return name;
        }
    }
}
