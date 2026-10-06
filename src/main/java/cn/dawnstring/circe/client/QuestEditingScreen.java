package cn.dawnstring.circe.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public abstract class QuestEditingScreen extends Screen
{
    protected final Screen parent;
    protected int frameLeft;
    protected int frameTop;
    protected int frameWidth;
    protected int frameHeight;
    protected float uiScale;
    protected Component status = Component.empty();
    protected int pointerX;
    protected int pointerY;
    private final List<Label> labels = new ArrayList<>();
    private Language language;

    private record Label(String text, int x, int y)
    {
    }

    protected QuestEditingScreen(Screen parent, String title)
    {
        this(parent, Component.translatable(title));
    }

    protected QuestEditingScreen(Screen parent, Component title)
    {
        super(title);
        this.parent = parent;
    }

    @Override
    protected final void init()
    {
        language = Language.getInstance();
        uiScale = height / 540.0F;
        int viewWidth = Math.round(width / uiScale);
        frameWidth = (int) (viewWidth * 0.8);
        frameHeight = 432;
        frameLeft = (viewWidth - frameWidth) / 2;
        frameTop = 54;
        configureFrame(viewWidth);
        labels.clear();
        initEditor();
    }

    protected abstract void initEditor();

    protected void configureFrame(int viewWidth)
    {
    }

    protected void renderBackdrop(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
    }

    protected QuestEditorButton button(int x, int y, int width, String text, Runnable action)
    {
        return addRenderableWidget(new QuestEditorButton(x, y, width, Component.literal(text), ignored -> action.run()));
    }

    protected EditBox field(int x, int y, int width, String label, String value, int limit)
    {
        label(label, x, y - 12);
        EditBox field = addRenderableWidget(new QuestEditBox(font, x, y, width, Component.literal(label)));
        field.setMaxLength(limit);
        field.setValue(value);
        return field;
    }

    protected void label(String text, int x, int y)
    {
        labels.add(new Label(text, x, y));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        renderBackdrop(graphics, mouseX, mouseY, partialTick);
        pointerX = (int) (mouseX / uiScale);
        pointerY = (int) (mouseY / uiScale);
        graphics.flush();
        graphics.pose().pushPose();
        graphics.pose().scale(uiScale, uiScale, 1);
        // 属性面板需覆盖背景关系图中的物品深度。
        graphics.pose().translate(0, 0, 400);
        QuestTheme.card(graphics, frameLeft, frameTop, frameWidth, frameHeight);
        if (QuestTheme.style() == QuestTheme.Style.CLASSIC)
        {
            graphics.fill(frameLeft + 4, frameTop, frameLeft + frameWidth - 4, frameTop + 2, QuestTheme.accent());
        }
        graphics.fill(frameLeft + 1, frameTop + 31, frameLeft + frameWidth - 1, frameTop + 32, QuestTheme.border());
        graphics.fill(frameLeft + 1, frameTop + frameHeight - 54, frameLeft + frameWidth - 1, frameTop + frameHeight - 53, QuestTheme.border());
        graphics.drawString(font, title, frameLeft + 12, frameTop + 12, QuestTheme.accent(), false);
        for (Label label : labels)
        {
            graphics.drawString(font, label.text(), label.x(), label.y(), QuestTheme.muted(), false);
        }
        renderContent(graphics);
        for (var renderable : renderables)
        {
            if (!(renderable instanceof QuestDropdown))
            {
                renderable.render(graphics, pointerX, pointerY, partialTick);
            }
        }
        renderables.stream().filter(renderable -> renderable instanceof QuestDropdown)
            .forEach(renderable -> renderable.render(graphics, pointerX, pointerY, partialTick));
        int statusY = frameTop + frameHeight - 46;
        for (var line : font.split(status, frameWidth - 24))
        {
            graphics.drawString(font, line, frameLeft + 12, statusY, QuestTheme.gold(), false);
            statusY += 10;
        }
        graphics.pose().popPose();
        QuestCompletionBanner.render(graphics);
    }

    protected void renderContent(GuiGraphics graphics)
    {
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        for (var child : children())
        {
            if (child instanceof QuestDropdown dropdown && dropdown.intercept(mouseX / uiScale, mouseY / uiScale, button))
            {
                setFocused(dropdown);
                return true;
            }
        }
        return super.mouseClicked(mouseX / uiScale, mouseY / uiScale, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY)
    {
        return super.mouseDragged(mouseX / uiScale, mouseY / uiScale, button, deltaX / uiScale, deltaY / uiScale);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button)
    {
        return super.mouseReleased(mouseX / uiScale, mouseY / uiScale, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalScroll, double verticalScroll)
    {
        for (var child : children())
        {
            if (child instanceof QuestDropdown dropdown && dropdown.isOpen())
            {
                return dropdown.mouseScrolled(mouseX / uiScale, mouseY / uiScale, horizontalScroll, verticalScroll);
            }
        }
        return super.mouseScrolled(mouseX / uiScale, mouseY / uiScale, horizontalScroll, verticalScroll);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers)
    {
        for (var child : children())
        {
            if (child instanceof QuestDropdown dropdown && dropdown.isOpen())
            {
                return dropdown.keyPressed(key, scanCode, modifiers);
            }
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public void tick()
    {
        if (!ClientQuestState.canEdit())
        {
            minecraft.setScreen(new QuestScreen());
            return;
        }
        if (language != Language.getInstance())
        {
            refreshLanguage();
        }
    }

    private void refreshLanguage()
    {
        // 重建翻译后的控件时保留原始输入，包括尚未通过校验的草稿。
        List<String> inputs = new ArrayList<>();
        for (var child : children())
        {
            if (child instanceof EditBox field)
            {
                inputs.add(field.getValue());
            }
            else if (child instanceof QuestMultilineEditBox field)
            {
                inputs.add(field.getValue());
            }
        }
        rebuild();
        int index = 0;
        for (var child : children())
        {
            if (index >= inputs.size())
            {
                break;
            }
            if (child instanceof EditBox field)
            {
                field.setValue(inputs.get(index++));
            }
            else if (child instanceof QuestMultilineEditBox field)
            {
                field.setValue(inputs.get(index++));
            }
        }
    }

    protected void rebuild()
    {
        clearWidgets();
        init();
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
