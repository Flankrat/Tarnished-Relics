package com.flankrat.tarnishedrelics.smith;

import com.flankrat.tarnishedrelics.TRConfig;
import com.flankrat.tarnishedrelics.TRItems;
import com.flankrat.tarnishedrelics.TarnishedRelics;
import com.flankrat.tarnishedrelics.name.QualityCompat;
import com.flankrat.tarnishedrelics.relic.UnidentifiedItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Identifying at a blacksmith. Use an unidentified relic on a villager whose profession is in the tag
 * {@code #tarnishedrelics:blacksmiths} (armorer, weaponsmith and toolsmith by default):
 * <ol>
 *   <li>If the villager's level is too low for the relic's rarity, they shake their head and say what level is needed.
 *       This is the only place the player finds out.</li>
 *   <li>Otherwise they name their price in emeralds. Using the relic on them again within [identify] confirmSeconds
 *       pays and identifies it: the real item replaces the relic in your hand, and the villager gets trade XP.</li>
 * </ol>
 * Non-blacksmith villagers say they can't help (so holding a relic doesn't open their trade screen by accident).
 */
public final class Blacksmith {

    public static final TagKey<VillagerProfession> BLACKSMITHS =
            TagKey.create(Registries.VILLAGER_PROFESSION, new ResourceLocation(TarnishedRelics.MODID, "blacksmiths"));

    /** A price a smith named, waiting for the player to accept. */
    private record Pending(UUID villager, CompoundTag relic, long expires) {
    }

    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    private static Method rewardTradeXp;   // Villager.rewardTradeXp (protected), found once

    private Blacksmith() {
    }

    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof AbstractVillager)) return;
        ItemStack held = event.getItemStack();
        if (!held.is(TRItems.UNIDENTIFIED.get())) return;
        // Both sides: never open the trade screen while handing over a relic
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (event.getLevel().isClientSide || !(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(event.getTarget() instanceof Villager villager) || villager.isBaby() || villager.isTrading()) return;
        handle(player, villager, event.getHand(), held);
    }

    private static void handle(ServerPlayer player, Villager villager, InteractionHand hand, ItemStack held) {
        VillagerProfession profession = villager.getVillagerData().getProfession();
        var tags = ForgeRegistries.VILLAGER_PROFESSIONS.tags();
        if (tags == null || !tags.getTag(BLACKSMITHS).contains(profession)) {
            say(player, villager, Component.translatable("tarnishedrelics.smith.not_smith"));
            refuse(villager);
            return;
        }
        CompoundTag tag = held.getOrCreateTag();
        if (!tag.contains("Hidden")) return;   // an empty relic (e.g. from /give) has nothing to identify
        int needed = Math.max(1, tag.getInt("Level"));
        int cost = Math.max(0, tag.getInt("Cost"));
        if (villager.getVillagerData().getLevel() < needed) {
            // vanilla's own level names: Novice, Apprentice, Journeyman, Expert, Master
            say(player, villager, Component.translatable("tarnishedrelics.smith.too_hard", Component.translatable("merchant.level." + needed)));
            refuse(villager);
            return;
        }

        long now = player.level().getGameTime();
        Pending pending = PENDING.get(player.getUUID());
        boolean confirmed = pending != null && pending.villager.equals(villager.getUUID()) && pending.relic.equals(tag) && now <= pending.expires;
        if (!confirmed) {
            PENDING.put(player.getUUID(), new Pending(villager.getUUID(), tag.copy(), now + TRConfig.CONFIRM_SECONDS.get() * 20L));
            say(player, villager, Component.translatable("tarnishedrelics.smith.price", cost));
            villager.playSound(SoundEvents.VILLAGER_AMBIENT, 1, villager.getVoicePitch());
            return;
        }
        PENDING.remove(player.getUUID());
        if (!player.getAbilities().instabuild && countEmeralds(player) < cost) {
            say(player, villager, Component.translatable("tarnishedrelics.smith.poor", cost));
            refuse(villager);
            return;
        }
        if (!player.getAbilities().instabuild) takeEmeralds(player, cost);

        ItemStack real = identify(held, player);
        player.setItemInHand(hand, real);
        rewardVillager(villager, cost, tag.getInt("Xp"));
        villager.playSound(SoundEvents.VILLAGER_YES, 1, villager.getVoicePitch());
        player.level().playSound(null, villager.blockPosition(), SoundEvents.ANVIL_USE, SoundSource.NEUTRAL, 0.6F, 1.2F);
        say(player, villager, Component.translatable("tarnishedrelics.smith.done", real.getHoverName()));
    }

    /** The real item inside a relic, ready to use (also used by /tarnishedrelics identify). */
    public static ItemStack identify(ItemStack wrapper, Player player) {
        ItemStack real = UnidentifiedItem.hidden(wrapper);
        QualityCompat.rollQuality(real, player.getRandom());
        return real;
    }

    private static void say(ServerPlayer player, Villager villager, Component message) {
        player.sendSystemMessage(Component.translatable("tarnishedrelics.smith.says", villager.getDisplayName(), message));
    }

    // head shake + "no" sound, like a trade they won't do
    private static void refuse(Villager villager) {
        villager.setUnhappyCounter(40);
        villager.playSound(SoundEvents.VILLAGER_NO, 1, villager.getVoicePitch());
    }

    private static int countEmeralds(Player player) {
        int n = 0;
        for (ItemStack s : player.getInventory().items) if (s.is(Items.EMERALD)) n += s.getCount();
        return n;
    }

    private static void takeEmeralds(Player player, int cost) {
        for (ItemStack s : player.getInventory().items) {
            if (cost <= 0) break;
            if (!s.is(Items.EMERALD)) continue;
            int take = Math.min(cost, s.getCount());
            s.shrink(take);
            cost -= take;
        }
    }

    // Trade XP the same way a trade gives it (so the smith can level up), plus happy particles.
    // Villager.rewardTradeXp is protected, so it's called by reflection (SRG name m_8058_).
    private static void rewardVillager(Villager villager, int cost, int xp) {
        villager.level().broadcastEntityEvent(villager, (byte) 14);   // happy villager particles
        if (xp <= 0) return;
        try {
            if (rewardTradeXp == null) rewardTradeXp = ObfuscationReflectionHelper.findMethod(Villager.class, "m_8058_", MerchantOffer.class);
            MerchantOffer offer = new MerchantOffer(new ItemStack(Items.EMERALD, Math.max(1, cost)), ItemStack.EMPTY, 1, xp, 0);
            rewardTradeXp.invoke(villager, offer);
        } catch (ReflectiveOperationException | RuntimeException e) {
            TarnishedRelics.LOGGER.warn("[Tarnished Relics] couldn't give the blacksmith trade XP", e);
        }
    }

    // Relics can't be renamed or combined in an anvil before they're identified
    @SubscribeEvent
    public static void onAnvil(AnvilUpdateEvent event) {
        if (event.getLeft().is(TRItems.UNIDENTIFIED.get()) || event.getRight().is(TRItems.UNIDENTIFIED.get())) event.setCanceled(true);
    }
}
