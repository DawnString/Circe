package cn.dawnstring.circe.quest;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class QuestTeamSavedData extends SavedData
{
    private final Map<UUID, Team> teams = new LinkedHashMap<>();
    private final Map<UUID, UUID> memberships = new LinkedHashMap<>();

    public static final class Team
    {
        private final UUID id;
        private UUID owner;
        private String name;
        private final Map<UUID, String> members = new LinkedHashMap<>();
        private final Set<UUID> invitations = new LinkedHashSet<>();

        private Team(UUID id, UUID owner, String name)
        {
            this.id = id;
            this.owner = owner;
            this.name = name;
        }

        public UUID id()
        {
            return id;
        }

        public UUID owner()
        {
            return owner;
        }

        public String name()
        {
            return name;
        }

        public Map<UUID, String> members()
        {
            return Collections.unmodifiableMap(members);
        }

        public Set<UUID> invitations()
        {
            return Set.copyOf(invitations);
        }
    }

    public static QuestTeamSavedData get(MinecraftServer server)
    {
        return server.overworld().getDataStorage().computeIfAbsent(
            new Factory<>(QuestTeamSavedData::new, QuestTeamSavedData::load), "circe_teams");
    }

    public Team team(UUID member)
    {
        return teams.get(memberships.get(member));
    }

    public Team byId(UUID id)
    {
        return teams.get(id);
    }

    public java.util.List<Team> invitations(UUID playerId)
    {
        return teams.values().stream().filter(team -> team.invitations.contains(playerId)).toList();
    }

    public Team create(UUID owner, String playerName, String name)
    {
        if (team(owner) != null)
        {
            throw new IllegalArgumentException("circe.team.error.already_member");
        }
        Team team = new Team(UUID.randomUUID(), owner, validName(name));
        teams.put(team.id, team);
        addMember(team, owner, playerName);
        return team;
    }

    public void rename(Team team, String name)
    {
        team.name = validName(name);
        setDirty();
    }

    public void invite(Team team, UUID playerId)
    {
        if (team.members.size() >= 64 || team.invitations.size() >= 64 || invitations(playerId).size() >= 16)
        {
            throw new IllegalArgumentException("circe.team.error.full");
        }
        if (team(playerId) != null)
        {
            throw new IllegalArgumentException("circe.team.error.already_member");
        }
        team.invitations.add(playerId);
        setDirty();
    }

    public void accept(UUID playerId, String playerName, UUID teamId)
    {
        Team team = byId(teamId);
        if (team == null || !team.invitations.contains(playerId))
        {
            throw new IllegalArgumentException("circe.team.error.invitation");
        }
        if (team(playerId) != null || team.members.size() >= 64)
        {
            throw new IllegalArgumentException("circe.team.error.full");
        }
        teams.values().forEach(candidate -> candidate.invitations.remove(playerId));
        addMember(team, playerId, playerName);
    }

    public void decline(UUID playerId, UUID teamId)
    {
        Team team = byId(teamId);
        if (team != null)
        {
            team.invitations.remove(playerId);
            setDirty();
        }
    }

    public void removeMember(Team team, UUID member)
    {
        if (member.equals(team.owner) || !team.members.containsKey(member))
        {
            throw new IllegalArgumentException("circe.team.error.owner_leave");
        }
        team.members.remove(member);
        memberships.remove(member);
        setDirty();
    }

    public void transfer(Team team, UUID member)
    {
        if (!team.members.containsKey(member))
        {
            throw new IllegalArgumentException("circe.team.error.member");
        }
        team.owner = member;
        setDirty();
    }

    public void disband(Team team)
    {
        team.members.keySet().forEach(memberships::remove);
        teams.remove(team.id);
        setDirty();
    }

    private void addMember(Team team, UUID playerId, String playerName)
    {
        team.members.put(playerId, playerName);
        memberships.put(playerId, team.id);
        setDirty();
    }

    private static String validName(String name)
    {
        String trimmed = name.strip();
        if (trimmed.isEmpty() || trimmed.length() > 32
            || trimmed.chars().anyMatch(character -> Character.isISOControl(character) || character == 167))
        {
            throw new IllegalArgumentException("circe.team.error.name");
        }
        return trimmed;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries)
    {
        tag.putInt("format", 1);
        CompoundTag entries = new CompoundTag();
        for (Team team : teams.values())
        {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("owner", team.owner);
            entry.putString("name", team.name);
            CompoundTag members = new CompoundTag();
            team.members.forEach((id, name) -> members.putString(id.toString(), name));
            entry.put("members", members);
            CompoundTag invitations = new CompoundTag();
            team.invitations.forEach(id -> invitations.putBoolean(id.toString(), true));
            entry.put("invitations", invitations);
            entries.put(team.id.toString(), entry);
        }
        tag.put("teams", entries);
        return tag;
    }

    public static QuestTeamSavedData load(CompoundTag tag, HolderLookup.Provider registries)
    {
        if (tag.getInt("format") != 1)
        {
            throw new IllegalArgumentException("Unsupported Circe team format");
        }
        QuestTeamSavedData saved = new QuestTeamSavedData();
        CompoundTag entries = tag.getCompound("teams");
        for (String key : entries.getAllKeys())
        {
            CompoundTag entry = entries.getCompound(key);
            Team team = new Team(UUID.fromString(key), entry.getUUID("owner"), validName(entry.getString("name")));
            CompoundTag members = entry.getCompound("members");
            for (String memberKey : members.getAllKeys())
            {
                UUID member = UUID.fromString(memberKey);
                if (saved.memberships.putIfAbsent(member, team.id) != null)
                {
                    throw new IllegalArgumentException("Duplicate Circe team membership");
                }
                team.members.put(member, members.getString(memberKey));
            }
            if (!team.members.containsKey(team.owner) || team.members.size() > 64)
            {
                throw new IllegalArgumentException("Invalid Circe team owner or size");
            }
            entry.getCompound("invitations").getAllKeys().forEach(id -> team.invitations.add(UUID.fromString(id)));
            saved.teams.put(team.id, team);
        }
        return saved;
    }
}
