package cn.dawnstring.circe.client;

import cn.dawnstring.circe.quest.ObjectiveType;
import cn.dawnstring.circe.quest.QuestDefinition;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;

import java.util.List;

public final class QuestGraphCanvas
{
    private List<QuestDefinition> definitions = List.of();
    private QuestGraphLayout layout = new QuestGraphLayout(List.of());
    private int left;
    private int top;
    private int width;
    private int height;
    private double panX;
    private double panY;
    private double zoom = 1;

    public void setBounds(int left, int top, int width, int height)
    {
        this.left = left;
        this.top = top;
        this.width = width;
        this.height = height;
    }

    public void setDefinitions(List<QuestDefinition> definitions, ResourceLocation focus)
    {
        setDefinitions(definitions, focus, true);
    }

    public void setDefinitions(List<QuestDefinition> definitions, ResourceLocation focus, boolean shouldFit)
    {
        this.definitions = List.copyOf(definitions);
        layout = new QuestGraphLayout(definitions);
        if (shouldFit)
        {
            fit(focus);
        }
    }

    public void fit(ResourceLocation focus)
    {
        zoom = Math.clamp(Math.min((width - 30.0) / layout.width(), (height - 40.0) / layout.height()), 0.6, QuestUiSettings.current().graphZoom());
        panX = (width - layout.width() * zoom) / 2 - layout.minimumX() * zoom;
        panY = (height - layout.height() * zoom) / 2 - layout.minimumY() * zoom;
        if (focus != null && layout.width() * zoom > width - 20)
        {
            var position = layout.position(focus);
            if (position != null)
            {
                panX = width / 2.0 - (position.x() + QuestGraphLayout.NODE_SIZE / 2.0) * zoom;
            }
        }
    }

    public void render(GuiGraphics graphics, Font font, ResourceLocation selected, int mouseX, int mouseY, boolean hasDetails)
    {
        graphics.fill(left, top, left + width, top + height, QuestTheme.alpha(QuestTheme.panel(), (int) (255 * QuestUiSettings.current().opacity())));
        QuestUiViewport.enableScissor(graphics, left, top, left + width, top + height);
        if (QuestUiSettings.current().hasGrid())
        {
            renderGrid(graphics);
        }
        renderEdges(graphics);
        for (QuestDefinition definition : definitions)
        {
            renderNode(graphics, font, definition, selected, mouseX, mouseY, hasDetails);
        }
        if (definitions.isEmpty())
        {
            QuestTheme.centered(graphics, font, Component.translatable("circe.screen.empty"),
                left + width / 2, top + height / 2, QuestTheme.muted());
        }
        graphics.disableScissor();
    }

    public ResourceLocation hit(double mouseX, double mouseY)
    {
        if (!contains(mouseX, mouseY))
        {
            return null;
        }
        for (QuestDefinition definition : definitions)
        {
            int x = nodeX(definition.id());
            int y = nodeY(definition.id());
            int size = nodeSize();
            if (mouseX >= x && mouseX < x + size && mouseY >= y && mouseY < y + size)
            {
                return definition.id();
            }
        }
        return null;
    }

    public boolean contains(double mouseX, double mouseY)
    {
        return mouseX >= left && mouseX < left + width && mouseY >= top && mouseY < top + height;
    }

    public void pan(double deltaX, double deltaY)
    {
        panX = Math.clamp(panX + deltaX, -(layout.minimumX() + layout.width()) * zoom, width - layout.minimumX() * zoom);
        panY = Math.clamp(panY + deltaY, -(layout.minimumY() + layout.height()) * zoom, height - layout.minimumY() * zoom);
    }

    public void revealDetails(ResourceLocation questId)
    {
        // 为左下方详情预留空间，避免边缘裁剪把详情变成脱离节点的弹窗。
        int maximumNodeX = left + Math.max(6, width - nodeSize() - 6);
        int minimumNodeX = Math.min(left + 220, maximumNodeX);
        int preferredX = Math.clamp(nodeX(questId), minimumNodeX, maximumNodeX);
        panX += preferredX - nodeX(questId);
        int preferredY = top + 42;
        panY += preferredY - nodeY(questId);
    }

    public void zoom(double amount, double mouseX, double mouseY)
    {
        double worldX = (mouseX - left - panX) / zoom;
        double worldY = (mouseY - top - panY) / zoom;
        zoom = Math.clamp(zoom * Math.pow(1.15, amount), 0.5, 2.0);
        panX = mouseX - left - worldX * zoom;
        panY = mouseY - top - worldY * zoom;
    }

