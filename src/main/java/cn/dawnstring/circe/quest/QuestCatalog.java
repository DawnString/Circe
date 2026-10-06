package cn.dawnstring.circe.quest;

import cn.dawnstring.circe.Circe;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import net.minecraft.util.GsonHelper;
import cn.dawnstring.circe.network.EditPayload;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.io.Reader;
import com.google.gson.JsonParser;

public class QuestCatalog extends SimpleJsonResourceReloadListener
{
    public static final int MAX_CATALOG_CHARACTERS = 262_144;
    private Map<ResourceLocation, QuestDefinition> definitions = Map.of();
    private String serialized = "{}";
    private Map<ResourceLocation, QuestDefinition> baseDefinitions = Map.of();
    private JsonObject book;
    private String observedFileFingerprint = "";
    private net.minecraft.core.HolderLookup.Provider lookup;

    public void setLookup(net.minecraft.core.HolderLookup.Provider lookup)
    {
        this.lookup = lookup;
    }

    public QuestCatalog()
    {
        super(new Gson(), "circe/quests");
    }

    @Override
    protected Map<ResourceLocation, JsonElement> prepare(ResourceManager resourceManager, ProfilerFiller profiler)
    {
        Map<ResourceLocation, JsonElement> entries = new LinkedHashMap<>();
        FileToIdConverter converter = FileToIdConverter.json("circe/quests");
        for (var entry : converter.listMatchingResources(resourceManager).entrySet())
        {
            ResourceLocation id = converter.fileToId(entry.getKey());
            try (Reader reader = entry.getValue().openAsReader())
            {
                entries.put(id, JsonParser.parseReader(reader));
            }
            catch (Exception exception)
            {
                throw new IllegalArgumentException("Cannot read Circe quest " + id, exception);
            }
        }
        return entries;
    }

    @Override
    protected void apply(
        Map<ResourceLocation, JsonElement> entries,
        ResourceManager resourceManager,
        ProfilerFiller profiler)
    {
        Map<ResourceLocation, QuestDefinition> candidate = new LinkedHashMap<>();
        try
        {
            if (entries.size() > 128)
            {
                throw new IllegalArgumentException("At most 128 quests are supported in this version");
            }
            for (var entry : entries.entrySet())
            {
                try
                {
                    QuestDefinition definition = QuestDefinition.parse(entry.getKey(), entry.getValue().getAsJsonObject());
                    validateResources(definition);
                    candidate.put(entry.getKey(), definition);
                }
                catch (RuntimeException exception)
                {
                    throw new IllegalArgumentException("Invalid quest " + entry.getKey(), exception);
                }
            }
            JsonObject loadedBook = QuestBookStore.read();
            if (loadedBook == null)
            {
                loadedBook = new JsonObject();
                loadedBook.addProperty("title", "CIRCE · 任务");
                loadedBook.addProperty("revision", 1L);
                JsonObject overrides = new JsonObject();
                candidate.forEach((id, definition) -> overrides.add(id.toString(), definition.toJson()));
                loadedBook.add("quests", overrides);
                loadedBook.add("deleted", new JsonArray());
            }
            boolean hasMigration = migrateChapters(loadedBook, candidate);
            Map<ResourceLocation, QuestDefinition> merged = validateBook(loadedBook, candidate);
            if (hasMigration || !java.nio.file.Files.exists(QuestBookStore.path()))
            {
                QuestBookStore.write(loadedBook);
            }
            baseDefinitions = Map.copyOf(candidate);
            publish(loadedBook, merged, cn.dawnstring.circe.api.event.QuestLifecycleEvent.CatalogReloaded.Reason.DATA_RELOAD);
            observedFileFingerprint = fileFingerprint();
            Circe.LOGGER.info("Loaded {} Circe quests", definitions.size());
        }
        catch (Exception exception)
        {
            throw new IllegalArgumentException("Circe quest reload rejected; previous catalog retained", exception);
        }
    }

    public QuestDefinition get(ResourceLocation id)
    {
        return definitions.get(id);
    }

    public List<QuestDefinition> all()
    {
        return sorted(definitions);
    }

    public String serialized()
    {
        return serialized;
    }

    public long revision()
    {
        return book == null ? 0 : GsonHelper.getAsLong(book, "revision", 1);
    }

    public String title()
    {
        return book == null ? "CIRCE · 任务" : GsonHelper.getAsString(book, "title");
    }

