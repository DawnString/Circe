package cn.dawnstring.circe.client;

import net.minecraft.client.gui.GuiGraphics;
import org.joml.Vector3f;

public final class QuestUiViewport
{
    private QuestUiViewport()
    {
    }

    public static void enableScissor(GuiGraphics graphics, int left, int top, int right, int bottom)
    {
        // 原版裁剪不读取绘制矩阵，统一转换缩放和动画后的坐标。
        var matrix = graphics.pose().last().pose();
        Vector3f firstCorner = matrix.transformPosition(new Vector3f(left, top, 0));
        Vector3f secondCorner = matrix.transformPosition(new Vector3f(right, bottom, 0));
        graphics.enableScissor((int) Math.floor(firstCorner.x), (int) Math.floor(firstCorner.y),
            (int) Math.ceil(secondCorner.x), (int) Math.ceil(secondCorner.y));
    }
}
