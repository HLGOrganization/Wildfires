package first.wildfires.tfc.interaction;

import first.wildfires.compat.tfc.TorchIgnitionRules;
import first.wildfires.compat.tfc.TorchIgnitionRules.Target;
import java.util.Arrays;
import java.util.TreeSet;

/**
 * Plain-Java checks for what a lit torch is allowed to light, and what that costs.
 *
 * <p>The list below is a list of registry ids, and a registry id is the one part of this feature that nothing else
 * would catch: every entry is a string on this side and a registered block on the other, and a string that names
 * nothing does not throw, does not warn and does not fail to compile - it simply means a torch that refuses to
 * light one particular block, which reads as a bug in the feature rather than as a typo in a name. So the exact
 * set is written out here in full. Adding a block to the mod means arguing with this line, which is the point.
 *
 * <p>The costs are pinned for the same reason. A target that spends a torch is a promise to the player, and the
 * two burnt out torches are the exception the whole list turns on: they are the same torch relit, so charging for
 * them would make the repair cost more than the thing being repaired.
 *
 * <p>The rest - that a weak request is the only one answered, that the client is cancelled alongside the server,
 * that the counter of a relit torch is restarted - is wiring, and is left to the in-game pass.
 */
public final class TorchIgnitionRulesSelfTest {

    /**
     * The complete list, sorted, as it must be. Nine blocks: the two burnt out torches, and the seven devices a
     * torch may light - the campfire, the charcoal forge, this mod's unrestricted forge, the blast furnace, the
     * bloomery, the pit kiln and the log pile.
     */
    private static final String EXPECTED_IDS = String.join(",",
            "tfc:blast_furnace",
            "tfc:bloomery",
            "tfc:charcoal_forge",
            "tfc:dead_torch",
            "tfc:dead_wall_torch",
            "tfc:firepit",
            "tfc:log_pile",
            "tfc:pit_kiln",
            "wildfires:unrestricted_charcoal_forge");

    private TorchIgnitionRulesSelfTest() {
    }

    public static void main(String[] args) {
        theMobBurnIsFireAspectOne();
        theListIsExactlyTheBlocksATorchMayLight();
        everyIdIsNamedOnce();
        everyIdLooksUpToItsOwnTarget();
        onlyABurntOutTorchIsFreeAndRelitInPlace();
        everyDeviceCostsOneTorch();
        anUnlistedBlockIsNotATarget();
        System.out.println("TorchIgnitionRulesSelfTest: all checks passed");
    }

    private static void theMobBurnIsFireAspectOne() {
        assertEquals(4, TorchIgnitionRules.MOB_FIRE_SECONDS, "a struck mob burns for four seconds");
    }

    private static void theListIsExactlyTheBlocksATorchMayLight() {
        String[] ids = new String[Target.values().length];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = Target.values()[i].id();
        }
        Arrays.sort(ids);
        assertEquals(EXPECTED_IDS, String.join(",", ids), "the list of blocks a torch may light");
    }

    private static void everyIdIsNamedOnce() {
        TreeSet<String> unique = new TreeSet<>();
        for (Target target : Target.values()) {
            unique.add(target.id());
        }
        assertEquals(Target.values().length, unique.size(), "no id is claimed by two targets");
    }

    private static void everyIdLooksUpToItsOwnTarget() {
        for (Target target : Target.values()) {
            assertSame(target, TorchIgnitionRules.byId(target.id()), target.id() + " resolves to itself");
        }
    }

    private static void onlyABurntOutTorchIsFreeAndRelitInPlace() {
        for (Target target : Target.values()) {
            boolean burntOut = target == Target.DEAD_TORCH || target == Target.DEAD_WALL_TORCH;
            assertEquals(burntOut, target.relightsInPlace(), target.id() + " is relit in place only if it is burnt out");
            assertEquals(burntOut, !target.consumesTorch(), target.id() + " is free only if it is burnt out");
        }
    }

    private static void everyDeviceCostsOneTorch() {
        int devices = 0;
        for (Target target : Target.values()) {
            if (!target.relightsInPlace()) {
                devices++;
                assertTrue(target.consumesTorch(), target.id() + " spends the torch that lit it");
            }
        }
        assertEquals(7, devices, "seven devices a torch may light");
    }

    private static void anUnlistedBlockIsNotATarget() {
        assertNull(TorchIgnitionRules.byId(null), "a missing id names nothing");
        assertNull(TorchIgnitionRules.byId(""), "an empty id names nothing");
        assertNull(TorchIgnitionRules.byId("tfc:torch"), "a lit torch is not a device to light");
        assertNull(TorchIgnitionRules.byId("minecraft:torch"), "neither is a vanilla one");
        assertNull(TorchIgnitionRules.byId("tfc:wall_torch"), "nor a lit wall torch");
        assertNull(TorchIgnitionRules.byId("tfc:charcoal_pile"), "the charcoal pile is heaped charcoal, not a kiln");
        assertNull(TorchIgnitionRules.byId("tfc:burning_log_pile"), "and a pile that is already burning needs no torch");
        assertNull(TorchIgnitionRules.byId("minecraft:campfire"), "a vanilla campfire is not TFC's firepit");
        assertNull(TorchIgnitionRules.byId("wildfires:unrestricted_charcoal_forge "), "an id is matched exactly");
        assertNull(TorchIgnitionRules.byId("TFC:FIREPIT"), "and case sensitively");
    }

    private static void assertTrue(boolean holds, String what) {
        if (!holds) {
            throw new AssertionError(what);
        }
    }

    private static void assertNull(Object actual, String what) {
        if (actual != null) {
            throw new AssertionError(what + ": expected nothing, got " + actual);
        }
    }

    private static void assertSame(Object expected, Object actual, String what) {
        if (expected != actual) {
            throw new AssertionError(what + ": expected " + expected + ", got " + actual);
        }
    }

    private static void assertEquals(Object expected, Object actual, String what) {
        if (!expected.equals(actual)) {
            throw new AssertionError(what + ": expected " + expected + ", got " + actual);
        }
    }
}
