package cn.dawnstring.circe.quest;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class QuestImageStore
{
    public static final int MAX_BYTES = 262_144;
    private QuestImageStore()
    {
    }

    public static void validate(byte[] png)
    {
        byte[] signature = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
        if (png.length < 33 || png.length > MAX_BYTES || !java.util.Arrays.equals(signature, java.util.Arrays.copyOf(png, 8)))
        {
            throw new IllegalArgumentException("circe.validation.png_size");
        }
        int width = ByteBuffer.wrap(png, 16, 4).getInt();
        int height = ByteBuffer.wrap(png, 20, 4).getInt();
        if (width < 1 || height < 1 || width > 1024 || height > 1024)
        {
            throw new IllegalArgumentException("circe.validation.png_dimensions");
        }
        try
        {
            var image = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(png));
            if (image == null || image.getWidth() != width || image.getHeight() != height)
            {
                throw new IllegalArgumentException("circe.validation.png_invalid");
            }
        }
        catch (IOException exception)
        {
            throw new IllegalArgumentException("circe.validation.png_decode", exception);
        }
    }

    public static ResourceLocation id(byte[] png)
    {
        try
        {
            return ResourceLocation.parse("circe:quest_images/" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(png)) + ".png");
        }
        catch (java.security.NoSuchAlgorithmException exception)
        {
            throw new IllegalStateException(exception);
        }
    }

    private static Path path(ResourceLocation id)
    {
        if (!id.getNamespace().equals("circe") || !id.getPath().matches("quest_images/[0-9a-f]{64}\\.png"))
        {
            throw new IllegalArgumentException("circe.validation.image_id");
        }
        return FMLPaths.CONFIGDIR.get().resolve("circe/images").resolve(id.getPath().substring("quest_images/".length()));
    }

    public static ResourceLocation save(byte[] png) throws IOException
    {
        validate(png);
        ResourceLocation id = id(png);
        Path path = path(id);
        Files.createDirectories(path.getParent());
        if (!Files.exists(path))
        {
            try (var files = Files.list(path.getParent()))
            {
                if (files.filter(entry -> entry.toString().endsWith(".png")).count() >= 128)
                {
                    throw new IOException("circe.validation.image_limit");
                }
            }
            // 内容哈希生成路径，未完成上传不会影响已有图片。
            Path pending = path.resolveSibling(path.getFileName() + ".tmp");
            try
            {
                Files.write(pending, png);
                Files.move(pending, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            finally
            {
                Files.deleteIfExists(pending);
            }
        }
        return id;
    }

    public static byte[] read(ResourceLocation id) throws IOException
    {
        Path path = path(id);
        if (Files.size(path) > MAX_BYTES)
        {
            throw new IOException("circe.validation.image_file_size");
        }
        byte[] png = Files.readAllBytes(path);
        validate(png);
        return png;
    }
}
