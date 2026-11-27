package com.cinematicchests.mixin;

import dev.dyslexic.cinematic.api.AddonStations;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The add-on station tables, so vanilla blocks can be added without loosening the public API's check for anyone else. */
@Mixin(value = AddonStations.class, remap = false)
public interface AddonStationsAccessor {
    @Accessor("BY_ID")
    static Map<ResourceLocation, AddonStations.Definition> cinematicchests$byId() {
        throw new AssertionError();
    }

    @Accessor("BY_BLOCK")
    static Map<ResourceLocation, AddonStations.Definition> cinematicchests$byBlock() {
        throw new AssertionError();
    }
}
