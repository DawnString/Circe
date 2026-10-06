package cn.dawnstring.circe.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.regex.Pattern;

public final class QuestRichText
{
    private static final Pattern IMAGE = Pattern.compile("^!\\[[^]]*]\\(([^)]+)\\)$");
    private static final Pattern INLINE = Pattern.compile("\\*\\*(.+?)\\*\\*|\\*(.+?)\\*|`(.+?)`|<#([0-9a-fA-F]{6})>(.+?)</>");

    private QuestRichText()
    {
    }

    public static int render(GuiGraphics graphics, Font font, String source, int x, int y, int width)
    {
        int startY = y;
        String translated = Component.translatable(source).getString();
        for (String line : translated.split("\\n", -1))
        {
            var image = IMAGE.matcher(line.trim());
            if (image.matches())
            {
                y += ClientQuestImages.render(graphics, image.group(1), x, y, width, 140) + 10;
                continue;
            }
            if (line.isBlank())
            {
                y += 7;
                continue;
            }
            boolean isHeading = line.startsWith("# ") || line.startsWith("## ") || line.startsWith("### ");
            if (isHeading)
            {
                line = line.replaceFirst("^#{1,3}\\s+", "");
            }
            if (line.startsWith("- "))
            {
                line = "• " + line.substring(2);
            }
            float scale = isHeading ? 1.2F : 1;
            Component content = inline(line);
            if (isHeading)
            {
                content = content.copy().withStyle(style -> style.withBold(true).withColor(QuestTheme.accent() & 0xFFFFFF));
            }
            for (var wrapped : font.split(content, Math.max(20, (int) (width / scale))))
            {
                graphics.pose().pushPose();
                graphics.pose().translate(x, y, 0);
                graphics.pose().scale(scale, scale, 1);
                graphics.drawString(font, wrapped, 0, 0, QuestTheme.text(), false);
                graphics.pose().popPose();
                y += isHeading ? 14 : 11;
            }
            y += isHeading ? 4 : 2;
        }
        return y - startY;
    }

    public static String plain(String source)
    {
        return Component.translatable(source).getString().replaceAll("!\\[[^]]*]\\([^)]+\\)", "")
            .replaceAll("<#(?:[0-9a-fA-F]{6})>|</>|[*`#]", "").replace('\n', ' ').trim();
    }

    private static Component inline(String text)
    {
        MutableComponent result = Component.empty();
        var matcher = INLINE.matcher(text);
        int offset = 0;
        while (matcher.find())
        {
            result.append(Component.literal(text.substring(offset, matcher.start())));
            String value;
            Style style = Style.EMPTY;
            if (matcher.group(1) != null)
            {
                value = matcher.group(1);
                style = style.withBold(true);
            }
            else if (matcher.group(2) != null)
            {
                value = matcher.group(2);
                style = style.withItalic(true);
            }
            else if (matcher.group(3) != null)
            {
                value = matcher.group(3);
                style = style.withColor(QuestTheme.gold() & 0xFFFFFF);
            }
            else
            {
                value = matcher.group(5);
                style = style.withColor(Integer.parseInt(matcher.group(4), 16));
            }
            result.append(Component.literal(value).setStyle(style));
            offset = matcher.end();
        }
        result.append(Component.literal(text.substring(offset)));
        return result;
    }
}
