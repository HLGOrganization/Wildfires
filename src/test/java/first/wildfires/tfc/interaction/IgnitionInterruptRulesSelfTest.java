package first.wildfires.tfc.interaction;

import first.wildfires.compat.tfc.IgnitionInterruptRules;

/**
 * Plain-Java checks for the window that refuses the click a burned out firestarter leaves behind.
 *
 * <p>Two things are pinned here. The first is the length of the window, and the direction it is allowed to
 * move in: it has to cover exactly the ticks it claims to, and a tick that arrives late must not be able to
 * pull its end back in, because the client and the server arm it a network trip apart and the earlier of the
 * two answers must not win.
 *
 * <p>The second is the reason the window is held open by the clicks it refuses. A held button re-clicks
 * every four ticks - vanilla's own repeat - and the accident this window exists for is exactly that held
 * button, so a window that stayed at its first deadline would let the accident through a second later. The
 * burst below is spelled out at that cadence, with the tick numbers written out, so that a later change to
 * either the window or the arithmetic has to argue with the numbers rather than with the prose.
 *
 * <p>What is left to the in-game pass is the wiring: which hand, which item, which block. Those are four
 * plain booleans here, and the truth table at the end says that all four of them are needed.
 */
public final class IgnitionInterruptRulesSelfTest {

    /**
     * How often a held use button is re-clicked by the client, from {@code Minecraft.startUseItem}. This is
     * the cadence the window has to survive, so it is written down rather than assumed.
     */
    private static final int CLICK_REPEAT_TICKS = 4;

    private IgnitionInterruptRulesSelfTest() {
    }

    public static void main(String[] args) {
        anUnopenedWindowRefusesNothing();
        theWindowIsExactlyAsLongAsItSays();
        theWindowOnlyEverMovesForward();
        theClickRepeatIsWellInsideTheWindow();
        aHeldClickKeepsTheWindowOpenPastItsFirstDeadline();
        theWindowEndsOneSecondAfterTheLastClick();
        everyConditionIsNeeded();
        System.out.println("IgnitionInterruptRulesSelfTest: all checks passed");
    }

    private static void anUnopenedWindowRefusesNothing() {
        assertFalse(IgnitionInterruptRules.isActive(0L, 0L), "a window that was never opened covers nothing");
        assertFalse(IgnitionInterruptRules.isActive(0L, 1_000L), "not even later, once time has passed it by");
        assertFalse(IgnitionInterruptRules.isActive(-5L, 0L), "and not one armed before the world began");
        assertFalse(interrupts(true, true, true, false), "so a bare-handed sneak click on a pot is allowed");
    }

    private static void theWindowIsExactlyAsLongAsItSays() {
        final long armed = IgnitionInterruptRules.arm(0L, 0L);
        assertEquals(0L + IgnitionInterruptRules.WINDOW_TICKS, armed, "the window ends one window from now");
        assertTrue(IgnitionInterruptRules.isActive(armed, 0L), "the tick it is armed on is inside it");
        assertTrue(IgnitionInterruptRules.isActive(armed, IgnitionInterruptRules.WINDOW_TICKS - 1L),
                "so is its last tick");
        assertFalse(IgnitionInterruptRules.isActive(armed, IgnitionInterruptRules.WINDOW_TICKS),
                "and the tick it ends on is the first one outside it");
    }

    private static void theWindowOnlyEverMovesForward() {
        assertEquals(100L, IgnitionInterruptRules.arm(100L, 5L),
                "an arm from a tick that has already been passed cannot shorten the window");
        assertEquals(220L, IgnitionInterruptRules.arm(100L, 200L),
                "an arm from later than the current end does extend it");
        assertEquals(220L, IgnitionInterruptRules.arm(IgnitionInterruptRules.arm(100L, 200L), 200L),
                "and re-arming from the same tick changes nothing");
    }

    private static void theClickRepeatIsWellInsideTheWindow() {
        assertTrue(CLICK_REPEAT_TICKS < IgnitionInterruptRules.WINDOW_TICKS,
                "the window has to outlast the click that follows the firestarter breaking");
        assertTrue(2 * CLICK_REPEAT_TICKS < IgnitionInterruptRules.WINDOW_TICKS,
                "and it should still be open if one of those clicks is late");
    }

    /**
     * The accident, tick by tick: the firestarter breaks, the window opens, and the button is still down, so
     * the client clicks again four ticks later and every four ticks after that. Every one of those clicks is
     * refused, and every one of them holds the window open again.
     */
    private static void aHeldClickKeepsTheWindowOpenPastItsFirstDeadline() {
        long window = IgnitionInterruptRules.arm(0L, 0L);
        final long firstDeadline = window;
        for (long click = 0L; click < 20L; click += CLICK_REPEAT_TICKS) {
            assertTrue(IgnitionInterruptRules.isActive(window, click),
                    "the click on tick " + click + " is refused");
            window = IgnitionInterruptRules.arm(window, click);
        }

        assertTrue(window > firstDeadline,
                "the burst has carried the window past the deadline it was opened with");
        assertFalse(IgnitionInterruptRules.isActive(firstDeadline, 20L),
                "which is what would have let the accident through if the window had not moved");
        assertTrue(IgnitionInterruptRules.isActive(window, 20L), "while the window as it stands is still open");
    }

    private static void theWindowEndsOneSecondAfterTheLastClick() {
        // The burst above ends on tick sixteen, four ticks before the deadline it was opened with.
        final long lastClick = 16L;
        final long window = IgnitionInterruptRules.arm(IgnitionInterruptRules.arm(0L, 0L), lastClick);
        assertEquals(lastClick + IgnitionInterruptRules.WINDOW_TICKS, window,
                "the window ends one window after the last click it refused");
        assertTrue(IgnitionInterruptRules.isActive(window, window - 1L), "it is open right up to then");
        assertFalse(IgnitionInterruptRules.isActive(window, window), "and closed from then on");

        // A player who stops clicking therefore gets their pot back after a second, and no sooner.
        assertEquals(IgnitionInterruptRules.WINDOW_TICKS, window - lastClick,
                "a pause of exactly one window is enough");
    }

    private static void everyConditionIsNeeded() {
        assertTrue(interrupts(true, true, true, true), "a bare-handed sneak click on a pot in the window");
        assertFalse(interrupts(false, true, true, true), "standing up, the pot take is not TFC's gesture");
        assertFalse(interrupts(true, false, true, true),
                "with an item in hand the click never reaches the block: it lights the device instead");
        assertFalse(interrupts(true, true, false, true), "and a pot is the only block this is about");
        assertFalse(interrupts(true, true, true, false), "with the window passed, the take is allowed again");
    }

    private static boolean interrupts(boolean sneaking, boolean emptyHand, boolean potTarget, boolean windowActive) {
        return IgnitionInterruptRules.shouldInterrupt(sneaking, emptyHand, potTarget, windowActive);
    }

    private static void assertTrue(boolean holds, String what) {
        if (!holds) {
            throw new AssertionError(what);
        }
    }

    private static void assertFalse(boolean holds, String what) {
        if (holds) {
            throw new AssertionError(what);
        }
    }

    private static void assertEquals(long expected, long actual, String what) {
        if (expected != actual) {
            throw new AssertionError(what + ": expected " + expected + ", got " + actual);
        }
    }
}