    public JsonObject chaptersJson()
    {
        return book == null ? new JsonObject() : book.getAsJsonObject("chapters").deepCopy();
    }

    private static boolean migrateChapters(JsonObject book, Map<ResourceLocation, QuestDefinition> defaults)
    {
        boolean hasChanges = !book.has("chapters");
        if (hasChanges)
        {
            book.add("chapters", new JsonObject());
        }
        JsonObject chapters = book.getAsJsonObject("chapters");
        Map<ResourceLocation, QuestDefinition> merged = new LinkedHashMap<>(defaults);
        book.getAsJsonObject("quests").entrySet().forEach(entry ->
            merged.put(ResourceLocation.parse(entry.getKey()), QuestDefinition.parse(ResourceLocation.parse(entry.getKey()), entry.getValue().getAsJsonObject())));
        for (var entry : book.getAsJsonArray("deleted"))
        {
            merged.remove(ResourceLocation.parse(entry.getAsString()));
        }
        for (var definition : merged.values())
        {
            if (!chapters.has(definition.chapter()))
            {
                chapters.add(definition.chapter(), new ChapterDefinition(definition.chapter(), definition.chapter(),
                    chapters.size(), ResourceLocation.parse("minecraft:book")).toJson());
                hasChanges = true;
            }
        }
        if (hasChanges)
        {
            book.addProperty("revision", GsonHelper.getAsLong(book, "revision", 1) + 1);
        }
        return hasChanges;
    }

    public void edit(EditPayload payload) throws java.io.IOException
    {
        if (payload.revision() != revision())
        {
            throw new IllegalArgumentException("任务书已被更新，请返回后重新打开编辑器");
        }
        if (!book.equals(QuestBookStore.read()))
        {
            throw new IllegalArgumentException("外部任务书已被修改，请重新加载后再编辑");
        }
        JsonObject candidateBook = book.deepCopy();
        JsonObject quests = candidateBook.getAsJsonObject("quests");
        JsonArray deleted = candidateBook.getAsJsonArray("deleted");
        switch (payload.operation())
        {
            case "chapter_save" ->
            {
                if (!candidateBook.getAsJsonObject("chapters").has(payload.questId()))
                {
                    ResourceLocation.parse(payload.questId());
                }
                candidateBook.getAsJsonObject("chapters").add(payload.questId(), JsonParser.parseString(payload.json()).getAsJsonObject());
            }
            case "chapter_delete" -> candidateBook.getAsJsonObject("chapters").remove(payload.questId());
            case "title" -> candidateBook.addProperty("title", payload.json());
            case "move" ->
            {
                ResourceLocation id = ResourceLocation.parse(payload.questId());
                QuestDefinition definition = definitions.get(id);
                if (definition == null)
                {
                    throw new IllegalArgumentException("任务不存在");
                }
                var position = QuestDefinition.GraphPosition.parse(JsonParser.parseString(payload.json()).getAsJsonObject());
                JsonObject edited = definition.toJson();
                edited.add("position", position.toJson());
                quests.add(id.toString(), edited);
            }
            case "auto_layout" ->
            {
                if (!candidateBook.getAsJsonObject("chapters").has(payload.questId()))
                {
                    throw new IllegalArgumentException("章节不存在");
                }
                for (QuestDefinition definition : all())
                {
                    if (definition.chapter().equals(payload.questId()))
                    {
                        JsonObject edited = definition.toJson();
                        edited.remove("position");
                        quests.add(definition.id().toString(), edited);
                    }
                }
            }
            case "save" ->
            {
                ResourceLocation id = ResourceLocation.parse(payload.questId());
                JsonObject edited = JsonParser.parseString(payload.json()).getAsJsonObject();
                QuestDefinition definition = QuestDefinition.parse(id, edited);
                QuestDefinition existing = definitions.get(id);
                if (existing != null && (definition.revision() < existing.revision()
                    || (definition.revision() == existing.revision()
                    && (!sameObjectives(definition.objectives(), existing.objectives())
                    || !definition.prerequisites().equals(existing.prerequisites())))))
                {
                    throw new IllegalArgumentException("修改目标或前置条件时请增加任务版本，版本不能降低");
                }
                quests.add(id.toString(), edited);
                deleted.remove(new com.google.gson.JsonPrimitive(id.toString()));
            }
            case "delete" ->
            {
                ResourceLocation id = ResourceLocation.parse(payload.questId());
                if (!definitions.containsKey(id))
                {
                    throw new IllegalArgumentException("任务不存在");
                }
                quests.remove(id.toString());
                if (!deleted.contains(new com.google.gson.JsonPrimitive(id.toString())))
                {
                    deleted.add(id.toString());
                }
            }
            default -> throw new IllegalArgumentException("未知编辑操作");
        }
        candidateBook.addProperty("revision", revision() + 1);
        Map<ResourceLocation, QuestDefinition> candidate = validateBook(candidateBook, baseDefinitions);
        QuestBookStore.write(candidateBook);
        publish(candidateBook, candidate, cn.dawnstring.circe.api.event.QuestLifecycleEvent.CatalogReloaded.Reason.EDIT);
        observedFileFingerprint = fileFingerprint();
    }

