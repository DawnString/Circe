package cn.dawnstring.circe.client;

import com.google.gson.JsonArray;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.stream.StreamSupport;

public final class QuestTeamScreen extends QuestEditingScreen
{
    private String teamName = "";
    private String selectedMember = "";
    private String selectedOnline = "";
    private String selectedInvitation = "";
    private String confirmation = "";
    private long observedSequence;
    private boolean isPending;

    public QuestTeamScreen(Screen parent)
    {
        super(parent, "circe.team.title");
        var team = ClientQuestTeams.team();
        teamName = team == null ? "" : team.get("name").getAsString();
        observedSequence = ClientQuestTeams.sequence();
    }

    @Override
    protected boolean requiresEditPermission()
    {
        return false;
    }

    @Override
    protected void configureFrame(int viewWidth)
    {
        frameWidth = Math.min(660, frameWidth);
        frameLeft = (viewWidth - frameWidth) / 2;
    }

    @Override
    protected void initEditor()
    {
        int left = frameLeft + 20;
        int top = frameTop + 65;
        int half = (frameWidth - 52) / 2;
        int right = left + half + 12;
        var team = ClientQuestTeams.team();
        var name = field(left, top, half, text("name"), teamName, 32);
        name.setResponder(value -> teamName = value);
        name.setEditable(team == null || ClientQuestTeams.isOwner());
        if (team == null)
        {
            button(right, top, half, text("create"), () -> request("create", teamName, false)).primary();
            var invitations = options(ClientQuestTeams.entries("invitations"));
            selectedInvitation = select(invitations, selectedInvitation);
            label(text("invitations"), left, top + 57);
            addRenderableWidget(new QuestDropdown(left, top + 72, half, invitations, selectedInvitation,
                value -> selectedInvitation = value));
            button(right, top + 72, half / 2 - 4, text("accept"), () -> request("accept", selectedInvitation, false))
                .active = !invitations.isEmpty();
            button(right + half / 2 + 4, top + 72, half / 2 - 4, text("decline"),
                () -> request("decline", selectedInvitation, false)).active = !invitations.isEmpty();
            label(text("join_hint"), left, top + 130);
        }
        else
        {
            boolean isOwner = ClientQuestTeams.isOwner();
            button(right, top, half, text("rename"), () -> request("rename", teamName, false)).active = isOwner;
            label(text(isOwner ? "role_owner" : "role_member"), left, top + 35);
            var members = options(team.getAsJsonArray("members")).stream()
                .filter(option -> !option.id().equals(team.get("owner").getAsString())).toList();
            selectedMember = select(members, selectedMember);
            label(text("members"), left, top + 65);
            addRenderableWidget(new QuestDropdown(left, top + 80, half, members, selectedMember,
                value -> selectedMember = value));
            button(left, top + 116, half / 2 - 4, text("kick"),
                () -> request("kick", selectedMember, true)).active = isOwner && !members.isEmpty();
            button(left + half / 2 + 4, top + 116, half / 2 - 4, text("transfer"),
                () -> request("transfer", selectedMember, true)).active = isOwner && !members.isEmpty();
            var online = options(ClientQuestTeams.entries("online"));
            selectedOnline = select(online, selectedOnline);
            label(text("invite_player"), right, top + 65);
            addRenderableWidget(new QuestDropdown(right, top + 80, half, online, selectedOnline,
                value -> selectedOnline = value));
            button(right, top + 116, half, text("invite"),
                () -> request("invite", selectedOnline, false)).active = isOwner && !online.isEmpty();
            button(left, top + 174, half, text(isOwner ? "disband" : "leave"),
                () -> request(isOwner ? "disband" : "leave", "", true));
            label(text("owner_hint"), left, top + 212);
        }
        int hintY = top + 254;
        for (var line : font.getSplitter().splitLines(Component.translatable("circe.team.progress_hint"), frameWidth - 40,
            net.minecraft.network.chat.Style.EMPTY))
        {
            label(line.getString(), left, hintY);
            hintY += 12;
        }
        button(left, frameTop + frameHeight - 26, 100, text("refresh"), () -> request("refresh", "", false));
        button(left + 112, frameTop + frameHeight - 26, 100, text("claim_all"), () -> request("claim_all", "", false)).primary();
        button(left + 224, frameTop + frameHeight - 26, 100, QuestTranslations.text("circe.button.back"), this::onClose);
        if (isPending)
        {
            children().forEach(child ->
            {
                if (child instanceof net.minecraft.client.gui.components.AbstractWidget widget)
                {
                    widget.active = false;
                }
            });
        }
    }

    private void request(String operation, String value, boolean needsConfirmation)
    {
        if (isPending)
        {
            return;
        }
        String key = operation + ":" + value;
        if (needsConfirmation && !confirmation.equals(key))
        {
            confirmation = key;
            status = Component.translatable("circe.team.confirm");
            return;
        }
        confirmation = "";
        observedSequence = ClientQuestTeams.sequence();
        isPending = true;
        status = Component.translatable("circe.team.pending");
        ClientQuestTeams.send(operation, value);
        rebuild();
    }

    @Override
    public void tick()
    {
        super.tick();
        if (observedSequence != ClientQuestTeams.sequence())
        {
            observedSequence = ClientQuestTeams.sequence();
            isPending = false;
            status = ClientQuestTeams.message();
            confirmation = "";
            var team = ClientQuestTeams.team();
            if (team != null)
            {
                teamName = team.get("name").getAsString();
            }
            rebuild();
        }
    }

    private static String text(String key)
    {
        return QuestTranslations.text("circe.team." + key);
    }

    private static String select(List<QuestDropdown.Option> options, String previous)
    {
        return options.stream().anyMatch(option -> option.id().equals(previous))
            ? previous : options.isEmpty() ? "" : options.getFirst().id();
    }

    private static List<QuestDropdown.Option> options(JsonArray entries)
    {
        return StreamSupport.stream(entries.spliterator(), false).map(element ->
        {
            var entry = element.getAsJsonObject();
            Component label = Component.literal(entry.get("name").getAsString());
            if (entry.has("online"))
            {
                label = label.copy().append(Component.translatable(entry.get("online").getAsBoolean()
                    ? "circe.team.online" : "circe.team.offline"));
            }
            return new QuestDropdown.Option(entry.get("id").getAsString(), label);
        }).toList();
    }
}
