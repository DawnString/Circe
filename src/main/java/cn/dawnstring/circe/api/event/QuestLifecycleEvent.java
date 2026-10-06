package cn.dawnstring.circe.api.event;

import cn.dawnstring.circe.Circe;
import cn.dawnstring.circe.api.QuestState;
import cn.dawnstring.circe.quest.QuestDefinition;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.common.NeoForge;

import java.util.List;

public abstract class QuestLifecycleEvent extends Event
{
    private static final ThreadLocal<Boolean> IS_DISPATCHING = ThreadLocal.withInitial(() -> false);

    public static boolean isDispatching()
    {
        return IS_DISPATCHING.get();
    }

    public static void publish(QuestLifecycleEvent event)
    {
        boolean isAlreadyDispatching = isDispatching();
        IS_DISPATCHING.set(true);
        try
        {
            NeoForge.EVENT_BUS.post(event);
        }
        catch (RuntimeException exception)
        {
            // 监听器异常不能回滚已经提交的任务状态。
            Circe.LOGGER.error("Circe lifecycle listener failed: {}", event.getClass().getSimpleName(), exception);
        }
        finally
        {
            IS_DISPATCHING.set(isAlreadyDispatching);
        }
    }

    public abstract static class PlayerEvent extends QuestLifecycleEvent
    {
        private final ServerPlayer player;
        private final QuestDefinition definition;
        private final QuestState state;

        protected PlayerEvent(ServerPlayer player, QuestDefinition definition, QuestState state)
        {
            this.player = player;
            this.definition = definition;
            this.state = state;
        }

        public ServerPlayer player()
        {
            return player;
        }

        public QuestDefinition definition()
        {
            return definition;
        }

        public QuestState state()
        {
            return state;
        }
    }

    public static final class Unlocked extends PlayerEvent
    {
        public Unlocked(ServerPlayer player, QuestDefinition definition, QuestState state)
        {
            super(player, definition, state);
        }
    }

    public static final class ProgressChanged extends PlayerEvent
    {
        private final QuestState previous;

        public ProgressChanged(ServerPlayer player, QuestDefinition definition, QuestState previous, QuestState state)
        {
            super(player, definition, state);
            this.previous = previous;
        }

        public QuestState previous()
        {
            return previous;
        }
    }

    public static final class Completed extends PlayerEvent
    {
        public Completed(ServerPlayer player, QuestDefinition definition, QuestState state)
        {
            super(player, definition, state);
        }
    }

    public static final class RewardClaimed extends PlayerEvent
    {
        public RewardClaimed(ServerPlayer player, QuestDefinition definition, QuestState state)
        {
            super(player, definition, state);
        }
    }

    public static final class RewardFailed extends PlayerEvent
    {
        private final int rewardIndex;

        public RewardFailed(ServerPlayer player, QuestDefinition definition, QuestState state, int rewardIndex)
        {
            super(player, definition, state);
            this.rewardIndex = rewardIndex;
        }

        public int rewardIndex()
        {
            return rewardIndex;
        }
    }

    public static final class CatalogReloaded extends QuestLifecycleEvent
    {
        public enum Reason
        {
            DATA_RELOAD, EDIT, EXTERNAL_RELOAD
        }

        private final List<QuestDefinition> previous;
        private final List<QuestDefinition> definitions;
        private final long revision;
        private final Reason reason;

        public CatalogReloaded(List<QuestDefinition> previous, List<QuestDefinition> definitions, long revision, Reason reason)
        {
            this.previous = List.copyOf(previous);
            this.definitions = List.copyOf(definitions);
            this.revision = revision;
            this.reason = reason;
        }

        public List<QuestDefinition> previous()
        {
            return previous;
        }

        public List<QuestDefinition> definitions()
        {
            return definitions;
        }

        public long revision()
        {
            return revision;
        }

        public Reason reason()
        {
            return reason;
        }
    }
}
