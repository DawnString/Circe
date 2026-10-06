package cn.dawnstring.circe.api;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;

public record QuestState(ResourceLocation questId, int revision, boolean isUnlocked, boolean isCompleted,
    boolean isClaimed, boolean isTracked, Map<String, Integer> counts)
{
    public QuestState
    {
        counts = Map.copyOf(counts);
    }

    public int count(String objectiveId)
    {
        return counts.getOrDefault(objectiveId, 0);
    }
}
