package first.wildfires.textile.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;

/**
 * 从生物战利品里移除指定物品（例如用毛皮替换掉原版的皮革）。
 *
 * <p>从 textile-2.2.3 的 {@code net.atobaazul.textile.loot.RemoveItemModifier} 移植。
 * JSON 形态：
 * <pre>
 * {
 *   "type": "textile:remove_item",
 *   "conditions": [ { "condition": "forge:loot_table_id", "loot_table_id": "tfc:entities/lion" } ],
 *   "item": "tfc:medium_raw_hide"
 * }
 * </pre>
 */
public class RemoveItemModifier extends LootModifier
{
    public static final Codec<RemoveItemModifier> CODEC = RecordCodecBuilder.create(instance ->
            codecStart(instance)
                    .and(ForgeRegistries.ITEMS.getCodec().fieldOf("item").forGetter(m -> m.item))
                    .apply(instance, RemoveItemModifier::new));

    private final Item item;

    public RemoveItemModifier(LootItemCondition[] conditions, Item item)
    {
        super(conditions);
        this.item = item;
    }

    @Override
    protected @NotNull ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context)
    {
        for (LootItemCondition condition : this.conditions)
        {
            if (!condition.test(context))
            {
                return generatedLoot;
            }
        }
        final ItemStack target = new ItemStack(this.item);
        final Iterator<ItemStack> it = generatedLoot.iterator();
        while (it.hasNext())
        {
            final ItemStack stack = it.next();
            if (ItemStack.isSameItem(stack, target))
            {
                it.remove();
            }
        }
        return generatedLoot;
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec()
    {
        return CODEC;
    }
}