    public boolean reloadExternalChanges()
    {
        if (book == null)
        {
            return false;
        }
        try
        {
            String fingerprint = fileFingerprint();
            if (fingerprint.equals(observedFileFingerprint))
            {
                return false;
            }
            observedFileFingerprint = fingerprint;
            JsonObject loaded = QuestBookStore.read();
            if (loaded == null || loaded.equals(book))
            {
                return false;
            }
            migrateChapters(loaded, baseDefinitions);
            loaded.addProperty("revision", Math.max(revision() + 1, GsonHelper.getAsLong(loaded, "revision", 1)));
            Map<ResourceLocation, QuestDefinition> candidate = validateBook(loaded, baseDefinitions);
            QuestBookStore.write(loaded);
            publish(loaded, candidate, cn.dawnstring.circe.api.event.QuestLifecycleEvent.CatalogReloaded.Reason.EXTERNAL_RELOAD);
            observedFileFingerprint = fileFingerprint();
            Circe.LOGGER.info("Reloaded external Circe quest book revision {}", revision());
            return true;
        }
        catch (Exception exception)
        {
            Circe.LOGGER.warn("External Circe quest book rejected; active catalog retained", exception);
            return false;
        }
    }

    private static String fileFingerprint() throws java.io.IOException
    {
        var path = QuestBookStore.path();
        if (!java.nio.file.Files.exists(path))
        {
            return "missing";
        }
        return java.nio.file.Files.getLastModifiedTime(path) + ":" + java.nio.file.Files.size(path);
    }

    private Map<ResourceLocation, QuestDefinition> validateBook(
        JsonObject book, Map<ResourceLocation, QuestDefinition> defaults)
    {
        String title = GsonHelper.getAsString(book, "title");
        if (title.isBlank() || title.length() > 128 || GsonHelper.getAsLong(book, "revision", 1) < 1)
        {
            throw new IllegalArgumentException("任务书标题应为 1..128 个字符，版本必须为正数");
        }
        Map<ResourceLocation, QuestDefinition> candidate = new LinkedHashMap<>(defaults);
        JsonObject quests = GsonHelper.getAsJsonObject(book, "quests");
        if (quests.size() > 128)
        {
            throw new IllegalArgumentException("任务数量不能超过 128");
        }
        for (var entry : quests.entrySet())
        {
            ResourceLocation id = ResourceLocation.parse(entry.getKey());
            candidate.put(id, QuestDefinition.parse(id, entry.getValue().getAsJsonObject()));
        }
        JsonArray deleted = GsonHelper.getAsJsonArray(book, "deleted");
        if (deleted.size() > 256)
        {
            throw new IllegalArgumentException("删除记录不能超过 256 项");
        }
        for (var entry : deleted)
        {
            candidate.remove(ResourceLocation.parse(entry.getAsString()));
        }
        if (candidate.size() > 128)
        {
            throw new IllegalArgumentException("任务数量不能超过 128");
        }
        candidate.values().forEach(QuestCatalog::validateResources);
        JsonObject chapters = GsonHelper.getAsJsonObject(book, "chapters");
        if (chapters.size() > 64)
        {
            throw new IllegalArgumentException("最多支持 64 个章节");
        }
        for (var entry : chapters.entrySet())
        {
            ChapterDefinition chapter = ChapterDefinition.parse(entry.getKey(), entry.getValue().getAsJsonObject());
            if (!BuiltInRegistries.ITEM.containsKey(chapter.icon()))
            {
                throw new IllegalArgumentException("章节图标物品不存在");
            }
        }
        for (var definition : candidate.values())
        {
            if (!chapters.has(definition.chapter()))
            {
                throw new IllegalArgumentException("请先创建章节，或移走章节内的任务后再删除章节");
            }
            if (!definition.image().isEmpty())
            {
                ResourceLocation.parse(definition.image());
            }
            definition.rewards().forEach(reward -> reward.type().handler().validate(reward, lookup));
        }
        validateGraph(candidate);
        if (serialize(candidate).length() + chapters.toString().length() > MAX_CATALOG_CHARACTERS - 2048)
        {
            throw new IllegalArgumentException("任务书超过同步大小限制");
        }
        return candidate;
    }

