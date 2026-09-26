# 素色背包配方 —— 插入说明

## 文件 1：01-shaped-plain-recipes.js

插入位置：`kubejs\server_scripts\recipe\shaped.js`

**必须插在** `const inheritSmallBackpackNbt = (smallId) => ...` 这个定义**之后**，
因为素色登山包的配方要用它。

推荐位置：紧跟在现有 4 条 `hiking_backpack_*` 配方（约 L137~L169）之后、
`blue_jeans` 配方（约 L171）之前。

## 文件 2：02-shapless-plain-dyes.js

插入位置：`kubejs\server_scripts\recipe\shapless.js`

**必须插在** `const inheritBackpackContents = (fromId) => ...` 定义**之后**，
且在现有 `backpackDyes.forEach(...)` 那段之后（文件末尾附近）。

文件末尾结构是：
```
    const backpackDyes = [ ... ]

    backpackDyes.forEach(([fromId, color, toId]) => {
        ...
    })
})                      <- 这一行是 ServerEvents.recipes(event => { 的收尾
```
把文件 2 的内容插在 `backpackDyes.forEach(...)` 结束之后、最后的 `})` 之前。