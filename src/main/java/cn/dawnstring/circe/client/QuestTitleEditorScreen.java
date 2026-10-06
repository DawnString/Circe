package cn.dawnstring.circe.client;

import cn.dawnstring.circe.network.EditPayload;
import net.minecraft.client.gui.components.EditBox;
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
        super(parent, "修改任务书标题");
        editedTitle = ClientQuestState.bookTitle();
        baselineRevision = ClientQuestState.bookRevision();
    }

    @Override
    protected void initEditor()
    {
        titleField = field(frameLeft + 24, frameTop + 78, frameWidth - 48,
            "任务界面左上角的标题", editedTitle, 128);
        titleField.setResponder(value -> editedTitle = value);
        button(frameLeft + 24, frameTop + 118, 70, "保存标题", this::save);
        button(frameLeft + 102, frameTop + 118, 70, "取消", this::onClose);
    }

    private void save()
    {
        if (isPending || editedTitle.isBlank())
        {
            status = "请输入标题";
            return;
        }
        isPending = true;
        observedResult = ClientQuestState.editResultSequence();
        PacketDistributor.sendToServer(new EditPayload("title", "", editedTitle, baselineRevision));
        status = "正在保存…";
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
