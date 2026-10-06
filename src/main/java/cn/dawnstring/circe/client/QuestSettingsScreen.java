package cn.dawnstring.circe.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.function.UnaryOperator;

public final class QuestSettingsScreen extends Screen
{
    private final QuestScreen parent;
    private float uiScale;
    private int left;
    private int top;
    private int panelWidth;
    private int page;
    private String status = "";

    public QuestSettingsScreen(QuestScreen parent)
    {
        super(Component.literal("任务界面设置"));
        this.parent = parent;
    }

    @Override
    protected void init()
    {
        uiScale = height / 540.0F;
        int viewWidth = Math.round(width / uiScale);
        int frameWidth = (int) (viewWidth * 0.8);
        panelWidth = Math.min(420, Math.max(360, frameWidth / 2));
        panelWidth = Math.min(panelWidth, frameWidth);
        left = (viewWidth + frameWidth) / 2 - panelWidth;
        top = 54;
        button(left + panelWidth - 30, top + 8, 22, "×", this::onClose);
        String[] tabs = {"外观", "任务 HUD", "关系图"};
        int tabWidth = (panelWidth - 32) / 3;
        for (int index = 0; index < tabs.length; index++)
        {
            int selectedPage = index;
            var tab = button(left + 16 + index * tabWidth, top + 40, tabWidth - 6, tabs[index], () ->
            {
                page = selectedPage;
                rebuild();
            });
            if (page == index)
            {
                tab.primary();
            }
        }
        var preferences = QuestUiSettings.current();
        switch (page)
        {
            case 0 -> initAppearance(preferences);
            case 1 -> initHud(preferences);
            default -> initGraph(preferences);
        }
        button(left + 16, top + 394, 108, "恢复显示默认值", () -> update(current -> QuestUiSettings.DEFAULT));
        button(left + panelWidth - 104, top + 394, 88, "完成", this::onClose).primary();
    }

    private void initAppearance(QuestUiSettings.Preferences preferences)
    {
        for (int index = 0; index < QuestTheme.Style.values().length; index++)
        {
            QuestTheme.Style style = QuestTheme.Style.values()[index];
            button(left + 16, top + 76 + index * 66, panelWidth - 32, style.title(), () -> update(current ->
                new QuestUiSettings.Preferences(style.name(), current.hasAnimations(), current.hasGrid(), current.hasHudDescription(),
                    current.hasHudCounters(), current.hudScale(), current.opacity(), current.graphZoom()))).setHeight(58);
        }
        button(left + 16, top + 288, panelWidth - 32, "背景不透明度  " + (int) (preferences.opacity() * 100) + "%", () -> update(current ->
            new QuestUiSettings.Preferences(current.theme(), current.hasAnimations(), current.hasGrid(), current.hasHudDescription(),
                current.hasHudCounters(), current.hudScale(), next(current.opacity(), new double[]{1, 0.9, 0.8}), current.graphZoom())));
        button(left + 16, top + 320, panelWidth - 32, "界面动画  " + enabled(preferences.hasAnimations()), () -> update(current ->
            new QuestUiSettings.Preferences(current.theme(), !current.hasAnimations(), current.hasGrid(), current.hasHudDescription(),
                current.hasHudCounters(), current.hudScale(), current.opacity(), current.graphZoom())));
    }

    private void initHud(QuestUiSettings.Preferences preferences)
    {
        button(left + 16, top + 86, panelWidth - 32, "调整 HUD 位置", () -> minecraft.setScreen(new QuestHudPositionScreen(this)));
        button(left + 16, top + 124, panelWidth - 32, "HUD 大小  " + (int) (preferences.hudScale() * 100) + "%", () -> update(current ->
            new QuestUiSettings.Preferences(current.theme(), current.hasAnimations(), current.hasGrid(), current.hasHudDescription(),
                current.hasHudCounters(), next(current.hudScale(), new double[]{0.75, 1, 1.25, 1.5}), current.opacity(), current.graphZoom())));
        button(left + 16, top + 162, panelWidth - 32, "显示任务描述  " + enabled(preferences.hasHudDescription()), () -> update(current ->
            new QuestUiSettings.Preferences(current.theme(), current.hasAnimations(), current.hasGrid(), !current.hasHudDescription(),
                current.hasHudCounters(), current.hudScale(), current.opacity(), current.graphZoom())));
        button(left + 16, top + 200, panelWidth - 32, "显示目标计数  " + enabled(preferences.hasHudCounters()), () -> update(current ->
            new QuestUiSettings.Preferences(current.theme(), current.hasAnimations(), current.hasGrid(), current.hasHudDescription(),
                !current.hasHudCounters(), current.hudScale(), current.opacity(), current.graphZoom())));
    }

