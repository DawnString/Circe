package cn.dawnstring.circe.client;

import cn.dawnstring.circe.Circe;
import cn.dawnstring.circe.network.QuestNetwork;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

@Mod(value = Circe.MODID, dist = Dist.CLIENT)
public class CirceClient
{
    private static final KeyMapping OPEN_QUESTS = new KeyMapping(
        "circe.key.open_quests", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, "circe.key.category");

    public CirceClient(IEventBus modEventBus)
    {
        QuestHudPosition.load();
        QuestUiSettings.load();
        QuestNetwork.installClientReceivers(ClientQuestState::receiveCatalog,
            ClientQuestState::receiveProgress, ClientQuestState::receiveEditResult);
        QuestNetwork.installMediaReceivers(QuestCompletionBanner::receive, ClientQuestImages::receive);
        modEventBus.addListener(CirceClient::registerKeys);
        modEventBus.addListener(CirceClient::registerHud);
        NeoForge.EVENT_BUS.addListener(CirceClient::clientTick);
        NeoForge.EVENT_BUS.addListener(CirceClient::logout);
        NeoForge.EVENT_BUS.addListener(QuestHud::renderPaused);
    }

    private static void registerKeys(RegisterKeyMappingsEvent event)
    {
        event.register(OPEN_QUESTS);
    }

    private static void registerHud(RegisterGuiLayersEvent event)
    {
        event.registerAboveAll(ResourceLocation.parse("circe:quest_tracker"), QuestHud::render);
        event.registerAboveAll(ResourceLocation.parse("circe:quest_completion"), (graphics, tracker) ->
        {
            var screen = Minecraft.getInstance().screen;
            if (!(screen instanceof QuestScreen) && !(screen instanceof QuestEditingScreen) && !(screen instanceof QuestSettingsScreen))
            {
                QuestCompletionBanner.render(graphics);
            }
        });
    }

    private static void clientTick(ClientTickEvent.Post event)
    {
        Minecraft minecraft = Minecraft.getInstance();
        while (OPEN_QUESTS.consumeClick())
        {
            if (minecraft.player != null && minecraft.screen == null)
            {
                minecraft.setScreen(new QuestScreen());
            }
        }
    }

    private static void logout(ClientPlayerNetworkEvent.LoggingOut event)
    {
        ClientQuestState.clear();
        ClientQuestImages.clear();
        QuestCompletionBanner.clear();
    }
}
