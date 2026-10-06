package cn.dawnstring.circe.client;

import cn.dawnstring.circe.network.EditPayload;
import cn.dawnstring.circe.quest.QuestDefinition;
import cn.dawnstring.circe.quest.QuestValidationException;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.LinkedHashMap;
import java.util.Map;

public final class QuestEditorScreen extends QuestEditingScreen
{
    private JsonObject draft;
    private String questId = "";
    private ResourceLocation originalId;
    private long baselineRevision;
    private int page;
    private int listPage;
    private boolean isPending;
    private boolean shouldConfirmDelete;
    private long observedResult;
    private long displayedRevision;
    private String selectedChapter;
    private final Map<String, String> values = new LinkedHashMap<>();
    private MultiLineEditBox description;
    private final boolean isGraphEditor;
    private boolean isDraggingCanvas;

    public QuestEditorScreen(Screen parent, ResourceLocation initialQuest)
    {
        this(parent, initialQuest, false);
    }

    private QuestEditorScreen(Screen parent, ResourceLocation initialQuest, boolean isGraphEditor)
    {
        super(parent, isGraphEditor ? "circe.editor.quest" : "circe.editor.studio");
        this.isGraphEditor = isGraphEditor;
        if (initialQuest != null && ClientQuestState.definition(initialQuest) != null)
        {
            select(initialQuest);
        }
        else
        {
            createDraft();
        }
    }

    public static QuestEditorScreen forGraph(QuestScreen parent, ResourceLocation questId)
    {
        return new QuestEditorScreen(parent, questId, true);
    }

    public static QuestEditorScreen graphDraft(QuestScreen parent, String chapter, QuestDefinition.GraphPosition position, QuestDefinition source)
    {
        var editor = new QuestEditorScreen(parent, null, true);
        if (source != null)
        {
            editor.draft = source.toJson();
            editor.draft.addProperty("title", QuestTranslations.text("circe.editor.copy_title", Component.translatable(source.title())));
            editor.draft.addProperty("revision", 1);
        }
        editor.selectedChapter = chapter;
        editor.draft.addProperty("chapter", chapter);
        editor.draft.add("position", position.toJson());
        editor.resetValues();
        parent.showDraft(QuestDefinition.parse(ResourceLocation.parse(editor.questId), editor.draft));
        return editor;
    }

    @Override
    protected void configureFrame(int viewWidth)
    {
        if (isGraphEditor)
        {
            int right = frameLeft + frameWidth;
            frameWidth = Math.min(440, Math.max(360, frameWidth / 2));
            frameWidth = Math.min(frameWidth, (int) (viewWidth * 0.8));
            frameLeft = right - frameWidth;
        }
    }

    @Override
    protected void renderBackdrop(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        if (isGraphEditor && parent instanceof QuestScreen graphScreen)
        {
            graphScreen.renderEditorBackground(graphics, mouseX, mouseY, partialTick, frameLeft);
        }
    }

