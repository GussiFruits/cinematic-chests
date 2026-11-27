package com.cinematicchests;

import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * The shape of a storage block's scene: where the player works from and how its slots are laid out. Vanilla blocks and modded
 * ones share these; see {@link StorageBlocks} for which block gets which.
 */
public enum Storage {
    /** A chest-shaped box: the lid opens and the items lie on its floor. Single or double. */
    CHEST,
    /** Chest-shaped, but always single. */
    ENDER_CHEST,
    /** Its open top when it faces up, its open front when it faces sideways. */
    BARREL,
    /** A box whose lid lifts: the items sit in the opening between base and lid. Only when it opens upwards. */
    SHULKER_BOX,
    /** Any other block: the items sit upright on its front face. */
    FRONT;

    private static final List<Direction> ANY_SIDE = List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST);

    /** The sides the player may work from; empty leaves the block its normal window. Read from the block's own properties. */
    public List<Direction> sides(BlockState state) {
        return switch (this) {
            case CHEST, ENDER_CHEST, FRONT -> {
                if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
                    yield List.of(state.getValue(BlockStateProperties.HORIZONTAL_FACING));
                }
                if (state.hasProperty(BlockStateProperties.FACING)) {
                    Direction facing = state.getValue(BlockStateProperties.FACING);
                    yield facing.getAxis().isHorizontal() ? List.of(facing) : ANY_SIDE;
                }
                yield ANY_SIDE;
            }
            case BARREL -> {
                if (!state.hasProperty(BlockStateProperties.FACING)) yield ANY_SIDE;
                Direction facing = state.getValue(BlockStateProperties.FACING);
                yield facing == Direction.UP ? ANY_SIDE : facing == Direction.DOWN ? List.of() : List.of(facing);
            }
            case SHULKER_BOX -> !state.hasProperty(BlockStateProperties.FACING)
                    || state.getValue(BlockStateProperties.FACING) == Direction.UP ? ANY_SIDE : List.of();
        };
    }

    /** A shape named in the config: chest, ender_chest, barrel, shulker (or shulker_box), front (or panel). */
    @Nullable
    public static Storage parse(String name) {
        return switch (name.trim().toLowerCase(Locale.ROOT)) {
            case "chest" -> CHEST;
            case "ender_chest", "enderchest" -> ENDER_CHEST;
            case "barrel" -> BARREL;
            case "shulker", "shulker_box" -> SHULKER_BOX;
            case "front", "panel" -> FRONT;
            default -> null;
        };
    }
}
