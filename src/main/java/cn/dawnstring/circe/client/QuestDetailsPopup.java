package cn.dawnstring.circe.client;

import cn.dawnstring.circe.network.ActionPayload;
import cn.dawnstring.circe.quest.ObjectiveType;
import cn.dawnstring.circe.quest.QuestDefinition;
import cn.dawnstring.circe.quest.RewardType;
import net.minecraft.client.Minecraft;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

public final class QuestDetailsPopup
{
    private final QuestButton trackButton;
    private final QuestButton submitButton;
    private final QuestButton claimButton;
    private final QuestButton closeButton;
    private ResourceLocation questId;
    private int left;
    private int top;
    private int width;
    private int height;
    private int scroll;
    private int contentHeight;
    private long openedAt;

    public QuestDetailsPopup()
    {
        trackButton = new QuestButton(0, 0, 60, Component.translatable("circe.button.track_short"),
            button -> send(ActionPayload.Action.TRACK, ""));
        submitButton = new QuestButton(0, 0, 60, Component.translatable("circe.button.submit_short"), button ->
        {
            var objective = nextSubmission();
            if (objective != null)
            {
                send(ActionPayload.Action.SUBMIT, objective.id());
            }
        });
        claimButton = new QuestButton(0, 0, 60, Component.translatable("circe.button.claim_short"),
            button -> send(ActionPayload.Action.CLAIM, ""));
        closeButton = new QuestButton(0, 0, 16, Component.literal("×"), button -> close());
        close();
    }

    public List<QuestButton> buttons()
    {
        return List.of(trackButton, submitButton, claimButton, closeButton);
    }

    public void open(ResourceLocation questId)
    {
        this.questId = questId;
        scroll = 0;
        contentHeight = 0;
        openedAt = Util.getMillis();
        update();
    }

    public void close()
    {
        questId = null;
        buttons().forEach(button -> button.visible = false);
    }

    public boolean isOpen()
    {
        return questId != null;
    }

    public float expansionScale()
    {
        if (!QuestUiSettings.current().hasAnimations())
        {
            return 1;
        }
        double progress = Math.clamp((Util.getMillis() - openedAt) / 180.0, 0, 1);
        return (float) Math.max(0.01, 1 - Math.pow(1 - progress, 3));
    }

    public ResourceLocation questId()
    {
        return questId;
    }

    public boolean contains(double mouseX, double mouseY)
    {
        return isOpen() && mouseX >= left && mouseX < left + width && mouseY >= top && mouseY < top + height;
    }

    public void position(int anchorX, int anchorY, int minimumX, int minimumY, int maximumX, int maximumY)
    {
        width = Math.min(220, maximumX - minimumX - 12);
        int availableBelow = maximumY - anchorY - 28;
        height = Math.min(260, availableBelow);
        // 优先从图标左下方展开，靠近边缘时将卡片限制在关系图内。
        left = Math.clamp(anchorX - width + 38, minimumX + 6, maximumX - width - 6);
        top = anchorY + 20;
        int buttonWidth = (width - 24) / 3;
        int footerY = top + height - 26;
        trackButton.setPosition(left + 8, footerY);
        submitButton.setPosition(left + 12 + buttonWidth, footerY);
        claimButton.setPosition(left + 16 + buttonWidth * 2, footerY);
        trackButton.setWidth(buttonWidth);
        submitButton.setWidth(buttonWidth);
        claimButton.setWidth(buttonWidth);
        closeButton.setPosition(left + width - 22, top + 5);
        closeButton.setWidth(16);
        closeButton.setHeight(16);
    }

    public void update()
    {
        QuestDefinition definition = questId == null ? null : ClientQuestState.definition(questId);
        if (definition == null)
        {
            close();
            return;
        }
        buttons().forEach(button -> button.visible = true);
        var progress = ClientQuestState.progress(definition);
        trackButton.active = ClientQuestState.isUnlocked(definition) || questId.equals(ClientQuestState.trackedQuest());
        trackButton.setMessage(Component.translatable(questId.equals(ClientQuestState.trackedQuest())
            ? "circe.button.untrack_short" : "circe.button.track_short"));
        submitButton.active = ClientQuestState.isUnlocked(definition) && !progress.isCompleted() && nextSubmission() != null;
        claimButton.active = progress.isCompleted() && !progress.isClaimed();
    }