    public int nodeX(ResourceLocation questId)
    {
        var position = layout.position(questId);
        return left + (int) Math.round(panX + (position == null ? 0 : position.x()) * zoom);
    }

    public int nodeY(ResourceLocation questId)
    {
        var position = layout.position(questId);
        return top + (int) Math.round(panY + (position == null ? 0 : position.y()) * zoom);
    }

    public int nodeSize()
    {
        return (int) Math.round(QuestGraphLayout.NODE_SIZE * zoom);
    }

    public QuestDefinition.GraphPosition positionAt(double mouseX, double mouseY)
    {
        return new QuestDefinition.GraphPosition(
            (int) Math.clamp(Math.round((mouseX - left - panX) / zoom), -10_000, 10_000),
            (int) Math.clamp(Math.round((mouseY - top - panY) / zoom), -10_000, 10_000));
    }

    public QuestDefinition.GraphPosition position(ResourceLocation questId)
    {
        var position = layout.position(questId);
        return position == null ? null : new QuestDefinition.GraphPosition(position.x(), position.y());
    }

    public void moveNode(ResourceLocation questId, QuestDefinition.GraphPosition position)
    {
        layout.move(questId, position);
    }

    public static ItemStack icon(QuestDefinition definition)
    {
        var held = definition.objectives().stream().filter(objective -> objective.type() == ObjectiveType.HOLD).findFirst();
        if (held.isPresent())
        {
            return new ItemStack(BuiltInRegistries.ITEM.get(held.get().target()));
        }
        var objective = definition.objectives().getFirst();
        if (objective.type() == ObjectiveType.SUBMIT)
        {
            return new ItemStack(BuiltInRegistries.ITEM.get(objective.target()));
        }
        if (objective.type() == ObjectiveType.KILL)
        {
            var egg = SpawnEggItem.byId(BuiltInRegistries.ENTITY_TYPE.get(objective.target()));
            if (egg != null)
            {
                return new ItemStack(egg);
            }
        }
        return new ItemStack(Items.BOOK);
    }

    private void renderGrid(GuiGraphics graphics)
    {
        int spacing = Math.max(12, (int) (24 * zoom));
        int offsetX = Math.floorMod((int) panX, spacing);
        int offsetY = Math.floorMod((int) panY, spacing);
        for (int x = left + offsetX; x < left + width; x += spacing)
        {
            graphics.fill(x, top, x + 1, top + height, QuestTheme.grid());
        }
        for (int y = top + offsetY; y < top + height; y += spacing)
        {
            graphics.fill(left, y, left + width, y + 1, QuestTheme.grid());
        }
    }

    private void renderEdges(GuiGraphics graphics)
    {
        int size = nodeSize();
        for (QuestDefinition definition : definitions)
        {
            for (ResourceLocation prerequisiteId : definition.prerequisites())
            {
                var prerequisite = ClientQuestState.definition(prerequisiteId);
                if (prerequisite == null || layout.position(prerequisiteId) == null)
                {
                    continue;
                }
                int sourceX = nodeX(prerequisiteId) + size / 2;
                int sourceY = nodeY(prerequisiteId) + size / 2;
                int targetX = nodeX(definition.id()) + size / 2;
                int targetY = nodeY(definition.id()) + size / 2;
                int color = ClientQuestState.progress(prerequisite).isCompleted() ? QuestTheme.accent() : QuestTheme.muted();
                renderConnection(graphics, sourceX, sourceY, targetX, targetY, size, color);
            }
        }
    }

    private void renderConnection(GuiGraphics graphics, int sourceX, int sourceY, int targetX, int targetY, int size, int color)
    {
        boolean isHorizontal = Math.abs(targetX - sourceX) >= Math.abs(targetY - sourceY);
        int direction = isHorizontal ? (targetX >= sourceX ? 1 : -1) : (targetY >= sourceY ? 1 : -1);
        if (isHorizontal)
        {
            int startX = sourceX + direction * size / 2;
            int endX = targetX - direction * size / 2;
            int elbowX = (startX + endX) / 2;
            graphics.fill(Math.min(startX, elbowX), sourceY, Math.max(startX, elbowX) + 1, sourceY + 1, color);
            graphics.fill(elbowX, Math.min(sourceY, targetY), elbowX + 1, Math.max(sourceY, targetY) + 1, color);
            graphics.fill(Math.min(elbowX, endX), targetY, Math.max(elbowX, endX) + 1, targetY + 1, color);
            for (int step = 0; step < 4; step++)
            {
                int x = endX - direction * (5 - step);
                graphics.fill(x, targetY - 3 + step, x + 1, targetY + 4 - step, color);
            }
            return;
        }
        int startY = sourceY + direction * size / 2;
        int endY = targetY - direction * size / 2;
        int elbowY = (startY + endY) / 2;
        graphics.fill(sourceX, Math.min(startY, elbowY), sourceX + 1, Math.max(startY, elbowY) + 1, color);
        graphics.fill(Math.min(sourceX, targetX), elbowY, Math.max(sourceX, targetX) + 1, elbowY + 1, color);
        graphics.fill(targetX, Math.min(elbowY, endY), targetX + 1, Math.max(elbowY, endY) + 1, color);
        for (int step = 0; step < 4; step++)
        {
            int y = endY - direction * (5 - step);
            graphics.fill(targetX - 3 + step, y, targetX + 4 - step, y + 1, color);
        }
    }