    @Override
    protected void initEditor()
    {
        description = null;
        displayedRevision = ClientQuestState.bookRevision();
        int left = frameLeft + (isGraphEditor ? 14 : 170);
        int contentWidth = frameWidth - (isGraphEditor ? 28 : 184);
        if (selectedChapter == null || ClientQuestState.chapter(selectedChapter) == null)
        {
            selectedChapter = ClientQuestState.chapters().isEmpty() ? "" : ClientQuestState.chapters().getFirst().id();
        }
        if (values.get("chapter").isEmpty())
        {
            values.put("chapter", selectedChapter);
        }
        if (!isGraphEditor)
        {
            button(frameLeft + frameWidth - 157, frameTop + 7, 68, QuestTranslations.text("circe.editor.chapters"), () ->
                minecraft.setScreen(new QuestChapterEditorScreen(this)));
            button(frameLeft + frameWidth - 80, frameTop + 7, 68, QuestTranslations.text("circe.editor.book_label"), () ->
                minecraft.setScreen(new QuestTitleEditorScreen(this)));
            addRenderableWidget(new QuestDropdown(frameLeft + 10, frameTop + 43, 145,
                ClientQuestState.chapters().stream().map(chapter -> new QuestDropdown.Option(chapter.id(),
                    Component.translatable(chapter.title()))).toList(), selectedChapter, chapter ->
                {
                    selectedChapter = chapter;
                    listPage = 0;
                    createDraft();
                    rebuild();
                }));
            button(frameLeft + 10, frameTop + 76, 145, QuestTranslations.text("circe.button.new_quest_in_chapter"), () ->
            {
                if (!isPending)
                {
                    if (ClientQuestState.chapters().isEmpty())
                    {
                        minecraft.setScreen(new QuestChapterEditorScreen(this));
                        return;
                    }
                    createDraft();
                    rebuild();
                }
            }).primary();
            var definitions = ClientQuestState.definitions().stream().filter(definition -> definition.chapter().equals(selectedChapter)).toList();
            listPage = Math.clamp(listPage, 0, Math.max(0, (definitions.size() - 1) / 8));
            int first = listPage * 8;
            for (int index = first; index < Math.min(first + 8, definitions.size()); index++)
            {
                QuestDefinition definition = definitions.get(index);
                button(frameLeft + 10, frameTop + 108 + (index - first) * 27, 145,
                    Component.translatable(definition.title()).getString(), () ->
                    {
                        if (!isPending)
                        {
                            select(definition.id());
                            rebuild();
                        }
                    });
            }
            button(frameLeft + 10, frameTop + 343, 68, QuestTranslations.text("circe.button.previous"), () ->
            {
                listPage--;
                rebuild();
            });
            button(frameLeft + 87, frameTop + 343, 68, QuestTranslations.text("circe.button.next"), () ->
            {
                listPage++;
                rebuild();
            });
        }
        String[] pages = {QuestTranslations.text("circe.editor.basic"), QuestTranslations.text("circe.editor.objectives"), QuestTranslations.text("circe.editor.rewards"), QuestTranslations.text("circe.editor.content")};
        int tabWidth = Math.min(84, contentWidth / pages.length);
        for (int index = 0; index < pages.length; index++)
        {
            int selectedPage = index;
            var tab = button(left + index * tabWidth, frameTop + 38, tabWidth - 6, pages[index], () ->
            {
                page = selectedPage;
                rebuild();
            });
            if (page == index)
            {
                tab.primary();
            }
        }
        if (page == 0)
        {
            basicFields(left, frameTop + 81, contentWidth);
        }
        else if (page < 3)
        {
            entryList(left, frameTop + 74, contentWidth, page == 1);
        }
        else
        {
            contentFields(left, frameTop + 87, contentWidth);
        }
        button(left, frameTop + frameHeight - 26, 68, QuestTranslations.text("circe.button.save_quest"), this::save).primary();
        button(left + 76, frameTop + frameHeight - 26, 68, QuestTranslations.text("circe.button.delete_quest"), this::delete);
        button(left + 152, frameTop + frameHeight - 26, 68, QuestTranslations.text("circe.button.back"), this::onClose);
        button(left + 228, frameTop + frameHeight - 26, 104, QuestTranslations.text("circe.button.load_server"), () ->
        {
            if (isPending)
            {
                return;
            }
            if (originalId != null && ClientQuestState.definition(originalId) != null)
            {
                select(originalId);
            }
            else
            {
                createDraft();
            }
            rebuild();
        });
    }

    private void basicFields(int left, int top, int width)
    {
        bind("id", left, top, width, QuestTranslations.text("circe.editor.quest_id"), 256).setEditable(originalId == null);
        bind("title", left, top + 42, width, QuestTranslations.text("circe.editor.quest_title"), 256);
        label(QuestTranslations.text("circe.editor.chapter"), left, top + 72);
        addRenderableWidget(new QuestDropdown(left, top + 84, width,
            ClientQuestState.chapters().stream().map(chapter -> new QuestDropdown.Option(chapter.id(),
                Component.translatable(chapter.title()))).toList(), values.get("chapter"),
            chapter -> values.put("chapter", chapter)));
        int half = (width - 12) / 2;
        bind("revision", left, top + 126, half, QuestTranslations.text("circe.editor.revision"), 12);
        bind("order", left + half + 12, top + 126, half, QuestTranslations.text("circe.editor.order"), 12);
        bind("prerequisites", left, top + 176, width, QuestTranslations.text("circe.editor.prerequisites"), 8192);
        label(QuestTranslations.text("circe.editor.revision_hint"), left, top + 212);
        label(QuestTranslations.text("circe.editor.reset_hint"), left, top + 228);
        label(QuestTranslations.text("circe.editor.content_hint"), left, top + 258);
    }

    private void contentFields(int left, int top, int width)
    {
        label(QuestTranslations.text("circe.editor.cover"), left, top - 12);
        bind("image", left, top, width - 86, "", 256);
        button(left + width - 78, top, 78, QuestTranslations.text("circe.button.pick_image"), () ->
            minecraft.setScreen(new QuestImagePickerScreen(this, image -> values.put("image", image))));
        button(left, top + 37, 112, QuestTranslations.text("circe.button.edit_content"), () ->
            minecraft.setScreen(new QuestRichTextEditorScreen(this, values.get("description"),
                text -> values.put("description", text)))).primary();
        label(QuestTranslations.text("circe.editor.markup_hint"), left, top + 66);
        description = addRenderableWidget(new QuestMultilineEditBox(font, left, top + 82, width, 166,
            Component.translatable("circe.editor.description_placeholder"), Component.translatable("circe.editor.description")));
        description.setCharacterLimit(8192);
        description.setValue(values.get("description"));
        description.setValueListener(value -> values.put("description", value));
    }

