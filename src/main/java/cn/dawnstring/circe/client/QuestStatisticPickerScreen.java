package cn.dawnstring.circe.client;

import cn.dawnstring.circe.quest.QuestStatistics;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public final class QuestStatisticPickerScreen extends QuestEditingScreen
{
    private static final int PAGE_SIZE = 8;
    private static final int ROW_HEIGHT = 34;
    private record Entry(ResourceLocation id, Component name)
    {
    }

    private final ResourceLocation statisticType;
    private final Consumer<ResourceLocation> onSelect;
    private final List<Entry> entries;
    private List<Entry> matches;
    private String query = "";
    private int page;

    public QuestStatisticPickerScreen(Screen parent, ResourceLocation statisticType, Consumer<ResourceLocation> onSelect)
    {
        super(parent, Component.translatable("circe.picker.stat_title", QuestStatistics.categoryName(statisticType)));
        this.statisticType = statisticType;
        this.onSelect = onSelect;
        var type = BuiltInRegistries.STAT_TYPE.get(statisticType);
        entries = type.getRegistry().keySet().stream().sorted()
            .map(id -> new Entry(id, QuestStatistics.name(type, id))).toList();
    }

    @Override
    protected void initEditor()
    {
        var search = field(frameLeft + 16, frameTop + 54, frameWidth - 32,
            QuestTranslations.text("circe.picker.stat_search"), query, 128);
        search.setResponder(value ->
        {
            query = value;
            page = 0;
            filter();
        });
        filter();
        button(frameLeft + 16, frameTop + frameHeight - 26, 70, QuestTranslations.text("circe.button.previous"), () -> page = Math.max(0, page - 1));
        button(frameLeft + 94, frameTop + frameHeight - 26, 70, QuestTranslations.text("circe.button.next"),
            () -> page = Math.min(Math.max(0, (matches.size() - 1) / PAGE_SIZE), page + 1));
        button(frameLeft + frameWidth - 86, frameTop + frameHeight - 26, 70, QuestTranslations.text("circe.button.back"), this::onClose);
    }

    private void filter()
    {
        String normalized = query.toLowerCase(Locale.ROOT);
        matches = entries.stream().filter(entry -> entry.name().getString().toLowerCase(Locale.ROOT).contains(normalized)
            || entry.id().toString().contains(normalized)).toList();
    }

    @Override
    protected void renderContent(GuiGraphics graphics)
    {
        for (int index = 0; index < PAGE_SIZE && page * PAGE_SIZE + index < matches.size(); index++)
        {
            Entry entry = matches.get(page * PAGE_SIZE + index);
            int left = frameLeft + 16;
            int top = frameTop + 88 + index * ROW_HEIGHT;
            boolean isHovered = pointerX >= left && pointerX < left + frameWidth - 32
                && pointerY >= top && pointerY < top + ROW_HEIGHT - 3;
            graphics.fill(left, top, left + frameWidth - 32, top + ROW_HEIGHT - 3,
                isHovered ? QuestTheme.selected() : QuestTheme.panel());
            String unit = QuestStatistics.unit(statisticType, entry.id()).title();
            graphics.drawString(font, QuestTheme.fit(font, entry.name(), frameWidth - 140),
                left + 8, top + 5, QuestTheme.text(), false);
            graphics.drawString(font, unit, left + frameWidth - 48 - font.width(unit), top + 5, QuestTheme.accent(), false);
            graphics.drawString(font, QuestTheme.fit(font, Component.literal(entry.id().toString()), frameWidth - 48),
                left + 8, top + 18, QuestTheme.muted(), false);
        }
        graphics.drawString(font, QuestTranslations.text("circe.picker.results", matches.size(), page + 1,
            Math.max(1, (matches.size() + PAGE_SIZE - 1) / PAGE_SIZE)),
            frameLeft + 16, frameTop + frameHeight - 48, QuestTheme.muted(), false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        int localX = (int) (mouseX / uiScale) - frameLeft - 16;
        int localY = (int) (mouseY / uiScale) - frameTop - 88;
        if (button == 0 && localX >= 0 && localX < frameWidth - 32 && localY >= 0
            && localY < PAGE_SIZE * ROW_HEIGHT && localY % ROW_HEIGHT < ROW_HEIGHT - 3)
        {
            int index = page * PAGE_SIZE + localY / ROW_HEIGHT;
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
        page = Math.clamp(page - (int) vertical, 0, Math.max(0, (matches.size() - 1) / PAGE_SIZE));
        return true;
    }
}
