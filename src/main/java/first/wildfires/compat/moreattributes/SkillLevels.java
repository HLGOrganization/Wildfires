package first.wildfires.compat.moreattributes;

import net.minecraft.world.entity.player.Player;

import java.lang.reflect.Method;

/**
 * Reads a player's skill level from More Attributes.
 *
 * <h2>Why this is reflective</h2>
 * The level lives in that mod's own player capability, reachable through
 * {@code org.mantodea.more_attributes.utils.LevelUtils#getLevel}. Calling it directly would make More
 * Attributes a hard dependency: the mod would fail to load without it, even though everything else here
 * works fine on its own. A single reflected call keeps the dependency soft, which is how the rest of this
 * mod treats the optional mods it cooperates with.
 *
 * <p>When the mod is absent - or answers unexpectedly - the level reads as {@code 0}, which is the same
 * answer More Attributes itself gives for a player it has no record of.
 */
public final class SkillLevels {

    /** The attribute name More Attributes files its "技巧" level under, from {@code attributes/skill.json}. */
    public static final String SKILL = "skill";

    private static final String LEVEL_UTILS = "org.mantodea.more_attributes.utils.LevelUtils";

    /** Resolved once on first use; null means More Attributes is not present. */
    private static volatile Method getLevel;
    private static volatile boolean resolved;

    private SkillLevels() {
    }

    public static int of(Player player) {
        Method method = lookup();
        if (method == null) {
            return 0;
        }
        try {
            Object level = method.invoke(null, player, SKILL);
            return level instanceof Integer value ? value : 0;
        } catch (ReflectiveOperationException | RuntimeException unexpected) {
            // A version of More Attributes that reshaped this method should cost a soft feature, not a crash.
            return 0;
        }
    }

    private static Method lookup() {
        if (!resolved) {
            synchronized (SkillLevels.class) {
                if (!resolved) {
                    getLevel = resolve();
                    resolved = true;
                }
            }
        }
        return getLevel;
    }

    private static Method resolve() {
        try {
            return Class.forName(LEVEL_UTILS).getMethod("getLevel", Player.class, String.class);
        } catch (ClassNotFoundException | NoSuchMethodException | LinkageError absent) {
            return null;
        }
    }
}