    public void render(GuiGraphics graphics, Font font, int anchorX, int anchorY)
    {
        if (!isOpen())
        {
            return;
        }
        QuestDefinition definition = ClientQuestState.definition(questId);
        if (definition == null)
        {
            return;
        }
        QuestTheme.card(graphics, left, top, width, height);
        renderAnchor(graphics, anchorX, anchorY);
        graphics.renderItem(QuestGraphCanvas.icon(definition), left + 8, top + 6);
        graphics.drawString(font, QuestTheme.fit(font, Component.translatable(definition.title()), width - 58),
            left + 29, top + 10, QuestTheme.accent(), false);
        graphics.fill(left + 8, top + 27, left + width - 8, top + 28, QuestTheme.border());
        renderBody(graphics, font, definition);
    }

    public void scroll(double amount)
    {
        scroll = Math.clamp(scroll - (int) (amount * 20), 0, Math.max(0, contentHeight - bodyHeight()));
    }

    private void renderBody(GuiGraphics graphics, Font font, QuestDefinition definition)
    {
        int bodyTop = top + 34;
        int bodyBottom = top + height - 32;
        scroll = Math.clamp(scroll, 0, Math.max(0, contentHeight - bodyHeight()));
        QuestUiViewport.enableScissor(graphics, left + 6, bodyTop, left + width - 6, bodyBottom);
        int startY = bodyTop - scroll;
        int cursorY = wrapped(graphics, font, QuestScreen.status(definition), startY, QuestTheme.muted());
        if (!definition.image().isBlank())
        {
            cursorY += 7 + ClientQuestImages.render(graphics, definition.image(),
                left + 10, cursorY + 7, width - 24, 120);
        }
        cursorY += 7 + QuestRichText.render(graphics, font, definition.description(), left + 10,
            cursorY + 7, width - 24);
        cursorY = renderObjectives(graphics, font, definition, cursorY + 10);
        cursorY = renderRewards(graphics, font, definition, cursorY + 10);
        cursorY = renderPrerequisites(graphics, font, definition, cursorY + 10);
        contentHeight = cursorY - startY + 4;
        graphics.disableScissor();
        if (contentHeight > bodyHeight())
        {
            int thumbHeight = Math.max(10, bodyHeight() * bodyHeight() / contentHeight);
            int thumbTop = bodyTop + scroll * (bodyHeight() - thumbHeight) / (contentHeight - bodyHeight());
            graphics.fill(left + width - 4, bodyTop, left + width - 2, bodyBottom, QuestTheme.panel());
            graphics.fill(left + width - 4, thumbTop, left + width - 2, thumbTop + thumbHeight, QuestTheme.accent());
        }
    }

    private int renderObjectives(GuiGraphics graphics, Font font, QuestDefinition definition, int cursorY)
    {
        cursorY = wrapped(graphics, font, Component.translatable("circe.screen.objectives"), cursorY, QuestTheme.muted());
        var progress = ClientQuestState.progress(definition);
        for (var objective : definition.objectives())
        {
            boolean isCompleted = progress.count(objective.id()) >= objective.count();
            Component label = Component.literal(isCompleted ? "✓ " : "□ ")
                .append(Component.translatable(objective.title()));
            String counter = cn.dawnstring.circe.quest.QuestStatistics.counter(objective, progress.count(objective.id()));
            cursorY += 6;
            int counterWidth = font.width(counter);
            int labelY = cursorY;
            for (var line : font.split(label, Math.max(20, width - counterWidth - 34)))
            {
                graphics.drawString(font, line, left + 10, cursorY,
                    isCompleted ? QuestTheme.accent() : QuestTheme.text(), false);
                cursorY += 11;
            }
            graphics.drawString(font, counter, left + width - 10 - counterWidth, labelY, QuestTheme.text(), false);
        }
        var submission = nextSubmission();
        if (submission != null && ClientQuestState.isUnlocked(definition))
        {
            cursorY = wrapped(graphics, font, Component.translatable("circe.screen.submission_hint",
                Component.translatable(submission.title())), cursorY + 6, QuestTheme.muted());
        }
        return cursorY;
    }

