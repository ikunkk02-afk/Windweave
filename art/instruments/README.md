# Windweave 0.5.1 仪器模型

四个 `.bbmodel` 是在 Blockbench 5.2.2 中创建、贴图、验证并保存的可编辑源文件；同名 PNG 是 Blockbench 实际渲染的预览。

- `anemometer`：四杯风速仪。`rotor/` 开头的元素随风速旋转，其余元素固定。
- `wind_vane`：风向标。`head/` 开头的元素随风向旋转，红色方位标记固定。
- `wind_controller`：风场改变器。
- `wind_meter`：带开放式八边形传感器的手持测风仪。表盘是静态美术图案；右键在聊天中读取实际风况。

使用原创 32×32 像素材质，资源位于 `src/main/resources/assets/windweave/textures/block/instruments/`。模型不依赖 GeckoLib 或额外资源包。

在 Blockbench 中修改后，将完整模型导出到对应的 `models/item/<name>.json`，再运行 `python scripts/finalize-instrument-models.py`。该脚本会修正游戏纹理路径，并将完整仪器拆分成静态方块与独立的旋转模型。不要直接将完整风速仪或风向标导出为放置方块，否则会重复显示转动部件。

`generate-instrument-designs.py` 保留原创几何和材质的设计参数；`*.design.json` 是建模规格。后续在 Blockbench 中手动修改时，以 `.bbmodel` 为准。
