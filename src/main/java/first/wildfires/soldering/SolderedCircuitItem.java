package first.wildfires.soldering;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The completed board. Its functionality is defined by the composition written into
 * {@link CircuitBoard#TAG}; this item does not simulate a circuit, it preserves what was built and
 * shows a short summary in the tooltip.
 *
 * <p>A board with both an {@code ic_chip} and a {@code switch} is labelled a logic board. That is a
 * presentation rule only, not an electrical check.
 */
public final class SolderedCircuitItem extends Item {

    public SolderedCircuitItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip,
                                TooltipFlag flag) {
        CompoundTag root = stack.getTagElement(CircuitBoard.TAG);
        if (root == null) {
            return;
        }
        ListTag components = root.getList("Components", Tag.TAG_COMPOUND);
        int wires = root.getIntArray("Wires").length;

        if (components.isEmpty()) {
            tooltip.add(Component.translatable("tooltip.wildfires.circuit.empty")
                    .withStyle(ChatFormatting.GRAY));
        } else {
            Map<String, Integer> counts = new LinkedHashMap<>();
            boolean hasChip = false;
            boolean hasSwitch = false;
            for (int i = 0; i < components.size(); i++) {
                CompoundTag component = components.getCompound(i);
                String type = component.getString("Type");
                counts.merge(type, 1, Integer::sum);
                hasChip |= "ic_chip".equals(type);
                hasSwitch |= "switch".equals(type);
            }
            StringBuilder names = new StringBuilder();
            for (Map.Entry<String, Integer> entry : counts.entrySet()) {
                if (names.length() > 0) {
                    names.append(" / ");
                }
                names.append(Component.translatable("item.wildfires." + entry.getKey()).getString())
                        .append(" x").append(entry.getValue());
            }
            tooltip.add(Component.translatable("tooltip.wildfires.circuit.components", names.toString())
                    .withStyle(ChatFormatting.GRAY));
            if (hasChip && hasSwitch) {
                tooltip.add(Component.translatable("tooltip.wildfires.circuit.logic")
                        .withStyle(ChatFormatting.GOLD));
            }
        }
        tooltip.add(Component.translatable("tooltip.wildfires.circuit.wires", wires)
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
