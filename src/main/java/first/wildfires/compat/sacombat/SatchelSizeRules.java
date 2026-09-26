package first.wildfires.compat.sacombat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.dries007.tfc.common.capabilities.size.IItemSize;
import net.dries007.tfc.common.capabilities.size.ItemSizeManager;
import net.dries007.tfc.common.capabilities.size.Size;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * TFC item-size limits for the Survivors Arsenal containers.
 *
 * <p>Survivors Arsenal exposes no configuration for container filtering, and its own slot checks
 * only reject nested backpacks, so the limits have to be applied from the outside. Three tiers are
 * enforced, matching how much a container is expected to hold:
 *
 * <ul>
 *   <li>{@code SMALL} &mdash; satchels, blue jeans and the jackets.</li>
 *   <li>{@code NORMAL} &mdash; leather backpack and the small backpacks.</li>
 *   <li>{@code LARGE} &mdash; hiking, duffel and military backpacks.</li>
 * </ul>
 *
 * <p>Two container families exist and both are covered:
 *
 * <ul>
 *   <li><b>Backpacks</b> ({@code com.ogaba.sa_survival.item.backpack}). The menu slot, the dynamic
 *       handler's {@code isItemValid} and its {@code insertItem} are all injected, because the
 *       dynamic handler's own {@code insertItem} does not consult its {@code isItemValid}.</li>
 *   <li><b>Clothing with storage</b> ({@code com.ogaba.sa_survival.item.clothing}). Only
 *       {@code DynamicClothingItemHandler#isItemValid} is injected: its {@code insertItem} calls
 *       {@code isItemValid} first, and {@code InventoryClothingSlot#mayPlace} calls it too, so one
 *       injection point covers the GUI, shift-click and automation.</li>
 * </ul>
 *
 * <p>The inherited {@code ItemStackHandler} behaviour is never touched, so no other mod is
 * affected. Survivors Arsenal is optional to compile and may be absent from a development pack, so
 * its types are never referenced here. Every lookup goes through reflection and fails open.
 */
public final class SatchelSizeRules {

    /**
     * Containers from other mods that must never be nested inside a Survivors Arsenal container.
     *
     * <p>Survivors Arsenal rejects another {@code BackpackItem}, and the plain Wildfires variants
     * inherit that class so they are covered automatically. Bags from Sacks 'N Such and
     * Sophisticated Backpacks are unrelated types, so they are named here instead.
     *
     * <p>Sophisticated's own Inception upgrade lets a player nest its backpacks deliberately; that
     * choice is respected, because this set only names the bags, and the mod's own handling is left
     * untouched. Only the containers themselves are listed, never their upgrade items.
     */
    private static final Set<ResourceLocation> NESTED_CONTAINERS = Set.of(
            // Sacks 'N Such
            id("sns", "straw_basket"),
            id("sns", "leather_sack"),
            id("sns", "burlap_sack"),
            id("sns", "ore_sack"),
            id("sns", "seed_pouch"),
            id("sns", "frame_pack"),
            id("sns", "lunchbox"),
            id("sns", "quiver"),
            // Sophisticated Backpacks
            id("sophisticatedbackpacks", "backpack"),
            id("sophisticatedbackpacks", "copper_backpack"),
            id("sophisticatedbackpacks", "iron_backpack"),
            id("sophisticatedbackpacks", "gold_backpack"),
            id("sophisticatedbackpacks", "diamond_backpack"),
            id("sophisticatedbackpacks", "netherite_backpack")
    );

    private static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }
    /** Highest size accepted by satchels, blue jeans and the jackets. */
    public static final Size SATCHEL_MAX = Size.SMALL;

    /** Highest size accepted by the leather and small backpacks. */
    public static final Size SMALL_BACKPACK_MAX = Size.NORMAL;

    /** Highest size accepted by the hiking, duffel and military backpacks. */
    public static final Size LARGE_BACKPACK_MAX = Size.LARGE;

    /**
     * Every limited container, mapped to the largest TFC size it accepts.
     *
     * <p>A container missing from this map is left completely alone, so an unknown or newly added
     * variant keeps its original behaviour instead of silently inheriting a limit.
     */
    public static final Map<ResourceLocation, Size> LIMITS = Map.ofEntries(
            // Satchels: the smallest tier.
            entry("satchel_black", SATCHEL_MAX),
            entry("satchel_brown", SATCHEL_MAX),
            entry("satchel_green", SATCHEL_MAX),
            entry("satchel_white", SATCHEL_MAX),

            // Clothing storage panels: the smallest tier, matching the satchels.
            //
            // Only clothing that actually carries slots belongs here. blue_jeans and the Kennedy
            // jacket declare their slots when they are built (2 and 4), while the vest and the
            // firefighter set gain theirs from [external_equipment_storage]. green_t_shirt and the
            // remaining uniforms carry no slots at all, so nothing needs limiting on them.
            entry("blue_jeans", SATCHEL_MAX),
            entry("leon_s_kennedy_jacket", SATCHEL_MAX),
            entry("black_tactical_vest", SATCHEL_MAX),
            entry("firefighter_jacket", SATCHEL_MAX),
            entry("firefighter_pants", SATCHEL_MAX),

            // Leather and small backpacks: the middle tier.
            entry("leather_backpack", SMALL_BACKPACK_MAX),
            entry("small_backpack_black", SMALL_BACKPACK_MAX),
            entry("small_backpack_blue", SMALL_BACKPACK_MAX),
            entry("small_backpack_green", SMALL_BACKPACK_MAX),
            entry("small_backpack_pink", SMALL_BACKPACK_MAX),

            // Hiking, duffel and military backpacks: the largest tier, every colour included.
            entry("hiking_backpack_black", LARGE_BACKPACK_MAX),
            entry("hiking_backpack_blue", LARGE_BACKPACK_MAX),
            entry("hiking_backpack_green", LARGE_BACKPACK_MAX),
            entry("hiking_backpack_red", LARGE_BACKPACK_MAX),
            entry("duffel_bag_black", LARGE_BACKPACK_MAX),
            entry("duffel_bag_blue", LARGE_BACKPACK_MAX),
            entry("duffel_bag_red", LARGE_BACKPACK_MAX),
            entry("duffel_bag_yellow", LARGE_BACKPACK_MAX),
            entry("military_backpack_blue", LARGE_BACKPACK_MAX),
            entry("military_backpack_camo", LARGE_BACKPACK_MAX),
            entry("military_backpack_desert", LARGE_BACKPACK_MAX),
            entry("military_backpack_green", LARGE_BACKPACK_MAX),

            // Wildfires undyed leather variants, limited exactly like the family they copy.
            //
            // These are registered under the "wildfires" namespace, not "sa_combat", so they need
            // the two-argument id() overload. Using the one-argument form here would silently map
            // them to sa_combat:satchel_leather and so on - items that do not exist - and the limit
            // would never apply to the Wildfires packs.
            leather("satchel_leather", SATCHEL_MAX),
            leather("small_backpack_leather", SMALL_BACKPACK_MAX),
            leather("duffel_bag_leather", LARGE_BACKPACK_MAX),
            leather("hiking_backpack_leather", LARGE_BACKPACK_MAX),
            leather("military_backpack_leather", LARGE_BACKPACK_MAX)
    );


    private static final String BACKPACK_GETTER = "getBackpackStack";
    private static final String CLOTHING_GETTER = "getClothingStack";
    private static final String BACKPACK_FIELD = "backpackStack";
    private static final String HANDLER_GETTER = "getItemHandler";
    private static final String SOURCE_STACKS_FIELD = "sourceStacks";

    private static final Map<String, Optional<Method>> METHODS = new ConcurrentHashMap<>();
    private static final Map<String, Optional<Field>> FIELDS = new ConcurrentHashMap<>();

    private SatchelSizeRules() {
    }

    private static Map.Entry<ResourceLocation, Size> entry(String path, Size max) {
        return Map.entry(id(path), max);
    }

    /** Maps a Wildfires-owned backpack variant, which lives in the "wildfires" namespace. */
    private static Map.Entry<ResourceLocation, Size> leather(String path, Size max) {
        return Map.entry(id("wildfires", path), max);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("sa_combat", path);
    }

    /** True when the candidate satisfies the limit of the owning container, or is empty. */
    public static boolean allows(ItemStack stack, Size maxSize) {
        if (stack == null || stack.isEmpty()) {
            return true;
        }
        IItemSize sized = ItemSizeManager.get(stack);
        if (sized == null) {
            return true;
        }
        Size size = sized.getSize(stack);
        return size == null || size.isEqualOrSmallerThan(maxSize);
    }

    /**
     * The containers that advertise their size ceiling in the item tooltip.
     *
     * <p>Deliberately only the backpacks. Clothing with storage is limited by the same rules, but a
     * player does not read a pair of jeans as a container, and a "fits at most size" line on a
     * jacket reads as a bug rather than as information. The limit still applies either way; this
     * set only decides what is worth telling the player about.
     */
    private static final Set<ResourceLocation> TOOLTIP_CONTAINERS = Set.of(
            // sa_combat backpacks
            id("satchel_black"), id("satchel_brown"), id("satchel_green"), id("satchel_white"),
            id("leather_backpack"),
            id("small_backpack_black"), id("small_backpack_blue"),
            id("small_backpack_green"), id("small_backpack_pink"),
            id("hiking_backpack_black"), id("hiking_backpack_blue"),
            id("hiking_backpack_green"), id("hiking_backpack_red"),
            id("duffel_bag_black"), id("duffel_bag_blue"),
            id("duffel_bag_red"), id("duffel_bag_yellow"),
            id("military_backpack_blue"), id("military_backpack_camo"),
            id("military_backpack_desert"), id("military_backpack_green"),
            // Wildfires undyed leather variants
            id("wildfires", "satchel_leather"),
            id("wildfires", "small_backpack_leather"),
            id("wildfires", "duffel_bag_leather"),
            id("wildfires", "hiking_backpack_leather"),
            id("wildfires", "military_backpack_leather")
    );

    /** Returns the limit for this container, or {@code null} when it is not limited. */
    public static Size limitFor(ItemStack container) {
        if (container == null || container.isEmpty()) {
            return null;
        }
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(container.getItem());
        return key == null ? null : LIMITS.get(key);
    }

    /**
     * The size ceiling to show in the tooltip, or {@code null} when this item should stay silent.
     *
     * <p>Same value as {@link #limitFor}, but restricted to the containers a player thinks of as
     * containers. See {@link #TOOLTIP_CONTAINERS}.
     */
    public static Size tooltipLimitFor(ItemStack container) {
        if (container == null || container.isEmpty()) {
            return null;
        }
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(container.getItem());
        return key != null && TOOLTIP_CONTAINERS.contains(key) ? LIMITS.get(key) : null;
    }

    /**
     * The translation key for a TFC size, so tooltips show the same wording the rest of the pack
     * uses. TFC ships {@code tfc.enum.size.<name>} for every constant, including zh_cn.
     *
     * <p>{@link Size#name} is the lower-case constant name, which is exactly the key suffix.
     */
    public static String sizeTranslationKey(Size size) {
        return "tfc.enum.size." + (size == null ? Size.NORMAL.name : size.name);
    }

    /**
     * True when the candidate must be rejected by the given container.
     *
     * <p>Two rules apply: a container from another mod is never nestable, and a limited container
     * enforces its TFC size ceiling. Both are checked here so every injected call site keeps a
     * single decision point.
     */
    public static boolean blocks(ItemStack container, ItemStack candidate) {
        if (isNestedContainer(candidate)) {
            return true;
        }
        Size limit = limitFor(container);
        return limit != null && !allows(candidate, limit);
    }

    /** True when this stack is a container that must not be nested. */
    public static boolean isNestedContainer(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return key != null && NESTED_CONTAINERS.contains(key);
    }


    /**
     * Returns the item that owns one slot of a merged clothing handler.
     *
     * <p>A clothing handler merges the slots of every equipped provider of one storage area, so the
     * panel can hold the slots of a limited jacket and an unlimited vest at the same time. The
     * per-slot owner is needed to apply the limit to the right slots only; the handler publishes it
     * in the {@code sourceStacks} array it already builds during its layout pass.
     */
    public static ItemStack ownerAt(Object handler, int slot) {
        if (handler == null || slot < 0) {
            return ItemStack.EMPTY;
        }
        Object value = readObjectField(handler, SOURCE_STACKS_FIELD);
        if (!(value instanceof ItemStack[] owners) || slot >= owners.length) {
            return ItemStack.EMPTY;
        }
        ItemStack owner = owners[slot];
        return owner == null ? ItemStack.EMPTY : owner;
    }
    /**
     * Resolves the owning container stack from a menu slot, a dynamic handler, or a raw inventory.
     * Returns {@link ItemStack#EMPTY} when the shape is unknown, which makes call sites fail open.
     */
    public static ItemStack containerOf(Object holder) {
        if (holder == null) {
            return ItemStack.EMPTY;
        }

        for (String getter : new String[]{BACKPACK_GETTER, CLOTHING_GETTER}) {
            ItemStack direct = readGetter(holder, getter);
            if (direct != null && !direct.isEmpty()) {
                return direct;
            }
        }
        ItemStack field = readField(holder, BACKPACK_FIELD);
        if (field != null && !field.isEmpty()) {
            return field;
        }

        // A menu slot owns no container reference of its own; unwrap its handler once.
        Object inner = callGetter(holder, HANDLER_GETTER);
        if (inner != null && inner != holder) {
            return containerOf(inner);
        }
        return ItemStack.EMPTY;
    }

    /** Returns the getter result, or {@code null} when the holder has no such getter. */
    private static ItemStack readGetter(Object target, String name) {
        Method getter = method(target.getClass(), name);
        if (getter == null) {
            return null;
        }
        Object value = invoke(getter, target);
        return value instanceof ItemStack stack ? stack : null;
    }

    private static Object callGetter(Object target, String name) {
        Method getter = method(target.getClass(), name);
        return getter == null ? null : invoke(getter, target);
    }

    /** Returns the field value, or {@code null} when the holder has no such field. */
    private static ItemStack readField(Object target, String name) {
        Object value = readObjectField(target, name);
        return value instanceof ItemStack stack ? stack : null;
    }

    /** Returns any field value, or {@code null} when the holder has no such field. */
    private static Object readObjectField(Object target, String name) {
        Field field = field(target.getClass(), name);
        if (field == null) {
            return null;
        }
        try {
            return field.get(target);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static Method method(Class<?> type, String name) {
        return METHODS.computeIfAbsent(type.getName() + '#' + name,
                key -> Optional.ofNullable(findMethod(type, name))).orElse(null);
    }

    private static Field field(Class<?> type, String name) {
        return FIELDS.computeIfAbsent(type.getName() + '#' + name,
                key -> Optional.ofNullable(findField(type, name))).orElse(null);
    }

    private static Method findMethod(Class<?> type, String name) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Method candidate : current.getDeclaredMethods()) {
                if (candidate.getName().equals(name) && candidate.getParameterCount() == 0) {
                    candidate.setAccessible(true);
                    return candidate;
                }
            }
        }
        return null;
    }

    private static Field findField(Class<?> type, String name) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                // Keep walking up the hierarchy.
            }
        }
        return null;
    }

    private static Object invoke(Method method, Object target) {
        try {
            return method.invoke(target);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }
}