    private EditBox bind(String key, int x, int y, int width, String label, int limit)
    {
        EditBox field = field(x, y, width, label, values.getOrDefault(key, ""), limit);
        field.setResponder(value -> values.put(key, value));
        return field;
    }

    private void entryList(int left, int top, int width, boolean isObjective)
    {
        String key = isObjective ? "objectives" : "rewards";
        JsonArray entries = draft.getAsJsonArray(key);
        for (int index = 0; index < entries.size(); index++)
        {
            int entryIndex = index;
            JsonObject entry = entries.get(index).getAsJsonObject();
            var rewardType = isObjective ? null : cn.dawnstring.circe.quest.RewardType.parse(
                entry.has("type") ? entry.get("type").getAsString() : "item");
            String name = isObjective ? Component.translatable(entry.get("title").getAsString()).getString()
                : QuestTranslations.text(rewardType.title()) + (rewardType.hasItem() ? " · " + entry.get("item").getAsString() : "");
            int y = top + index * 18;
            button(left, y, width - 34, name + " ×" + entry.get("count").getAsString(), () ->
                minecraft.setScreen(new QuestEntryEditorScreen(this, entry, isObjective,
                    updated -> entries.set(entryIndex, updated)))).setHeight(16);
            button(left + width - 28, y, 28, "×", () ->
            {
                entries.remove(entryIndex);
                rebuild();
            }).setHeight(16);
        }
        var add = button(left, top + 294, 100, isObjective ? QuestTranslations.text("circe.button.add_objective") : QuestTranslations.text("circe.button.add_reward"), () ->
            minecraft.setScreen(new QuestEntryEditorScreen(this, newEntry(isObjective, entries),
                isObjective, entries::add)));
        add.active = entries.size() < 16;
    }

    private static JsonObject defaultEntry(boolean isObjective, int index)
    {
        JsonObject entry = new JsonObject();
        if (isObjective)
        {
            entry.addProperty("id", "objective_" + index);
            entry.addProperty("type", "hold");
            entry.addProperty("title", QuestTranslations.text("circe.editor.default_objective"));
        }
        else
        {
            entry.addProperty("type", "circe:item");
            entry.add("components", new JsonObject());
        }
        entry.addProperty(isObjective ? "target" : "item", "minecraft:oak_log");
        entry.addProperty("count", 1);
        return entry;
    }

    private static JsonObject newEntry(boolean isObjective, JsonArray entries)
    {
        int suffix = 1;
        java.util.Set<String> identifiers = new java.util.HashSet<>();
        if (isObjective)
        {
            entries.forEach(entry -> identifiers.add(entry.getAsJsonObject().get("id").getAsString()));
            while (identifiers.contains("objective_" + suffix))
            {
                suffix++;
            }
        }
        return defaultEntry(isObjective, suffix);
    }

    private void select(ResourceLocation id)
    {
        originalId = id;
        questId = id.toString();
        draft = ClientQuestState.definition(id).toJson();
        selectedChapter = draft.get("chapter").getAsString();
        resetValues();
    }

    private void createDraft()
    {
        originalId = null;
        int suffix = 1;
        do
        {
            questId = "circe:quest_" + suffix++;
        }
        while (ClientQuestState.definition(ResourceLocation.parse(questId)) != null);
        draft = new JsonObject();
        draft.addProperty("title", QuestTranslations.text("circe.editor.new_quest"));
        if (selectedChapter == null)
        {
            selectedChapter = ClientQuestState.chapters().isEmpty() ? "" : ClientQuestState.chapters().getFirst().id();
        }
        draft.addProperty("chapter", selectedChapter);
        draft.addProperty("description", "");
        draft.addProperty("image", "");
        draft.addProperty("revision", 1);
        draft.addProperty("order", ClientQuestState.definitions().size());
        draft.add("prerequisites", new JsonArray());
        JsonArray objectives = new JsonArray();
        objectives.add(defaultEntry(true, 1));
        draft.add("objectives", objectives);
        draft.add("rewards", new JsonArray());
        resetValues();
    }