    private static boolean sameObjectives(List<QuestDefinition.Objective> first, List<QuestDefinition.Objective> second)
    {
        if (first.size() != second.size())
        {
            return false;
        }
        for (var objective : first)
        {
            boolean hasMatch = second.stream().anyMatch(previous -> previous.id().equals(objective.id())
                && previous.type() == objective.type() && previous.target().equals(objective.target())
                && previous.count() == objective.count()
                && previous.configuration().equals(objective.configuration())
                && (objective.type() != ObjectiveType.STATISTIC
                    || previous.statisticType().equals(objective.statisticType())
                        && previous.isSinceUnlock() == objective.isSinceUnlock()));
            if (!hasMatch)
            {
                return false;
            }
        }
        return true;
    }

    private void publish(JsonObject book, Map<ResourceLocation, QuestDefinition> candidate,
        cn.dawnstring.circe.api.event.QuestLifecycleEvent.CatalogReloaded.Reason reason)
    {
        List<QuestDefinition> previous = all();
        this.book = book;
        definitions = Map.copyOf(candidate);
        serialized = serialize(candidate);
        cn.dawnstring.circe.api.event.QuestLifecycleEvent.publish(
            new cn.dawnstring.circe.api.event.QuestLifecycleEvent.CatalogReloaded(previous, all(), revision(), reason));
    }

    private static String serialize(Map<ResourceLocation, QuestDefinition> definitions)
    {
        JsonObject json = new JsonObject();
        sorted(definitions).forEach(definition -> json.add(definition.id().toString(), definition.toJson()));
        return json.toString();
    }

    public static void validateGraph(Map<ResourceLocation, QuestDefinition> definitions)
    {
        Set<ResourceLocation> visited = new HashSet<>();
        Set<ResourceLocation> visiting = new HashSet<>();
        for (ResourceLocation id : definitions.keySet())
        {
            visit(id, definitions, visiting, visited);
        }
    }

    private static void visit(
        ResourceLocation id,
        Map<ResourceLocation, QuestDefinition> definitions,
        Set<ResourceLocation> visiting,
        Set<ResourceLocation> visited)
    {
        if (visited.contains(id))
        {
            return;
        }
        QuestDefinition definition = definitions.get(id);
        if (definition == null)
        {
            throw new IllegalArgumentException("Missing prerequisite " + id);
        }
        if (!visiting.add(id))
        {
            throw new IllegalArgumentException("Dependency cycle at " + id);
        }
        for (ResourceLocation prerequisite : definition.prerequisites())
        {
            visit(prerequisite, definitions, visiting, visited);
        }
        visiting.remove(id);
        visited.add(id);
    }

    private static List<QuestDefinition> sorted(Map<ResourceLocation, QuestDefinition> definitions)
    {
        return definitions.values().stream()
            .sorted(Comparator.comparing(QuestDefinition::chapter)
                .thenComparingInt(QuestDefinition::order)
                .thenComparing(definition -> definition.id().toString()))
            .toList();
    }

    private static void validateResources(QuestDefinition definition)
    {
        for (var objective : definition.objectives())
        {
            objective.type().validate(objective);
            boolean isValid = switch (objective.type().targetKind())
            {
                case ITEM -> BuiltInRegistries.ITEM.containsKey(objective.target())
                    && !objective.target().equals(ResourceLocation.withDefaultNamespace("air"));
                case ENTITY -> BuiltInRegistries.ENTITY_TYPE.containsKey(objective.target());
                case EVENT -> true;
                case STATISTIC -> QuestStatistics.resolve(objective.statisticType(), objective.target()) != null;
            };
            if (!isValid)
            {
                throw new IllegalArgumentException("Unknown objective target " + objective.target());
            }
        }
        for (var reward : definition.rewards())
        {
            if (reward.type().hasItem() && (!BuiltInRegistries.ITEM.containsKey(reward.item())
                || reward.item().equals(ResourceLocation.withDefaultNamespace("air"))))
            {
                throw new IllegalArgumentException("Unknown reward item " + reward.item());
            }
        }
    }
}
