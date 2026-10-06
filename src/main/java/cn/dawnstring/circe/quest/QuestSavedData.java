package cn.dawnstring.circe.quest;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class QuestSavedData extends SavedData
{
    private static final int FORMAT_VERSION = 1;
    private final Map<UUID, Map<ResourceLocation, QuestProgress>> players = new HashMap<>();
    private final Map<UUID, ResourceLocation> trackedQuests = new HashMap<>();

    public static QuestSavedData get(MinecraftServer server)
    {
        return server.overworld().getDataStorage().computeIfAbsent(
            new Factory<>(QuestSavedData::new, QuestSavedData::load), "circe_quests");
    }

    public QuestProgress progress(UUID playerId, QuestDefinition definition)
    {
        Map<ResourceLocation, QuestProgress> quests = players.computeIfAbsent(playerId, ignored -> new HashMap<>());
        QuestProgress progress = quests.get(definition.id());
        if (progress == null || progress.revision() != definition.revision())
        {
            progress = new QuestProgress(definition.revision());
            quests.put(definition.id(), progress);
            setDirty();
        }
        return progress;
    }

    public QuestProgress peekProgress(UUID playerId, QuestDefinition definition)
    {
        Map<ResourceLocation, QuestProgress> quests = players.get(playerId);
        QuestProgress progress = quests == null ? null : quests.get(definition.id());
        return progress == null || progress.revision() != definition.revision()
            ? new QuestProgress(definition.revision()) : QuestProgress.load(progress.save());
    }

    public ResourceLocation tracked(UUID playerId)
    {
        return trackedQuests.get(playerId);
    }

    public void track(UUID playerId, ResourceLocation questId)
    {
        if (questId == null)
        {
            trackedQuests.remove(playerId);
        }
        else
        {
            trackedQuests.put(playerId, questId);
        }
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries)
    {
        tag.putInt("format", FORMAT_VERSION);
        CompoundTag playerEntries = new CompoundTag();
        for (var playerEntry : players.entrySet())
        {
            CompoundTag playerTag = new CompoundTag();
            CompoundTag questEntries = new CompoundTag();
            playerEntry.getValue().forEach((questId, progress) -> questEntries.put(questId.toString(), progress.save()));
            playerTag.put("quests", questEntries);
            ResourceLocation tracked = trackedQuests.get(playerEntry.getKey());
            if (tracked != null)
            {
                playerTag.putString("tracked", tracked.toString());
            }
            playerEntries.put(playerEntry.getKey().toString(), playerTag);
        }
        tag.put("players", playerEntries);
        return tag;
    }

    public static QuestSavedData load(CompoundTag tag, HolderLookup.Provider registries)
    {
        if (tag.getInt("format") != FORMAT_VERSION)
        {
            throw new IllegalArgumentException("Unsupported Circe save format: " + tag.getInt("format"));
        }
        QuestSavedData saved = new QuestSavedData();
        CompoundTag playerEntries = tag.getCompound("players");
        for (String playerKey : playerEntries.getAllKeys())
        {
            UUID playerId = UUID.fromString(playerKey);
            CompoundTag playerTag = playerEntries.getCompound(playerKey);
            CompoundTag questEntries = playerTag.getCompound("quests");
            Map<ResourceLocation, QuestProgress> quests = new HashMap<>();
            for (String questKey : questEntries.getAllKeys())
            {
                quests.put(ResourceLocation.parse(questKey), QuestProgress.load(questEntries.getCompound(questKey)));
            }
            saved.players.put(playerId, quests);
            if (playerTag.contains("tracked"))
            {
                saved.trackedQuests.put(playerId, ResourceLocation.parse(playerTag.getString("tracked")));
            }
        }
        return saved;
    }
}
