package cn.dawnstring.circe.client;

import cn.dawnstring.circe.api.QuestConfigSchema;
import cn.dawnstring.circe.quest.QuestValidationException;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Consumer;

public final class QuestConfigEditorScreen extends QuestEditingScreen
{
    private static final int PAGE_SIZE = 6;
    private final QuestConfigSchema schema;
    private final Consumer<JsonObject> onSave;
    private final JsonObject draft;
    private int page;

    public QuestConfigEditorScreen(Screen parent, QuestConfigSchema schema, JsonObject configuration, Consumer<JsonObject> onSave)
    {
        super(parent, "circe.editor.extension");
        this.schema = schema;
        this.onSave = onSave;
        draft = schema.normalize(configuration);
    }

    @Override
    protected void initEditor()
    {
        int left = frameLeft + 24;
        int width = frameWidth - 48;
        for (int index = 0; index < PAGE_SIZE && page * PAGE_SIZE + index < schema.fields().size(); index++)
        {
            var descriptor = schema.fields().get(page * PAGE_SIZE + index);
            addField(descriptor, left, frameTop + 56 + index * 44, width);
        }
        button(left, frameTop + frameHeight - 26, 70, QuestTranslations.text("circe.button.save_config"), this::save).primary();
        button(left + 78, frameTop + frameHeight - 26, 70, QuestTranslations.text("circe.button.cancel"), this::onClose);
        button(frameLeft + frameWidth - 180, frameTop + frameHeight - 26, 70, QuestTranslations.text("circe.button.previous"), () ->
        {
            page = Math.max(0, page - 1);
            rebuild();
        });
        button(frameLeft + frameWidth - 102, frameTop + frameHeight - 26, 70, QuestTranslations.text("circe.button.next"), () ->
        {
            page = Math.min((schema.fields().size() - 1) / PAGE_SIZE, page + 1);
            rebuild();
        });
    }

    private void addField(QuestConfigSchema.Field descriptor, int left, int top, int width)
    {
        String title = QuestTranslations.text(descriptor.title()) + (descriptor.unit().isBlank() ? "" : " (" + QuestTranslations.text(descriptor.unit()) + ")");
        if (descriptor.kind() == QuestConfigSchema.Kind.BOOLEAN || descriptor.kind() == QuestConfigSchema.Kind.CHOICE)
        {
            label(title, left, top - 12);
            List<QuestDropdown.Option> options = descriptor.kind() == QuestConfigSchema.Kind.BOOLEAN
                ? List.of(new QuestDropdown.Option("true", "circe.option.yes"), new QuestDropdown.Option("false", "circe.option.no"))
                : descriptor.options().stream().map(option -> new QuestDropdown.Option(option.id(), option.title())).toList();
            addRenderableWidget(new QuestDropdown(left, top, width, options, draft.get(descriptor.key()).getAsString(), value ->
                draft.add(descriptor.key(), descriptor.kind() == QuestConfigSchema.Kind.BOOLEAN
                    ? new JsonPrimitive(Boolean.parseBoolean(value)) : new JsonPrimitive(value))));
            return;
        }
        boolean hasPicker = List.of(QuestConfigSchema.Kind.ITEM, QuestConfigSchema.Kind.BLOCK,
            QuestConfigSchema.Kind.ENTITY, QuestConfigSchema.Kind.FLUID).contains(descriptor.kind());
        var input = field(left, top, width - (hasPicker ? 90 : 0), title,
            draft.get(descriptor.key()).getAsString(), descriptor.maximumLength());
        input.setResponder(value -> draft.addProperty(descriptor.key(), value));
        if (hasPicker)
        {
            button(left + width - 82, top, 82, QuestTranslations.text("circe.button.pick_resource"), () -> minecraft.setScreen(
                new QuestItemPickerScreen(this, descriptor.kind(), id -> draft.addProperty(descriptor.key(), id.toString()))));
        }
    }

    private void save()
    {
        try
        {
            JsonObject configuration = draft.deepCopy();
            for (var descriptor : schema.fields())
            {
                if (descriptor.kind() == QuestConfigSchema.Kind.INTEGER || descriptor.kind() == QuestConfigSchema.Kind.DECIMAL)
                {
                    configuration.add(descriptor.key(), new JsonPrimitive(new BigDecimal(configuration.get(descriptor.key()).getAsString())));
                }
            }
            onSave.accept(schema.normalize(configuration));
            onClose();
        }
        catch (RuntimeException exception)
        {
            status = Component.translatable("circe.error.config_invalid", QuestValidationException.message(exception));
        }
    }

    @Override
    protected void renderContent(GuiGraphics graphics)
    {
        graphics.drawString(font, QuestTranslations.text("circe.editor.config_pages", page + 1,
            Math.max(1, (schema.fields().size() + PAGE_SIZE - 1) / PAGE_SIZE), schema.fields().size()), frameLeft + 24, frameTop + frameHeight - 72, QuestTheme.muted(), false);
    }
}
