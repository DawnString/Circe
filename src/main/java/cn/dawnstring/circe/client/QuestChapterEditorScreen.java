package cn.dawnstring.circe.client;

import cn.dawnstring.circe.network.EditPayload;
import cn.dawnstring.circe.quest.ChapterDefinition;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.resources.ResourceLocation;
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
        super(parent, "章节管理");
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
        values.put("title", "新章节");
        values.put("order", Integer.toString(ClientQuestState.chapters().size()));
        values.put("icon", "minecraft:book");
        baseline = ClientQuestState.bookRevision();
        shouldConfirmDelete = false;
    }

    @Override
    protected void initEditor()
    {
        button(frameLeft + 12, frameTop + 43, 148, "＋ 新建章节", () ->
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
                net.minecraft.network.chat.Component.translatable(chapter.title()).getString(), () ->
                {
                    select(chapter);
                    rebuild();
                });
        }
        button(frameLeft + 12, frameTop + 342, 70, "上一页", () ->
        {
            listPage--;
            rebuild();
        });
        button(frameLeft + 90, frameTop + 342, 70, "下一页", () ->
        {
            listPage++;
            rebuild();
        });
        int left = frameLeft + 182;
        int width = frameWidth - 200;
        bind("id", left, frameTop + 61, width, "章节 ID（保存后固定）").setEditable(originalId == null);
        bind("title", left, frameTop + 108, width, "章节标题");
        bind("order", left, frameTop + 155, width, "章节排序");
        bind("icon", left, frameTop + 202, width - 82, "章节图标");
        button(left + width - 74, frameTop + 202, 74, "选择物品", () ->
            minecraft.setScreen(new QuestItemPickerScreen(this, false, id -> values.put("icon", id.toString()))));
        label("先创建章节，再在章节中添加任务。", left, frameTop + 245);
        label("删除章节前，需要先移走或删除其中的任务。", left, frameTop + 261);
        button(left, frameTop + frameHeight - 26, 76, "保存章节", () -> save(false)).primary();
        button(left + 84, frameTop + frameHeight - 26, 76, "删除章节", () -> save(true));
        button(left + 168, frameTop + frameHeight - 26, 68, "返回", this::onClose);
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
            status = "再次点击确认删除章节。";
            return;
        }
        try
        {
            String id = values.get("id").trim();
            if (originalId == null && ClientQuestState.chapter(id) != null)
            {
                throw new IllegalArgumentException("章节 ID 已存在");
            }
            ChapterDefinition chapter = new ChapterDefinition(id, values.get("title"), Integer.parseInt(values.get("order")),
                ResourceLocation.parse(values.get("icon")));
            observed = ClientQuestState.editResultSequence();
            isPending = true;
            PacketDistributor.sendToServer(new EditPayload(isDelete ? "chapter_delete" : "chapter_save", id,
                chapter.toJson().toString(), baseline));
            status = "正在保存章节…";
        }
        catch (RuntimeException exception)
        {
            status = exception.getMessage();
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
