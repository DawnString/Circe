package cn.dawnstring.circe.client;

import cn.dawnstring.circe.network.EditPayload;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public final class QuestTitleEditorScreen extends QuestEditingScreen
{
    private EditBox titleField;
    private long baselineRevision;
    private long observedResult;
    private boolean isPending;
    private String editedTitle;

    public QuestTitleEditorScreen(net.minecraft.client.gui.screens.Screen parent)
    {
        super(parent, "circe.editor.book_title");
        editedTitle = ClientQuestState.bookTitle();
        baselineRevision = ClientQuestState.bookRevision();
    }

    @Override
    protected void initEditor()
    {
        titleField = field(frameLeft + 24, frameTop + 78, frameWidth - 48,
            QuestTranslations.text("circe.editor.book_title_hint"), editedTitle, 128);
        titleField.setResponder(value -> editedTitle = value);
        button(frameLeft + 24, frameTop + 118, 70, QuestTranslations.text("circe.button.save_title"), this::save);
        button(frameLeft + 102, frameTop + 118, 70, QuestTranslations.text("circe.button.cancel"), this::onClose);
    }

    private void save()
    {
        if (isPending || editedTitle.isBlank())
        {
            status = Component.translatable("circe.error.title_empty");
            return;
        }
        isPending = true;
        observedResult = ClientQuestState.editResultSequence();
        PacketDistributor.sendToServer(new EditPayload("title", "", editedTitle, baselineRevision));
        status = Component.translatable("circe.editor.saving");
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
            onClose();
        }
    }
}
