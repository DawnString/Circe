package cn.dawnstring.circe.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import java.util.function.Consumer;

public final class QuestRichTextEditorScreen extends QuestEditingScreen
{
    private final Consumer<String> onSave;
    private String markup;
    private QuestMultilineEditBox source;
    private int previewLeft;
    private int previewTop;
    private int previewWidth;
    private int previewHeight;
    private int previewScroll;
    private int contentHeight;

    public QuestRichTextEditorScreen(Screen parent, String markup, Consumer<String> onSave)
    {
        super(parent, "图文编辑器");
        this.markup = markup;
        this.onSave = onSave;
    }

    @Override
    protected void initEditor()
    {
        String[] labels = {"标题", "强调", "斜体", "颜色", "列表"};
        String[] templates = {"\n## 标题\n", "**强调文字**", "*斜体文字*", "<#76D46A>彩色文字</>", "\n- 列表内容\n"};
        for (int index = 0; index < labels.length; index++)
        {
            String template = templates[index];
            button(frameLeft + 16 + index * 63, frameTop + 44, 57, labels[index], () -> insert(template));
        }
        button(frameLeft + 331, frameTop + 44, 70, "插入图片", () ->
            minecraft.setScreen(new QuestImagePickerScreen(this, id ->
            {
                if (!id.isEmpty())
                {
                    markup += "\n![插图](" + id + ")\n";
                }
            })));
        int columnWidth = (frameWidth - 48) / 2;
        label("编辑内容 · Markdown", frameLeft + 16, frameTop + 80);
        source = addRenderableWidget(new QuestMultilineEditBox(font, frameLeft + 16, frameTop + 94, columnWidth, 260,
            Component.literal("输入描述，右侧实时预览"), Component.literal("图文内容")));
        source.setCharacterLimit(8192);
        source.setValue(markup);
        source.setValueListener(value -> markup = value);
        previewLeft = frameLeft + 32 + columnWidth;
        previewTop = frameTop + 94;
        previewWidth = columnWidth;
        previewHeight = 260;
        label("实时预览", previewLeft, frameTop + 80);
        button(frameLeft + 16, frameTop + frameHeight - 26, 100, "应用到任务", () ->
        {
            onSave.accept(markup);
            onClose();
        }).primary();
        button(frameLeft + 124, frameTop + frameHeight - 26, 70, "取消", this::onClose);
    }

    private void insert(String template)
    {
        source.setFocused(true);
        for (char character : template.toCharArray())
        {
            if (character == '\n')
            {
                source.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
            }
            else
            {
                source.charTyped(character, 0);
            }
        }
    }

    @Override
    protected void renderContent(GuiGraphics graphics)
    {
        graphics.fill(previewLeft, previewTop, previewLeft + previewWidth, previewTop + previewHeight, QuestTheme.panel());
        QuestUiViewport.enableScissor(graphics, previewLeft + 6, previewTop + 6, previewLeft + previewWidth - 6, previewTop + previewHeight - 6);
        contentHeight = QuestRichText.render(graphics, font, markup, previewLeft + 10, previewTop + 10 - previewScroll, previewWidth - 20);
        graphics.disableScissor();
        previewScroll = Math.clamp(previewScroll, 0, Math.max(0, contentHeight - previewHeight + 20));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical)
    {
        double x = mouseX / uiScale;
        double y = mouseY / uiScale;
        if (x >= previewLeft && x < previewLeft + previewWidth && y >= previewTop && y < previewTop + previewHeight)
        {
            previewScroll = Math.clamp(previewScroll - (int) (vertical * 16), 0, Math.max(0, contentHeight - previewHeight + 20));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }
}