    private void initGraph(QuestUiSettings.Preferences preferences)
    {
        button(left + 16, top + 86, panelWidth - 32, "显示背景网格  " + enabled(preferences.hasGrid()), () -> update(current ->
            new QuestUiSettings.Preferences(current.theme(), current.hasAnimations(), !current.hasGrid(), current.hasHudDescription(),
                current.hasHudCounters(), current.hudScale(), current.opacity(), current.graphZoom())));
        button(left + 16, top + 124, panelWidth - 32, "适配缩放上限  " + (int) Math.round(preferences.graphZoom() * 100) + "%", () -> update(current ->
            new QuestUiSettings.Preferences(current.theme(), current.hasAnimations(), current.hasGrid(), current.hasHudDescription(),
                current.hasHudCounters(), current.hudScale(), current.opacity(), next(current.graphZoom(), new double[]{0.8, 1, 1.15, 1.5}))));
    }

    private static String enabled(boolean isEnabled)
    {
        return isEnabled ? "开启" : "关闭";
    }

    private static double next(double value, double[] choices)
    {
        for (int index = 0; index < choices.length; index++)
        {
            if (Math.abs(value - choices[index]) < 0.001)
            {
                return choices[(index + 1) % choices.length];
            }
        }
        return choices[0];
    }

    private QuestEditorButton button(int x, int y, int width, String title, Runnable action)
    {
        return addRenderableWidget(new QuestEditorButton(x, y, width, Component.literal(title), ignored -> action.run()));
    }

    private void update(UnaryOperator<QuestUiSettings.Preferences> change)
    {
        try
        {
            QuestUiSettings.update(change);
            status = "已保存 · 仅对当前客户端生效";
        }
        catch (Exception exception)
        {
            status = "保存失败：" + exception.getMessage();
        }
        rebuild();
    }

    private void rebuild()
    {
        clearWidgets();
        init();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        parent.renderEditorBackground(graphics, mouseX, mouseY, partialTick, left);
        graphics.flush();
        graphics.pose().pushPose();
        graphics.pose().scale(uiScale, uiScale, 1);
        graphics.pose().translate(0, 0, 400);
        QuestTheme.card(graphics, left, top, panelWidth, 432);
        graphics.drawString(font, title, left + 16, top + 14, QuestTheme.text(), false);
        int pointerX = (int) (mouseX / uiScale);
        int pointerY = (int) (mouseY / uiScale);
        for (var renderable : renderables)
        {
            renderable.render(graphics, pointerX, pointerY, partialTick);
        }
        if (page == 0)
        {
            renderThemeCards(graphics, pointerX, pointerY);
        }
        else
        {
            String hint = page == 1 ? "位置设置提供可拖动的实际 HUD 预览。" : "修改上限后，点击关系图的「复位」应用。";
            graphics.drawString(font, QuestTheme.fit(font, Component.literal(hint), panelWidth - 32), left + 16, top + 254, QuestTheme.muted(), false);
        }
        graphics.drawString(font, QuestTheme.fit(font, Component.literal(status), panelWidth - 32), left + 16, top + 368, QuestTheme.muted(), false);
        graphics.pose().popPose();
        QuestCompletionBanner.render(graphics);
    }

    private void renderThemeCards(GuiGraphics graphics, int mouseX, int mouseY)
    {
        for (int index = 0; index < QuestTheme.Style.values().length; index++)
        {
            var style = QuestTheme.Style.values()[index];
            var palette = style.palette();
            int y = top + 76 + index * 66;
            boolean isSelected = QuestTheme.style() == style;
            boolean isHovered = mouseX >= left + 16 && mouseX < left + panelWidth - 16 && mouseY >= y && mouseY < y + 58;
            QuestTheme.fill(graphics, left + 16, y, left + panelWidth - 16, y + 58,
                isSelected ? QuestTheme.accent() : isHovered ? QuestTheme.hover() : QuestTheme.border());
            QuestTheme.fill(graphics, left + 17, y + 1, left + panelWidth - 17, y + 57, QuestTheme.panel());
            QuestTheme.rounded(graphics, left + 26, y + 10, left + 94, y + 48, palette.background(), palette.radius());
            graphics.fill(left + 32, y + 16, left + 49, y + 42, palette.selected());
            graphics.fill(left + 55, y + 19, left + 86, y + 21, palette.text());
            graphics.fill(left + 55, y + 26, left + 80, y + 27, palette.muted());
            QuestTheme.rounded(graphics, left + 55, y + 33, left + 86, y + 41, palette.accent(), 2);
            graphics.drawString(font, style.title() + (isSelected ? "  ✓" : ""), left + 106, y + 15, QuestTheme.text(), false);
            graphics.drawString(font, QuestTheme.fit(font, Component.literal(style.description()), panelWidth - 134), left + 106, y + 33, QuestTheme.muted(), false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        return super.mouseClicked(mouseX / uiScale, mouseY / uiScale, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button)
    {
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
