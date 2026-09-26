    // ===== 素色背包染色（Wildfires）=====
    // 素色版是该家族的"未染色模板"，可染成家族内任意颜色。
    // 与既有有色染色表同样的机制：shapeless，并把源背包的内容物完整继承到成品。
    // 每个家族都包含"染成原色"一项，用于把素色版转成该家族的标准基础色，
    // 之后即可沿用现有的有色染色表继续换色。
    const plainBackpackDyes = [
        // satchel：15 格
        ['wildfires:satchel_plain', 'black', 'sa_combat:satchel_black'],
        ['wildfires:satchel_plain', 'brown', 'sa_combat:satchel_brown'],
        ['wildfires:satchel_plain', 'green', 'sa_combat:satchel_green'],
        ['wildfires:satchel_plain', 'white', 'sa_combat:satchel_white'],
        // small_backpack：18 格
        ['wildfires:small_backpack_plain', 'black', 'sa_combat:small_backpack_black'],
        ['wildfires:small_backpack_plain', 'blue', 'sa_combat:small_backpack_blue'],
        ['wildfires:small_backpack_plain', 'green', 'sa_combat:small_backpack_green'],
        ['wildfires:small_backpack_plain', 'pink', 'sa_combat:small_backpack_pink'],
        // duffel_bag：30 格
        ['wildfires:duffel_bag_plain', 'black', 'sa_combat:duffel_bag_black'],
        ['wildfires:duffel_bag_plain', 'blue', 'sa_combat:duffel_bag_blue'],
        ['wildfires:duffel_bag_plain', 'red', 'sa_combat:duffel_bag_red'],
        ['wildfires:duffel_bag_plain', 'yellow', 'sa_combat:duffel_bag_yellow'],
        // hiking_backpack：36 格
        ['wildfires:hiking_backpack_plain', 'black', 'sa_combat:hiking_backpack_black'],
        ['wildfires:hiking_backpack_plain', 'blue', 'sa_combat:hiking_backpack_blue'],
        ['wildfires:hiking_backpack_plain', 'green', 'sa_combat:hiking_backpack_green'],
        ['wildfires:hiking_backpack_plain', 'red', 'sa_combat:hiking_backpack_red'],
        // military_backpack：42 格。camo/desert 没有对应染料，保持原样不可染
        ['wildfires:military_backpack_plain', 'blue', 'sa_combat:military_backpack_blue'],
        ['wildfires:military_backpack_plain', 'green', 'sa_combat:military_backpack_green'],
    ]

    plainBackpackDyes.forEach(([fromId, color, toId]) => {
        const name = toId.replace('sa_combat:', '')
        // 独立的 id 命名空间，避免与既有配方冲突
        const recipeId = `wildfires:bags/${name}_from_plain`
        event.shapeless(Item.of(toId, 1), [
            fromId,
            `minecraft:${color}_dye`
        ])
            .modifyResult(inheritBackpackContents(fromId))
            .id(recipeId)
    })
