package cn.dawnstring.circe.client;

import cn.dawnstring.circe.network.EditPayload;
import cn.dawnstring.circe.quest.QuestDefinition;
import com.google.gson.JsonArray;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class QuestScreen extends Screen
{
    private static final int HEADER_HEIGHT = 30;
    private static final int CHAPTER_ROW_HEIGHT = 34;
    private static final long OPEN_DURATION_MILLIS = 280;
    private static final int REFERENCE_HEIGHT = 540;
    private float uiScale;
    private int viewWidth;
    private int viewHeight;
    private final long openedAt = Util.getMillis();
    private final QuestGraphCanvas graph = new QuestGraphCanvas();
    private QuestDetailsPopup popup;
    private QuestButton closeButton;
    private QuestButton fitButton;
    private QuestButton editorButton;
    private QuestButton settingsButton;
    private List<QuestDefinition> knownDefinitions = List.of();
    private List<String> chapters = List.of();
    private String selectedChapter;
    private ResourceLocation selectedQuest;
    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;
    private int sidebarWidth;
    private int graphViewportWidth;
    private int chapterScroll;
    private boolean isDraggingGraph;
    private boolean isEditing;
    private boolean hasGraphView;
    private boolean isPendingGraphEdit;
    private boolean shouldFitAfterEdit;
    private boolean hasMovedNode;
    private ResourceLocation draggedQuest;
    private ResourceLocation prerequisiteTarget;
    private double dragOffsetX;
    private double dragOffsetY;
    private double pressedX;
    private double pressedY;
    private long dragRevision;
    private long observedEditResult;
    private Component graphStatus = Component.empty();
    private long statusExpiresAt;
    private QuestDefinition graphDraft;
    private final QuestGraphContextMenu contextMenu = new QuestGraphContextMenu();

    public QuestScreen()
    {
        this(ClientQuestState.trackedQuest());
    }

    public QuestScreen(ResourceLocation initialQuest)
    {
        super(Component.translatable("circe.screen.title"));
        selectedQuest = initialQuest;
    }

    @Override
    protected void init()
    {
        uiScale = height / (float) REFERENCE_HEIGHT;
        viewWidth = Math.round(width / uiScale);
        viewHeight = REFERENCE_HEIGHT;
        panelWidth = (int) (viewWidth * 0.8);
        panelHeight = (int) (viewHeight * 0.8);
        panelLeft = (viewWidth - panelWidth) / 2;
        panelTop = (viewHeight - panelHeight) / 2;
        sidebarWidth = Math.clamp(panelWidth / 5, 70, 156);
        configureGraphViewport(panelWidth - sidebarWidth - 3);
        closeButton = addRenderableWidget(new QuestButton(panelLeft + panelWidth - 27, panelTop + 6,
            20, Component.literal("×"), button -> onClose()));
        closeButton.setHeight(18);
        fitButton = addRenderableWidget(new QuestButton(panelLeft + panelWidth - 48, panelTop + HEADER_HEIGHT + 4,
            40, Component.translatable("circe.button.fit"), button ->
            {
                popup.close();
                graph.fit(selectedQuest);
            }));
        fitButton.setHeight(16);
        editorButton = addRenderableWidget(new QuestButton(panelLeft + panelWidth - 93, panelTop + 6,
            60, Component.translatable(isEditing ? "circe.button.finish_editing" : "circe.button.edit"), button -> toggleEditing()));
        editorButton.setHeight(18);
        editorButton.visible = ClientQuestState.canEdit();
        settingsButton = addRenderableWidget(new QuestButton(panelLeft + panelWidth - 145, panelTop + 6,
            46, Component.translatable("circe.button.settings"), button -> minecraft.setScreen(new QuestSettingsScreen(this))));
        settingsButton.setHeight(18);
        popup = new QuestDetailsPopup();
        popup.buttons().forEach(this::addRenderableWidget);
        refreshCatalog(true);
    }

    @Override
    public void tick()
    {
        refreshCatalog(false);
        popup.update();
        editorButton.visible = ClientQuestState.canEdit();
        if (!ClientQuestState.canEdit() && isEditing)
        {
            isEditing = false;
            draggedQuest = null;
            prerequisiteTarget = null;
            contextMenu.close();
            clearDraft();
            editorButton.setMessage(Component.translatable("circe.button.edit"));
        }
        if (isPendingGraphEdit && observedEditResult != ClientQuestState.editResultSequence())
        {
            isPendingGraphEdit = false;
            showStatus(ClientQuestState.editResult().message());
            graph.setDefinitions(chapterQuests(), selectedQuest,
                shouldFitAfterEdit && ClientQuestState.editResult().isSuccessful());
        }
    }

    private void refreshCatalog(boolean shouldForce)
    {
        if (draggedQuest != null && dragRevision != ClientQuestState.bookRevision())
        {
            draggedQuest = null;
            hasMovedNode = false;
            graph.setDefinitions(chapterQuests(), selectedQuest, false);
            showStatus(Component.translatable("circe.graph.drag_cancelled"));
        }
        List<QuestDefinition> definitions = ClientQuestState.definitions();
        List<String> chapterIds = ClientQuestState.chapters().stream().map(chapter -> chapter.id()).toList();
        if (!shouldForce && definitions.equals(knownDefinitions) && chapterIds.equals(chapters))
        {
            return;
        }
        ResourceLocation openedQuest = popup.isOpen() ? popup.questId() : null;
        knownDefinitions = definitions;
        chapters = chapterIds;
        if (selectedQuest != null && ClientQuestState.definition(selectedQuest) == null
            && (graphDraft == null || !graphDraft.id().equals(selectedQuest)))
        {
            selectedQuest = null;
        }
        if (selectedChapter == null || !chapters.contains(selectedChapter))
        {
            QuestDefinition selected = selectedQuest == null ? null : ClientQuestState.definition(selectedQuest);
            selectedChapter = selected == null ? (chapters.isEmpty() ? null : chapters.getFirst()) : selected.chapter();
        }
        graph.setDefinitions(chapterQuests(), selectedQuest, !hasGraphView);
        hasGraphView = true;
        contextMenu.close();
        if (openedQuest != null && chapterQuests().stream().anyMatch(definition -> definition.id().equals(openedQuest)))
        {
            popup.update();
        }
        else
        {
            popup.close();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        int slideY = slideOffset();
        int localMouseX = (int) (mouseX / uiScale);
        int localMouseY = (int) (mouseY / uiScale) - slideY;
        graphics.pose().pushPose();
        graphics.pose().scale(uiScale, uiScale, 1);
        graphics.pose().translate(0, slideY, 0);
        renderFrame(graphics);
        renderChapters(graphics, localMouseX, localMouseY);
        graph.render(graphics, font, selectedQuest, localMouseX, localMouseY, popup.isOpen());
        closeButton.render(graphics, localMouseX, localMouseY, partialTick);
        fitButton.render(graphics, localMouseX, localMouseY, partialTick);
        editorButton.render(graphics, localMouseX, localMouseY, partialTick);
        settingsButton.render(graphics, localMouseX, localMouseY, partialTick);
        positionPopup();
        if (popup.isOpen())
        {
            graphics.flush();
            graphics.pose().pushPose();
            // 物品图标自带深度偏移，详情层必须位于关系图物品之上。
            graphics.pose().translate(0, 0, 300);
            int anchorX = graph.nodeX(popup.questId());
            int anchorY = graph.nodeY(popup.questId()) + graph.nodeSize();
            float expansionScale = popup.expansionScale();
            graphics.pose().translate(anchorX, anchorY, 0);
            graphics.pose().scale(expansionScale, expansionScale, 1);
            graphics.pose().translate(-anchorX, -anchorY, 0);
            popup.render(graphics, font, anchorX, anchorY);
            for (var button : popup.buttons())
            {
                button.render(graphics, localMouseX, localMouseY, partialTick);
            }
            graphics.pose().popPose();
        }
        contextMenu.render(graphics, font, localMouseX, localMouseY);
        if (!graphStatus.getString().isBlank() && (Util.getMillis() < statusExpiresAt || isPendingGraphEdit || prerequisiteTarget != null))
        {
            graphics.drawString(font, QuestTheme.fit(font, graphStatus, panelWidth - sidebarWidth - 20),
                graphLeft() + 8, panelTop + panelHeight - 19, QuestTheme.gold(), false);
        }
        graphics.pose().popPose();
        if (minecraft.screen == this)
        {
            QuestCompletionBanner.render(graphics);
        }
    }

    private void renderFrame(GuiGraphics graphics)
    {
        QuestTheme.card(graphics, panelLeft, panelTop, panelWidth, panelHeight);
        if (QuestTheme.style() == QuestTheme.Style.CLASSIC || isEditing)
        {
            graphics.fill(panelLeft + 4, panelTop + 1, panelLeft + panelWidth - 4, panelTop + 3,
                isEditing ? QuestTheme.gold() : QuestTheme.accent());
        }
        graphics.renderItem(new ItemStack(Items.BOOK),
            panelLeft + 10, panelTop + 7);
        graphics.drawString(font, QuestTheme.fit(font, isEditing ? Component.translatable("circe.graph.edit_title", Component.translatable(ClientQuestState.bookTitle()))
            : Component.translatable(ClientQuestState.bookTitle()), panelWidth - 188),
            panelLeft + 32, panelTop + 12, QuestTheme.text(), false);
        graphics.fill(panelLeft + 1, panelTop + HEADER_HEIGHT - 1,
            panelLeft + panelWidth - 1, panelTop + HEADER_HEIGHT, QuestTheme.border());
        graphics.fill(graphLeft() - 1, panelTop + HEADER_HEIGHT, graphLeft(), panelTop + panelHeight - 1, QuestTheme.border());
        if (selectedChapter != null)
        {
            graphics.drawString(font, QuestTheme.fit(font, Component.translatable(ClientQuestState.chapter(selectedChapter).title()),
                panelWidth - sidebarWidth - 58), graphLeft() + 8,
                panelTop + HEADER_HEIGHT + 8, QuestTheme.accent(), false);
        }
    }

    private void renderChapters(GuiGraphics graphics, int mouseX, int mouseY)
    {
        int listTop = panelTop + HEADER_HEIGHT + 22;
        int listBottom = panelTop + panelHeight - 6;
        chapterScroll = Math.clamp(chapterScroll, 0,
            Math.max(0, chapters.size() * CHAPTER_ROW_HEIGHT - (listBottom - listTop)));
        graphics.drawString(font, Component.translatable("circe.screen.chapters"),
            panelLeft + 9, panelTop + HEADER_HEIGHT + 8, QuestTheme.muted(), false);
        QuestUiViewport.enableScissor(graphics, panelLeft + 2, listTop, graphLeft() - 2, listBottom);
        for (int index = 0; index < chapters.size(); index++)
        {
            String chapter = chapters.get(index);
            int y = listTop + index * CHAPTER_ROW_HEIGHT - chapterScroll;
            boolean isSelected = chapter.equals(selectedChapter);
            boolean isHovered = mouseX >= panelLeft && mouseX < graphLeft()
                && mouseY >= y && mouseY < y + CHAPTER_ROW_HEIGHT;
            if (isSelected || isHovered)
            {
                QuestTheme.fill(graphics, panelLeft + 4, y, graphLeft() - 4, y + CHAPTER_ROW_HEIGHT - 3,
                    isSelected ? QuestTheme.selected() : QuestTheme.panel());
                if (isSelected && QuestTheme.style() == QuestTheme.Style.CLASSIC)
                {
                    graphics.fill(panelLeft + 4, y, panelLeft + 6, y + CHAPTER_ROW_HEIGHT - 3, QuestTheme.accent());
                }
            }
            var quests = knownDefinitions.stream().filter(definition -> definition.chapter().equals(chapter)).toList();
            long completed = quests.stream().filter(definition -> ClientQuestState.progress(definition).isCompleted()).count();
            if (sidebarWidth >= 96)
            {
                graphics.renderItem(new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                    ClientQuestState.chapter(chapter).icon())), panelLeft + 9, y + 7);
            }
            int textX = panelLeft + (sidebarWidth >= 96 ? 30 : 10);
            int textWidth = graphLeft() - textX - 8;
            graphics.drawString(font, QuestTheme.fit(font, Component.translatable(ClientQuestState.chapter(chapter).title()), textWidth),
                textX, y + 5, isSelected ? QuestTheme.text() : QuestTheme.muted(), false);
            graphics.drawString(font, completed + "/" + quests.size(), textX, y + 18, QuestTheme.muted(), false);
            if (QuestTheme.style() != QuestTheme.Style.CLASSIC)
            {
                int progressLeft = textX + 34;
                int progressRight = graphLeft() - 12;
                graphics.fill(progressLeft, y + 23, progressRight, y + 25, QuestTheme.border());
                if (!quests.isEmpty())
                {
                    graphics.fill(progressLeft, y + 23, progressLeft + (int) ((progressRight - progressLeft) * completed / quests.size()),
                        y + 25, QuestTheme.accent());
                }
            }
        }
        graphics.disableScissor();
    }

    private void positionPopup()
    {
        if (popup.isOpen())
        {
            popup.position(graph.nodeX(popup.questId()), graph.nodeY(popup.questId()) + graph.nodeSize(),
                graphLeft(), panelTop + HEADER_HEIGHT, panelLeft + panelWidth - 1, panelTop + panelHeight - 2);
        }
    }

    private int slideOffset()
    {
        if (!QuestUiSettings.current().hasAnimations())
        {
            return 0;
        }
        double progress = Math.clamp((Util.getMillis() - openedAt) / (double) OPEN_DURATION_MILLIS, 0, 1);
        double remaining = Math.pow(1 - progress, 3);
        return (int) Math.round((viewHeight - panelTop) * remaining);
    }

    private int graphLeft()
    {
        return panelLeft + sidebarWidth + 1;
    }

    private int graphTop()
    {
        return panelTop + HEADER_HEIGHT + 24;
    }

    private List<QuestDefinition> chapterQuests()
    {
        var definitions = new ArrayList<>(knownDefinitions.stream().filter(definition -> definition.chapter().equals(selectedChapter)).toList());
        if (graphDraft != null && graphDraft.chapter().equals(selectedChapter) && ClientQuestState.definition(graphDraft.id()) == null)
        {
            definitions.add(graphDraft);
        }
        return definitions;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        mouseX /= uiScale;
        double localMouseY = mouseY / uiScale - slideOffset();
        if (contextMenu.click(mouseX, localMouseY, button))
        {
            return true;
        }
        if (isEditing && ClientQuestState.canEdit() && button == GLFW.GLFW_MOUSE_BUTTON_RIGHT)
        {
            if (!isPendingGraphEdit)
            {
                openContextMenu(mouseX, localMouseY);
            }
            return true;
        }
        positionPopup();
        if (popup.contains(mouseX, localMouseY))
        {
            for (var popupButton : popup.buttons())
            {
                if (popupButton.mouseClicked(mouseX, localMouseY, button))
                {
                    setFocused(popupButton);
                    return true;
                }
            }
            return true;
        }
        if (super.mouseClicked(mouseX, localMouseY, button))
        {
            return true;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT)
        {
            return false;
        }
        if (selectChapter(mouseX, localMouseY))
        {
            return true;
        }
        if (graph.contains(mouseX, localMouseY))
        {
            ResourceLocation hit = graph.hit(mouseX, localMouseY);
            if (hit != null)
            {
                selectedQuest = hit;
                if (isEditing)
                {
                    if (isPendingGraphEdit)
                    {
                        return true;
                    }
                    if (prerequisiteTarget != null)
                    {
                        confirmPrerequisite(hit);
                        return true;
                    }
                    draggedQuest = hit;
                    hasMovedNode = false;
                    pressedX = mouseX;
                    pressedY = localMouseY;
                    dragOffsetX = mouseX - graph.nodeX(hit);
                    dragOffsetY = localMouseY - graph.nodeY(hit);
                    dragRevision = ClientQuestState.bookRevision();
                    return true;
                }
                graph.revealDetails(hit);
                popup.open(hit);
                positionPopup();
            }
            else
            {
                popup.close();
                isDraggingGraph = true;
            }
            return true;
        }
        popup.close();
        return true;
    }

    private boolean selectChapter(double mouseX, double mouseY)
    {
        int listTop = panelTop + HEADER_HEIGHT + 22;
        if (mouseX < panelLeft || mouseX >= graphLeft() || mouseY < listTop
            || mouseY >= panelTop + panelHeight - 6)
        {
            return false;
        }
        int index = ((int) mouseY - listTop + chapterScroll) / CHAPTER_ROW_HEIGHT;
        if (index >= 0 && index < chapters.size())
        {
            selectedChapter = chapters.get(index);
            selectedQuest = null;
            popup.close();
            graph.setDefinitions(chapterQuests(), null);
            prerequisiteTarget = null;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY)
    {
        if (draggedQuest != null && isEditing && ClientQuestState.canEdit() && button == GLFW.GLFW_MOUSE_BUTTON_LEFT)
        {
            double localX = mouseX / uiScale;
            double localY = mouseY / uiScale - slideOffset();
            if (Math.hypot(localX - pressedX, localY - pressedY) >= 3)
            {
                hasMovedNode = true;
            }
            if (hasMovedNode)
            {
                graph.moveNode(draggedQuest, graph.positionAt(localX - dragOffsetX, localY - dragOffsetY));
            }
            return true;
        }
        if (isDraggingGraph && button == GLFW.GLFW_MOUSE_BUTTON_LEFT)
        {
            graph.pan(deltaX / uiScale, deltaY / uiScale);
            return true;
        }
        return super.mouseDragged(mouseX / uiScale, mouseY / uiScale - slideOffset(),
            button, deltaX / uiScale, deltaY / uiScale);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button)
    {
        isDraggingGraph = false;
        if (draggedQuest != null && button == GLFW.GLFW_MOUSE_BUTTON_LEFT)
        {
            ResourceLocation questId = draggedQuest;
            draggedQuest = null;
            if (hasMovedNode)
            {
                sendGraphEdit("move", questId.toString(), graph.position(questId).toJson().toString(), dragRevision);
            }
            else if (isEditing && ClientQuestState.canEdit())
            {
                openTaskEditor(questId);
            }
            return true;
        }
        return super.mouseReleased(mouseX / uiScale, mouseY / uiScale - slideOffset(), button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalScroll, double verticalScroll)
    {
        if (contextMenu.isOpen() || draggedQuest != null)
        {
            return true;
        }
        mouseX /= uiScale;
        double localMouseY = mouseY / uiScale - slideOffset();
        if (popup.contains(mouseX, localMouseY))
        {
            popup.scroll(verticalScroll);
            return true;
        }
        if (graph.contains(mouseX, localMouseY))
        {
            popup.close();
            graph.zoom(verticalScroll, mouseX, localMouseY);
            return true;
        }
        if (mouseX >= panelLeft && mouseX < graphLeft()
            && localMouseY >= panelTop + HEADER_HEIGHT && localMouseY < panelTop + panelHeight)
        {
            chapterScroll = Math.max(0, chapterScroll - (int) (verticalScroll * CHAPTER_ROW_HEIGHT));
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers)
    {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && (contextMenu.isOpen() || prerequisiteTarget != null))
        {
            contextMenu.close();
            prerequisiteTarget = null;
            showStatus("");
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && popup.isOpen())
        {
            popup.close();
            setFocused(null);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void toggleEditing()
    {
        if (!ClientQuestState.canEdit() || isPendingGraphEdit)
        {
            return;
        }
        isEditing = !isEditing;
        popup.close();
        contextMenu.close();
        prerequisiteTarget = null;
        draggedQuest = null;
        isDraggingGraph = false;
        editorButton.setMessage(Component.translatable(isEditing ? "circe.button.finish_editing" : "circe.button.edit"));
        showStatus("");
    }

    private void showStatus(String message)
    {
        showStatus(Component.literal(message));
    }

    private void showStatus(Component message)
    {
        graphStatus = message;
        statusExpiresAt = Util.getMillis() + 6000;
    }

    private void sendGraphEdit(String operation, String id, String json, long revision)
    {
        if (!isEditing || !ClientQuestState.canEdit() || isPendingGraphEdit)
        {
            return;
        }
        observedEditResult = ClientQuestState.editResultSequence();
        shouldFitAfterEdit = operation.equals("auto_layout");
        isPendingGraphEdit = true;
        showStatus(Component.translatable("circe.editor.saving_server"));
        PacketDistributor.sendToServer(new EditPayload(operation, id, json, revision));
    }

    private void openTaskEditor(ResourceLocation id)
    {
        if (isPendingGraphEdit || ClientQuestState.definition(id) == null)
        {
            return;
        }
        popup.close();
        minecraft.setScreen(QuestEditorScreen.forGraph(this, id));
    }

    public void showDraft(QuestDefinition definition)
    {
        graphDraft = definition;
        selectedChapter = definition.chapter();
        selectedQuest = definition.id();
        graph.setDefinitions(chapterQuests(), selectedQuest, false);
    }

    public void clearDraft()
    {
        graphDraft = null;
        if (selectedQuest != null && ClientQuestState.definition(selectedQuest) == null)
        {
            selectedQuest = null;
        }
        graph.setDefinitions(chapterQuests(), selectedQuest, false);
    }

    public void selectEditedQuest(ResourceLocation id)
    {
        QuestDefinition definition = ClientQuestState.definition(id);
        selectedQuest = definition == null ? null : id;
        if (definition != null)
        {
            selectedChapter = definition.chapter();
        }
        refreshCatalog(true);
    }

    private void configureGraphViewport(int viewportWidth)
    {
        boolean hasChanged = graphViewportWidth != viewportWidth;
        graphViewportWidth = viewportWidth;
        graph.setBounds(graphLeft(), graphTop(), viewportWidth, panelHeight - HEADER_HEIGHT - 38);
        if (hasChanged && hasGraphView)
        {
            graph.fit(selectedQuest);
        }
    }

    public void renderEditorBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, int editorLeft)
    {
        refreshCatalog(false);
        popup.close();
        configureGraphViewport(Math.max(40, editorLeft - graphLeft() - 3));
        // 侧栏透明时只透出游戏场景，避免底层按钮和文字穿过属性面板。
        graphics.enableScissor(0, 0, (int) (editorLeft * uiScale), height);
        render(graphics, mouseX, mouseY, partialTick);
        graphics.disableScissor();
    }

    public boolean containsEditorGraph(double mouseX, double mouseY)
    {
        return graph.contains(mouseX / uiScale, mouseY / uiScale - slideOffset());
    }

    public void panEditorGraph(double deltaX, double deltaY)
    {
        graph.pan(deltaX / uiScale, deltaY / uiScale);
    }

    public void zoomEditorGraph(double mouseX, double mouseY, double amount)
    {
        graph.zoom(amount, mouseX / uiScale, mouseY / uiScale - slideOffset());
    }

    private void openContextMenu(double mouseX, double mouseY)
    {
        popup.close();
        prerequisiteTarget = null;
        isDraggingGraph = false;
        List<QuestGraphContextMenu.Action> actions = new ArrayList<>();
        if (graph.contains(mouseX, mouseY))
        {
            ResourceLocation hit = graph.hit(mouseX, mouseY);
            if (hit == null)
            {
                var position = graph.positionAt(mouseX, mouseY);
                actions.add(new QuestGraphContextMenu.Action(Component.translatable("circe.button.new_quest"), () ->
                {
                    if (selectedChapter == null)
                    {
                        minecraft.setScreen(new QuestChapterEditorScreen(this));
                        return;
                    }
                    minecraft.setScreen(QuestEditorScreen.graphDraft(this, selectedChapter, position, null));
                }));
                if (selectedChapter != null)
                {
                    String chapter = selectedChapter;
                    long revision = ClientQuestState.bookRevision();
                    actions.add(new QuestGraphContextMenu.Action(Component.translatable("circe.graph.arrange_chapter"), () -> minecraft.setScreen(
                        new QuestGraphConfirmScreen(this, Component.translatable("circe.graph.arrange"), Component.translatable("circe.graph.arrange_confirm"),
                            true, () -> sendGraphEdit("auto_layout", chapter, "", revision)))));
                }
            }
            else
            {
                addTaskActions(actions, hit);
            }
        }
        else if (mouseX >= panelLeft && mouseX < graphLeft()
            && mouseY >= panelTop + HEADER_HEIGHT && mouseY < panelTop + panelHeight)
        {
            int index = ((int) mouseY - panelTop - HEADER_HEIGHT - 22 + chapterScroll) / CHAPTER_ROW_HEIGHT;
            String chapter = mouseY >= panelTop + HEADER_HEIGHT + 22 && index >= 0 && index < chapters.size()
                ? chapters.get(index) : null;
            actions.add(new QuestGraphContextMenu.Action(Component.translatable("circe.button.new_chapter"), () -> minecraft.setScreen(new QuestChapterEditorScreen(this))));
            if (chapter != null)
            {
                actions.add(new QuestGraphContextMenu.Action(Component.translatable("circe.button.edit_chapter"), () -> minecraft.setScreen(new QuestChapterEditorScreen(this, chapter))));
                actions.add(new QuestGraphContextMenu.Action(Component.translatable("circe.button.delete_chapter"), () -> confirmDeleteChapter(chapter)));
            }
        }
        else if (mouseX >= panelLeft && mouseX < panelLeft + panelWidth
            && mouseY >= panelTop && mouseY < panelTop + HEADER_HEIGHT)
        {
            actions.add(new QuestGraphContextMenu.Action(Component.translatable("circe.editor.book_title"), () -> minecraft.setScreen(new QuestTitleEditorScreen(this))));
        }
        contextMenu.open((int) mouseX, (int) mouseY, panelLeft + 2, panelTop + 3,
            panelLeft + panelWidth - 2, panelTop + panelHeight - 2, actions);
    }

    private void addTaskActions(List<QuestGraphContextMenu.Action> actions, ResourceLocation id)
    {
        selectedQuest = id;
        QuestDefinition definition = ClientQuestState.definition(id);
        actions.add(new QuestGraphContextMenu.Action(Component.translatable("circe.button.edit_quest"), () -> openTaskEditor(id)));
        actions.add(new QuestGraphContextMenu.Action(Component.translatable("circe.button.copy_quest"), () ->
        {
            var position = graph.position(id);
            var offset = new QuestDefinition.GraphPosition(Math.min(10000, position.x() + 48), Math.min(10000, position.y() + 48));
            minecraft.setScreen(QuestEditorScreen.graphDraft(this, definition.chapter(), offset, definition));
        }));
        actions.add(new QuestGraphContextMenu.Action(Component.translatable("circe.button.add_prerequisite"), () ->
        {
            prerequisiteTarget = id;
            showStatus(Component.translatable("circe.graph.pick_prerequisite", Component.translatable(definition.title())));
        }));
        if (!definition.prerequisites().isEmpty())
        {
            actions.add(new QuestGraphContextMenu.Action(Component.translatable("circe.button.clear_prerequisites"), () -> confirmClearPrerequisites(definition)));
        }
        actions.add(new QuestGraphContextMenu.Action(Component.translatable("circe.button.delete_quest"), () -> confirmDeleteTask(definition)));
    }

    private void confirmDeleteTask(QuestDefinition definition)
    {
        var dependents = knownDefinitions.stream().filter(quest -> quest.prerequisites().contains(definition.id())).toList();
        Component message = dependents.isEmpty()
            ? Component.translatable("circe.graph.delete_quest_confirm", Component.translatable(definition.title()))
            : Component.translatable("circe.graph.delete_blocked", dependents.size(),
                String.join("\n", dependents.stream().map(quest -> quest.id().toString()).toList()));
        long revision = ClientQuestState.bookRevision();
        minecraft.setScreen(new QuestGraphConfirmScreen(this, Component.translatable("circe.button.delete_quest"), message, dependents.isEmpty(),
            () -> sendGraphEdit("delete", definition.id().toString(), "", revision)));
    }

    private void confirmDeleteChapter(String chapter)
    {
        long count = knownDefinitions.stream().filter(quest -> quest.chapter().equals(chapter)).count();
        long revision = ClientQuestState.bookRevision();
        Component message = count == 0 ? Component.translatable("circe.graph.delete_chapter_confirm", chapter)
            : Component.translatable("circe.graph.chapter_not_empty", count);
        minecraft.setScreen(new QuestGraphConfirmScreen(this, Component.translatable("circe.button.delete_chapter"), message, count == 0,
            () -> sendGraphEdit("chapter_delete", chapter, "", revision)));
    }

    private void confirmClearPrerequisites(QuestDefinition definition)
    {
        var updated = definition.toJson();
        updated.add("prerequisites", new JsonArray());
        updated.addProperty("revision", definition.revision() + 1);
        long revision = ClientQuestState.bookRevision();
        minecraft.setScreen(new QuestGraphConfirmScreen(this, Component.translatable("circe.graph.clear_prerequisites"),
            Component.translatable("circe.graph.clear_confirm"), true,
            () -> sendGraphEdit("save", definition.id().toString(), updated.toString(), revision)));
    }

    private void confirmPrerequisite(ResourceLocation prerequisite)
    {
        QuestDefinition definition = ClientQuestState.definition(prerequisiteTarget);
        if (definition == null)
        {
            prerequisiteTarget = null;
            return;
        }
        if (definition.id().equals(prerequisite) || definition.prerequisites().contains(prerequisite))
        {
            showStatus(Component.translatable("circe.graph.invalid_prerequisite"));
            return;
        }
        prerequisiteTarget = null;
        var updated = definition.toJson();
        updated.getAsJsonArray("prerequisites").add(prerequisite.toString());
        updated.addProperty("revision", definition.revision() + 1);
        long revision = ClientQuestState.bookRevision();
        minecraft.setScreen(new QuestGraphConfirmScreen(this, Component.translatable("circe.graph.add_prerequisite"),
            Component.translatable("circe.graph.add_confirm", prerequisite, definition.id()), true,
            () -> sendGraphEdit("save", definition.id().toString(), updated.toString(), revision)));
    }

    public static Component status(QuestDefinition definition)
    {
        var progress = ClientQuestState.progress(definition);
        if (progress.isCompleted())
        {
            return Component.translatable(progress.isClaimed() ? "circe.status.claimed" : "circe.status.claimable");
        }
        return Component.translatable(ClientQuestState.isUnlocked(definition) ? "circe.status.active" : "circe.status.locked");
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }
}
