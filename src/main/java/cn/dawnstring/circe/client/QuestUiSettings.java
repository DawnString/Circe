package cn.dawnstring.circe.client;

import cn.dawnstring.circe.Circe;
import com.google.gson.Gson;
import net.neoforged.fml.loading.FMLPaths;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.io.IOException;
import java.util.function.UnaryOperator;

public final class QuestUiSettings
{
    public record Preferences(String theme, boolean hasAnimations, boolean hasGrid,
        boolean hasHudDescription, boolean hasHudCounters, double hudScale, double opacity, double graphZoom)
    {
        public Preferences
        {
            QuestTheme.Style.valueOf(theme);
            if (!Double.isFinite(hudScale) || hudScale < 0.75 || hudScale > 1.5
                || !Double.isFinite(opacity) || opacity < 0.8 || opacity > 1
                || !Double.isFinite(graphZoom) || graphZoom < 0.8 || graphZoom > 1.5)
            {
                throw new IllegalArgumentException("界面配置超出范围");
            }
        }
    }

    public static final Preferences DEFAULT = new Preferences("CLASSIC", true, true, true, true, 1, 1, 1.15);
    private static final Gson GSON = new Gson();
    private static Preferences current = DEFAULT;

    private QuestUiSettings()
    {
    }

    public static Preferences current()
    {
        return current;
    }

    public static Path path()
    {
        return FMLPaths.CONFIGDIR.get().resolve("circe/ui-settings.json");
    }

    public static void load()
    {
        current = DEFAULT;
        try
        {
            if (Files.exists(path()))
            {
                if (Files.size(path()) > 4096)
                {
                    throw new IOException("界面配置文件过大");
                }
                Preferences loaded = GSON.fromJson(Files.readString(path(), StandardCharsets.UTF_8), Preferences.class);
                if (loaded == null)
                {
                    throw new IOException("界面配置为空");
                }
                current = loaded;
            }
        }
        catch (Exception exception)
        {
            Circe.LOGGER.warn("Cannot load Circe UI settings; defaults retained", exception);
        }
        QuestTheme.apply(QuestTheme.Style.valueOf(current.theme()), false);
    }

    public static void update(UnaryOperator<Preferences> change) throws IOException
    {
        Preferences next = change.apply(current);
        Path destination = path();
        Files.createDirectories(destination.getParent());
        Path pending = destination.resolveSibling("ui-settings.json.tmp");
        try
        {
            Files.writeString(pending, GSON.toJson(next), StandardCharsets.UTF_8);
            try
            {
                Files.move(pending, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            }
            catch (AtomicMoveNotSupportedException exception)
            {
                Files.move(pending, destination, StandardCopyOption.REPLACE_EXISTING);
            }
            current = next;
            QuestTheme.apply(QuestTheme.Style.valueOf(next.theme()), next.hasAnimations());
        }
        finally
        {
            Files.deleteIfExists(pending);
        }
    }
}
