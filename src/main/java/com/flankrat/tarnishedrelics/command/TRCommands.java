package com.flankrat.tarnishedrelics.command;

import com.flankrat.tarnishedrelics.TRItems;
import com.flankrat.tarnishedrelics.data.Category;
import com.flankrat.tarnishedrelics.data.Rarity;
import com.flankrat.tarnishedrelics.data.TRData;
import com.flankrat.tarnishedrelics.editor.EditorServer;
import com.flankrat.tarnishedrelics.relic.Roller;
import com.flankrat.tarnishedrelics.smith.Blacksmith;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Commands for making and testing content (op level 2):
 * <pre>
 *   /tarnishedrelics give [rarity] [category] [count]   random relics (any rarity/category if left out)
 *   /tarnishedrelics identify                           identifies the relic in your main hand
 *   /tarnishedrelics errors                             problems found in the datapack files
 *   /tarnishedrelics editor                             opens the in-game editor for all of the above
 * </pre>
 */
public final class TRCommands {

    private TRCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("tarnishedrelics").requires(s -> s.hasPermission(2))
                .then(Commands.literal("give")
                        .executes(c -> give(c, null, null, 1))
                        .then(Commands.argument("rarity", ResourceLocationArgument.id())
                                .suggests((c, b) -> SharedSuggestionProvider.suggestResource(TRData.get().rarities().keySet(), b))
                                .executes(c -> give(c, ResourceLocationArgument.getId(c, "rarity"), null, 1))
                                .then(Commands.argument("category", ResourceLocationArgument.id())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggestResource(TRData.get().categories().keySet(), b))
                                        .executes(c -> give(c, ResourceLocationArgument.getId(c, "rarity"), ResourceLocationArgument.getId(c, "category"), 1))
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                                .executes(c -> give(c, ResourceLocationArgument.getId(c, "rarity"),
                                                        ResourceLocationArgument.getId(c, "category"), IntegerArgumentType.getInteger(c, "count")))))))
                .then(Commands.literal("identify").executes(TRCommands::identify))
                .then(Commands.literal("errors").executes(TRCommands::errors))
                .then(Commands.literal("editor").executes(c -> {
                    EditorServer.open(c.getSource().getPlayerOrException(), false);
                    return 1;
                })));
    }

    private static int give(CommandContext<CommandSourceStack> c, ResourceLocation rarityId, ResourceLocation categoryId, int count)
            throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        Rarity rarity = rarityId == null ? null : TRData.get().rarities().get(rarityId);
        Category category = categoryId == null ? null : TRData.get().categories().get(categoryId);
        if (rarityId != null && rarity == null) return fail(c, "Unknown rarity " + rarityId);
        if (categoryId != null && category == null) return fail(c, "Unknown category " + categoryId);
        int made = 0;
        for (int i = 0; i < count; i++) {
            ItemStack relic = Roller.generate(player.getRandom(), rarity, category);
            if (relic.isEmpty()) break;
            if (!player.getInventory().add(relic)) player.drop(relic, false);
            made++;
        }
        if (made == 0) return fail(c, "Nothing to make: no rarity has a base item in that category");
        int finalMade = made;
        c.getSource().sendSuccess(() -> Component.literal("Gave " + finalMade + " unidentified relic(s)"), false);
        return made;
    }

    private static int identify(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        ItemStack held = player.getMainHandItem();
        if (!held.is(TRItems.UNIDENTIFIED.get())) return fail(c, "Hold an unidentified relic in your main hand");
        ItemStack real = Blacksmith.identify(held, player);
        player.setItemInHand(InteractionHand.MAIN_HAND, real);
        c.getSource().sendSuccess(() -> Component.literal("Identified: ").append(real.getHoverName()), false);
        return 1;
    }

    private static int errors(CommandContext<CommandSourceStack> c) {
        var errors = TRData.get().errors();
        if (errors.isEmpty()) {
            c.getSource().sendSuccess(() -> Component.literal("No problems in the Tarnished Relics datapack files"), false);
        } else {
            for (String e : errors) c.getSource().sendFailure(Component.literal(e));
        }
        return errors.size();
    }

    private static int fail(CommandContext<CommandSourceStack> c, String message) {
        c.getSource().sendFailure(Component.literal(message));
        return 0;
    }
}
