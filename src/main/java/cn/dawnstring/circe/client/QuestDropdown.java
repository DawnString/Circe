package cn.dawnstring.circe.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public final class QuestDropdown extends AbstractWidget
{
    public record Option(String id, String title)
    {
    }

    private final List<Option> options;
    private final Consumer<String> onSelect;
    private String selected;
    private String query = "";
    private boolean isOpen;
    private int offset;

    public QuestDropdown(int x, int y, int width, List<Option> options, String selected, Consumer<String> onSelect)
    {
        super(x, y, width, 22, Component.literal("选择类型"));
        this.options = List.copyOf(options);
        this.selected = selected;
        this.onSelect = onSelect;
    }

    public boolean isOpen()
    {
        return isOpen;
    }

    public boolean intercept(double x, double y, int button)
    {
        if (!isOpen)
        {
            return false;
        }
        mouseClicked(x, y, button);
        return true;
    }

    private List<Option> filtered()
    {
        String normalized = query.toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.title().toLowerCase(Locale.ROOT).contains(normalized)
            || option.id().toLowerCase(Locale.ROOT).contains(normalized)).toList();
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        var font = Minecraft.getInstance().font;
        QuestTheme.fill(graphics, getX(), getY(), getX() + width, getY() + height, QuestTheme.panel());
        graphics.fill(getX(), getY() + height - 1, getX() + width, getY() + height, QuestTheme.accent());
        String name = options.stream().filter(option -> option.id().equals(selected)).map(Option::title).findFirst().orElse("请选择");
        graphics.drawString(font, QuestTheme.fit(font, Component.literal(name), width - 28), getX() + 8, getY() + 7, QuestTheme.text(), false);
        graphics.drawString(font, "▾", getX() + width - 15, getY() + 7, QuestTheme.accent(), false);
        if (!isOpen)
        {
            return;
        }
        graphics.flush();
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 600);
        List<Option> choices = filtered();
        offset = Math.clamp(offset, 0, Math.max(0, choices.size() - 6));
        int top = getY() + height + 2;
        int rows = Math.min(6, choices.size());
        graphics.fill(getX(), top, getX() + width, top + 22 + rows * 22, QuestTheme.border());
        graphics.fill(getX() + 1, top + 1, getX() + width - 1, top + 21 + rows * 22, QuestTheme.background());
        graphics.drawString(font, QuestTheme.fit(font, Component.literal(query.isEmpty() ? "输入筛选 · 滚轮查看更多" : query), width - 16),
            getX() + 8, top + 7, QuestTheme.muted(), false);
        for (int index = 0; index < rows; index++)
        {
            int rowY = top + 22 + index * 22;
            Option choice = choices.get(offset + index);
            boolean isHovered = mouseX >= getX() && mouseX < getX() + width && mouseY >= rowY && mouseY < rowY + 22;
            if (isHovered || choice.id().equals(selected))
            {
                graphics.fill(getX() + 2, rowY, getX() + width - 2, rowY + 22, QuestTheme.selected());
            }
            graphics.drawString(font, QuestTheme.fit(font, Component.literal(choice.title()), width - 16),
                getX() + 8, rowY + 7, QuestTheme.text(), false);
        }
        graphics.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        if (!visible || !active || button != 0)
        {
            return false;
        }
        if (isOpen)
        {
            int index = ((int) mouseY - getY() - height - 24) / 22;
            List<Option> choices = filtered();
            if (mouseX >= getX() && mouseX < getX() + width && mouseY >= getY() + height + 24
                && index >= 0 && index < 6 && offset + index < choices.size())
            {
                String identifier = choices.get(offset + index).id();
                isOpen = false;
                if (identifier.equals(selected))
                {
                    return true;
                }
                selected = identifier;
                onSelect.accept(selected);
                return true;
            }
            isOpen = false;
            return true;
        }
        if (!isMouseOver(mouseX, mouseY))
        {
            return false;
        }
        isOpen = true;
        query = "";
        offset = 0;
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical)
    {
        if (!isOpen)
        {
            return false;
        }
        offset = Math.clamp(offset - (int) vertical, 0, Math.max(0, filtered().size() - 6));
        return true;
    }

    @Override
    public boolean charTyped(char character, int modifiers)
    {
        if (!isOpen || query.length() >= 64)
        {
            return false;
        }
        query += character;
        offset = 0;
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers)
    {
        if (!isOpen)
        {
            return false;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE)
        {
            isOpen = false;
        }
        else if (key == GLFW.GLFW_KEY_BACKSPACE && !query.isEmpty())
        {
            query = query.substring(0, query.length() - 1);
            offset = 0;
        }
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output)
    {
        output.add(NarratedElementType.TITLE, getMessage());
    }
}
