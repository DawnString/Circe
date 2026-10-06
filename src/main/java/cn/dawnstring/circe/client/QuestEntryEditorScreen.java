package cn.dawnstring.circe.client;

import cn.dawnstring.circe.quest.ObjectiveType;
import cn.dawnstring.circe.quest.QuestStatistics;
import cn.dawnstring.circe.quest.QuestValidationException;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Consumer;

public final class QuestEntryEditorScreen extends QuestEditingScreen
{
    private final JsonObject entry;
    private final boolean isObjective;
    private final Consumer<JsonObject> onSave;
    private EditBox identifier;
    private EditBox name;
    private EditBox target;
    private EditBox count;
    private ObjectiveType type;
    private cn.dawnstring.circe.quest.RewardType rewardType;
    private String componentsText;
    private String customName = "";

    public QuestEntryEditorScreen(QuestEditorScreen parent, JsonObject entry, boolean isObjective, Consumer<JsonObject> onSave)
    {
        super(parent, isObjective ? "circe.editor.objective" : "circe.editor.reward");
        this.entry = entry.deepCopy();
        this.isObjective = isObjective;
        this.onSave = onSave;
        if (isObjective)
        {
            type = ObjectiveType.parse(entry.get("type").getAsString());
        }
        else
        {
            rewardType = cn.dawnstring.circe.quest.RewardType.parse(entry.has("type") ? entry.get("type").getAsString() : "item");
            componentsText = entry.has("components") ? entry.get("components").toString() : "{}";
        }
    }

    @Override
    protected void initEditor()
    {
        int left = frameLeft + 24;
        int width = frameWidth - 48;
        int y = frameTop + 56;
        if (isObjective)
        {
            identifier = field(left, y, width, QuestTranslations.text("circe.editor.objective_id"), entry.get("id").getAsString(), 64);
            name = field(left, y + 44, width, QuestTranslations.text("circe.editor.objective_title"), entry.get("title").getAsString(), 256);
            identifier.setResponder(value -> entry.addProperty("id", value));
            name.setResponder(value -> entry.addProperty("title", value));
            label(QuestTranslations.text("circe.editor.objective_type"), left, y + 75);
            addRenderableWidget(new QuestDropdown(left, y + 88, width,
                ObjectiveType.all().stream().map(candidate -> new QuestDropdown.Option(candidate.id(), candidate.title())).toList(), type.id(), id ->
                {
                    type = ObjectiveType.parse(id);
                    entry.addProperty("type", type.id());
                    entry.addProperty("target", switch (type.targetKind())
                    {
                        case ITEM -> "minecraft:oak_log";
                        case ENTITY -> "minecraft:zombie";
                        case EVENT -> "circe:event";
                        case STATISTIC -> "minecraft:jump";
                    });
                    entry.addProperty("statistic_type", "minecraft:custom");
                    entry.addProperty("statistic_mode", "total");
                    entry.remove("config");
                    rebuild();
                }));
            y += 142;
        }
        else
        {
            label(QuestTranslations.text("circe.editor.reward_type"), left, y - 12);
            addRenderableWidget(new QuestDropdown(left, y, width,
                cn.dawnstring.circe.quest.RewardType.all().stream().map(candidate -> new QuestDropdown.Option(candidate.id(), candidate.title())).toList(),
                rewardType.id(), id ->
                {
                    rewardType = cn.dawnstring.circe.quest.RewardType.parse(id);
                    entry.addProperty("type", rewardType.id());
                    entry.remove("config");
                    rebuild();
                }));
            y += 50;
        }
        boolean isStatistic = isObjective && type == ObjectiveType.STATISTIC;
        if (isStatistic)
        {
            y = statisticFields(left, y, width);
        }
        boolean hasTarget = !isStatistic && (isObjective || rewardType.hasItem());
        if (hasTarget)
        {
            target = field(left, y, width - 90, isObjective ? QuestTranslations.text("circe.editor.target") : QuestTranslations.text("circe.editor.reward_item"),
                entry.get(isObjective ? "target" : "item").getAsString(), 256);
            target.setResponder(value -> entry.addProperty(isObjective ? "target" : "item", value));
            if (!isObjective || type.targetKind() != ObjectiveType.TargetKind.EVENT)
            {
                button(left + width - 82, y, 82, isObjective && type.targetKind() == ObjectiveType.TargetKind.ENTITY ? QuestTranslations.text("circe.button.pick_entity") : QuestTranslations.text("circe.button.pick_item"), () ->
                    minecraft.setScreen(new QuestItemPickerScreen(this, isObjective && type.targetKind() == ObjectiveType.TargetKind.ENTITY,
                        id -> entry.addProperty(isObjective ? "target" : "item", id.toString()))));
            }
            y += 44;
        }
        String countLabel = isStatistic ? QuestTranslations.text("circe.editor.stat_count", statisticUnit().title())
            : isObjective ? QuestTranslations.text("circe.editor.objective_count") : QuestTranslations.text("circe.editor.reward_count", rewardType.maximumCount());
        String countValue = isStatistic ? statisticUnit().input(entry.get("count").getAsInt()) : entry.get("count").getAsString();
        count = field(left, y, width, countLabel, countValue, 24);
        count.setResponder(value ->
        {
            if (!isStatistic)
            {
                entry.addProperty("count", value);
                return;
            }
            try
            {
                entry.addProperty("count", statisticUnit().parse(value));
            }
            catch (RuntimeException ignored)
            {
                // 保留最后有效的数量，确定时再报告当前输入错误。
            }
        });
        var schema = isObjective ? type.schema() : rewardType.schema();
        if (!schema.fields().isEmpty())
        {
            button(left, y + 44, 140, QuestTranslations.text("circe.editor.config_count", schema.fields().size()), () ->
                minecraft.setScreen(new QuestConfigEditorScreen(this, schema,
                    entry.has("config") ? entry.getAsJsonObject("config") : new JsonObject(),
                    configuration -> entry.add("config", configuration))));
        }
        if (!isObjective && rewardType == cn.dawnstring.circe.quest.RewardType.SPECIAL_ITEM)
        {
            var nameField = field(left, y + 44, width, QuestTranslations.text("circe.editor.custom_name"), customName, 256);
            nameField.setResponder(value -> customName = value);
            label(QuestTranslations.text("circe.editor.components_hint"), left, y + 77);
            var componentField = addRenderableWidget(new QuestMultilineEditBox(font, left, y + 91, width, 78,
                Component.literal("{}"), Component.translatable("circe.editor.components")));
            componentField.setCharacterLimit(8192);
            componentField.setValue(componentsText);
            componentField.setValueListener(value -> componentsText = value);
        }
        button(left, frameTop + frameHeight - 26, 70, QuestTranslations.text("circe.button.ok"), this::save).primary();
        button(left + 78, frameTop + frameHeight - 26, 70, QuestTranslations.text("circe.button.cancel"), this::onClose);
    }

