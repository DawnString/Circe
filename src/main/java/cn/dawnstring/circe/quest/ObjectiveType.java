package cn.dawnstring.circe.quest;

import cn.dawnstring.circe.api.QuestConfigSchema;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.ToIntBiFunction;

public final class ObjectiveType
{
    public enum TargetKind
    {
        ITEM, ENTITY, EVENT, STATISTIC
    }

    private static final Map<String, ObjectiveType> TYPES = new LinkedHashMap<>();
    public static final ObjectiveType HOLD = register("circe:hold", "circe.type.hold", TargetKind.ITEM,
        (player, objective) -> QuestService.countItems(player, objective.target()));
    public static final ObjectiveType SUBMIT = register("circe:submit", "circe.type.submit", TargetKind.ITEM, null);
    public static final ObjectiveType KILL = register("circe:kill", "circe.type.kill", TargetKind.ENTITY, null);
    public static final ObjectiveType EVENT = register("circe:event", "circe.type.event", TargetKind.EVENT, null);
    public static final ObjectiveType STATISTIC = register("circe:statistic", "circe.type.statistic", TargetKind.STATISTIC,
        (player, objective) -> player.getStats().getValue(QuestStatistics.resolve(objective.statisticType(), objective.target())));
    private final String id;
    private final String title;
    private final TargetKind targetKind;
    private final ToIntBiFunction<ServerPlayer, QuestDefinition.Objective> poll;
    private final QuestConfigSchema schema;
    private final Consumer<QuestDefinition.Objective> validator;

    private ObjectiveType(String id, String title, TargetKind targetKind, QuestConfigSchema schema,
        Consumer<QuestDefinition.Objective> validator, ToIntBiFunction<ServerPlayer, QuestDefinition.Objective> poll)
    {
        this.id = id;
        this.title = title;
        this.targetKind = targetKind;
        this.poll = poll;
        this.schema = schema;
        this.validator = validator;
    }

    public static ObjectiveType register(String id, String title, TargetKind kind, ToIntBiFunction<ServerPlayer, QuestDefinition.Objective> poll)
    {
        return register(id, title, kind, QuestConfigSchema.EMPTY, objective ->
        {
        }, poll);
    }

    public static synchronized ObjectiveType register(String id, String title, TargetKind kind, QuestConfigSchema schema,
        Consumer<QuestDefinition.Objective> validator, ToIntBiFunction<ServerPlayer, QuestDefinition.Objective> poll)
    {
        String normalized = ResourceLocation.parse(id).toString();
        if (TYPES.containsKey(normalized) || title == null || title.isBlank() || kind == null)
        {
            throw new IllegalArgumentException("重复目标类型 " + normalized);
        }
        ObjectiveType type = new ObjectiveType(normalized, title, kind, java.util.Objects.requireNonNull(schema),
            java.util.Objects.requireNonNull(validator), poll);
        TYPES.put(normalized, type);
        return type;
    }

    public static synchronized ObjectiveType parse(String id)
    {
        String normalized = id.contains(":") ? ResourceLocation.parse(id).toString() : "circe:" + id.toLowerCase(java.util.Locale.ROOT);
        ObjectiveType type = TYPES.get(normalized);
        if (type == null)
        {
            throw new QuestValidationException("circe.validation.objective_type", id);
        }
        return type;
    }

    public static synchronized List<ObjectiveType> all()
    {
        return List.copyOf(TYPES.values());
    }

    public String id()
    {
        return id;
    }

    /** 显示名称的翻译键或原文，由客户端解析。 */
    public String title()
    {
        return title;
    }

    public TargetKind targetKind()
    {
        return targetKind;
    }

    public boolean hasPoll()
    {
        return poll != null;
    }

    public QuestConfigSchema schema()
    {
        return schema;
    }

    public void validate(QuestDefinition.Objective objective)
    {
        schema.normalize(objective.configuration());
        validator.accept(objective);
    }

    public int poll(ServerPlayer player, QuestDefinition.Objective objective)
    {
        return poll.applyAsInt(player, objective);
    }
}
