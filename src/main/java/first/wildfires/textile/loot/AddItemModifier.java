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

/**
 * 给生物战利品追加物品。
 *
 * <p>从 textile-2.2.3 的 {@code net.atobaazul.textile.loot.AddItemModifier} 移植。
 * JSON 形态（{@code data/textile/loot_modifiers/*.json}）：
 * <pre>
 * {
 *   "type": "textile:add_item",
 *   "conditions": [ { "condition": "forge:loot_table_id", "loot_table_id": "tfc:entities/crocodile" } ],
 *   "item": "textile:crocodile_leather"
 * }
 * </pre>
 *
 * <p>★ 2026-10-23 实测：不注册这个序列化器，34 个 loot_modifier 全部解码失败：
 * <pre>
 * Could not decode GlobalLootModifier with json id textile:crocodile_fur_add
 * </pre>
 */
public class AddItemModifier extends LootModifier
{
    public static final Codec<AddItemModifier> CODEC = RecordCodecBuilder.create(instance ->
            codecStart(instance)
                    .and(ForgeRegistries.ITEMS.getCodec().fieldOf("item").forGetter(m -> m.item))
                    .apply(instance, AddItemModifier::new));

    private final Item item;

    public AddItemModifier(LootItemCondition[] conditions, Item item)
    {
        super(conditions);
        this.item = item;
    }

    @Override
    protected @NotNull ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context)
    {
        // 与 LootModifier 基类的 conditions 判断一致（父类也会过滤，这里照原实现重复一次，语义不变）
        for (LootItemCondition condition : this.conditions)
        {
            if (!condition.test(context))
            {
                return generatedLoot;
            }
        }
        generatedLoot.add(new ItemStack(this.item));
        return generatedLoot;
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec()
    {
        return CODEC;
    }
}