    private ResourceLocation statisticType()
    {
        return ResourceLocation.parse(entry.has("statistic_type") ? entry.get("statistic_type").getAsString() : "minecraft:custom");
    }

    private QuestStatistics.Unit statisticUnit()
    {
        return QuestStatistics.unit(statisticType(), ResourceLocation.parse(entry.get("target").getAsString()));
    }

    private int statisticFields(int left, int y, int width)
    {
        label(QuestTranslations.text("circe.editor.stat_category"), left, y - 12);
        addRenderableWidget(new QuestDropdown(left, y, width,
            BuiltInRegistries.STAT_TYPE.keySet().stream().sorted().map(id -> new QuestDropdown.Option(id.toString(),
                QuestStatistics.categoryName(id))).toList(), statisticType().toString(), id ->
            {
                entry.addProperty("statistic_type", id);
                var registry = BuiltInRegistries.STAT_TYPE.get(ResourceLocation.parse(id)).getRegistry();
                var first = registry.keySet().stream().sorted().findFirst().orElseThrow();
                entry.addProperty("target", first.toString());
                entry.addProperty("count", 1);
                rebuild();
            }));
        target = field(left, y + 44, width - 90, QuestTranslations.text("circe.editor.stat_target"), entry.get("target").getAsString(), 256);
        target.setEditable(false);
        button(left + width - 82, y + 44, 82, QuestTranslations.text("circe.button.pick_statistic"), () ->
            minecraft.setScreen(new QuestStatisticPickerScreen(this, statisticType(), id ->
            {
                entry.addProperty("target", id.toString());
                entry.addProperty("count", 1);
            })));
        label(QuestTranslations.text("circe.editor.stat_mode"), left, y + 76);
        addRenderableWidget(new QuestDropdown(left, y + 88, width,
            java.util.List.of(new QuestDropdown.Option("total", "circe.stat.mode.total"),
                new QuestDropdown.Option("since_unlock", "circe.stat.mode.since_unlock")),
            entry.has("statistic_mode") ? entry.get("statistic_mode").getAsString() : "total",
            mode -> entry.addProperty("statistic_mode", mode)));
        return y + 132;
    }

    private void save()
    {
        try
        {
            boolean isStatistic = isObjective && type == ObjectiveType.STATISTIC;
            int quantity = isStatistic ? statisticUnit().parse(count.getValue()) : Integer.parseInt(count.getValue());
            int maximumCount = isStatistic ? Integer.MAX_VALUE : isObjective ? 1_000_000 : rewardType.maximumCount();
            if (quantity < 1 || quantity > maximumCount)
            {
                throw new IllegalArgumentException(QuestTranslations.text("circe.error.count_range"));
            }
            if (isObjective || rewardType.hasItem())
            {
                net.minecraft.resources.ResourceLocation.parse(target.getValue());
            }
            if (isObjective)
            {
                if (!identifier.getValue().matches("[a-z0-9_./-]+") || name.getValue().isBlank())
                {
                    throw new IllegalArgumentException(QuestTranslations.text("circe.error.objective_identity"));
                }
                entry.addProperty("id", identifier.getValue());
                entry.addProperty("title", name.getValue());
                entry.addProperty("type", type.id());
                if (isStatistic)
                {
                    QuestStatistics.resolve(statisticType(), ResourceLocation.parse(target.getValue()));
                }
                else
                {
                    entry.remove("statistic_type");
                    entry.remove("statistic_mode");
                }
            }
            else
            {
                entry.addProperty("type", rewardType.id());
                var components = com.google.gson.JsonParser.parseString(componentsText).getAsJsonObject();
                if (!customName.isBlank())
                {
                    com.google.gson.JsonObject name = new com.google.gson.JsonObject();
                    name.addProperty("text", customName);
                    components.addProperty("minecraft:custom_name", name.toString());
                }
                entry.add("components", components);
            }
            entry.addProperty(isObjective ? "target" : "item", isObjective || rewardType.hasItem() ? target.getValue() : "minecraft:air");
            entry.addProperty("count", quantity);
            entry.add("config", (isObjective ? type.schema() : rewardType.schema()).normalize(
                entry.has("config") ? entry.getAsJsonObject("config") : new JsonObject()));
            onSave.accept(entry);
            onClose();
        }
        catch (RuntimeException exception)
        {
            status = QuestValidationException.message(exception);
        }
    }

}
