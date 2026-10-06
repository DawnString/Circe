package cn.dawnstring.circe.client;

import cn.dawnstring.circe.network.ImageUploadPayload;
import cn.dawnstring.circe.quest.QuestImageStore;
import cn.dawnstring.circe.quest.QuestValidationException;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public final class QuestImagePickerScreen extends QuestEditingScreen
{
    private final Consumer<String> onSelect;
    private String query = "";
    private String localPath = "";
    private List<ResourceLocation> available;
    private int page;
    private ResourceLocation pendingImage;
    private long observed;
    private long uploadedAt;

    public QuestImagePickerScreen(Screen parent, Consumer<String> onSelect)
    {
        super(parent, "circe.image.library");
        this.onSelect = onSelect;
    }

    @Override
    protected void initEditor()
    {
        var search = field(frameLeft + 16, frameTop + 50, frameWidth - 32, QuestTranslations.text("circe.image.search"), query, 128);
        search.setResponder(value ->
        {
            query = value;
            page = 0;
            filter();
        });
        var path = field(frameLeft + 16, frameTop + 102, frameWidth - 112, QuestTranslations.text("circe.image.import_hint"), localPath, 1024);
        path.setResponder(value -> localPath = value);
        button(frameLeft + frameWidth - 88, frameTop + 102, 72, QuestTranslations.text("circe.button.import_image"), this::upload).primary();
        filter();
        button(frameLeft + 16, frameTop + frameHeight - 26, 70, QuestTranslations.text("circe.button.previous"), () -> page = Math.max(0, page - 1));
        button(frameLeft + 94, frameTop + frameHeight - 26, 70, QuestTranslations.text("circe.button.next"), () -> page = Math.min(Math.max(0, (available.size() - 1) / 8), page + 1));
        button(frameLeft + 172, frameTop + frameHeight - 26, 70, QuestTranslations.text("circe.button.clear_image"), () ->
        {
            onSelect.accept("");
            onClose();
        });
        button(frameLeft + frameWidth - 86, frameTop + frameHeight - 26, 70, QuestTranslations.text("circe.button.back"), this::onClose);
    }

    private void filter()
    {
        var resources = new java.util.TreeSet<ResourceLocation>();
        resources.addAll(minecraft.getResourceManager().listResources("textures", id -> id.getPath().endsWith(".png")).keySet());
        resources.addAll(ClientQuestImages.imported());
        available = resources.stream().filter(id -> id.toString().contains(query.toLowerCase(Locale.ROOT))).toList();
    }

    private void upload()
    {
        if (pendingImage != null)
        {
            return;
        }
        try
        {
            Path path = Path.of(localPath.trim());
            if (Files.size(path) > QuestImageStore.MAX_BYTES)
            {
                throw new IllegalArgumentException(QuestTranslations.text("circe.error.image_size"));
            }
            byte[] png = Files.readAllBytes(path);
            QuestImageStore.validate(png);
            pendingImage = QuestImageStore.id(png);
            observed = ClientQuestState.editResultSequence();
            uploadedAt = net.minecraft.Util.getMillis();
            PacketDistributor.sendToServer(new ImageUploadPayload(png));
            status = Component.translatable("circe.image.importing");
        }
        catch (Exception exception)
        {
            status = Component.translatable("circe.error.import", QuestValidationException.message(exception));
        }
    }

    @Override
    public void tick()
    {
        super.tick();
        if (!ClientQuestState.canEdit())
        {
            return;
        }
        if (pendingImage != null && ClientQuestImages.has(pendingImage))
        {
            onSelect.accept(pendingImage.toString());
            onClose();
        }
        else if (pendingImage != null && observed != ClientQuestState.editResultSequence())
        {
            status = ClientQuestState.editResult().message();
            pendingImage = null;
        }
        else if (pendingImage != null && net.minecraft.Util.getMillis() - uploadedAt > 15000)
        {
            pendingImage = null;
            status = Component.translatable("circe.error.image_timeout");
        }
    }

    @Override
    protected void renderContent(GuiGraphics graphics)
    {
        int cellWidth = (frameWidth - 32) / 4;
        for (int index = 0; index < 8 && page * 8 + index < available.size(); index++)
        {
            ResourceLocation id = available.get(page * 8 + index);
            int x = frameLeft + 16 + index % 4 * cellWidth;
            int y = frameTop + 144 + index / 4 * 108;
            graphics.fill(x, y, x + cellWidth - 6, y + 100, QuestTheme.panel());
            ClientQuestImages.render(graphics, id.toString(), x + 8, y + 6, cellWidth - 22, 66);
            graphics.drawString(font, QuestTheme.fit(font, Component.literal(id.getPath()), cellWidth - 16),
                x + 6, y + 82, QuestTheme.muted(), false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        int x = (int) (mouseX / uiScale) - frameLeft - 16;
        int y = (int) (mouseY / uiScale) - frameTop - 144;
        int cellWidth = (frameWidth - 32) / 4;
        if (button == 0 && x >= 0 && x < cellWidth * 4 && y >= 0 && y < 216 && y % 108 < 100 && x % cellWidth < cellWidth - 6)
        {
            int index = page * 8 + y / 108 * 4 + x / cellWidth;
            if (index < available.size())
            {
                onSelect.accept(available.get(index).toString());
                onClose();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
