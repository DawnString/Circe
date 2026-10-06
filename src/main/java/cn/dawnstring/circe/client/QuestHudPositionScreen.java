package cn.dawnstring.circe.client;

import cn.dawnstring.circe.quest.QuestValidationException;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class QuestHudPositionScreen extends Screen
{
    private final Screen parent;
    private QuestHudPosition.Position position = QuestHudPosition.current();
    private boolean isDragging;
    private float dragOffsetX;
    private float dragOffsetY;
    private Component status = Component.translatable("circe.hud.position_hint");
    private float uiScale;

    public QuestHudPositionScreen(Screen parent)
    {
        super(Component.translatable("circe.hud.position"));
        this.parent = parent;
    }

    @Override
    protected void init()
    {
        isDragging = false;
        uiScale = height / 540.0F;
        int center = Math.round(width / uiScale) / 2;
        addRenderableWidget(new QuestEditorButton(center - 126, 494, 76, Component.translatable("circe.button.save_position"), button -> save()).primary());
        addRenderableWidget(new QuestEditorButton(center - 42, 494, 76, Component.translatable("circe.button.reset"), button -> position = QuestHudPosition.DEFAULT));
        addRenderableWidget(new QuestEditorButton(center + 42, 494, 76, Component.translatable("circe.button.cancel"), button -> onClose()));
    }

    private void save()
    {
        try
        {
            QuestHudPosition.save(position);
            onClose();
        }
        catch (Exception exception)
        {
            status = Component.translatable("circe.error.save", QuestValidationException.message(exception));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        QuestHud.renderPreview(graphics, position);
        graphics.pose().pushPose();
        graphics.pose().scale(uiScale, uiScale, 1);
        int center = Math.round(width / uiScale) / 2;
        graphics.fill(center - 180, 12, center + 180, 46, QuestTheme.surface());
        QuestTheme.centered(graphics, font, title, center, 18, QuestTheme.accent());
        QuestTheme.centered(graphics, font, status, center, 32, QuestTheme.muted());
        for (var renderable : renderables)
        {
            renderable.render(graphics, (int) (mouseX / uiScale), (int) (mouseY / uiScale), partialTick);
        }
        graphics.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        if (super.mouseClicked(mouseX / uiScale, mouseY / uiScale, button))
        {
            return true;
        }
        var bounds = QuestHud.previewBounds(width, height, position);
        if (button == 0 && bounds.contains(mouseX, mouseY))
        {
            isDragging = true;
            dragOffsetX = (float) mouseX - bounds.left();
            dragOffsetY = (float) mouseY - bounds.top();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY)
    {
        if (!isDragging || button != 0)
        {
            return false;
        }
        position = QuestHud.positionAt(width, height, (float) mouseX - dragOffsetX, (float) mouseY - dragOffsetY);
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button)
    {
        isDragging = false;
        return super.mouseReleased(mouseX / uiScale, mouseY / uiScale, button);
    }

    @Override
    public void onClose()
    {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }
}
