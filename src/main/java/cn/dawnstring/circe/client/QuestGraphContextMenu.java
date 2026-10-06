package cn.dawnstring.circe.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class QuestGraphContextMenu
{
    private static final int WIDTH = 136;
    private static final int ROW_HEIGHT = 22;

    public record Action(Component title, Runnable onSelect)
    {
    }

    private List<Action> actions = List.of();
    private int left;
    private int top;

    public boolean isOpen()
    {
        return !actions.isEmpty();
    }

    public void close()
    {
        actions = List.of();
    }

    public void open(int x, int y, int minimumX, int minimumY, int maximumX, int maximumY, List<Action> actions)
    {
        this.actions = List.copyOf(actions);
        left = Math.clamp(x, minimumX, Math.max(minimumX, maximumX - WIDTH));
        top = Math.clamp(y, minimumY, Math.max(minimumY, maximumY - actions.size() * ROW_HEIGHT - 4));
    }

    public boolean click(double x, double y, int button)
    {
        if (!isOpen())
        {
            return false;
        }
        List<Action> choices = actions;
        boolean isInside = x >= left && x < left + WIDTH && y >= top + 2 && y < top + 2 + choices.size() * ROW_HEIGHT;
        int index = (int) (y - top - 2) / ROW_HEIGHT;
        close();
        if (button == 0 && isInside)
        {
            choices.get(index).onSelect().run();
        }
        return true;
    }

    public void render(GuiGraphics graphics, Font font, int mouseX, int mouseY)
    {
        if (!isOpen())
        {
            return;
        }
        graphics.flush();
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 600);
        graphics.fill(left - 1, top - 1, left + WIDTH + 1, top + actions.size() * ROW_HEIGHT + 5, QuestTheme.border());
        graphics.fill(left, top, left + WIDTH, top + actions.size() * ROW_HEIGHT + 4, QuestTheme.background());
        for (int index = 0; index < actions.size(); index++)
        {
            int rowTop = top + 2 + index * ROW_HEIGHT;
            if (mouseX >= left && mouseX < left + WIDTH && mouseY >= rowTop && mouseY < rowTop + ROW_HEIGHT)
            {
                graphics.fill(left + 2, rowTop, left + WIDTH - 2, rowTop + ROW_HEIGHT, QuestTheme.selected());
            }
            graphics.drawString(font, QuestTheme.fit(font, actions.get(index).title(), WIDTH - 16),
                left + 8, rowTop + 7, QuestTheme.text(), false);
        }
        graphics.pose().popPose();
    }
}
