package first.wildfires.mixin.create;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.simibubi.create.content.logistics.filter.FilterItemStack;
import com.simibubi.create.content.logistics.item.filter.attribute.ItemAttribute;
import first.wildfires.api.tfc.TemperatureFilter;
import net.createmod.catnip.data.Pair;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Mixin(value = FilterItemStack.AttributeFilterItemStack.class, remap = false)
public abstract class AttributeFilterItemStackMixin {

    @Unique
    private static final String TEMPERATURE_FILTERS_KEY = "TemperatureFilters";

    @Unique
    private static final Pattern TEMPERATURE_PATTERN = Pattern.compile("^([><=]=?)\\s*(-?\\d+(?:\\.\\d+)?)$");

    @Shadow
    public FilterItemStack.AttributeFilterItemStack.WhitelistMode whitelistMode;

    @Shadow public List<Pair<ItemAttribute, Boolean>> attributeTests;

    @WrapMethod(method = "test(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;Z)Z")
    private boolean wrapTemperatureFilter(Level world, ItemStack stack, boolean matchNBT, Operation<Boolean> original) {
        List<TemperatureFilter> tempFilters = wildfires$readTemperatureFilters();
        if (!tempFilters.isEmpty()) {
            return switch (this.whitelistMode) {
                case BLACKLIST -> {
                    if (tempFilters.stream().anyMatch(filter -> filter.matches(stack))) {
                        yield false;
                    }
                    if (attributeTests.isEmpty()){
                        yield true;
                    }
                    yield original.call(world, stack, matchNBT);
                }
                case WHITELIST_CONJ -> tempFilters.stream().allMatch(filter -> filter.matches(stack)) && original.call(world, stack, matchNBT);
                case WHITELIST_DISJ -> tempFilters.stream().anyMatch(filter -> filter.matches(stack)) || original.call(world, stack, matchNBT);
            };
        }
        return original.call(world, stack, matchNBT);
    }

    @Unique
    public ItemStack wildfires$item() {
        return ((FilterItemStack.AttributeFilterItemStack) (Object) this).item();
    }

    /**
     * Reads the shared {@code TemperatureFilters} string list.
     *
     * <p>The list may hold both kinds of entry at once, because the screen keeps the player's hand-written
     * expressions even while 可加工 / 可焊接 is showing - that is what makes switching away and back, or
     * closing and reopening the screen, lose nothing. The precedence rule lives here:
     *
     * <ul>
     *   <li>a preset token ({@code workable} / {@code weldable} / {@code any}) is the row's state, and while
     *       one is present the numeric expressions are <b>inert</b> - so 可加工 plus a leftover {@code >800}
     *       can never both apply;</li>
     *   <li>with no preset token at all, only the numeric expressions are read, which is exactly the
     *       original behaviour and what every save written before this row existed looks like.</li>
     * </ul>
     *
     * <p>Anything else - including tokens written by a newer version - is skipped, so an unknown entry can
     * never break loading or judging.
     */
    @Unique
    private List<TemperatureFilter> wildfires$readTemperatureFilters() {
        ItemStack filterStack = wildfires$item();
        if (filterStack.isEmpty()) return List.of();

        CompoundTag tag = filterStack.getTag();
        if (tag == null || !tag.contains(TEMPERATURE_FILTERS_KEY)) return List.of();

        ListTag list = tag.getList(TEMPERATURE_FILTERS_KEY, net.minecraft.nbt.Tag.TAG_STRING);

        // Pass 1: is a state token present? If so it owns the row and the numbers below do not apply.
        for (int i = 0; i < list.size(); i++) {
            TemperatureFilter preset = TemperatureFilter.preset(list.getString(i));
            if (preset != null) {
                return List.of(preset);
            }
        }

        // Pass 2: no state token - read the numeric expressions, as this row always did.
        List<TemperatureFilter> filters = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            Matcher m = TEMPERATURE_PATTERN.matcher(list.getString(i));
            if (m.matches()) {
                String op = m.group(1);
                float value = Float.parseFloat(m.group(2));
                filters.add(new TemperatureFilter(op, value));
            }
        }
        return filters;
    }
}
