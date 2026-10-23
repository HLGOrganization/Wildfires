package first.wildfires.textile.loot;

import net.dries007.tfc.util.loot.CropYieldProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.providers.number.LootNumberProviderType;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import org.jetbrains.annotations.NotNull;

/**
 * 按作物实体的 {@code yield} 值在 [min, max] 之间插值的产量提供器。
 *
 * <p>从 textile-2.2.3 的 {@code net.atobaazul.textile.util.TextileCropYieldProvider} 移植。
 * 字节码逻辑：
 * <pre>
 * BlockEntity be = ctx.getParam(LootContextParams.BLOCK_ENTITY);
 * if (be instanceof TextileCropBlockEntity crop) {
 *     return Mth.lerp(crop.getYield(), min.getFloat(ctx), max.getFloat(ctx));
 * } else {
 *     return min.getFloat(ctx);   // 没有作物实体（野生/枯死）就取下限
 * }
 * </pre>
 *
 * <p>★ 2026-10-23 实测：不注册会导导致 2 个 crop loot table 解析失败：
 * <pre>
 * Couldn't parse element loot_tables:textile:blocks/crop/cotton
 * Couldn't parse element loot_tables:textile:blocks/crop/flax
 * </pre>
 * 后果是棉花/亚麻<b>收获时什么都不掉</b>。
 */
public class TextileCropYieldProvider extends CropYieldProvider
{
    public TextileCropYieldProvider(NumberProvider min, NumberProvider max)
    {
        super(min, max);
    }

    @Override
    public float getFloat(@NotNull LootContext context)
    {
        final BlockEntity entity = context.getParamOrNull(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY);
        if (entity instanceof first.wildfires.textile.crop.TextileCropBlockEntity crop)
        {
            return Mth.lerp(crop.getYield(), this.min.getFloat(context), this.max.getFloat(context));
        }
        return this.min.getFloat(context);
    }

    @Override
    public @NotNull LootNumberProviderType getType()
    {
        return TextileLoot.CROP_YIELD.get();
    }
/** 序列化器。放这里是因为 {@code min}/{@code max} 是父类 protected 字段，只有子类能读。 */
    public static final net.minecraft.world.level.storage.loot.Serializer<TextileCropYieldProvider> SERIALIZER =
            new net.minecraft.world.level.storage.loot.Serializer<TextileCropYieldProvider>()
            {
                @Override
                public void serialize(com.google.gson.JsonObject json, TextileCropYieldProvider value,
                                      com.google.gson.JsonSerializationContext ctx)
                {
                    json.add("min", ctx.serialize(value.min));
                    json.add("max", ctx.serialize(value.max));
                }

                @Override
                public TextileCropYieldProvider deserialize(com.google.gson.JsonObject json,
                                                            com.google.gson.JsonDeserializationContext ctx)
                {
                    return new TextileCropYieldProvider(
                            ctx.deserialize(json.get("min"), NumberProvider.class),
                            ctx.deserialize(json.get("max"), NumberProvider.class));
                }
            };
}