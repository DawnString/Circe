package cn.dawnstring.circe.client;

import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.MultilineTextField;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class QuestMultilineEditBox extends MultiLineEditBox
{
    private final Font textFont;
    private final Component placeholder;
    private final ThemeTextField editor;
    private int characterLimit;

    private record Span(int beginIndex, int endIndex)
    {
    }

    private static final class ThemeTextField extends MultilineTextField
    {
        private ThemeTextField(Font font, int width)
        {
            super(font, width);
        }

        private Span selection()
        {
            var selected = getSelected();
            return new Span(selected.beginIndex(), selected.endIndex());
        }

        private List<Span> lines()
        {
            List<Span> lines = new ArrayList<>();
            for (var line : iterateLines())
            {
                lines.add(new Span(line.beginIndex(), line.endIndex()));
            }
            return lines;
        }
    }

    public QuestMultilineEditBox(Font font, int x, int y, int width, int height, Component placeholder, Component message)
    {
        super(font, x, y, width, height, placeholder, message);
        this.textFont = font;
        this.placeholder = placeholder;
        editor = new ThemeTextField(font, width - totalInnerPadding());
        editor.setCursorListener(this::scrollToCursor);
    }

    @Override
    public void setCharacterLimit(int limit)
    {
        characterLimit = limit;
        editor.setCharacterLimit(limit);
    }

    @Override
    public void setValueListener(Consumer<String> listener)
    {
        editor.setValueListener(listener);
    }

    @Override
    public void setValue(String value)
    {
        editor.setValue(value);
    }

    @Override
    public String getValue()
    {
        return editor.value();
    }

    @Override
    public int getInnerHeight()
    {
        return editor == null ? 0 : editor.getLineCount() * textFont.lineHeight;
    }

    @Override
    protected boolean scrollbarVisible()
    {
        return getInnerHeight() > getHeight() - totalInnerPadding();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        if (withinContentAreaPoint(mouseX, mouseY) && button == 0)
        {
            editor.setSelecting(Screen.hasShiftDown());
            seekCursor(mouseX, mouseY);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY)
    {
        if (withinContentAreaPoint(mouseX, mouseY) && button == 0)
        {
            editor.setSelecting(true);
            seekCursor(mouseX, mouseY);
            editor.setSelecting(Screen.hasShiftDown());
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers)
    {
        return editor.keyPressed(keyCode);
    }

    @Override
    public boolean charTyped(char character, int modifiers)
    {
        if (!visible || !isFocused() || !StringUtil.isAllowedChatCharacter(character))
        {
            return false;
        }
        editor.insertText(Character.toString(character));
        return true;
    }

    private void seekCursor(double mouseX, double mouseY)
    {
        editor.seekCursorToPoint(mouseX - getX() - innerPadding(), mouseY - getY() - innerPadding() + scrollAmount());
    }

    private void scrollToCursor()
    {
        int cursorTop = editor.getLineAtCursor() * textFont.lineHeight;
        int availableHeight = getHeight() - totalInnerPadding();
        if (cursorTop < scrollAmount())
        {
            setScrollAmount(cursorTop);
        }
        else if (cursorTop + textFont.lineHeight > scrollAmount() + availableHeight)
        {
            setScrollAmount(cursorTop + textFont.lineHeight - availableHeight);
        }
    }

    @Override
    protected void renderBorder(GuiGraphics graphics, int x, int y, int width, int height)
    {
        QuestTheme.fill(graphics, x, y, x + width, y + height, isFocused() ? QuestTheme.accent() : QuestTheme.border());
        QuestTheme.fill(graphics, x + 1, y + 1, x + width - 1, y + height - 1, QuestTheme.background());
    }

    @Override
    protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        String value = editor.value();
        int x = getX() + innerPadding();
        int y = getY() + innerPadding();
        if (value.isEmpty() && !isFocused())
        {
            for (var line : textFont.split(placeholder, getWidth() - totalInnerPadding()))
            {
                graphics.drawString(textFont, line, x, y, QuestTheme.muted(), false);
                y += textFont.lineHeight;
            }
            return;
        }
        var selection = editor.selection();
        boolean hasCursor = isFocused() && Util.getMillis() / 300 % 2 == 0;
        for (var line : editor.lines())
        {
            if (editor.hasSelection())
            {
                int start = Math.max(selection.beginIndex(), line.beginIndex());
                int end = Math.min(selection.endIndex(), line.endIndex());
                if (end > start)
                {
                    int selectionX = x + textFont.width(value.substring(line.beginIndex(), start));
                    int selectionEnd = x + textFont.width(value.substring(line.beginIndex(), end));
                    graphics.fill(selectionX, y, selectionEnd, y + textFont.lineHeight, QuestTheme.selected());
                }
            }
            graphics.drawString(textFont, value.substring(line.beginIndex(), line.endIndex()), x, y, QuestTheme.text(), false);
            if (hasCursor && editor.cursor() >= line.beginIndex() && editor.cursor() <= line.endIndex())
            {
                int cursorX = x + textFont.width(value.substring(line.beginIndex(), editor.cursor()));
                graphics.fill(cursorX, y, cursorX + 1, y + textFont.lineHeight, QuestTheme.accent());
                hasCursor = false;
            }
            y += textFont.lineHeight;
        }
    }

    @Override
    protected void renderDecorations(GuiGraphics graphics)
    {
        super.renderDecorations(graphics);
        if (characterLimit > 0)
        {
            String counter = getValue().length() + "/" + characterLimit;
            graphics.drawString(textFont, counter, getX() + getWidth() - textFont.width(counter), getY() + getHeight() + 4, QuestTheme.muted(), false);
        }
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        if (!visible)
        {
            return;
        }
        renderBackground(graphics);
        // 沿用任务界面的矩阵裁剪，防止界面缩放后文字越界。
        QuestUiViewport.enableScissor(graphics, getX() + 1, getY() + 1, getX() + getWidth() - 1, getY() + getHeight() - 1);
        graphics.pose().pushPose();
        graphics.pose().translate(0, -scrollAmount(), 0);
        renderContents(graphics, mouseX, mouseY, partialTick);
        graphics.pose().popPose();
        graphics.disableScissor();
        renderDecorations(graphics);
    }
}
