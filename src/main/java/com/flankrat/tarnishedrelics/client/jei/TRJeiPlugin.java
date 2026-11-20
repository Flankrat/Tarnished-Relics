package com.flankrat.tarnishedrelics.client.jei;

import com.flankrat.tarnishedrelics.TRItems;
import com.flankrat.tarnishedrelics.TarnishedRelics;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Hides the unidentified relic from JEI's item list. An empty one has nothing inside and only shows
 * "Unidentified Relic", so listing it just gives players a useless item to look up (or cheat in).
 * JEI only loads this class when JEI is installed.
 */
@JeiPlugin
public class TRJeiPlugin implements IModPlugin {

    @Nonnull
    @Override
    public ResourceLocation getPluginUid() {
        return new ResourceLocation(TarnishedRelics.MODID, "jei_plugin");
    }

    // JEI rebuilds its list every time the runtime starts (each world join), so this runs each time
    @Override
    public void onRuntimeAvailable(@Nonnull IJeiRuntime runtime) {
        runtime.getIngredientManager().removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, List.of(new ItemStack(TRItems.UNIDENTIFIED.get())));
    }
}
