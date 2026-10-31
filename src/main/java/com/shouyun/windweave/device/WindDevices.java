package com.shouyun.windweave.device;

import com.shouyun.windweave.Windweave;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.*;

public final class WindDevices {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Windweave.MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Windweave.MODID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Windweave.MODID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Windweave.MODID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Windweave.MODID);
    private static BlockBehaviour.Properties metal() { return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2).sound(SoundType.METAL).noOcclusion(); }
    public static final DeferredBlock<WindDeviceBlock> ANEMOMETER = BLOCKS.register("anemometer", () -> new WindDeviceBlock(metal(), 0));
    public static final DeferredBlock<WindDeviceBlock> VANE = BLOCKS.register("wind_vane", () -> new WindDeviceBlock(metal(), 1));
    public static final DeferredBlock<WindDeviceBlock> CONTROLLER = BLOCKS.register("wind_controller", () -> new WindDeviceBlock(metal(), 2));
    public static final DeferredItem<BlockItem> ANEMOMETER_ITEM = ITEMS.registerSimpleBlockItem(ANEMOMETER);
    public static final DeferredItem<BlockItem> VANE_ITEM = ITEMS.registerSimpleBlockItem(VANE);
    public static final DeferredItem<BlockItem> CONTROLLER_ITEM = ITEMS.registerSimpleBlockItem(CONTROLLER);
    public static final DeferredItem<Item> METER = ITEMS.register("wind_meter", () -> new WindMeterItem(new Item.Properties().stacksTo(1)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WindStationBlockEntity>> STATION = ENTITIES.register("wind_station",
            () -> BlockEntityType.Builder.of(WindStationBlockEntity::new, ANEMOMETER.get(), VANE.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WindControllerBlockEntity>> FIELD = ENTITIES.register("wind_controller",
            () -> BlockEntityType.Builder.of(WindControllerBlockEntity::new, CONTROLLER.get()).build(null));
    public static final DeferredHolder<MenuType<?>, MenuType<WindControllerMenu>> MENU = MENUS.register("wind_controller",
            () -> IMenuTypeExtension.create(WindControllerMenu::new));
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("instruments", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.windweave")).icon(() -> METER.get().getDefaultInstance())
            .displayItems((parameters, output) -> { output.accept(METER.get()); output.accept(ANEMOMETER_ITEM.get()); output.accept(VANE_ITEM.get()); output.accept(CONTROLLER_ITEM.get()); }).build());
    public static void register(IEventBus bus) { BLOCKS.register(bus); ITEMS.register(bus); ENTITIES.register(bus); MENUS.register(bus); TABS.register(bus); }
    private WindDevices() {}
}
