package cn.dawnstring.circe.client;

import cn.dawnstring.circe.Circe;
import cn.dawnstring.circe.network.EditPayload;
import cn.dawnstring.circe.network.ActionPayload;
import cn.dawnstring.circe.quest.QuestService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.util.concurrent.CompletableFuture;

@EventBusSubscriber(modid = "circe", value = Dist.CLIENT)
public final class ThemeValidation
{
    private static int ticks;
    private static int stage;
    private static QuestScreen quests;
    private static QuestSettingsScreen settings;
    private static final ResourceLocation QUEST = ResourceLocation.parse("circe:theme_preview");

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event)
    {
        var client = Minecraft.getInstance();
        if (!Boolean.getBoolean("circe.themeValidation") || client.player == null || client.getSingleplayerServer() == null || ++ticks % 35 != 0)
        {
            return;
        }
        try
        {
            run(client);
            Circe.LOGGER.info("THEME_STAGE {}", stage++);
        }
        catch (Throwable exception)
        {
            Circe.LOGGER.error("THEME_VALIDATION_FAILED stage=" + stage, exception);
            client.stop();
        }
    }

    private static void run(Minecraft client) throws Exception
    {
        if (Boolean.getBoolean("circe.themeRestart"))
        {
            switch (stage)
            {
                case 0 ->
                {
                    check(QuestTheme.style() == QuestTheme.Style.PAPER, "persisted theme");
                    check(!QuestUiSettings.current().hasAnimations() && !QuestUiSettings.current().hasGrid(), "persisted toggles");
                    check(QuestUiSettings.current().hudScale() == 1.25, "persisted HUD scale");
                    quests = new QuestScreen(QUEST);
                    client.setScreen(quests);
                }
                case 1 ->
                {
                    button(quests, "设置");
                    settings = (QuestSettingsScreen) client.screen;
                    button(settings, "背景不透明度  100%");
                    check(QuestUiSettings.current().opacity() == 0.9, "opacity toggle");
                }
                case 2 ->
                {
                    shot(client, "themes-paper-opacity.png");
                    button(settings, "任务 HUD");
                    button(settings, "调整 HUD 位置");
                    check(client.screen instanceof QuestHudPositionScreen, "position entry");
                }
                case 3 ->
                {
                    var positionScreen = client.screen;
                    var dimensions = QuestHud.previewBounds(positionScreen.width, positionScreen.height, QuestHudPosition.current());
                    double x = dimensions.left() + 5;
                    double y = dimensions.top() + 5;
                    positionScreen.mouseClicked(x, y, 0);
                    positionScreen.mouseDragged(positionScreen.width * 0.2, positionScreen.height * 0.35, 0, 0, 0);
                    positionScreen.mouseReleased(positionScreen.width * 0.2, positionScreen.height * 0.35, 0);
                }
                case 4 ->
                {
                    shot(client, "themes-hud-position.png");
                    button(client.screen, "保存位置");
                    check(client.screen == settings && QuestHudPosition.current().x() < 0.5, "position saved and returned");
                    button(settings, "完成");
                    client.setScreen(null);
                    server(client, () ->
                    {
                        var player = client.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                        player.getInventory().add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 16));
                        QuestService.reconcile(player);
                    });
                }
                case 5 ->
                {
                    shot(client, "themes-paper-completion.png");
                    check(ClientQuestState.progress(ClientQuestState.definition(QUEST)).isCompleted(), "real completion");
                    String saved = java.nio.file.Files.readString(QuestUiSettings.path());
                    java.nio.file.Files.writeString(QuestUiSettings.path(), "{\"theme\":\"UNKNOWN\"}");
                    QuestUiSettings.load();
                    check(QuestUiSettings.current().equals(QuestUiSettings.DEFAULT), "invalid settings fallback");
                    java.nio.file.Files.writeString(QuestUiSettings.path(), saved);
                    QuestUiSettings.load();
                    check(QuestTheme.style() == QuestTheme.Style.PAPER, "valid settings restored");
                }
                case 6 ->
                {
                    Circe.LOGGER.info("THEME_RESTART_PASSED");
                    client.stop();
                }
            }
            return;
        }
        switch (stage)
        {
            case 0 -> server(client, () ->
            {
                var player = client.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                player.server.getPlayerList().op(player.getGameProfile());
                QuestService.CATALOG.edit(new EditPayload("chapter_save", "circe:theme",
                    "{\"title\":\"启程 · 主题预览\",\"order\":0,\"icon\":\"minecraft:book\"}", QuestService.CATALOG.revision()));
                QuestService.CATALOG.edit(new EditPayload("save", QUEST.toString(),
                    "{\"title\":\"启程：收集第一颗钻石\",\"description\":\"## 探索地下世界\\n找到钻石，开始你的冒险。\",\"chapter\":\"circe:theme\",\"revision\":1,\"objectives\":[{\"id\":\"diamond\",\"type\":\"circe:hold\",\"target\":\"minecraft:diamond\",\"title\":\"收集钻石\",\"count\":16}],\"rewards\":[]}", QuestService.CATALOG.revision()));
                QuestService.synchronizeAll(player);
                QuestService.handleAction(player, new ActionPayload(ActionPayload.Action.TRACK, QUEST, ""));
            });
            case 1 ->
            {
                quests = new QuestScreen(QUEST);
                client.setScreen(quests);
            }
            case 2 ->
            {
                button(quests, "设置");
                check(client.screen instanceof QuestSettingsScreen, "settings entry");
                settings = (QuestSettingsScreen) client.screen;
            }
            case 3 ->
            {
                shot(client, "themes-classic-settings.png");
                button(settings, "现代深色");
            }
            case 4 ->
            {
                check(QuestTheme.style() == QuestTheme.Style.MODERN, "modern immediate preview");
                shot(client, "themes-modern-settings.png");
                button(settings, "纸页浅色");
            }
            case 5 ->
            {
                check(QuestTheme.style() == QuestTheme.Style.PAPER, "paper immediate preview");
                shot(client, "themes-paper-settings.png");
                button(settings, "界面动画  开启");
                check(!QuestUiSettings.current().hasAnimations(), "animation toggle");
                button(settings, "任务 HUD");
                button(settings, "HUD 大小  100%");
                check(QuestUiSettings.current().hudScale() == 1.25, "HUD size");
                button(settings, "显示任务描述  开启");
                button(settings, "显示目标计数  开启");
                button(settings, "关系图");
                button(settings, "显示背景网格  开启");
                check(!QuestUiSettings.current().hasGrid(), "grid toggle");
                button(settings, "完成");
            }
            case 6 ->
            {
                shot(client, "themes-paper-graph.png");
                double scale = quests.height / 540.0;
                var graph = (QuestGraphCanvas) field(quests, "graph");
                quests.mouseClicked((graph.nodeX(QUEST) + 10) * scale, (graph.nodeY(QUEST) + 10) * scale, 0);
            }
            case 7 ->
            {
                shot(client, "themes-paper-details.png");
                client.setScreen(QuestEditorScreen.forGraph(quests, QUEST));
            }
            case 8 ->
            {
                check(client.screen instanceof QuestEditorScreen, "paper editor");
                shot(client, "themes-paper-editor.png");
                var text = client.screen.children().stream().filter(child -> child instanceof QuestEditBox)
                    .map(child -> (QuestEditBox) child).filter(box -> box.getValue().equals("启程：收集第一颗钻石")).findFirst().orElseThrow();
                text.setFocused(true);
                text.moveCursorToEnd(false);
                text.charTyped('！', 0);
                check(text.getValue().endsWith("！"), "single line typing");
                button(client.screen, "内容与图片");
            }
            case 9 ->
            {
                var text = client.screen.children().stream().filter(child -> child instanceof QuestMultilineEditBox)
                    .map(child -> (QuestMultilineEditBox) child).findFirst().orElseThrow();
                text.setFocused(true);
                text.keyPressed(269, 0, 0);
                text.charTyped('！', 0);
                check(text.getValue().endsWith("！"), "multiline typing");
                text.keyPressed(257, 0, 0);
                text.charTyped('新', 0);
                check(text.getValue().endsWith("\n新"), "multiline newline");
                shot(client, "themes-paper-text.png");
            }
            case 10 ->
            {
                client.screen.onClose();
                client.setScreen(null);
            }
            case 11 ->
            {
                shot(client, "themes-paper-hud.png");
                quests = new QuestScreen(QUEST);
                client.setScreen(quests);
            }
            case 12 ->
            {
                button(quests, "设置");
                settings = (QuestSettingsScreen) client.screen;
                button(settings, "恢复显示默认值");
                check(QuestUiSettings.current().equals(QuestUiSettings.DEFAULT), "restore defaults");
                button(settings, "纸页浅色");
                button(settings, "界面动画  开启");
                button(settings, "任务 HUD");
                button(settings, "HUD 大小  100%");
                button(settings, "关系图");
                button(settings, "显示背景网格  开启");
            }
            case 13 ->
            {
                Circe.LOGGER.info("THEME_FIRST_PASSED");
                client.stop();
            }
        }
    }

    private static Object field(Object owner, String name) throws Exception
    {
        var field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    private static void button(Screen screen, String title)
    {
        var button = screen.children().stream().filter(child -> child instanceof AbstractWidget widget && widget.getMessage().getString().equals(title))
            .map(child -> (AbstractWidget) child).findFirst().orElseThrow();
        double scale = screen.height / 540.0;
        screen.mouseClicked((button.getX() + 5) * scale, (button.getY() + 5) * scale, 0);
        screen.mouseReleased((button.getX() + 5) * scale, (button.getY() + 5) * scale, 0);
    }

    private interface ServerAction
    {
        void run() throws Exception;
    }

    private static void server(Minecraft client, ServerAction action)
    {
        CompletableFuture<Void> result = new CompletableFuture<>();
        client.getSingleplayerServer().execute(() ->
        {
            try
            {
                action.run();
                result.complete(null);
            }
            catch (Throwable exception)
            {
                result.completeExceptionally(exception);
            }
        });
        result.join();
    }

    private static void shot(Minecraft client, String name)
    {
        Screenshot.grab(client.gameDirectory, name, client.getMainRenderTarget(), ignored -> {});
    }

    private static void check(boolean condition, String message)
    {
        if (!condition)
        {
            throw new IllegalStateException(message);
        }
        Circe.LOGGER.info("THEME_CHECK {}", message);
    }
}
