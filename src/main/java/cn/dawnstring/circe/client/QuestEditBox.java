package cn.dawnstring.circe.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

public final class QuestEditBox extends EditBox
{
    public QuestEditBox(Font font, int x, int y, int width, Component message)
    {
        super(font, x, y, width, 20, message);
        setTextColor(QuestTheme.text());
        setTextColorUneditable(QuestTheme.muted());
        setTextShadow(false);
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        if (!visible)
        {
            return;
        }
        setTextColor(QuestTheme.text());
        setTextColorUneditable(QuestTheme.muted());
        QuestTheme.fill(graphics, getX(), getY(), getX() + getWidth(), getY() + getHeight(),
            isFocused() ? QuestTheme.accent() : QuestTheme.border());
        QuestTheme.fill(graphics, getX() + 1, getY() + 1, getX() + getWidth() - 1, getY() + getHeight() - 1, QuestTheme.background());
        // 保留原版边距和光标定位，仅跳过原版黑色纹理背景。
        graphics.pose().pushPose();
        graphics.pose().translate(4, (getHeight() - 8) / 2.0, 0);
        setBordered(false);
        width -= 8;
        super.renderWidget(graphics, mouseX, mouseY, partialTick);
        width += 8;
        setBordered(true);
        graphics.pose().popPose();
    }
}
