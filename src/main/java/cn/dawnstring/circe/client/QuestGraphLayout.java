package cn.dawnstring.circe.client;

import cn.dawnstring.circe.quest.QuestDefinition;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class QuestGraphLayout
{
    public static final int NODE_SIZE = 34;
    private static final int COLUMN_SPACING = 100;
    private static final int ROW_SPACING = 82;
    private final Map<ResourceLocation, Position> positions = new LinkedHashMap<>();
    private int width;
    private int height;
    private int minimumX;
    private int minimumY;

    public record Position(int x, int y)
    {
    }

    public QuestGraphLayout(List<QuestDefinition> definitions)
    {
        Map<ResourceLocation, QuestDefinition> byId = new HashMap<>();
        definitions.forEach(definition -> byId.put(definition.id(), definition));
        Map<ResourceLocation, Integer> depths = new HashMap<>();
        Map<Integer, List<QuestDefinition>> columns = new LinkedHashMap<>();
        for (QuestDefinition definition : definitions)
        {
            int depth = depth(definition, byId, depths);
            columns.computeIfAbsent(depth, ignored -> new ArrayList<>()).add(definition);
        }
        int maximumRows = columns.values().stream().mapToInt(List::size).max().orElse(1);
        for (var column : columns.entrySet())
        {
            int rowOffset = (maximumRows - column.getValue().size()) * ROW_SPACING / 2;
            for (int row = 0; row < column.getValue().size(); row++)
            {
                var definition = column.getValue().get(row);
                positions.put(definition.id(), definition.position() == null
                    ? new Position(column.getKey() * COLUMN_SPACING, rowOffset + row * ROW_SPACING)
                    : new Position(definition.position().x(), definition.position().y()));
            }
        }
        updateBounds();
    }

    public void move(ResourceLocation questId, QuestDefinition.GraphPosition position)
    {
        if (positions.containsKey(questId))
        {
            positions.put(questId, new Position(position.x(), position.y()));
            updateBounds();
        }
    }

    private void updateBounds()
    {
        minimumX = positions.values().stream().mapToInt(Position::x).min().orElse(0);
        minimumY = positions.values().stream().mapToInt(Position::y).min().orElse(0);
        width = positions.values().stream().mapToInt(Position::x).max().orElse(0) - minimumX + NODE_SIZE;
        height = positions.values().stream().mapToInt(Position::y).max().orElse(0) - minimumY + NODE_SIZE + 16;
    }

    public int minimumX()
    {
        return minimumX;
    }

    public int minimumY()
    {
        return minimumY;
    }

    public Position position(ResourceLocation questId)
    {
        return positions.get(questId);
    }

    public int width()
    {
        return width;
    }

    public int height()
    {
        return height;
    }

    private static int depth(
        QuestDefinition definition,
        Map<ResourceLocation, QuestDefinition> byId,
        Map<ResourceLocation, Integer> depths)
    {
        Integer existing = depths.get(definition.id());
        if (existing != null)
        {
            return existing;
        }
        // 服务端已校验依赖无环；跨章节前置只在详情中展示。
        int depth = definition.prerequisites().stream()
            .map(byId::get)
            .filter(java.util.Objects::nonNull)
            .mapToInt(prerequisite -> depth(prerequisite, byId, depths) + 1)
            .max().orElse(0);
        depths.put(definition.id(), depth);
        return depth;
    }
}
