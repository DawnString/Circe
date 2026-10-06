package cn.dawnstring.circe.quest;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public class QuestEvents
{
    private int externalCheckTicks;

    @SubscribeEvent
    public void serverTick(ServerTickEvent.Post event)
    {
        if (++externalCheckTicks < 20)
        {
            return;
        }
        externalCheckTicks = 0;
        if (QuestService.CATALOG.reloadExternalChanges())
        {
            event.getServer().getPlayerList().getPlayers().forEach(QuestService::synchronizeAll);
        }
    }

    @SubscribeEvent
    public void addReloadListener(AddReloadListenerEvent event)
    {
        event.addListener(QuestService.CATALOG);
        QuestService.CATALOG.setLookup(event.getRegistryAccess());
    }

    @SubscribeEvent
    public void synchronizeDatapack(OnDatapackSyncEvent event)
    {
        event.getRelevantPlayers().forEach(QuestService::synchronizeAll);
    }

    @SubscribeEvent
    public void playerTick(PlayerTickEvent.Post event)
    {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % 10 == 0)
        {
            QuestService.tick(player);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void livingDeath(LivingDeathEvent event)
    {
        if (event.getSource().getEntity() instanceof ServerPlayer player && !event.isCanceled())
        {
            QuestService.record(player, ObjectiveType.KILL,
                BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType()), 1);
        }
    }

    @SubscribeEvent
    public void playerLogout(PlayerEvent.PlayerLoggedOutEvent event)
    {
        QuestService.forget(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public void playerRespawn(PlayerEvent.PlayerRespawnEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player)
        {
            QuestService.synchronizeAll(player);
        }
    }

    @SubscribeEvent
    public void serverStopped(ServerStoppedEvent event)
    {
        externalCheckTicks = 0;
        QuestService.clearSession();
    }
}
