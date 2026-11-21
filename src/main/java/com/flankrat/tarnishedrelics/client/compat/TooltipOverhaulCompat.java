package com.flankrat.tarnishedrelics.client.compat;

import com.flankrat.tarnishedrelics.TarnishedRelics;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Field;

/**
 * Optional Tooltip Overhaul support (client). When its rating line is on, that line already shows the relic's tier
 * (TooltipOverhaulRatingMixin), so the tooltip body leaves its own tier line out. Read with reflection, so Tooltip
 * Overhaul isn't needed to build or run.
 */
public final class TooltipOverhaulCompat {

    private static Field showRating;   // TooltipsConfig.SHOW_RATING, a static boolean kept up to date by its config
    private static boolean checked;

    private TooltipOverhaulCompat() {
    }

    /** True when Tooltip Overhaul is installed and shows the rating line under item names. */
    public static boolean showsRating() {
        if (!checked) {
            checked = true;
            if (ModList.get() != null && ModList.get().isLoaded("tooltipoverhaul")) {
                try {
                    showRating = Class.forName("dev.xylonity.tooltipoverhaul.config.TooltipsConfig").getField("SHOW_RATING");
                } catch (ReflectiveOperationException | LinkageError e) {
                    TarnishedRelics.LOGGER.warn("[Tarnished Relics] couldn't read Tooltip Overhaul's SHOW_RATING, relics keep the tier line in their tooltip", e);
                }
            }
        }
        if (showRating == null) return false;
        try {
            return showRating.getBoolean(null);
        } catch (IllegalAccessException e) {
            showRating = null;
            return false;
        }
    }
}
