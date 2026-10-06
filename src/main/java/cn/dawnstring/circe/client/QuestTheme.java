package cn.dawnstring.circe.client;

import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class QuestTheme
{
    public record Palette(int background, int panel, int selected, int accent, int text, int muted,
        int gold, int border, int hover, int grid, int locked, int radius)
    {
    }

    public enum Style
    {
        CLASSIC("经典黑绿", "像素轮廓 · 原版风格", new Palette(0xFF101712, 0xFF1A231D, 0xFF284E31,
            0xFF76D46A, 0xFFE7EFE8, 0xFF9CAA9E, 0xFFE5C76B, 0xFF405449, 0xFF427A3F, 0x182F4937, 0xFF59675D, 0)),
        MODERN("现代深色", "石墨灰 · 柔和层次", new Palette(0xFF191D24, 0xFF242A33, 0xFF244B43,
            0xFF70D6AE, 0xFFE8EDF3, 0xFFA1ADBD, 0xFFE6BE73, 0xFF3B4452, 0xFF344A49, 0x163F4855, 0xFF66717F, 4)),
        PAPER("纸页浅色", "暖白纸页 · 墨绿强调", new Palette(0xFFF4F1E8, 0xFFEAE6DC, 0xFFD5E5D6,
            0xFF2C704F, 0xFF28372E, 0xFF566257, 0xFF886019, 0xFFCCCFC3, 0xFFC4DBC7, 0x187C8876, 0xFF8B9487, 4));

        private final String title;
        private final String description;
        private final Palette palette;

        Style(String title, String description, Palette palette)
        {
            this.title = title;
            this.description = description;
            this.palette = palette;
        }

        public String title()
        {
            return title;
        }

        public String description()
        {
            return description;
        }

        public Palette palette()
        {
            return palette;
        }
    }

    private static Style style = Style.CLASSIC;
    private static Palette previous = style.palette();
    private static long changedAt;

    private QuestTheme()
    {
    }

    public static Style style()
    {
        return style;
    }

    public static void apply(Style next, boolean hasAnimation)
    {
        if (style == next)
        {
            if (!hasAnimation)
            {
                changedAt = 0;
            }
            return;
        }
        previous = new Palette(background(), panel(), selected(), accent(), text(), muted(), gold(), border(), hover(), grid(), locked(), style.palette().radius());
        style = next;
        changedAt = hasAnimation ? Util.getMillis() : 0;
    }

    private static int transition(int before, int after)
    {
        double fraction = changedAt == 0 ? 1 : Math.clamp((Util.getMillis() - changedAt) / 240.0, 0, 1);
        return mix(before, after, fraction);
    }

    public static int mix(int before, int after, double fraction)
    {
        int result = 0;
        for (int shift = 0; shift <= 24; shift += 8)
        {
            int start = before >>> shift & 255;
            int end = after >>> shift & 255;
            result |= (int) Math.round(start + (end - start) * fraction) << shift;
        }
        return result;
    }

    public static int background()
    {
        return transition(previous.background(), style.palette().background());
    }

    public static int panel()
    {
        return transition(previous.panel(), style.palette().panel());
    }

    public static int selected()
    {
        return transition(previous.selected(), style.palette().selected());
    }

    public static int accent()
    {
        return transition(previous.accent(), style.palette().accent());
    }

    public static int text()
    {
        return transition(previous.text(), style.palette().text());
    }

    public static int muted()
    {
        return transition(previous.muted(), style.palette().muted());
    }

    public static int gold()
    {
        return transition(previous.gold(), style.palette().gold());
    }

    public static int border()
    {
        return transition(previous.border(), style.palette().border());
    }

    public static int hover()
    {
        return transition(previous.hover(), style.palette().hover());
    }

    public static int grid()
    {
        return transition(previous.grid(), style.palette().grid());
    }

    public static int locked()
    {
        return transition(previous.locked(), style.palette().locked());
    }

    public static int surface()
    {
        return alpha(background(), (int) (255 * QuestUiSettings.current().opacity()));
    }

    public static int alpha(int color, int opacity)
    {
        return color & 0xFFFFFF | Math.clamp(opacity, 0, 255) << 24;
    }

    public static void fill(GuiGraphics graphics, int left, int top, int right, int bottom, int color)
    {
        int radius = Math.min(style.palette().radius(), Math.min((right - left) / 2, (bottom - top) / 2));
        rounded(graphics, left, top, right, bottom, color, Math.max(0, radius));
    }

    public static void rounded(GuiGraphics graphics, int left, int top, int right, int bottom, int color, int radius)
    {
        graphics.fill(left, top + radius, right, bottom - radius, color);
        for (int row = 0; row < radius; row++)
        {
            int inset = (int) Math.ceil(radius - Math.sqrt(radius * radius - Math.pow(radius - row - 0.5, 2)));
            graphics.fill(left + inset, top + row, right - inset, top + row + 1, color);
            graphics.fill(left + inset, bottom - row - 1, right - inset, bottom - row, color);
        }
    }

    public static void card(GuiGraphics graphics, int left, int top, int width, int height)
    {
        fill(graphics, left, top + 3, left + width, top + height + 3, 0x30000000);
        fill(graphics, left, top, left + width, top + height, surface());
        outline(graphics, left, top, left + width, top + height, border());
    }

    public static void outline(GuiGraphics graphics, int left, int top, int right, int bottom, int color)
    {
        int radius = Math.min(style.palette().radius(), Math.min((right - left) / 2, (bottom - top) / 2));
        graphics.fill(left, top + radius, left + 1, bottom - radius, color);
        graphics.fill(right - 1, top + radius, right, bottom - radius, color);
        graphics.fill(left + radius, top, right - radius, top + 1, color);
        graphics.fill(left + radius, bottom - 1, right - radius, bottom, color);
        for (int row = 0; row < radius; row++)
        {
            int inset = (int) Math.ceil(radius - Math.sqrt(radius * radius - Math.pow(radius - row - 0.5, 2)));
            graphics.fill(left + inset, top + row, left + inset + 1, top + row + 1, color);
            graphics.fill(right - inset - 1, top + row, right - inset, top + row + 1, color);
            graphics.fill(left + inset, bottom - row - 1, left + inset + 1, bottom - row, color);
            graphics.fill(right - inset - 1, bottom - row - 1, right - inset, bottom - row, color);
        }
    }

    public static void centered(GuiGraphics graphics, Font font, Component text, int center, int y, int color)
    {
        graphics.drawString(font, text, center - font.width(text) / 2, y, color, false);
    }

    public static void centered(GuiGraphics graphics, Font font, String text, int center, int y, int color)
    {
        graphics.drawString(font, text, center - font.width(text) / 2, y, color, false);
    }

    public static String fit(Font font, Component text, int width)
    {
        String value = text.getString();
        if (font.width(value) <= width)
        {
            return value;
        }
        return font.plainSubstrByWidth(value, Math.max(0, width - font.width("…"))) + "…";
    }
}
