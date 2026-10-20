package cn.dawnstring.circe.quest;

import cn.dawnstring.circe.network.TeamActionPayload;
import cn.dawnstring.circe.network.TeamStatePayload;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class QuestTeamService
{
    private static final Map<UUID, Long> LAST_ACTION = new HashMap<>();

    private QuestTeamService()
    {
    }

    public static List<ServerPlayer> participants(ServerPlayer player, QuestDefinition definition)
    {
        var team = definition.isTeamShared() ? QuestTeamSavedData.get(player.server).team(player.getUUID()) : null;
        if (team == null)
        {
            return List.of(player);
        }
        return player.server.getPlayerList().getPlayers().stream()
            .filter(member -> team.members().containsKey(member.getUUID())).toList();
    }

    public static void handle(ServerPlayer player, TeamActionPayload payload)
    {
        long tick = player.server.overworld().getGameTime();
        Long previous = LAST_ACTION.get(player.getUUID());
        if (previous != null && tick - previous < 10)
        {
            synchronize(player, Component.translatable("circe.team.error.wait"));
            return;
        }
        LAST_ACTION.put(player.getUUID(), tick);
        if (player.isSpectator())
        {
            synchronize(player, Component.translatable("circe.team.error.spectator"));
            return;
        }
        if (payload.operation().equals("refresh"))
        {
            synchronize(player, Component.empty());
            return;
        }
        if (payload.operation().equals("claim_all"))
        {
            QuestService.reconcile(player);
            int claimed = 0;
            for (QuestDefinition definition : QuestService.CATALOG.all())
            {
                if (QuestService.isUnlocked(player, definition) && QuestService.claim(player, definition))
                {
                    claimed++;
                }
            }
            QuestService.tick(player);
            synchronize(player, Component.translatable("circe.team.claimed_count", claimed));
            return;
        }
        try
        {
            apply(player, payload);
            for (ServerPlayer online : List.copyOf(player.server.getPlayerList().getPlayers()))
            {
                QuestService.synchronizeAll(online);
            }
            synchronize(player, Component.translatable("circe.team.saved"));
        }
        catch (IllegalArgumentException exception)
        {
            String key = exception.getMessage();
            synchronize(player, Component.translatable(key != null && key.startsWith("circe.team.error.")
                ? key : "circe.team.error.request"));
        }
    }

    private static void apply(ServerPlayer player, TeamActionPayload payload)
    {
        QuestTeamSavedData saved = QuestTeamSavedData.get(player.server);
        UUID playerId = player.getUUID();
        var team = saved.team(playerId);
        switch (payload.operation())
        {
            case "create" -> saved.create(playerId, player.getGameProfile().getName(), payload.value());
            case "accept" -> saved.accept(playerId, player.getGameProfile().getName(), UUID.fromString(payload.value()));
            case "decline" -> saved.decline(playerId, UUID.fromString(payload.value()));
            case "leave" ->
            {
                requireTeam(team);
                saved.removeMember(team, playerId);
            }
            default ->
            {
                requireTeam(team);
                if (!team.owner().equals(playerId))
                {
                    throw new IllegalArgumentException("circe.team.error.owner_only");
                }
                switch (payload.operation())
                {
                    case "rename" -> saved.rename(team, payload.value());
                    case "invite" ->
                    {
                        ServerPlayer target = player.server.getPlayerList().getPlayer(UUID.fromString(payload.value()));
                        if (target == null)
                        {
                            throw new IllegalArgumentException("circe.team.error.online");
                        }
                        saved.invite(team, target.getUUID());
                    }
                    case "kick" -> saved.removeMember(team, UUID.fromString(payload.value()));
                    case "transfer" -> saved.transfer(team, UUID.fromString(payload.value()));
                    case "disband" ->
                    {
                        QuestSavedData.get(player.server).removeTeam(team.id());
                        saved.disband(team);
                    }
                    default -> throw new IllegalArgumentException("circe.team.error.request");
                }
            }
        }
    }

    private static void requireTeam(QuestTeamSavedData.Team team)
    {
        if (team == null)
        {
            throw new IllegalArgumentException("circe.team.error.no_team");
        }
    }

    public static void synchronize(ServerPlayer player, Component message)
    {
        var saved = QuestTeamSavedData.get(player.server);
        var team = saved.team(player.getUUID());
        JsonObject json = new JsonObject();
        json.addProperty("self", player.getUUID().toString());
        json.addProperty("message", Component.Serializer.toJson(message, player.registryAccess()));
        if (team != null)
        {
            JsonObject entry = summary(team);
            JsonArray members = new JsonArray();
            team.members().forEach((id, name) ->
            {
                JsonObject member = person(id, name);
                member.addProperty("online", player.server.getPlayerList().getPlayer(id) != null);
                members.add(member);
            });
            entry.add("members", members);
            json.add("team", entry);
        }
        JsonArray invitations = new JsonArray();
        saved.invitations(player.getUUID()).forEach(invitation -> invitations.add(summary(invitation)));
        json.add("invitations", invitations);
        JsonArray online = new JsonArray();
        player.server.getPlayerList().getPlayers().stream().limit(256).forEach(candidate ->
        {
            if (!candidate.getUUID().equals(player.getUUID()) && saved.team(candidate.getUUID()) == null)
            {
                online.add(person(candidate.getUUID(), candidate.getGameProfile().getName()));
            }
        });
        json.add("online", online);
        PacketDistributor.sendToPlayer(player, new TeamStatePayload(json.toString()));
    }

    private static JsonObject summary(QuestTeamSavedData.Team team)
    {
        JsonObject entry = new JsonObject();
        entry.addProperty("id", team.id().toString());
        entry.addProperty("name", team.name());
        entry.addProperty("owner", team.owner().toString());
        return entry;
    }

    private static JsonObject person(UUID id, String name)
    {
        JsonObject entry = new JsonObject();
        entry.addProperty("id", id.toString());
        entry.addProperty("name", name);
        return entry;
    }

    public static void forget(UUID playerId)
    {
        LAST_ACTION.remove(playerId);
    }

    public static void clearSession()
    {
        LAST_ACTION.clear();
    }
}
