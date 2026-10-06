package cn.dawnstring.circe.client;

import cn.dawnstring.circe.network.ImageDataPayload;
import cn.dawnstring.circe.network.ImageRequestPayload;
import cn.dawnstring.circe.quest.QuestImageStore;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

import java.io.ByteArrayInputStream;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ClientQuestImages
{
    private record ImageInfo(int width, int height, boolean isDynamic)
    {
    }

    private static final Map<ResourceLocation, ImageInfo> IMAGES = new LinkedHashMap<>();
    private static final Map<ResourceLocation, Long> REQUESTED = new LinkedHashMap<>();

    private ClientQuestImages()
    {
    }

    public static void receive(ImageDataPayload payload)
    {
        try
        {
            QuestImageStore.validate(payload.png());
            if (!QuestImageStore.id(payload.png()).equals(payload.id()))
            {
                return;
            }
            NativeImage image = NativeImage.read(new ByteArrayInputStream(payload.png()));
            var manager = Minecraft.getInstance().getTextureManager();
            manager.register(payload.id(), new DynamicTexture(image));
            IMAGES.put(payload.id(), new ImageInfo(image.getWidth(), image.getHeight(), true));
            long count = IMAGES.values().stream().filter(ImageInfo::isDynamic).count();
            if (count > 32)
            {
                ResourceLocation oldest = IMAGES.entrySet().stream().filter(entry -> entry.getValue().isDynamic())
                    .map(Map.Entry::getKey).findFirst().orElseThrow();
                manager.release(oldest);
                IMAGES.remove(oldest);
                REQUESTED.remove(oldest);
            }
        }
        catch (Exception exception)
        {
            cn.dawnstring.circe.Circe.LOGGER.warn("Cannot decode quest image {}", payload.id());
        }
    }

    public static boolean has(ResourceLocation id)
    {
        return IMAGES.containsKey(id);
    }

    public static java.util.List<ResourceLocation> imported()
    {
        return IMAGES.entrySet().stream().filter(entry -> entry.getValue().isDynamic()).map(Map.Entry::getKey).toList();
    }

    public static int render(GuiGraphics graphics, String identifier, int x, int y, int width, int maximumHeight)
    {
        ResourceLocation id = ResourceLocation.tryParse(identifier);
        if (id == null)
        {
            return 0;
        }
        ImageInfo info = IMAGES.get(id);
        if (info == null && id.getNamespace().equals("circe") && id.getPath().startsWith("quest_images/"))
        {
            long now = Util.getMillis();
            if (now - REQUESTED.getOrDefault(id, 0L) > 5000)
            {
                REQUESTED.put(id, now);
                PacketDistributor.sendToServer(new ImageRequestPayload(id));
            }
        }
        else if (info == null)
        {
            try
            {
                var resource = Minecraft.getInstance().getResourceManager().getResource(id);
                if (resource.isPresent())
                {
                    try (var stream = resource.get().open(); NativeImage image = NativeImage.read(stream))
                    {
                        info = new ImageInfo(image.getWidth(), image.getHeight(), false);
                        IMAGES.put(id, info);
                    }
                }
            }
            catch (Exception exception)
            {
                return 0;
            }
        }
        if (info == null)
        {
            graphics.fill(x, y, x + width, y + 40, QuestTheme.panel());
            QuestTheme.centered(graphics, Minecraft.getInstance().font, QuestTranslations.text("circe.image.loading"), x + width / 2, y + 16, QuestTheme.muted());
            return 40;
        }
        int height = Math.max(1, Math.min(maximumHeight, width * info.height() / info.width()));
        int renderedWidth = Math.min(width, height * info.width() / info.height());
        graphics.blit(id, x + (width - renderedWidth) / 2, y, renderedWidth, height, 0, 0,
            info.width(), info.height(), info.width(), info.height());
        return height;
    }

    public static void clear()
    {
        var manager = Minecraft.getInstance().getTextureManager();
        IMAGES.forEach((id, info) ->
        {
            if (info.isDynamic())
            {
                manager.release(id);
            }
        });
        IMAGES.clear();
        REQUESTED.clear();
    }
}
