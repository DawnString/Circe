package cn.dawnstring.circe.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import cn.dawnstring.circe.api.QuestConfigSchema;

public final class QuestItemPickerScreen extends QuestEditingScreen
{
    private record Entry(ResourceLocation id, String name, ItemStack icon)
    {
    }

    private final Consumer<ResourceLocation> onSelect;
    private final List<Entry> entries = new ArrayList<>();
    private List<Entry> matches;
    private String query = "";
    private int page;
    private int columns;

    public QuestItemPickerScreen(Screen parent, boolean isEntity, Consumer<ResourceLocation> onSelect)
    {
        this(parent, isEntity ? QuestConfigSchema.Kind.ENTITY : QuestConfigSchema.Kind.ITEM, onSelect);
    }

    public QuestItemPickerScreen(Screen parent, QuestConfigSchema.Kind kind, Consumer<ResourceLocation> onSelect)
    {
        super(parent, switch (kind)
        {
            case ENTITY -> "实体选择器";
            case BLOCK -> "方块选择器";
            case FLUID -> "流体选择器";
            default -> "物品选择器";
        });
        this.onSelect = onSelect;
        if (kind == QuestConfigSchema.Kind.BLOCK)
        {
            BuiltInRegistries.BLOCK.forEach(block -> entries.add(new Entry(BuiltInRegistries.BLOCK.getKey(block),
                block.getName().getString(), new ItemStack(block.asItem() == Items.AIR ? Items.BOOK : block.asItem()))));
        }
        else if (kind == QuestConfigSchema.Kind.FLUID)
        {
            BuiltInRegistries.FLUID.forEach(fluid ->
            {
                var id = BuiltInRegistries.FLUID.getKey(fluid);
                ItemStack icon = new ItemStack(fluid.getBucket() == Items.AIR ? Items.BUCKET : fluid.getBucket());
                String name = fluid.getFluidType().getDescription().getString();
                if (id.getPath().startsWith("flowing_"))
                {
                    name += "（流动）";
                }
                entries.add(new Entry(id, name, icon));
            });
        }
        else if (kind == QuestConfigSchema.Kind.ENTITY)
        {
            BuiltInRegistries.ENTITY_TYPE.forEach(type ->
            {
                var egg = SpawnEggItem.byId(type);
                entries.add(new Entry(BuiltInRegistries.ENTITY_TYPE.getKey(type), type.getDescription().getString(),
                    new ItemStack(egg == null ? Items.BOOK : egg)));
            });
        }
        else
        {
            BuiltInRegistries.ITEM.forEach(item ->
            {
                if (item != Items.AIR)
                {
                    ItemStack stack = item.getDefaultInstance();
                    entries.add(new Entry(BuiltInRegistries.ITEM.getKey(item), stack.getHoverName().getString(), stack));
                }
            });
        }
        entries.sort(java.util.Comparator.comparing(entry -> entry.id().toString()));
    }

    @Override
    protected void initEditor()
    {
        columns = Math.max(4, (frameWidth - 32) / 62);
        var search = field(frameLeft + 16, frameTop + 48, frameWidth - 32,
            "搜索名称、注册 ID 或模组命名空间", query, 128);
        search.setResponder(value ->
        {
            query = value;
            page = 0;
            filter();
        });
        filter();
        button(frameLeft + 16, frameTop + frameHeight - 26, 70, "上一页", () -> page = Math.max(0, page - 1));
        button(frameLeft + 94, frameTop + frameHeight - 26, 70, "下一页",
            () -> page = Math.min((Math.max(1, matches.size()) - 1) / pageSize(), page + 1));
        button(frameLeft + frameWidth - 86, frameTop + frameHeight - 26, 70, "返回", this::onClose);
    }

    private void filter()
    {
        String normalized = query.toLowerCase(Locale.ROOT);
        matches = entries.stream().filter(entry -> entry.name().toLowerCase(Locale.ROOT).contains(normalized)
            || entry.id().toString().contains(normalized)).toList();
    }

    private int pageSize()
    {
        return columns * 5;
    }

    @Override
    protected void renderContent(GuiGraphics graphics)
    {
        int cellWidth = (frameWidth - 32) / columns;
        for (int index = 0; index < pageSize() && page * pageSize() + index < matches.size(); index++)
        {
            Entry entry = matches.get(page * pageSize() + index);
            int x = frameLeft + 16 + index % columns * cellWidth;
            int y = frameTop + 85 + index / columns * 56;
            graphics.fill(x, y, x + cellWidth - 5, y + 50, QuestTheme.panel());
            graphics.renderItem(entry.icon(), x + (cellWidth - 16) / 2, y + 7);
            QuestTheme.centered(graphics, font, QuestTheme.fit(font, net.minecraft.network.chat.Component.literal(entry.name()), cellWidth - 10),
                x + (cellWidth - 5) / 2, y + 31, QuestTheme.text());
            if (pointerX >= x && pointerX < x + cellWidth - 5 && pointerY >= y && pointerY < y + 50)
            {
                graphics.renderTooltip(font, net.minecraft.network.chat.Component.literal(entry.name() + " · " + entry.id()), pointerX, pointerY);
            }
        }
        graphics.drawString(font, "找到 " + matches.size() + " 项  ·  " + (page + 1) + " / "
            + Math.max(1, (matches.size() + pageSize() - 1) / pageSize()), frameLeft + 16,
            frameTop + frameHeight - 48, QuestTheme.muted(), false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        int localX = (int) (mouseX / uiScale) - frameLeft - 16;
        int localY = (int) (mouseY / uiScale) - frameTop - 85;
        int cellWidth = (frameWidth - 32) / columns;
        if (button == 0 && localX >= 0 && localX < columns * cellWidth && localY >= 0 && localY < 5 * 56
            && localX % cellWidth < cellWidth - 5 && localY % 56 < 50)
        {
            int index = page * pageSize() + localY / 56 * columns + localX / cellWidth;
            if (index < matches.size())
            {
                onSelect.accept(matches.get(index).id());
                onClose();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical)
    {
        page = Math.clamp(page - (int) vertical, 0, Math.max(0, (matches.size() - 1) / pageSize()));
        return true;
    }
}