    private void renderNode(
        GuiGraphics graphics,
        Font font,
        QuestDefinition definition,
        ResourceLocation selected,
        int mouseX,
        int mouseY,
        boolean hasDetails)
    {
        int x = nodeX(definition.id());
        int y = nodeY(definition.id());
        int drawnSize = nodeSize();
        int size = QuestGraphLayout.NODE_SIZE;
        boolean isSelected = definition.id().equals(selected);
        boolean isHovered = mouseX >= x && mouseX < x + drawnSize && mouseY >= y && mouseY < y + drawnSize;
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale((float) zoom, (float) zoom, 1);
        var progress = ClientQuestState.progress(definition);
        int color = progress.isCompleted() ? (progress.isClaimed() ? QuestTheme.accent() : QuestTheme.gold())
            : ClientQuestState.isUnlocked(definition) ? QuestTheme.muted() : QuestTheme.locked();
        int borderColor = isSelected ? QuestTheme.accent() : isHovered ? QuestTheme.hover() : color;
        QuestTheme.fill(graphics, 0, 0, size, size, borderColor);
        QuestTheme.fill(graphics, 1, 1, size - 1, size - 1, QuestTheme.panel());
        if (isSelected && QuestTheme.style() != QuestTheme.Style.CLASSIC)
        {
            QuestTheme.fill(graphics, -2, -2, size + 2, size + 2, borderColor);
            QuestTheme.fill(graphics, -1, -1, size + 1, size + 1, QuestTheme.panel());
        }
        if (isSelected && QuestTheme.style() == QuestTheme.Style.CLASSIC)
        {
            // 外圈只画细线，不让选中填充与普通边框叠加变粗。
            graphics.fill(-2, -2, size + 2, -1, borderColor);
            graphics.fill(-2, size + 1, size + 2, size + 2, borderColor);
            graphics.fill(-2, -1, -1, size + 1, borderColor);
            graphics.fill(size + 1, -1, size + 2, size + 1, borderColor);
        }
        float iconScale = (size - 10) / 16.0F;
        graphics.pose().pushPose();
        graphics.pose().translate(5, 5, 0);
        graphics.pose().scale(iconScale, iconScale, 1);
        graphics.renderItem(icon(definition), 0, 0);
        graphics.pose().popPose();
        if (progress.isCompleted())
        {
            graphics.drawString(font, "✓", size - 9, size - 9, color, false);
        }
        else if (!ClientQuestState.isUnlocked(definition))
        {
            renderLock(graphics, size - 9, size - 10);
        }
        var lines = font.split(Component.translatable(definition.title()), 88);
        // 展开详情时名称移至节点上方，避免被斜肩轮廓挡住。
        int labelY = isSelected && hasDetails ? -lines.size() * 10 - 6 : size + 6;
        for (var line : lines)
        {
            graphics.drawString(font, line, (size - font.width(line)) / 2, labelY,
                ClientQuestState.isUnlocked(definition) || progress.isCompleted() ? QuestTheme.text() : QuestTheme.muted(), false);
            labelY += 10;
        }
        graphics.pose().popPose();
    }

    private static void renderLock(GuiGraphics graphics, int x, int y)
    {
        graphics.fill(x + 1, y, x + 6, y + 5, QuestTheme.muted());
        graphics.fill(x + 2, y + 1, x + 5, y + 4, QuestTheme.panel());
        graphics.fill(x, y + 4, x + 7, y + 10, QuestTheme.muted());
        graphics.fill(x + 3, y + 6, x + 4, y + 8, QuestTheme.panel());
    }
}
