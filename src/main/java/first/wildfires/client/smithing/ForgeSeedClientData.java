package first.wildfires.client.smithing;

import java.util.OptionalLong;

/**
 * The world seed, as last sent by the server, for working out anvil targets on the client.
 *
 * <p>A recipe's target comes from the world seed ({@code ForgeTargets}), which the client has no way to
 * read on a server, so the server sends it once on join and again whenever the player changes dimension.
 * Empty means "not told yet", and the manual says so rather than inventing a target.
 */
public final class ForgeSeedClientData {

    private static volatile long seed;
    private static volatile boolean known;

    private ForgeSeedClientData() {
    }

    public static void accept(long incoming) {
        seed = incoming;
        known = true;
    }

    public static OptionalLong seed() {
        return known ? OptionalLong.of(seed) : OptionalLong.empty();
    }
}
