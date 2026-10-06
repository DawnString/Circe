package cn.dawnstring.circe.client;

import cn.dawnstring.circe.quest.ObjectiveType;
import cn.dawnstring.circe.quest.QuestDefinition;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.util.Comparator;
import java.util.List;

public final class QuestHud
{
    private static final float BASE_SCALE = 0.8F;
    private static final int CARD_WIDTH = 154;
    private static final int MAX_OBJECTIVES = 3;
    private static final QuestDefinition PREVIEW = new QuestDefinition(
        ResourceLocation.parse("circe:hud_preview"), 1, "", 0, "circe.hud.preview_title", "circe.hud.preview_description", "",
        List.of(), List.of(new QuestDefinition.Objective("preview", ObjectiveType.HOLD,
            ResourceLocation.parse("minecraft:oak_log"), 16, "circe.hud.preview_objective")), List.of());

    public record Bounds(float left, float top, float width, float height)
    {
        public boolean contains(double x, double y)
        {
            return x >= left && x <= left + width && y >= top && y <= top + height;
        }
    }

    private QuestHud()
    {
    }

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker)
    {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui || minecraft.screen != null
            || ClientQuestState.trackedQuest() == null)
        {
            return;
        }
        renderTracked(graphics);
    }

    public static void renderPaused(ScreenEvent.Render.Post event)
    {
        if (event.getScreen() instanceof PauseScreen)
        {
            // 暂停菜单会重绘模糊背景，追踪卡片需要在菜单完成绘制后显示。
            event.getGuiGraphics().pose().pushPose();
            event.getGuiGraphics().pose().translate(0, 0, 500);
            renderTracked(event.getGuiGraphics());
            event.getGuiGraphics().pose().popPose();
        }
    }

    private static void renderTracked(GuiGraphics graphics)
    {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui || ClientQuestState.trackedQuest() == null)
        {
            return;
        }
        var definition = ClientQuestState.definition(ClientQuestState.trackedQuest());
        if (definition == null)
        {
            return;
        }
        renderCard(graphics, definition, QuestHudPosition.current());
    }

    private static QuestDefinition previewDefinition()
    {
        var definition = ClientQuestState.trackedQuest() == null ? null : ClientQuestState.definition(ClientQuestState.trackedQuest());
        return definition == null ? PREVIEW : definition;
    }

    public static Bounds previewBounds(int width, int height, QuestHudPosition.Position position)
    {
        return bounds(width, height, previewDefinition(), position);
    }

    public static void renderPreview(GuiGraphics graphics, QuestHudPosition.Position position)
    {
        renderCard(graphics, previewDefinition(), position);
    }

    private static Bounds bounds(int width, int height, QuestDefinition definition, QuestHudPosition.Position position)
    {
        float scale = height / 540.0F * BASE_SCALE * (float) QuestUiSettings.current().hudScale();
        int objectives = Math.min(MAX_OBJECTIVES, definition.objectives().size());
        float cardHeight = ((QuestUiSettings.current().hasHudDescription() ? 38 : 27) + objectives * 11 + (definition.objectives().size() > MAX_OBJECTIVES ? 11 : 0)) * scale;
        float cardWidth = CARD_WIDTH * scale;
        float margin = 4 * scale;
        return new Bounds(margin + (float) position.x() * Math.max(0, width - cardWidth - 2 * margin),
            margin + (float) position.y() * Math.max(0, height - cardHeight - 2 * margin), cardWidth, cardHeight);
    }

    public static QuestHudPosition.Position positionAt(int width, int height, float left, float top)
    {
        Bounds dimensions = previewBounds(width, height, QuestHudPosition.DEFAULT);
        float margin = 4 * height / 540.0F * BASE_SCALE * (float) QuestUiSettings.current().hudScale();
        double x = (left - margin) / Math.max(1, width - dimensions.width() - 2 * margin);
        double y = (top - margin) / Math.max(1, height - dimensions.height() - 2 * margin);
        return new QuestHudPosition.Position(Math.clamp(x, 0, 1), Math.clamp(y, 0, 1));
    }

    private static void renderCard(GuiGraphics graphics, QuestDefinition definition, QuestHudPosition.Position position)
    {
        var font = Minecraft.getInstance().font;
        var progress = ClientQuestState.progress(definition);
        var objectives = definition.objectives().stream()
            .sorted(Comparator.comparing(objective -> progress.count(objective.id()) >= objective.count()))
            .limit(MAX_OBJECTIVES).toList();
        boolean hasMore = definition.objectives().size() > objectives.size();
        float scale = graphics.guiHeight() / 540.0F * BASE_SCALE * (float) QuestUiSettings.current().hudScale();
        Bounds bounds = bounds(graphics.guiWidth(), graphics.guiHeight(), definition, position);
        int height = Math.round(bounds.height() / scale);
        int left = Math.round(bounds.left() / scale);
        int top = Math.round(bounds.top() / scale);
        graphics.pose().pushPose();
        graphics.pose().scale(scale, scale, 1);
        QuestTheme.fill(graphics, left, top, left + CARD_WIDTH, top + height, QuestTheme.alpha(QuestTheme.background(), (int) (230 * QuestUiSettings.current().opacity())));
        graphics.fill(left + CARD_WIDTH - 2, top, left + CARD_WIDTH, top + height,
            ClientQuestState.hasRecentProgressChange() ? QuestTheme.gold() : QuestTheme.accent());
        graphics.drawString(font, QuestTheme.fit(font, Component.translatable(definition.title()), CARD_WIDTH - 16),
            left + 6, top + 6, QuestTheme.accent(), false);
        if (QuestUiSettings.current().hasHudDescription())
        {
            graphics.drawString(font, QuestTheme.fit(font, Component.literal(QuestRichText.plain(definition.description())), CARD_WIDTH - 16),
                left + 6, top + 17, QuestTheme.muted(), false);
        }
        int cursorY = top + (QuestUiSettings.current().hasHudDescription() ? 30 : 19);
        for (QuestDefinition.Objective objective : objectives)
        {
            int count = progress.count(objective.id());
            String counter = QuestUiSettings.current().hasHudCounters() ? cn.dawnstring.circe.quest.QuestStatistics.counter(objective, count) : "";
            Component label = Component.literal(count >= objective.count() ? "✓ " : "□ ")
                .append(Component.translatable(objective.title()));
            graphics.drawString(font, QuestTheme.fit(font, label, CARD_WIDTH - font.width(counter) - 21),
                left + 6, cursorY, count >= objective.count() ? QuestTheme.accent() : QuestTheme.text(), false);
            graphics.drawString(font, counter, left + CARD_WIDTH - 8 - font.width(counter), cursorY, QuestTheme.muted(), false);
            cursorY += 11;
        }
        if (hasMore)
        {
            graphics.drawString(font, QuestTheme.fit(font, Component.translatable("circe.hud.more_compact",
                definition.objectives().size() - objectives.size()), CARD_WIDTH - 16),
                left + 6, cursorY, QuestTheme.muted(), false);
        }
        if (progress.isCompleted() && !progress.isClaimed())
        {
            graphics.drawString(font, "!", left + CARD_WIDTH - 10, top + 6, QuestTheme.gold(), false);
        }
        graphics.pose().popPose();
    }
}
