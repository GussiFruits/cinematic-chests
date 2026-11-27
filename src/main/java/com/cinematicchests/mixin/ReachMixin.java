package com.cinematicchests.mixin;

import com.cinematicchests.client.StorageNames;
import dev.dyslexic.cinematic.client.Reach;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * "In the way: …" names the block in the way by its block name, which for some storage blocks is a template (see
 * {@link StorageNames}). The block's position is noted as it is looked up, then used to name it by the item it gives.
 */
@Mixin(value = Reach.class, remap = false)
public abstract class ReachMixin {
    @Unique
    private static BlockPos cinematicchests$looked;

    @Redirect(method = {"planHere", "planBeside"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private static BlockState cinematicchests$note(Level level, BlockPos pos) {
        cinematicchests$looked = pos;
        return level.getBlockState(pos);
    }

    @Redirect(method = {"planHere", "planBeside"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/Block;getName()Lnet/minecraft/network/chat/MutableComponent;"))
    private static MutableComponent cinematicchests$name(Block block) {
        return StorageNames.of(block, cinematicchests$looked);
    }
}
