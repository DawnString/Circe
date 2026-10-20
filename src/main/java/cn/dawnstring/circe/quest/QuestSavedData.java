package cn.dawnstring.circe.quest;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class QuestSavedData extends SavedData
{
    private static final int FORMAT_VERSION = 2;
    private final Map<UUID, Map<ResourceLocation, QuestProgress>> players = new HashMap<>();
    private final Map<UUID, Map<ResourceLocation, QuestProgress>> teams = new HashMap<>();
    private final Map<UUID, Map<ResourceLocation, Integer>> claims = new HashMap<>();
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

    public QuestProgress progressFor(ServerPlayer player, QuestDefinition definition)
    {
        var team = definition.isTeamShared() ? QuestTeamSavedData.get(player.server).team(player.getUUID()) : null;
        if (team == null)
        {
            return progress(player.getUUID(), definition);
        }
        var quests = teams.computeIfAbsent(team.id(), ignored -> new HashMap<>());
        QuestProgress progress = quests.get(definition.id());
        if (progress == null || progress.revision() != definition.revision())
        {
            progress = new QuestProgress(definition.revision());
            quests.put(definition.id(), progress);
            setDirty();
        }
        return progress;
    }

    public QuestProgress peekFor(ServerPlayer player, QuestDefinition definition)
    {
        var team = definition.isTeamShared() ? QuestTeamSavedData.get(player.server).team(player.getUUID()) : null;
        if (team == null)
        {
            return peekProgress(player.getUUID(), definition);
        }
        var quests = teams.get(team.id());
        QuestProgress progress = quests == null ? null : quests.get(definition.id());
        return progress == null || progress.revision() != definition.revision()
            ? new QuestProgress(definition.revision()) : QuestProgress.load(progress.save());
    }

    public boolean isClaimed(ServerPlayer player, QuestDefinition definition)
    {
        QuestProgress progress = peekFor(player, definition);
        if (!progress.isCompleted())
        {
            return false;
        }
        boolean hasPersonalClaim = claims.getOrDefault(player.getUUID(), Map.of())
            .getOrDefault(definition.id(), 0) == definition.revision()
            || peekProgress(player.getUUID(), definition).isClaimed();
        return definition.rewards().isEmpty() || hasPersonalClaim || progress.isClaimed();
    }

    public boolean claimFor(ServerPlayer player, QuestDefinition definition)
    {
        QuestProgress progress = progressFor(player, definition);
        if (!progress.isCompleted() || isClaimed(player, definition))
        {
            return false;
        }
        // 领奖账本独立于队伍，退队、换队或重建队伍不能重复领同一版本的奖励。
        claims.computeIfAbsent(player.getUUID(), ignored -> new HashMap<>()).put(definition.id(), definition.revision());
        if (!definition.isTeamShared() || definition.isTeamReward()
            || QuestTeamSavedData.get(player.server).team(player.getUUID()) == null)
        {
            progress.claim();
        }
        setDirty();
        return true;
    }

    public void removeTeam(UUID teamId)
    {
        teams.remove(teamId);
        setDirty();
    }

    public CompoundTag stateFor(ServerPlayer player, QuestDefinition definition)
    {
        CompoundTag state = progressFor(player, definition).save();
        state.putBoolean("claimed", isClaimed(player, definition));
        return state;
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
        CompoundTag teamEntries = new CompoundTag();
        teams.forEach((teamId, quests) ->
        {
            CompoundTag entries = new CompoundTag();
            quests.forEach((questId, progress) -> entries.put(questId.toString(), progress.save()));
            teamEntries.put(teamId.toString(), entries);
        });
        tag.put("teams", teamEntries);
        CompoundTag claimEntries = new CompoundTag();
        claims.forEach((playerId, quests) ->
        {
            CompoundTag entries = new CompoundTag();
            quests.forEach((questId, revision) -> entries.putInt(questId.toString(), revision));
            claimEntries.put(playerId.toString(), entries);
        });
        tag.put("claims", claimEntries);
        return tag;
    }

    public static QuestSavedData load(CompoundTag tag, HolderLookup.Provider registries)
    {
        if (tag.getInt("format") != 1 && tag.getInt("format") != FORMAT_VERSION)
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
        CompoundTag teamEntries = tag.getCompound("teams");
        for (String teamKey : teamEntries.getAllKeys())
        {
            Map<ResourceLocation, QuestProgress> quests = new HashMap<>();
            CompoundTag entries = teamEntries.getCompound(teamKey);
            entries.getAllKeys().forEach(key -> quests.put(ResourceLocation.parse(key), QuestProgress.load(entries.getCompound(key))));
            saved.teams.put(UUID.fromString(teamKey), quests);
        }
        CompoundTag claimEntries = tag.getCompound("claims");
        for (String playerKey : claimEntries.getAllKeys())
        {
            Map<ResourceLocation, Integer> quests = new HashMap<>();
            CompoundTag entries = claimEntries.getCompound(playerKey);
            entries.getAllKeys().forEach(key -> quests.put(ResourceLocation.parse(key), entries.getInt(key)));
            saved.claims.put(UUID.fromString(playerKey), quests);
        }
        return saved;
    }
}
