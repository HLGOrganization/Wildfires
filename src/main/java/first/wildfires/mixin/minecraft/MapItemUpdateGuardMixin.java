package first.wildfires.mixin.minecraft;

import first.wildfires.compat.minecraft.MapChunkLoadGuard;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * <h2>地图刷新不许把服务器卡在读区块上</h2>
 * 手持地图每刻都会走 {@code MapItem#inventoryTick} → {@code MapItem#update}：这一趟按 1/16 的相位
 * 扫自己画的那 128×128 像素里的一块，每个像素换算成方块再换算成区块，然后
 * {@code level.getChunk(x, z)} 把区块读出来上色。在服务端线程上，这个读法就是"必须拿到"：
 * 区块只是被排进队列、还没送到（模拟距离小、刚传送、区块正在生成，都是这种状态）的时候，
 * 它会停在 {@code ServerChunkCache} 内部的 {@code managedBlock} 上等——等本身会结束，
 * 但它所在的那一 tick 没结束，超过 {@code max-tick-time} 就是看门狗眼里的"服务器卡死"，
 * 服务器会被直接关掉。野火这一包里就有这么一次崩溃。
 * <p>
 * 这道注入在 {@code update} 的开头问一句 {@link MapChunkLoadGuard#defers}：
 * 这一趟真的会碰到的区块是不是都已经在了。缺一个就 {@code cancel}，把这一趟推到下一 tick——
 * 地图本来就是每 16 tick 把整张纸滚动着刷一遍的，少刷一轮的代价是十六分之一张纸晚十六 tick 更新，
 * 而代价换来的是服务器不会为了它停住。
 * <p>
 * <b>为什么相位要自己往前推：</b>那个 1/16 的相位就是地图 {@code HoldingPlayer} 里的 {@code step}，
 * 原版是在这一趟开头自增的。从这里 {@code cancel} 就永远走不到那句自增，
 * 于是每一 tick 问的都是同一列像素——只要那一列上有个区块一直没加载，这张地图就再也不会刷新。
 * 所以守卫在取消前自己把 {@code step} 推到下一个相位（见
 * {@link first.wildfires.compat.minecraft.MapChunkLoadRules#nextPhase}）。
 * <p>
 * 只在"服务端 + 服务端线程 + 同一个维度 + 持有者是玩家"时才会真的拦：
 * 其余情况原版本来什么都不做，而 {@code getChunkNow} 在别的线程上一律回答 null，
 * 那些线程上问不出结果，拦下来只会变成误伤。守卫自己出错时放行并记一条日志——
 * 为了一个优化把服务器搞崩，比它要修的那个问题更糟。
 */
@Mixin(MapItem.class)
public class MapItemUpdateGuardMixin {

    /**
     * 原版 {@code update} 的入口。守卫说"缺区块"就取消这一趟；相位已经在守卫里推过了。
     */
    @Inject(
            method = "update(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/saveddata/maps/MapItemSavedData;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void wildfires$deferWhenChunksAreMissing(Level level, Entity entity, MapItemSavedData data,
            CallbackInfo ci) {
        if (MapChunkLoadGuard.defers(level, entity, data)) {
            ci.cancel();
        }
    }
}