    private int renderRewards(GuiGraphics graphics, Font font, QuestDefinition definition, int cursorY)
    {
        if (definition.rewards().isEmpty())
        {
            return cursorY;
        }
        cursorY = wrapped(graphics, font, Component.translatable("circe.screen.rewards"), cursorY, QuestTheme.muted());
        for (var reward : definition.rewards())
        {
            ItemStack stack = reward.type().hasItem()
                ? RewardType.stack(reward, Minecraft.getInstance().level.registryAccess())
                : new ItemStack(Items.EXPERIENCE_BOTTLE);
            graphics.renderItem(stack, left + 9, cursorY + 5);
            Component rewardLabel = reward.type().hasItem()
                ? stack.getHoverName().copy().append(" ×" + reward.count())
                : Component.literal(reward.type().title() + " +" + reward.count());
            String label = QuestTheme.fit(font, rewardLabel, width - 44);
            graphics.drawString(font, label, left + 30, cursorY + 9, QuestTheme.gold(), false);
            cursorY += 23;
        }
        return cursorY;
    }

    private int renderPrerequisites(GuiGraphics graphics, Font font, QuestDefinition definition, int cursorY)
    {
        if (definition.prerequisites().isEmpty())
        {
            return cursorY;
        }
        cursorY = wrapped(graphics, font, Component.translatable("circe.screen.prerequisites"), cursorY, QuestTheme.muted());
        for (ResourceLocation prerequisiteId : definition.prerequisites())
        {
            var prerequisite = ClientQuestState.definition(prerequisiteId);
            if (prerequisite != null)
            {
                boolean isCompleted = ClientQuestState.progress(prerequisite).isCompleted();
                cursorY = wrapped(graphics, font, Component.literal(isCompleted ? "✓ " : "□ ")
                    .append(Component.translatable(prerequisite.title())), cursorY + 5,
                    isCompleted ? QuestTheme.accent() : QuestTheme.muted());
            }
        }
        return cursorY;
    }

    private int wrapped(GuiGraphics graphics, Font font, Component text, int y, int color)
    {
        for (var line : font.split(text, Math.max(20, width - 24)))
        {
            graphics.drawString(font, line, left + 10, y, color, false);
            y += 11;
        }
        return y;
    }

    private int bodyHeight()
    {
        return Math.max(1, height - 66);
    }

    private QuestDefinition.Objective nextSubmission()
    {
        QuestDefinition definition = questId == null ? null : ClientQuestState.definition(questId);
        if (definition == null)
        {
            return null;
        }
        var progress = ClientQuestState.progress(definition);
        return definition.objectives().stream()
            .filter(objective -> objective.type() == ObjectiveType.SUBMIT && progress.count(objective.id()) < objective.count())
            .findFirst().orElse(null);
    }

    private void send(ActionPayload.Action action, String objectiveId)
    {
        if (questId != null)
        {
            PacketDistributor.sendToServer(new ActionPayload(action, questId, objectiveId));
        }
    }

    private void renderAnchor(GuiGraphics graphics, int anchorX, int anchorY)
    {
        // 填充斜肩与卡片共用轮廓，节点下沿直接延伸到详情，而不是独立连线。
        int neckRight = anchorX + 14;
        for (int y = anchorY; y <= top; y++)
        {
            int neckLeft = anchorX - (y - anchorY);
            graphics.fill(neckLeft, y, neckRight, y + 1, QuestTheme.surface());
            graphics.fill(neckLeft, y, neckLeft + 1, y + 1, QuestTheme.border());
            graphics.fill(neckRight - 1, y, neckRight, y + 1, QuestTheme.border());
        }
    }
}
