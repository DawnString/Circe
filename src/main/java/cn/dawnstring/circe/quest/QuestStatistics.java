package cn.dawnstring.circe.quest;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.stats.Stat;
import net.minecraft.stats.StatType;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.math.BigDecimal;
import java.util.Set;

public final class QuestStatistics
{
    private static final Set<String> TIME_STATS = Set.of("play_time", "total_world_time", "time_since_death",
        "time_since_rest", "sneak_time");

    public enum Unit
    {
        COUNT("次／个", 1), DISTANCE("米", 100), TIME("分钟", 1200), DAMAGE("伤害点", 10);

        private final String title;
        private final int multiplier;

        Unit(String title, int multiplier)
        {
            this.title = title;
            this.multiplier = multiplier;
        }

        public String title()
        {
            return title;
        }

        public String input(int rawCount)
        {
            return BigDecimal.valueOf(rawCount).divide(BigDecimal.valueOf(multiplier), 8,
                java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
        }

        public int parse(String value)
        {
            BigDecimal scaled = new BigDecimal(value).multiply(BigDecimal.valueOf(multiplier));
            // 分钟无法精确表示每个游戏刻，时间目标按最接近的游戏刻取整。
            int rawCount = (this == TIME ? scaled.setScale(0, java.math.RoundingMode.HALF_UP) : scaled).intValueExact();
            if (rawCount < 1)
            {
                throw new IllegalArgumentException("目标数量必须大于零");
            }
            return rawCount;
        }
    }

    private QuestStatistics()
    {
    }

    public static Component categoryName(ResourceLocation typeId)
    {
        if (!typeId.getNamespace().equals("minecraft"))
        {
            return BuiltInRegistries.STAT_TYPE.get(typeId).getDisplayName();
        }
        String title = switch (typeId.getPath())
        {
            case "mined" -> "挖掘方块";
            case "crafted" -> "合成物品";
            case "used" -> "使用物品";
            case "broken" -> "损坏物品";
            case "picked_up" -> "捡起物品";
            case "dropped" -> "丢出物品";
            case "killed" -> "击杀实体";
            case "killed_by" -> "被实体击杀";
            case "custom" -> "通用统计";
            default -> typeId.toString();
        };
        return Component.literal(title);
    }

    public static Stat<?> resolve(ResourceLocation typeId, ResourceLocation target)
    {
        if (!BuiltInRegistries.STAT_TYPE.containsKey(typeId))
        {
            throw new IllegalArgumentException("统计类别不存在：" + typeId);
        }
        return resolveValue(BuiltInRegistries.STAT_TYPE.get(typeId), target);
    }

    private static <T> Stat<T> resolveValue(StatType<T> type, ResourceLocation target)
    {
        if (!type.getRegistry().containsKey(target))
        {
            throw new IllegalArgumentException("统计项目不存在：" + target);
        }
        return type.get(type.getRegistry().get(target));
    }

    public static Component name(StatType<?> type, ResourceLocation target)
    {
        Object value = type.getRegistry().get(target);
        if (value instanceof Item item)
        {
            return item.getDescription();
        }
        if (value instanceof Block block)
        {
            return block.getName();
        }
        if (value instanceof EntityType<?> entity)
        {
            return entity.getDescription();
        }
        if (type == Stats.CUSTOM)
        {
            return Component.translatable("stat." + target.getNamespace() + "." + target.getPath().replace('/', '.'));
        }
        return Component.literal(target.toString());
    }

    public static Unit unit(ResourceLocation typeId, ResourceLocation target)
    {
        if (!typeId.equals(ResourceLocation.withDefaultNamespace("custom")) || !target.getNamespace().equals("minecraft"))
        {
            return Unit.COUNT;
        }
        if (target.getPath().endsWith("_one_cm"))
        {
            return Unit.DISTANCE;
        }
        if (TIME_STATS.contains(target.getPath()))
        {
            return Unit.TIME;
        }
        return target.getPath().startsWith("damage_") ? Unit.DAMAGE : Unit.COUNT;
    }

    public static String counter(QuestDefinition.Objective objective, int count)
    {
        if (objective.type() != ObjectiveType.STATISTIC)
        {
            return count + "/" + objective.count();
        }
        Unit unit = unit(objective.statisticType(), objective.target());
        String suffix = unit == Unit.COUNT ? "" : " " + unit.title();
        return unit.input(count) + "/" + unit.input(objective.count()) + suffix;
    }
}
