package cn.dawnstring.circe.quest;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import cn.dawnstring.circe.api.QuestConfigSchema;

public final class RewardType
{
    public interface Handler
    {
        void validate(QuestDefinition.Reward reward, HolderLookup.Provider lookup);
        List<ItemStack> items(QuestDefinition.Reward reward, HolderLookup.Provider lookup);
        void grant(ServerPlayer player, QuestDefinition.Reward reward);
    }

    private static final Map<String, RewardType> TYPES = new LinkedHashMap<>();
    public static final RewardType ITEM = register("circe:item", "普通物品", true, 64, itemHandler());
    public static final RewardType SPECIAL_ITEM = register("circe:special_item", "特殊物品（组件）", true, 64, itemHandler());
    public static final RewardType EXPERIENCE = register("circe:experience", "经验值", false, 1_000_000, experienceHandler(false));
    public static final RewardType LEVELS = register("circe:levels", "经验等级", false, 1000, experienceHandler(true));
    private final String id;
    private final String title;
    private final boolean hasItem;
    private final int maximumCount;
    private final Handler handler;
    private final QuestConfigSchema schema;

    private RewardType(String id, String title, boolean hasItem, int maximumCount, QuestConfigSchema schema, Handler handler)
    {
        this.id = id;
        this.title = title;
        this.hasItem = hasItem;
        this.maximumCount = maximumCount;
        this.handler = handler;
        this.schema = schema;
    }

    public static RewardType register(String id, String title, boolean hasItem, int maximumCount, Handler handler)
    {
        return register(id, title, hasItem, maximumCount, QuestConfigSchema.EMPTY, handler);
    }

    public static synchronized RewardType register(String id, String title, boolean hasItem, int maximumCount, QuestConfigSchema schema, Handler handler)
    {
        String normalized = ResourceLocation.parse(id).toString();
        if (TYPES.containsKey(normalized) || maximumCount < 1 || handler == null || title == null || title.isBlank())
        {
            throw new IllegalArgumentException("奖励类型无效或重复 " + id);
        }
        RewardType type = new RewardType(normalized, title, hasItem, maximumCount, java.util.Objects.requireNonNull(schema), handler);
        TYPES.put(normalized, type);
        return type;
    }

    public static synchronized RewardType parse(String id)
    {
        String normalized = id.contains(":") ? ResourceLocation.parse(id).toString() : "circe:" + id;
        RewardType type = TYPES.get(normalized);
        if (type == null)
        {
            throw new IllegalArgumentException("未注册的奖励类型 " + id);
        }
        return type;
    }

    public static synchronized List<RewardType> all()
    {
        return List.copyOf(TYPES.values());
    }

    public String id()
    {
        return id;
    }

    public String title()
    {
        return title;
    }

    public boolean hasItem()
    {
        return hasItem;
    }

    public int maximumCount()
    {
        return maximumCount;
    }

    public Handler handler()
    {
        return handler;
    }

    public QuestConfigSchema schema()
    {
        return schema;
    }

    public static ItemStack stack(QuestDefinition.Reward reward, HolderLookup.Provider lookup)
    {
        JsonObject json = new JsonObject();
        json.addProperty("id", reward.item().toString());
        json.addProperty("count", reward.count());
        json.add("components", reward.components());
        return ItemStack.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, lookup), json).getOrThrow();
    }

    private static Handler itemHandler()
    {
        return new Handler()
        {
            @Override
            public void validate(QuestDefinition.Reward reward, HolderLookup.Provider lookup)
            {
                if (!BuiltInRegistries.ITEM.containsKey(reward.item()) || reward.item().getPath().equals("air"))
                {
                    throw new IllegalArgumentException("未知奖励物品 " + reward.item());
                }
                stack(reward, lookup);
            }

            @Override
            public List<ItemStack> items(QuestDefinition.Reward reward, HolderLookup.Provider lookup)
            {
                return List.of(stack(reward, lookup));
            }

            @Override
            public void grant(ServerPlayer player, QuestDefinition.Reward reward)
            {
            }
        };
    }

    private static Handler experienceHandler(boolean isLevels)
    {
        return new Handler()
        {
            @Override
            public void validate(QuestDefinition.Reward reward, HolderLookup.Provider lookup)
            {
            }

            @Override
            public List<ItemStack> items(QuestDefinition.Reward reward, HolderLookup.Provider lookup)
            {
                return List.of();
            }

            @Override
            public void grant(ServerPlayer player, QuestDefinition.Reward reward)
            {
                if (isLevels)
                {
                    player.giveExperienceLevels(reward.count());
                }
                else
                {
                    player.giveExperiencePoints(reward.count());
                }
            }
        };
    }
}
