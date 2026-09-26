    // ===== 素色背包（Wildfires）=====
    // 定位：不用染料的便宜基础版，格数与同家族完全一致，可以用染料染成家族内任意颜色。
    //
    // 用料原则：把原配方里的染料槽换成皮革类材料，并让基础材料降一级
    // （bound_leather_strip -> leather_strip），这样每个配方都与同家族的现有配方不同，
    // 不会出现两个同形状配方导致产出随机的问题。
    //
    // 注意：本段必须放在 inheritSmallBackpackNbt 定义（登山包那段之前）之后，
    // 因为登山包配方要用它。

    // 挎包：satchel 家族已有 satchel_brown（皮革色、不用染料），所以这里刻意用更原始的
    // leather_strip 而不是 bound_leather_strip，保证两条配方可区分。
    event.shaped("wildfires:satchel_plain", [' ab', 'c c', ' d '], {
        a: "sns:leather_strip",
        b: '#tfc:sewing_needles',
        c: '#forge:leather',
        d: "sns:unfinished_leather_sack",
    }).damageIngredient('#tfc:sewing_needles', 32);//素色挎包

    // 小背包：原配方 a 槽是黑色染料，这里换成皮革
    event.shaped("wildfires:small_backpack_plain", ['abc', 'dbd', 'efe'], {
        a: '#forge:leather',
        b: "sns:unfinished_leather_sack",
        c: '#tfc:sewing_needles',
        d: "sns:bound_leather_strip",
        e: '#forge:leather',
        f: "kubejs:hardened_leather"
    }).damageIngredient('#tfc:sewing_needles', 65);//素色小背包

    // 行李袋：原配方 d 槽是黑色染料，这里换成皮革
    event.shaped("wildfires:duffel_bag_plain", ['ab ', 'cdc', 'fff'], {
        a: "sns:buckle",
        b: "sns:bound_leather_strip",
        c: '#forge:leather',
        d: '#forge:leather',
        f: "kubejs:hardened_leather"
    }).damageIngredient('#tfc:sewing_needles', 65);//素色行李袋

    // 军用背包：原配方 b 槽是绿色染料，这里换成绑扎皮条
    event.shaped("wildfires:military_backpack_plain", ['abg', 'cdc', 'efe'], {
        a: "sns:buckle",
        b: "sns:bound_leather_strip",
        c: "sns:bound_leather_strip",
        d: "sns:unfinished_leather_sack",
        e: "sns:reinforced_fabric",
        f: "tfc:metal/sheet/steel",
        g: '#tfc:sewing_needles'
    }).damageIngredient('#tfc:sewing_needles', 65);//素色军用背包

    // 登山包：以素色小背包为核心，内容物完整继承（与有色版同一套 NBT 逻辑）
    event.shaped("wildfires:hiking_backpack_plain", ['aaa', 'bcb', 'ded'], {
        a: '#tfc:high_quality_cloth',
        b: "sns:buckle",
        c: "wildfires:small_backpack_plain",
        d: "sns:bound_leather_strip",
        e: "kubejs:hardened_leather"
    }).modifyResult(inheritSmallBackpackNbt('wildfires:small_backpack_plain'))
      .damageIngredient('#tfc:sewing_needles', 65);//素色登山包
