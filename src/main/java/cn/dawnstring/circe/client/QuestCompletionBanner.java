package cn.dawnstring.circe.client;

import cn.dawnstring.circe.network.CompletionPayload;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayDeque;

public final class QuestCompletionBanner
{
    private static final ArrayDeque<CompletionPayload> QUEUE = new ArrayDeque<>();
    private static CompletionPayload current;
    private static long startedAt;

    private QuestCompletionBanner()
    {
    }

    public static void receive(CompletionPayload payload)
    {
        if (QUEUE.size() < 8)
        {
            QUEUE.add(payload);
        }
    }

    public static void render(GuiGraphics graphics)
    {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui)
        {
            return;
        }
        long now = Util.getMillis();
        if (current != null && now - startedAt > 4200)
        {
            current = null;
        }
        if (current == null)
        {
            current = QUEUE.poll();
            if (current == null)
            {
                return;
            }
            startedAt = now;
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.2F, 0.55F));
        }
        double elapsed = now - startedAt;
        double entrance = 1 - Math.pow(1 - Math.clamp(elapsed / 550, 0, 1), 3);
        double exit = Math.clamp((elapsed - 3600) / 600, 0, 1);
        float scale = graphics.guiHeight() / 540.0F;
        int center = Math.round(graphics.guiWidth() / scale) / 2;
        int top = QuestUiSettings.current().hasAnimations() ? 5 - (int) ((1 - entrance + exit) * 56) : 5;
        graphics.pose().pushPose();
        graphics.pose().scale(scale, scale, 1);
        graphics.pose().translate(0, 0, 700);
        for (int glow = QuestTheme.style() == QuestTheme.Style.CLASSIC ? 5 : 0; glow >= 1; glow--)
        {
            graphics.fill(center - 142 - glow, top - glow, center + 142 + glow, top + 43 + glow, 0x06F2D688);
        }
        QuestTheme.fill(graphics, center - 142, top, center + 142, top + 43, QuestTheme.gold());
        QuestTheme.fill(graphics, center - 141, top + 1, center + 141, top + 42, QuestTheme.surface());
        graphics.fill(center - 141, top + 1, center - 99, top + 42, QuestTheme.selected());
        QuestTheme.centered(graphics, minecraft.font, "✦", center - 120, top + 14, QuestTheme.gold());
        graphics.drawString(minecraft.font, QuestTranslations.text("circe.banner.completed"), center - 89, top + 8, QuestTheme.gold(), false);
        graphics.drawString(minecraft.font, QuestTheme.fit(minecraft.font, Component.translatable(current.title()), 212),
            center - 89, top + 24, QuestTheme.text(), false);
        for (int index = 0; QuestUiSettings.current().hasAnimations() && index < 12; index++)
        {
            double phase = elapsed / 1100 + index * 0.72;
            int x = center + (int) (Math.cos(phase) * (151 + index % 3 * 9));
            int y = top + 20 + (int) (Math.sin(phase * 1.4) * 19);
            graphics.fill(x - 1, y, x + 2, y + 1, QuestTheme.gold());
            graphics.fill(x, y - 1, x + 1, y + 2, QuestTheme.gold());
        }
        graphics.pose().popPose();
    }

    public static void clear()
    {
        QUEUE.clear();
        current = null;
    }
}
