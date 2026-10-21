package first.wildfires.soldering;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A perfboard part. The item itself is only a token: installing it on the board records the part
 * and leaves the stack in the player's inventory. The tooltip spells out the footprint and that
 * behaviour so a player does not have to read the page source to learn the rules.
 */
public final class CircuitComponentItem extends Item {

    private final CircuitComponent component;

    public CircuitComponentItem(Properties properties, CircuitComponent component) {
        super(properties);
        this.component = component;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip,
                                TooltipFlag flag) {
        int width = this.component.baseWidth();
        int height = this.component.baseHeight();
        if (this.component.directional() && width != height) {
            tooltip.add(Component.translatable("tooltip.wildfires.component.footprint_rotated",
                            width, height, height, width)
                    .withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.wildfires.component.footprint", width, height)
                    .withStyle(ChatFormatting.GRAY));
        }
        if (this.component.directional()) {
            tooltip.add(Component.translatable("tooltip.wildfires.component.directional")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        if (this.component.polarized()) {
            tooltip.add(Component.translatable("tooltip.wildfires.component.polarized")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        tooltip.add(Component.translatable("tooltip.wildfires.component.non_consuming")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
