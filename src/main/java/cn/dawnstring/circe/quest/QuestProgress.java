package cn.dawnstring.circe.quest;

import net.minecraft.nbt.CompoundTag;

import java.util.HashMap;
import java.util.Map;

public class QuestProgress
{
    private final int revision;
    private final Map<String, Integer> counts = new HashMap<>();
    private final Map<String, Integer> statisticBaselines = new HashMap<>();
    private boolean isCompleted;
    private boolean isClaimed;
    private boolean isActivated;

    public boolean activate()
    {
        if (isActivated)
        {
            return false;
        }
        isActivated = true;
        return true;
    }

    public QuestProgress(int revision)
    {
        this.revision = revision;
    }

    public int revision()
    {
        return revision;
    }

    public int count(String objectiveId)
    {
        return counts.getOrDefault(objectiveId, 0);
    }

    public boolean isCompleted()
    {
        return isCompleted;
    }

    public boolean isClaimed()
    {
        return isClaimed;
    }

    public boolean setCount(QuestDefinition.Objective objective, int count)
    {
        if (isCompleted)
        {
            return false;
        }
        int boundedCount = Math.clamp(count, 0, objective.count());
        if (boundedCount == count(objective.id()))
        {
            return false;
        }
        counts.put(objective.id(), boundedCount);
        return true;
    }

    public boolean updateStatistic(QuestDefinition.Objective objective, int currentCount)
    {
        if (isCompleted)
        {
            return false;
        }
        boolean hasNewBaseline = objective.isSinceUnlock() && !statisticBaselines.containsKey(objective.id());
        if (hasNewBaseline)
        {
            statisticBaselines.put(objective.id(), Math.max(0, currentCount));
        }
        int baseline = objective.isSinceUnlock() ? statisticBaselines.get(objective.id()) : 0;
        int count = (int) Math.max(0, (long) currentCount - baseline);
        return setCount(objective, count) || hasNewBaseline;
    }

    public boolean completeIfReady(QuestDefinition definition)
    {
        if (isCompleted || definition.objectives().stream()
            .anyMatch(objective -> count(objective.id()) < objective.count()))
        {
            return false;
        }
        isCompleted = true;
        isClaimed = definition.rewards().isEmpty();
        return true;
    }

    public boolean claim()
    {
        if (!isCompleted || isClaimed)
        {
            return false;
        }
        isClaimed = true;
        return true;
    }

    public CompoundTag save()
    {
        CompoundTag tag = new CompoundTag();
        tag.putInt("revision", revision);
        tag.putBoolean("completed", isCompleted);
        tag.putBoolean("claimed", isClaimed);
        tag.putBoolean("activated", isActivated);
        CompoundTag countEntries = new CompoundTag();
        counts.forEach(countEntries::putInt);
        tag.put("counts", countEntries);
        CompoundTag baselines = new CompoundTag();
        statisticBaselines.forEach(baselines::putInt);
        tag.put("statisticBaselines", baselines);
        return tag;
    }

    public static QuestProgress load(CompoundTag tag)
    {
        QuestProgress progress = new QuestProgress(tag.getInt("revision"));
        progress.isCompleted = tag.getBoolean("completed");
        progress.isClaimed = progress.isCompleted && tag.getBoolean("claimed");
        progress.isActivated = tag.contains("activated") ? tag.getBoolean("activated") : progress.isCompleted;
        CompoundTag countEntries = tag.getCompound("counts");
        for (String objectiveId : countEntries.getAllKeys())
        {
            progress.counts.put(objectiveId, Math.max(0, countEntries.getInt(objectiveId)));
        }
        CompoundTag baselines = tag.getCompound("statisticBaselines");
        for (String objectiveId : baselines.getAllKeys())
        {
            progress.statisticBaselines.put(objectiveId, Math.max(0, baselines.getInt(objectiveId)));
        }
        return progress;
    }
}
