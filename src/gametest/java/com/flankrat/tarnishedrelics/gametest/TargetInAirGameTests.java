package com.flankrat.tarnishedrelics.gametest;

import com.flankrat.tarnishedrelics.TarnishedRelics;
import com.flankrat.tarnishedrelics.data.Conditions;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** The target_in_air condition: the target isn't standing on anything, in water or lava, or riding. */
@GameTestHolder(TarnishedRelics.MODID)
@PrefixGameTestTemplate(false)
public class TargetInAirGameTests {

    private static final Conditions IN_AIR = Conditions.parse(JsonParser.parseString("{\"target_in_air\": true}"));
    private static final Conditions ON_GROUND = Conditions.parse(JsonParser.parseString("{\"target_in_air\": false}"));

    @GameTest(template = "gametest/empty")
    public static void groundAndAir(GameTestHelper helper) {
        Zombie wielder = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
        Zombie target = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 1, 3));
        target.setOnGround(true);
        helper.assertFalse(IN_AIR.test(wielder, target, wielder.getRandom()), "a zombie on the ground counted as in the air");
        helper.assertTrue(ON_GROUND.test(wielder, target, wielder.getRandom()), "target_in_air false failed on the ground");
        target.setOnGround(false);
        helper.assertTrue(IN_AIR.test(wielder, target, wielder.getRandom()), "a zombie off the ground didn't count");
        helper.assertFalse(IN_AIR.test(wielder, null, wielder.getRandom()), "passed with no target");
        helper.succeed();
    }

    @GameTest(template = "gametest/empty")
    public static void flyingMobCounts(GameTestHelper helper) {
        Zombie wielder = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
        Phantom phantom = helper.spawn(EntityType.PHANTOM, new BlockPos(3, 3, 3));
        phantom.setOnGround(false);
        helper.assertTrue(IN_AIR.test(wielder, phantom, wielder.getRandom()), "a flying phantom didn't count");
        helper.succeed();
    }

    @GameTest(template = "gametest/empty")
    public static void swimmingDoesntCount(GameTestHelper helper) {
        Zombie wielder = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
        helper.setBlock(new BlockPos(3, 1, 3), Blocks.WATER);
        helper.setBlock(new BlockPos(3, 2, 3), Blocks.WATER);
        Zombie target = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 1, 3));
        target.setOnGround(false);
        target.baseTick();   // works out whether it's in water
        helper.assertTrue(target.isInWater(), "the target isn't in the water (test setup)");
        helper.assertFalse(IN_AIR.test(wielder, target, wielder.getRandom()), "a mob in water counted as in the air");
        helper.succeed();
    }

    @GameTest(template = "gametest/empty")
    public static void ridingDoesntCount(GameTestHelper helper) {
        Zombie wielder = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
        Zombie target = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 1, 3));
        Boat boat = helper.spawn(EntityType.BOAT, new BlockPos(3, 1, 3));
        target.startRiding(boat, true);
        target.setOnGround(false);
        helper.assertTrue(target.isPassenger(), "the target isn't riding (test setup)");
        helper.assertFalse(IN_AIR.test(wielder, target, wielder.getRandom()), "a mob riding a boat counted as in the air");
        helper.succeed();
    }
}
