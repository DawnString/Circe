package cn.dawnstring.circe.quest;

import cn.dawnstring.circe.network.ActionPayload;
import cn.dawnstring.circe.network.CatalogPayload;
import cn.dawnstring.circe.network.ProgressPayload;
import cn.dawnstring.circe.network.EditPayload;
import cn.dawnstring.circe.network.EditResultPayload;
import cn.dawnstring.circe.Circe;
import cn.dawnstring.circe.api.QuestApi;
import cn.dawnstring.circe.api.QuestState;
import cn.dawnstring.circe.api.event.QuestLifecycleEvent;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class QuestService
{
    public static final QuestCatalog CATALOG = new QuestCatalog();
    private static final Map<UUID, Map<ResourceLocation, CompoundTag>> LAST_SENT = new HashMap<>();
    private static final Map<UUID, Long> LAST_ACTION = new HashMap<>();
    private static final Map<UUID, Boolean> LAST_PERMISSION = new HashMap<>();
    private static final Map<UUID, Map<String, Long>> POLL_RETRY_TICKS = new HashMap<>();

    private QuestService()
    {
    }

    public static boolean isUnlocked(ServerPlayer player, QuestDefinition definition)
    {
        QuestSavedData saved = QuestSavedData.get(player.server);
        return definition.prerequisites().stream().allMatch(id ->
        {
            QuestDefinition prerequisite = CATALOG.get(id);
            return prerequisite != null && saved.peekProgress(player.getUUID(), prerequisite).isCompleted();
        });
    }

    public static void synchronizeAll(ServerPlayer player)
    {
        reconcile(player);
        JsonObject catalog = new JsonObject();
        catalog.add("quests", JsonParser.parseString(CATALOG.serialized()));
        catalog.addProperty("title", CATALOG.title());
        catalog.addProperty("canEdit", canEdit(player));
        catalog.addProperty("revision", CATALOG.revision());
        catalog.add("chapters", CATALOG.chaptersJson());
        QuestSavedData saved = QuestSavedData.get(player.server);
        ResourceLocation tracked = saved.tracked(player.getUUID());
        if (tracked != null && CATALOG.get(tracked) == null)
        {
            saved.track(player.getUUID(), null);
            tracked = null;
        }
        catalog.addProperty("tracked", tracked == null ? "" : tracked.toString());
        JsonObject states = new JsonObject();
        Map<ResourceLocation, CompoundTag> previous = new HashMap<>();
        for (var definition : CATALOG.all())
        {
            CompoundTag state = saved.progress(player.getUUID(), definition).save();
            state.putBoolean("unlocked", isUnlocked(player, definition));
            state.putString("tracked", tracked == null ? "" : tracked.toString());
            states.add(definition.id().toString(), net.minecraft.nbt.NbtOps.INSTANCE.convertTo(
                com.mojang.serialization.JsonOps.INSTANCE, state));
            previous.put(definition.id(), state.copy());
        }
        catalog.add("progress", states);
        LAST_PERMISSION.put(player.getUUID(), canEdit(player));
        PacketDistributor.sendToPlayer(player, new CatalogPayload(catalog.toString()));
        LAST_SENT.put(player.getUUID(), previous);
    }

    public static void tick(ServerPlayer player)
    {
        if (!Boolean.valueOf(canEdit(player)).equals(LAST_PERMISSION.get(player.getUUID())))
        {
            synchronizeAll(player);
        }
        reconcile(player);
        synchronizeChanges(player);
    }

    public static boolean canEdit(ServerPlayer player)
    {
        return player.hasPermissions(2);
    }

    public static void handleEdit(ServerPlayer player, EditPayload payload)
    {
        if (!canEdit(player))
        {
            PacketDistributor.sendToPlayer(player, new EditResultPayload(false, "只有管理员能编辑任务"));
            return;
        }
        try
        {
            CATALOG.edit(payload);
            player.server.getPlayerList().getPlayers().forEach(QuestService::synchronizeAll);
            PacketDistributor.sendToPlayer(player, new EditResultPayload(true, "已保存"));
        }
        catch (Exception exception)
        {
            String message = exception.getMessage();
            if (message == null)
            {
                message = "保存失败";
            }
            PacketDistributor.sendToPlayer(player, new EditResultPayload(false,
                message.substring(0, Math.min(1024, message.length()))));
        }
    }

    public static void reconcile(ServerPlayer player)
    {
        QuestSavedData saved = QuestSavedData.get(player.server);
        List<QuestDefinition> definitions = CATALOG.all();
        boolean hasNewCompletion;
        do
        {
            hasNewCompletion = false;
            for (QuestDefinition definition : definitions)
            {
                QuestProgress progress = saved.progress(player.getUUID(), definition);
                if (!isUnlocked(player, definition))
                {
                    continue;
                }
                if (progress.activate())
                {
                    saved.setDirty();
                    if (!progress.isCompleted())
                    {
                        QuestLifecycleEvent.publish(new QuestLifecycleEvent.Unlocked(player, definition, QuestApi.snapshot(player, definition)));
                    }
                }
                if (progress.isCompleted())
                {
                    continue;
                }
                QuestState previous = QuestApi.snapshot(player, definition);
                for (var objective : definition.objectives())
                {
                    if (pollObjective(player, definition, objective, progress))
                    {
                        saved.setDirty();
                    }
                }
                publishProgress(player, definition, previous);
                if (progress.completeIfReady(definition))
                {
                    saved.setDirty();
                    hasNewCompletion = true;
                    QuestLifecycleEvent.publish(new QuestLifecycleEvent.Completed(player, definition, QuestApi.snapshot(player, definition)));
                    PacketDistributor.sendToPlayer(player, new cn.dawnstring.circe.network.CompletionPayload(definition.id(), definition.title()));
                }
            }
        }
        while (hasNewCompletion);
    }

    private static boolean pollObjective(ServerPlayer player, QuestDefinition definition,
        QuestDefinition.Objective objective, QuestProgress progress)
    {
        if (!objective.type().hasPoll())
        {
            return false;
        }
        var retries = POLL_RETRY_TICKS.computeIfAbsent(player.getUUID(), ignored -> new HashMap<>());
        String key = definition.id() + "/" + definition.revision() + "/" + objective.id();
        long currentTick = player.server.overworld().getGameTime();
        if (currentTick < retries.getOrDefault(key, Long.MIN_VALUE))
        {
            return false;
        }
        try
        {
            int count = objective.type().poll(player, objective);
            retries.remove(key);
            return objective.type() == ObjectiveType.STATISTIC
                ? progress.updateStatistic(objective, count) : progress.setCount(objective, count);
        }
        catch (RuntimeException exception)
        {
            retries.put(key, currentTick + 200);
            Circe.LOGGER.error("Circe objective failed; retry in 200 ticks: player={} quest={} objective={} type={}",
                player.getUUID(), definition.id(), objective.id(), objective.type().id(), exception);
            return false;
        }
    }

    private static void publishProgress(ServerPlayer player, QuestDefinition definition, QuestState previous)
    {
        QuestState state = QuestApi.snapshot(player, definition);
        if (!previous.counts().equals(state.counts()))
        {
            QuestLifecycleEvent.publish(new QuestLifecycleEvent.ProgressChanged(player, definition, previous, state));
        }
    }

    public static void record(ServerPlayer player, ObjectiveType type, ResourceLocation target, int amount)
    {
        if (amount < 1)
        {
            return;
        }
        reconcile(player);
        QuestSavedData saved = QuestSavedData.get(player.server);
        // 固定事件发生时的资格，避免同一事件又解锁又计入后继任务。
        List<QuestDefinition> eligible = CATALOG.all().stream()
            .filter(definition -> isUnlocked(player, definition))
            .toList();
        for (QuestDefinition definition : eligible)
        {
            QuestProgress progress = saved.progress(player.getUUID(), definition);
            QuestState previous = QuestApi.snapshot(player, definition);
            for (var objective : definition.objectives())
            {
                if (objective.type() == type && objective.target().equals(target)
                    && progress.setCount(objective,
                        (int) Math.min(objective.count(), (long) progress.count(objective.id()) + amount)))
                {
                    saved.setDirty();
                }
            }
            publishProgress(player, definition, previous);
        }
        tick(player);
    }

    public static void handleAction(ServerPlayer player, ActionPayload payload)
    {
        long tick = player.server.overworld().getGameTime();
        Long previousTick = LAST_ACTION.get(player.getUUID());
        if (previousTick != null && tick - previousTick < 2)
        {
            return;
        }
        LAST_ACTION.put(player.getUUID(), tick);
        QuestDefinition definition = CATALOG.get(payload.questId());
        if (definition == null || player.isSpectator())
        {
            return;
        }
        reconcile(player);
        switch (payload.action())
        {
            case TRACK -> toggleTracking(player, definition);
            case SUBMIT -> submit(player, definition, payload.objectiveId());
            case CLAIM -> claim(player, definition);
        }
        tick(player);
    }

    public static boolean submit(ServerPlayer player, QuestDefinition definition, String objectiveId)
    {
        if (!isUnlocked(player, definition))
        {
            return false;
        }
        QuestSavedData saved = QuestSavedData.get(player.server);
        QuestProgress progress = saved.progress(player.getUUID(), definition);
        if (progress.isCompleted())
        {
            return false;
        }
        var objective = definition.objectives().stream()
            .filter(candidate -> candidate.id().equals(objectiveId) && candidate.type() == ObjectiveType.SUBMIT)
            .findFirst().orElse(null);
        if (objective == null)
        {
            return false;
        }
        int remaining = objective.count() - progress.count(objectiveId);
        int submitted = Math.min(remaining, countItems(player, objective.target()));
        if (submitted == 0)
        {
            player.displayClientMessage(Component.translatable("circe.message.no_items"), true);
            return false;
        }
        consumeItems(player, objective.target(), submitted);
        QuestState previous = QuestApi.snapshot(player, definition);
        progress.setCount(objective, progress.count(objectiveId) + submitted);
        saved.setDirty();
        publishProgress(player, definition, previous);
        reconcile(player);
        return true;
    }

    public static boolean claim(ServerPlayer player, QuestDefinition definition)
    {
        QuestSavedData saved = QuestSavedData.get(player.server);
        QuestProgress progress = saved.progress(player.getUUID(), definition);
        if (!progress.isCompleted() || progress.isClaimed())
        {
            return false;
        }
        List<ItemStack> rewards = new ArrayList<>();
        try
        {
            for (var reward : definition.rewards())
            {
                reward.type().handler().validate(reward, player.registryAccess());
                rewards.addAll(reward.type().handler().items(reward, player.registryAccess()));
            }
        }
        catch (RuntimeException exception)
        {
            player.displayClientMessage(Component.literal("奖励配置无效，请联系管理员"), false);
            return false;
        }
        if (!canFitRewards(player, rewards))
        {
            player.displayClientMessage(Component.translatable("circe.message.inventory_full"), true);
            return false;
        }
        if (!progress.claim())
        {
            return false;
        }
        saved.setDirty();
        for (ItemStack reward : rewards)
        {
            player.getInventory().add(reward.copy());
        }
        for (int index = 0; index < definition.rewards().size(); index++)
        {
            var reward = definition.rewards().get(index);
            try
            {
                reward.type().handler().grant(player, reward);
            }
            catch (RuntimeException exception)
            {
                // 外部奖励可能已经产生副作用，保持领取标记以避免重复发放。
                Circe.LOGGER.error("Circe reward partially failed: player={} quest={} index={} type={}",
                    player.getUUID(), definition.id(), index, reward.type().id(), exception);
                player.getInventory().setChanged();
                player.containerMenu.broadcastChanges();
                QuestLifecycleEvent.publish(new QuestLifecycleEvent.RewardFailed(player, definition, QuestApi.snapshot(player, definition), index));
                player.displayClientMessage(Component.literal("部分奖励发放失败，请联系管理员处理"), false);
                return false;
            }
        }
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        player.displayClientMessage(Component.translatable("circe.message.reward_claimed"), true);
        QuestLifecycleEvent.publish(new QuestLifecycleEvent.RewardClaimed(player, definition, QuestApi.snapshot(player, definition)));
        return true;
    }

    public static int countItems(ServerPlayer player, ResourceLocation itemId)
    {
        return carriedStacks(player).stream()
            .filter(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(itemId))
            .mapToInt(ItemStack::getCount).sum();
    }

    public static void forget(UUID playerId)
    {
        LAST_SENT.remove(playerId);
        LAST_ACTION.remove(playerId);
        LAST_PERMISSION.remove(playerId);
        POLL_RETRY_TICKS.remove(playerId);
    }

    public static void clearSession()
    {
        LAST_SENT.clear();
        LAST_ACTION.clear();
        LAST_PERMISSION.clear();
        POLL_RETRY_TICKS.clear();
    }

    private static void toggleTracking(ServerPlayer player, QuestDefinition definition)
    {
        QuestSavedData saved = QuestSavedData.get(player.server);
        if (definition.id().equals(saved.tracked(player.getUUID())))
        {
            saved.track(player.getUUID(), null);
        }
        else if (isUnlocked(player, definition))
        {
            saved.track(player.getUUID(), definition.id());
        }
    }

    private static void synchronizeChanges(ServerPlayer player)
    {
        QuestSavedData saved = QuestSavedData.get(player.server);
        ResourceLocation tracked = saved.tracked(player.getUUID());
        if (tracked != null && CATALOG.get(tracked) == null)
        {
            saved.track(player.getUUID(), null);
            tracked = null;
        }
        Map<ResourceLocation, CompoundTag> previous = LAST_SENT.computeIfAbsent(player.getUUID(), ignored -> new HashMap<>());
        for (QuestDefinition definition : CATALOG.all())
        {
            CompoundTag state = saved.progress(player.getUUID(), definition).save();
            state.putBoolean("unlocked", isUnlocked(player, definition));
            state.putString("tracked", tracked == null ? "" : tracked.toString());
            if (!state.equals(previous.get(definition.id())))
            {
                PacketDistributor.sendToPlayer(player, new ProgressPayload(definition.id(), state, CATALOG.revision()));
                previous.put(definition.id(), state.copy());
            }
        }
    }

    private static List<ItemStack> carriedStacks(ServerPlayer player)
    {
        List<ItemStack> stacks = new ArrayList<>(player.getInventory().items);
        stacks.addAll(player.getInventory().offhand);
        return stacks;
    }

    private static void consumeItems(ServerPlayer player, ResourceLocation itemId, int count)
    {
        int remaining = count;
        for (ItemStack stack : carriedStacks(player))
        {
            if (!BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(itemId))
            {
                continue;
            }
            int consumed = Math.min(remaining, stack.getCount());
            stack.shrink(consumed);
            remaining -= consumed;
            if (remaining == 0)
            {
                break;
            }
        }
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
    }

    private static boolean canFitRewards(ServerPlayer player, List<ItemStack> rewards)
    {
        List<ItemStack> slots = player.getInventory().items.stream().map(ItemStack::copy).toList();
        slots = new ArrayList<>(slots);
        for (ItemStack reward : rewards)
        {
            int remaining = reward.getCount();
            for (ItemStack stack : slots)
            {
                if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, reward))
                {
                    int inserted = Math.min(remaining,
                        Math.max(0, Math.min(stack.getMaxStackSize(), player.getInventory().getMaxStackSize()) - stack.getCount()));
                    stack.grow(inserted);
                    remaining -= inserted;
                }
            }
            for (int slot = 0; slot < slots.size() && remaining > 0; slot++)
            {
                if (slots.get(slot).isEmpty())
                {
                    int inserted = Math.min(remaining,
                        Math.min(reward.getMaxStackSize(), player.getInventory().getMaxStackSize()));
                    slots.set(slot, reward.copyWithCount(inserted));
                    remaining -= inserted;
                }
            }
            if (remaining > 0)
            {
                return false;
            }
        }
        return true;
    }
}
