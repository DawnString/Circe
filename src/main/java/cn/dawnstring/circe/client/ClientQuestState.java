package cn.dawnstring.circe.client;

import cn.dawnstring.circe.network.CatalogPayload;
import cn.dawnstring.circe.network.EditResultPayload;
import cn.dawnstring.circe.network.ProgressPayload;
import cn.dawnstring.circe.quest.ChapterDefinition;
import cn.dawnstring.circe.quest.QuestDefinition;
import cn.dawnstring.circe.quest.QuestProgress;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ClientQuestState
{
    private static final Map<ResourceLocation, QuestDefinition> DEFINITIONS = new LinkedHashMap<>();
    private static final Map<ResourceLocation, QuestProgress> PROGRESS = new HashMap<>();
    private static final Map<ResourceLocation, Boolean> UNLOCKED = new HashMap<>();
    private static final Map<String, ChapterDefinition> CHAPTERS = new LinkedHashMap<>();
    private static ResourceLocation trackedQuest;
    private static long lastProgressChange;
    private static String bookTitle = "circe.screen.title";
    private static boolean canEdit;
    private static long bookRevision;
    private static EditResultPayload editResult;
    private static long editResultSequence;

    private ClientQuestState()
    {
    }

    public static void receiveCatalog(CatalogPayload payload)
    {
        var catalog = JsonParser.parseString(payload.json()).getAsJsonObject();
        Map<ResourceLocation, QuestDefinition> definitions = new LinkedHashMap<>();
        Map<String, ChapterDefinition> chapters = new LinkedHashMap<>();
        Map<ResourceLocation, QuestProgress> progress = new HashMap<>();
        Map<ResourceLocation, Boolean> unlocked = new HashMap<>();
        catalog.getAsJsonObject("chapters").entrySet().forEach(entry ->
            chapters.put(entry.getKey(), ChapterDefinition.parse(entry.getKey(), entry.getValue().getAsJsonObject())));
        catalog.getAsJsonObject("quests").entrySet().forEach(entry ->
        {
            ResourceLocation id = ResourceLocation.parse(entry.getKey());
            QuestDefinition definition = QuestDefinition.parse(id, entry.getValue().getAsJsonObject());
            definitions.put(id, definition);
            var state = (net.minecraft.nbt.CompoundTag) com.mojang.serialization.JsonOps.INSTANCE.convertTo(
                net.minecraft.nbt.NbtOps.INSTANCE, catalog.getAsJsonObject("progress").get(entry.getKey()));
            progress.put(id, QuestProgress.load(state));
            unlocked.put(id, state.getBoolean("unlocked"));
        });
        String title = catalog.get("title").getAsString();
        boolean hasEditPermission = catalog.get("canEdit").getAsBoolean();
        long revision = catalog.get("revision").getAsLong();
        String tracked = catalog.get("tracked").getAsString();
        ResourceLocation trackedId = tracked.isEmpty() ? null : ResourceLocation.parse(tracked);
        // 先解析整份服务端状态，再替换，避免广播期间清空 HUD 与个人进度。
        DEFINITIONS.clear();
        DEFINITIONS.putAll(definitions);
        CHAPTERS.clear();
        CHAPTERS.putAll(chapters);
        PROGRESS.clear();
        PROGRESS.putAll(progress);
        UNLOCKED.clear();
        UNLOCKED.putAll(unlocked);
        bookTitle = title;
        canEdit = hasEditPermission;
        bookRevision = revision;
        trackedQuest = trackedId != null && definitions.containsKey(trackedId) ? trackedId : null;
    }

    public static void receiveProgress(ProgressPayload payload)
    {
        QuestDefinition definition = DEFINITIONS.get(payload.questId());
        if (definition == null || payload.progress() == null || payload.catalogRevision() != bookRevision
            || payload.progress().getInt("revision") != definition.revision())
        {
            return;
        }
        QuestProgress previous = PROGRESS.get(payload.questId());
        QuestProgress current = QuestProgress.load(payload.progress());
        String tracked = payload.progress().getString("tracked");
        trackedQuest = tracked.isEmpty() ? null : ResourceLocation.parse(tracked);
        if (payload.questId().equals(trackedQuest) && previous != null
            && definition.objectives().stream().anyMatch(objective ->
                previous.count(objective.id()) != current.count(objective.id())))
        {
            lastProgressChange = System.currentTimeMillis();
        }
        PROGRESS.put(payload.questId(), current);
        UNLOCKED.put(payload.questId(), payload.progress().getBoolean("unlocked"));
    }

    public static List<QuestDefinition> definitions()
    {
        return List.copyOf(DEFINITIONS.values());
    }

    public static String bookTitle()
    {
        // 兼容旧配置的默认标题；自定义标题仍按原文或翻译键显示。
        return bookTitle.equals("CIRCE · 任务") ? "circe.screen.title" : bookTitle;
    }

    public static List<ChapterDefinition> chapters()
    {
        return CHAPTERS.values().stream().sorted(java.util.Comparator.comparingInt(ChapterDefinition::order)
            .thenComparing(ChapterDefinition::id)).toList();
    }

    public static ChapterDefinition chapter(String id)
    {
        return CHAPTERS.get(id);
    }

    public static boolean canEdit()
    {
        return canEdit;
    }

    public static long bookRevision()
    {
        return bookRevision;
    }

    public static void receiveEditResult(EditResultPayload payload)
    {
        editResult = payload;
        editResultSequence++;
    }

    public static EditResultPayload editResult()
    {
        return editResult;
    }

    public static long editResultSequence()
    {
        return editResultSequence;
    }

    public static QuestDefinition definition(ResourceLocation id)
    {
        return DEFINITIONS.get(id);
    }

    public static QuestProgress progress(QuestDefinition definition)
    {
        return PROGRESS.getOrDefault(definition.id(), new QuestProgress(definition.revision()));
    }

    public static boolean isUnlocked(QuestDefinition definition)
    {
        return UNLOCKED.getOrDefault(definition.id(), false);
    }

    public static ResourceLocation trackedQuest()
    {
        return trackedQuest;
    }

    public static boolean hasRecentProgressChange()
    {
        return System.currentTimeMillis() - lastProgressChange < 1200;
    }

    public static void clear()
    {
        DEFINITIONS.clear();
        PROGRESS.clear();
        UNLOCKED.clear();
        CHAPTERS.clear();
        trackedQuest = null;
        lastProgressChange = 0;
        bookTitle = "circe.screen.title";
        canEdit = false;
        bookRevision = 0;
        editResult = null;
        editResultSequence = 0;
    }
}
