package cn.dawnstring.circe.client;

import cn.dawnstring.circe.network.EditPayload;
import cn.dawnstring.circe.quest.ChapterDefinition;
import cn.dawnstring.circe.quest.QuestValidationException;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.LinkedHashMap;
import java.util.Map;

public final class QuestChapterEditorScreen extends QuestEditingScreen
{
    private final Map<String, String> values = new LinkedHashMap<>();
    private String originalId;
    private long baseline;
    private long observed;
    private boolean isPending;
    private boolean shouldConfirmDelete;
    private int listPage;

    public QuestChapterEditorScreen(net.minecraft.client.gui.screens.Screen parent)
    {
        this(parent, null);
    }

    public QuestChapterEditorScreen(net.minecraft.client.gui.screens.Screen parent, String chapterId)
    {
        super(parent, "circe.editor.chapters");
        createDraft();
        if (chapterId != null && ClientQuestState.chapter(chapterId) != null)
        {
            select(ClientQuestState.chapter(chapterId));
        }
    }

    private void select(ChapterDefinition chapter)
    {
        originalId = chapter.id();
        values.put("id", chapter.id());
        values.put("title", chapter.title());
        values.put("order", Integer.toString(chapter.order()));
        values.put("icon", chapter.icon().toString());
        baseline = ClientQuestState.bookRevision();
        shouldConfirmDelete = false;
    }

    private void createDraft()
    {
        originalId = null;
        int suffix = 1;
        while (ClientQuestState.chapter("circe:chapter_" + suffix) != null)
        {
            suffix++;
        }
        values.put("id", "circe:chapter_" + suffix);
        values.put("title", QuestTranslations.text("circe.editor.new_chapter"));
        values.put("order", Integer.toString(ClientQuestState.chapters().size()));
        values.put("icon", "minecraft:book");
        baseline = ClientQuestState.bookRevision();
        shouldConfirmDelete = false;
    }

    @Override
    protected void initEditor()
    {
        button(frameLeft + 12, frameTop + 43, 148, QuestTranslations.text("circe.button.new_chapter"), () ->
        {
            createDraft();
            rebuild();
        }).primary();
        var chapters = ClientQuestState.chapters();
        listPage = Math.clamp(listPage, 0, Math.max(0, (chapters.size() - 1) / 10));
        for (int index = listPage * 10; index < Math.min(chapters.size(), listPage * 10 + 10); index++)
        {
            ChapterDefinition chapter = chapters.get(index);
            button(frameLeft + 12, frameTop + 74 + (index % 10) * 26, 148,
                Component.translatable(chapter.title()).getString(), () ->
                {
                    select(chapter);
                    rebuild();
                });
        }
        button(frameLeft + 12, frameTop + 342, 70, QuestTranslations.text("circe.button.previous"), () ->
        {
            listPage--;
            rebuild();
        });
        button(frameLeft + 90, frameTop + 342, 70, QuestTranslations.text("circe.button.next"), () ->
        {
            listPage++;
            rebuild();
        });
        int left = frameLeft + 182;
        int width = frameWidth - 200;
        bind("id", left, frameTop + 61, width, QuestTranslations.text("circe.editor.chapter_id")).setEditable(originalId == null);
        bind("title", left, frameTop + 108, width, QuestTranslations.text("circe.editor.chapter_title"));
        bind("order", left, frameTop + 155, width, QuestTranslations.text("circe.editor.chapter_order"));
        bind("icon", left, frameTop + 202, width - 82, QuestTranslations.text("circe.editor.chapter_icon"));
        button(left + width - 74, frameTop + 202, 74, QuestTranslations.text("circe.button.pick_item"), () ->
            minecraft.setScreen(new QuestItemPickerScreen(this, false, id -> values.put("icon", id.toString()))));
        label(QuestTranslations.text("circe.editor.chapter_hint"), left, frameTop + 245);
        label(QuestTranslations.text("circe.editor.chapter_delete_hint"), left, frameTop + 261);
        button(left, frameTop + frameHeight - 26, 76, QuestTranslations.text("circe.button.save_chapter"), () -> save(false)).primary();
        button(left + 84, frameTop + frameHeight - 26, 76, QuestTranslations.text("circe.button.delete_chapter"), () -> save(true));
        button(left + 168, frameTop + frameHeight - 26, 68, QuestTranslations.text("circe.button.back"), this::onClose);
    }

    private net.minecraft.client.gui.components.EditBox bind(String key, int x, int y, int width, String label)
    {
        var field = field(x, y, width, label, values.get(key), 256);
        field.setResponder(value -> values.put(key, value));
        return field;
    }

    private void save(boolean isDelete)
    {
        if (isPending || isDelete && originalId == null)
        {
            return;
        }
        if (isDelete && !shouldConfirmDelete)
        {
            shouldConfirmDelete = true;
            status = Component.translatable("circe.editor.confirm_chapter_delete");
            return;
        }
        try
        {
            String id = values.get("id").trim();
            if (originalId == null && ClientQuestState.chapter(id) != null)
            {
                throw new IllegalArgumentException(QuestTranslations.text("circe.error.chapter_exists"));
            }
            ChapterDefinition chapter = new ChapterDefinition(id, values.get("title"), Integer.parseInt(values.get("order")),
                ResourceLocation.parse(values.get("icon")));
            observed = ClientQuestState.editResultSequence();
            isPending = true;
            PacketDistributor.sendToServer(new EditPayload(isDelete ? "chapter_delete" : "chapter_save", id,
                chapter.toJson().toString(), baseline));
            status = Component.translatable("circe.editor.saving_chapter");
        }
        catch (RuntimeException exception)
        {
            status = QuestValidationException.message(exception);
        }
    }

    @Override
    public void tick()
    {
        super.tick();
        if (!ClientQuestState.canEdit())
        {
            return;
        }
        if (!isPending || observed == ClientQuestState.editResultSequence())
        {
            return;
        }
        isPending = false;
        status = ClientQuestState.editResult().message();
        if (ClientQuestState.editResult().isSuccessful())
        {
            if (parent instanceof QuestEditorScreen editor)
            {
                editor.titleSaved();
            }
            baseline = ClientQuestState.bookRevision();
            originalId = ClientQuestState.chapter(values.get("id")) == null ? null : values.get("id");
            if (parent instanceof QuestScreen)
            {
                onClose();
                return;
            }
            rebuild();
        }
    }
}
