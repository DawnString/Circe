package cn.dawnstring.circe.client;

import cn.dawnstring.circe.quest.QuestValidationException;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.Language;
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
    private Component status = Component.empty();
    private Language language;

    public QuestSettingsScreen(QuestScreen parent)
    {
        super(Component.translatable("circe.settings.title"));
        this.parent = parent;
    }

    @Override
    protected void init()
    {
        language = Language.getInstance();
        uiScale = height / 540.0F;
        int viewWidth = Math.round(width / uiScale);
        int frameWidth = (int) (viewWidth * 0.8);
        panelWidth = Math.min(420, Math.max(360, frameWidth / 2));
        panelWidth = Math.min(panelWidth, frameWidth);
        left = (viewWidth + frameWidth) / 2 - panelWidth;
        top = 54;
        button(left + panelWidth - 30, top + 8, 22, "×", this::onClose);
        String[] tabs =
        {
            QuestTranslations.text("circe.settings.appearance"),
            QuestTranslations.text("circe.settings.hud"),
            QuestTranslations.text("circe.settings.graph")
        };
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
        button(left + 16, top + 394, 108, QuestTranslations.text("circe.button.reset_display"), () -> update(current -> QuestUiSettings.DEFAULT));
        button(left + panelWidth - 104, top + 394, 88, QuestTranslations.text("circe.button.done"), this::onClose).primary();
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
        button(left + 16, top + 288, panelWidth - 32, QuestTranslations.text("circe.settings.opacity", (int) (preferences.opacity() * 100)), () -> update(current ->
            new QuestUiSettings.Preferences(current.theme(), current.hasAnimations(), current.hasGrid(), current.hasHudDescription(),
                current.hasHudCounters(), current.hudScale(), next(current.opacity(), new double[]{1, 0.9, 0.8}), current.graphZoom())));
        button(left + 16, top + 320, panelWidth - 32, QuestTranslations.text("circe.settings.animations", enabled(preferences.hasAnimations())), () -> update(current ->
            new QuestUiSettings.Preferences(current.theme(), !current.hasAnimations(), current.hasGrid(), current.hasHudDescription(),
                current.hasHudCounters(), current.hudScale(), current.opacity(), current.graphZoom())));
    }

    private void initHud(QuestUiSettings.Preferences preferences)
    {
        button(left + 16, top + 86, panelWidth - 32, QuestTranslations.text("circe.button.hud_position"), () -> minecraft.setScreen(new QuestHudPositionScreen(this)));
        button(left + 16, top + 124, panelWidth - 32, QuestTranslations.text("circe.settings.hud_scale", (int) (preferences.hudScale() * 100)), () -> update(current ->
            new QuestUiSettings.Preferences(current.theme(), current.hasAnimations(), current.hasGrid(), current.hasHudDescription(),
                current.hasHudCounters(), next(current.hudScale(), new double[]{0.75, 1, 1.25, 1.5}), current.opacity(), current.graphZoom())));
        button(left + 16, top + 162, panelWidth - 32, QuestTranslations.text("circe.settings.description", enabled(preferences.hasHudDescription())), () -> update(current ->
            new QuestUiSettings.Preferences(current.theme(), current.hasAnimations(), current.hasGrid(), !current.hasHudDescription(),
                current.hasHudCounters(), current.hudScale(), current.opacity(), current.graphZoom())));
        button(left + 16, top + 200, panelWidth - 32, QuestTranslations.text("circe.settings.counters", enabled(preferences.hasHudCounters())), () -> update(current ->
            new QuestUiSettings.Preferences(current.theme(), current.hasAnimations(), current.hasGrid(), current.hasHudDescription(),
                !current.hasHudCounters(), current.hudScale(), current.opacity(), current.graphZoom())));
    }

    private void initGraph(QuestUiSettings.Preferences preferences)
    {
        button(left + 16, top + 86, panelWidth - 32, QuestTranslations.text("circe.settings.grid", enabled(preferences.hasGrid())), () -> update(current ->
            new QuestUiSettings.Preferences(current.theme(), current.hasAnimations(), !current.hasGrid(), current.hasHudDescription(),
                current.hasHudCounters(), current.hudScale(), current.opacity(), current.graphZoom())));
        button(left + 16, top + 124, panelWidth - 32, QuestTranslations.text("circe.settings.zoom", (int) Math.round(preferences.graphZoom() * 100)), () -> update(current ->
            new QuestUiSettings.Preferences(current.theme(), current.hasAnimations(), current.hasGrid(), current.hasHudDescription(),
                current.hasHudCounters(), current.hudScale(), current.opacity(), next(current.graphZoom(), new double[]{0.8, 1, 1.15, 1.5}))));
    }

    private static String enabled(boolean isEnabled)
    {
        return isEnabled ? QuestTranslations.text("circe.option.enabled") : QuestTranslations.text("circe.option.disabled");
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
            status = Component.translatable("circe.settings.saved");
        }
        catch (Exception exception)
        {
            status = Component.translatable("circe.error.save", QuestValidationException.message(exception));
        }
        rebuild();
    }

    private void rebuild()
    {
        clearWidgets();
        init();
    }

    @Override
    public void tick()
    {
        if (language != Language.getInstance())
        {
            rebuild();
        }
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
            String hint = page == 1 ? QuestTranslations.text("circe.settings.hud_hint") : QuestTranslations.text("circe.settings.graph_hint");
            graphics.drawString(font, QuestTheme.fit(font, Component.literal(hint), panelWidth - 32), left + 16, top + 254, QuestTheme.muted(), false);
        }
        graphics.drawString(font, QuestTheme.fit(font, status, panelWidth - 32), left + 16, top + 368, QuestTheme.muted(), false);
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
