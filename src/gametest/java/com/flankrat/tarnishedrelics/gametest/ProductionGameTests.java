package com.flankrat.tarnishedrelics.gametest;

import com.flankrat.tarnishedrelics.TarnishedRelics;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestRegistry;
import net.minecraft.gametest.framework.GameTestTicker;
import net.minecraft.gametest.framework.TestCommand;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLLoader;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Runs the mod-compatibility GameTests in a real modpack. Forge only turns GameTests on outside production, so in a
 * modpack (with the gametest jar from ./gradlew gametestJar) this registers {@link CompatGameTests} and the /test
 * command itself, and ticks the test runner (the server only does that when Forge's GameTest switch is on); then
 * "/test runall" runs them. Does nothing in the dev runs (Forge registers the tests there).
 */
@Mod.EventBusSubscriber(modid = TarnishedRelics.MODID)
public final class ProductionGameTests {

    private static boolean registered;
    private static boolean running;   // tests were in the runner at the end of the last tick

    private ProductionGameTests() {
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        if (!FMLLoader.isProduction()) return;
        if (!registered) {
            // only the compatibility tests: the others check the mod on its own, and other mods change what they see.
            // PackLocalGameTests: extra pack tests someone keeps in their own checkout (not in the repo), if present
            for (Method m : testMethods()) {
                // the batch setup too (@BeforeBatch loads the tests' own affixes), not just the tests
                if (m.isAnnotationPresent(GameTest.class) || m.isAnnotationPresent(BeforeBatch.class) || m.isAnnotationPresent(AfterBatch.class)) {
                    GameTestRegistry.register(m, Set.of(TarnishedRelics.MODID));
                }
            }
            registered = true;
        }
        TestCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        // the server skips this outside dev runs: without it the tests start and then never move on
        if (!registered || event.phase != TickEvent.Phase.END) return;
        // tests are added between ticks (by the command) and quick ones finish and leave the runner inside one tick, so
        // look before ticking too
        running |= !runningTests().isEmpty();
        GameTestTicker.SINGLETON.tick();
        // Passed tests log nothing in production and the summary only goes to the command's chat, so say when the
        // runner is empty again: scripts wait for this line (failures are logged by LogTestReporter as they happen)
        boolean now = !runningTests().isEmpty();
        if (running && !now) TarnishedRelics.LOGGER.info("[Tarnished Relics test] GameTests finished");
        running = now;
    }

    // the runner's list of current tests (its only Collection field; found by type, since the name is obfuscated)
    private static Collection<?> runningTests() {
        for (Field f : GameTestTicker.class.getDeclaredFields()) {
            if (Collection.class.isAssignableFrom(f.getType())) {
                try {
                    f.setAccessible(true);
                    return (Collection<?>) f.get(GameTestTicker.SINGLETON);
                } catch (ReflectiveOperationException e) {
                    break;
                }
            }
        }
        return List.of();
    }

    private static List<Method> testMethods() {
        List<Method> methods = new ArrayList<>(List.of(CompatGameTests.class.getMethods()));
        try {
            methods.addAll(List.of(Class.forName("com.flankrat.tarnishedrelics.gametest.PackLocalGameTests").getMethods()));
        } catch (ClassNotFoundException ignored) {
            // not in this checkout
        }
        return methods;
    }
}
