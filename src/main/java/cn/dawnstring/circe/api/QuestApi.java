package cn.dawnstring.circe.api;

import cn.dawnstring.circe.quest.ObjectiveType;
import cn.dawnstring.circe.quest.QuestService;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import cn.dawnstring.circe.quest.QuestDefinition;
import cn.dawnstring.circe.quest.QuestSavedData;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

public final class QuestApi
{
    public static final int API_VERSION = 1;

    public static List<QuestDefinition> definitions(MinecraftServer server)
    {
        requireServerThread(server);
        return List.copyOf(QuestService.CATALOG.all());
    }

    public static Optional<QuestDefinition> definition(MinecraftServer server, ResourceLocation questId)
    {
        requireServerThread(server);
        return Optional.ofNullable(QuestService.CATALOG.get(questId));
    }

    public static Optional<QuestState> state(ServerPlayer player, ResourceLocation questId)
    {
        requireServerThread(player.server);
        return definition(player.server, questId).map(definition -> snapshot(player, definition));
    }

    public static List<QuestState> states(ServerPlayer player)
    {
        requireServerThread(player.server);
        return definitions(player.server).stream().map(definition -> snapshot(player, definition)).toList();
    }

    public static Optional<ResourceLocation> trackedQuest(ServerPlayer player)
    {
        requireServerThread(player.server);
        ResourceLocation tracked = QuestSavedData.get(player.server).tracked(player.getUUID());
        return tracked != null && QuestService.CATALOG.get(tracked) != null ? Optional.of(tracked) : Optional.empty();
    }

    public static QuestState snapshot(ServerPlayer player, QuestDefinition definition)
    {
        requireServerThread(player.server);
        var saved = QuestSavedData.get(player.server);
        var progress = saved.peekProgress(player.getUUID(), definition);
        var counts = new LinkedHashMap<String, Integer>();
        definition.objectives().forEach(objective -> counts.put(objective.id(), progress.count(objective.id())));
        return new QuestState(definition.id(), definition.revision(), QuestService.isUnlocked(player, definition),
            progress.isCompleted(), progress.isClaimed(), definition.id().equals(saved.tracked(player.getUUID())), counts);
    }

    public static void requireServerThread(MinecraftServer server)
    {
        if (!server.isSameThread())
        {
            throw new IllegalStateException("任务 API 必须在服务器线程调用");
        }
    }

    public static void record(ServerPlayer player, ObjectiveType type, ResourceLocation target, int amount)
    {
        requireServerThread(player.server);
        if (cn.dawnstring.circe.api.event.QuestLifecycleEvent.isDispatching())
        {
            throw new IllegalStateException("请将生命周期监听器中的任务写入安排到下一次服务器 tick");
        }
        if (amount < 1)
        {
            throw new IllegalArgumentException("Event amount must be positive");
        }
        QuestService.record(player, type, target, amount);
    }

    private QuestApi()
    {
    }

    /** 先决条件解锁后，在服务器线程上记录正事件计数 */
    public static void record(ServerPlayer player, ResourceLocation eventId, int amount)
    {
        record(player, ObjectiveType.EVENT, eventId, amount);
    }
}