    private void resetValues()
    {
        values.clear();
        values.put("id", questId);
        for (String key : new String[]{"title", "chapter", "description", "image", "revision", "order"})
        {
            values.put(key, draft.get(key).getAsString());
        }
        values.put("prerequisites", java.util.stream.StreamSupport.stream(draft.getAsJsonArray("prerequisites").spliterator(), false)
            .map(entry -> entry.getAsString()).collect(java.util.stream.Collectors.joining(", ")));
        baselineRevision = ClientQuestState.bookRevision();
        shouldConfirmDelete = false;
        status = Component.empty();
        page = 0;
    }

    private void save()
    {
        if (isPending)
        {
            return;
        }
        try
        {
            ResourceLocation id = ResourceLocation.parse(values.get("id").trim());
            if (originalId == null && ClientQuestState.definition(id) != null)
            {
                throw new IllegalArgumentException(QuestTranslations.text("circe.error.quest_exists"));
            }
            for (String key : new String[]{"title", "chapter", "description", "image"})
            {
                draft.addProperty(key, values.get(key));
            }
            draft.addProperty("revision", Integer.parseInt(values.get("revision")));
            draft.addProperty("order", Integer.parseInt(values.get("order")));
            JsonArray prerequisites = new JsonArray();
            for (String prerequisite : values.get("prerequisites").split("[,，\\n]"))
            {
                if (!prerequisite.isBlank())
                {
                    prerequisites.add(ResourceLocation.parse(prerequisite.trim()).toString());
                }
            }
            draft.add("prerequisites", prerequisites);
            QuestDefinition.parse(id, draft);
            questId = id.toString();
            send("save", questId, draft.toString());
        }
        catch (RuntimeException exception)
        {
            status = QuestValidationException.message(exception);
        }
    }

    private void delete()
    {
        if (isPending || originalId == null)
        {
            return;
        }
        if (!shouldConfirmDelete)
        {
            shouldConfirmDelete = true;
            status = Component.translatable("circe.editor.confirm_quest_delete");
            return;
        }
        send("delete", originalId.toString(), "");
    }

    private void send(String operation, String id, String json)
    {
        observedResult = ClientQuestState.editResultSequence();
        isPending = true;
        for (var child : children())
        {
            if (child instanceof QuestButton button)
            {
                button.active = false;
            }
        }
        status = Component.translatable("circe.editor.saving");
        PacketDistributor.sendToServer(new EditPayload(operation, id, json, baselineRevision));
    }

    @Override
    public void tick()
    {
        super.tick();
        if (!ClientQuestState.canEdit())
        {
            return;
        }
        if (!isPending || observedResult == ClientQuestState.editResultSequence())
        {
            if (!isPending && displayedRevision != ClientQuestState.bookRevision())
            {
                status = Component.translatable("circe.editor.conflict");
                rebuild();
            }
            return;
        }
        isPending = false;
        var result = ClientQuestState.editResult();
        status = result.message();
        if (result.isSuccessful())
        {
            baselineRevision = ClientQuestState.bookRevision();
            if (ClientQuestState.definition(ResourceLocation.parse(questId)) == null)
            {
                createDraft();
            }
            else
            {
                originalId = ResourceLocation.parse(questId);
            }
            if (isGraphEditor)
            {
                ((QuestScreen) parent).selectEditedQuest(ResourceLocation.parse(questId));
                onClose();
                return;
            }
        }
        rebuild();
    }

    public void titleSaved()
    {
        if (baselineRevision == ClientQuestState.bookRevision() - 1)
        {
            baselineRevision++;
        }
    }

    protected void rebuild()
    {
        clearWidgets();
        init();
    }

    @Override
    protected void renderContent(GuiGraphics graphics)
    {
        if (!isGraphEditor)
        {
            graphics.fill(frameLeft + 161, frameTop + 32, frameLeft + 162, frameTop + frameHeight - 52, QuestTheme.border());
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        if (isGraphEditor && mouseX / uiScale < frameLeft
            && ((QuestScreen) parent).containsEditorGraph(mouseX, mouseY))
        {
            isDraggingCanvas = button == 0;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY)
    {
        if (isDraggingCanvas && button == 0)
        {
            ((QuestScreen) parent).panEditorGraph(deltaX, deltaY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button)
    {
        if (isDraggingCanvas && button == 0)
        {
            isDraggingCanvas = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalScroll, double verticalScroll)
    {
        if (isGraphEditor && mouseX / uiScale < frameLeft
            && ((QuestScreen) parent).containsEditorGraph(mouseX, mouseY))
        {
            ((QuestScreen) parent).zoomEditorGraph(mouseX, mouseY, verticalScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalScroll, verticalScroll);
    }

    @Override
    public void onClose()
    {
        if (isPending)
        {
            status = Component.translatable("circe.editor.wait_save");
            return;
        }
        if (isGraphEditor)
        {
            ((QuestScreen) parent).clearDraft();
        }
        super.onClose();
    }
}
