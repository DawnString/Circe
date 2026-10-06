package cn.dawnstring.circe.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class QuestGraphConfirmScreen extends QuestEditingScreen
{
    private final Component message;
    private final Runnable onConfirm;
    private final boolean canConfirm;

    public QuestGraphConfirmScreen(QuestScreen parent, Component title, Component message, boolean canConfirm, Runnable onConfirm)
    {
        super(parent, title);
        this.message = message;
        this.canConfirm = canConfirm;
        this.onConfirm = onConfirm;
    }

    @Override
    protected void configureFrame(int viewWidth)
    {
        frameWidth = Math.min(440, frameWidth);
        frameHeight = 230;
        frameLeft = (viewWidth - frameWidth) / 2;
        frameTop = (540 - frameHeight) / 2;
    }

    @Override
    protected void initEditor()
    {
        button(frameLeft + 24, frameTop + frameHeight - 26, 80, QuestTranslations.text("circe.button.confirm_action"), () ->
        {
            onConfirm.run();
            onClose();
        }).primary().active = canConfirm;
        button(frameLeft + 112, frameTop + frameHeight - 26, 70, QuestTranslations.text("circe.button.back"), this::onClose);
    }

    @Override
    protected void renderContent(GuiGraphics graphics)
    {
        int top = frameTop + 54;
        for (var line : font.split(message, frameWidth - 48))
        {
            if (top > frameTop + frameHeight - 68)
            {
                graphics.drawString(font, "…", frameLeft + 24, top, QuestTheme.muted(), false);
                break;
            }
            graphics.drawString(font, line, frameLeft + 24, top, QuestTheme.text(), false);
            top += 14;
        }
    }
}
