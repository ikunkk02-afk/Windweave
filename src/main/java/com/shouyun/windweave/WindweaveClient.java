package com.shouyun.windweave;

import com.shouyun.windweave.client.WindIndicatorRenderer;
import com.shouyun.windweave.client.WindFlowVisuals;
import com.shouyun.windweave.client.WindTerrainShaders;
import com.shouyun.windweave.client.WindWeather;
import com.shouyun.windweave.client.StormFog;
import com.shouyun.windweave.wind.ClientWindState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = Windweave.MODID, dist = Dist.CLIENT)
public class WindweaveClient {
    public WindweaveClient(IEventBus modBus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        modBus.addListener(this::renderers);
        modBus.addListener(com.shouyun.windweave.client.WindStationRenderer::registerModels);
        modBus.addListener(WindTerrainShaders::register);
        modBus.addListener(WindWeather::register);
        modBus.addListener(com.shouyun.windweave.client.WindSand::register);
        NeoForge.EVENT_BUS.addListener(this::logout);
        NeoForge.EVENT_BUS.addListener(WindFlowVisuals::tick);
        NeoForge.EVENT_BUS.addListener(WindFlowVisuals::render);
        NeoForge.EVENT_BUS.addListener(WindWeather::tick);
        NeoForge.EVENT_BUS.addListener(com.shouyun.windweave.client.WindSand::tick);
        NeoForge.EVENT_BUS.addListener(com.shouyun.windweave.client.WindAudio::tick);
        NeoForge.EVENT_BUS.addListener(StormFog::color);
        NeoForge.EVENT_BUS.addListener(StormFog::tick);
        NeoForge.EVENT_BUS.addListener(StormFog::render);
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) ->
                event.register(com.shouyun.windweave.device.WindDevices.MENU.get(), com.shouyun.windweave.client.WindControllerScreen::new));
    }
    private void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(Windweave.WIND_INDICATOR.get(), WindIndicatorRenderer::new);
        event.registerBlockEntityRenderer(com.shouyun.windweave.device.WindDevices.STATION.get(), com.shouyun.windweave.client.WindStationRenderer::new);
    }
    private void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientWindState.clear(); WindFlowVisuals.clear(); WindTerrainShaders.clear(); WindWeather.clear();
        com.shouyun.windweave.client.WindSand.clear(); com.shouyun.windweave.client.WindAudio.clear();
        StormFog.clear();
    }
}
