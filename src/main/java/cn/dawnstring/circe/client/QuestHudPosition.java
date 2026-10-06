package cn.dawnstring.circe.client;

import cn.dawnstring.circe.Circe;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class QuestHudPosition
{
    public record Position(double x, double y)
    {
        public Position
        {
            if (!Double.isFinite(x) || !Double.isFinite(y) || x < 0 || x > 1 || y < 0 || y > 1)
            {
                throw new IllegalArgumentException("HUD 位置必须在屏幕范围内");
            }
        }
    }

    public static final Position DEFAULT = new Position(1, 0.5);
    private static Position current = DEFAULT;

    private QuestHudPosition()
    {
    }

    public static Position current()
    {
        return current;
    }

    public static Path path()
    {
        return FMLPaths.CONFIGDIR.get().resolve("circe/hud-position.json");
    }

    public static void load()
    {
        current = DEFAULT;
        if (!Files.exists(path()))
        {
            return;
        }
        try
        {
            if (Files.size(path()) > 4096)
            {
                throw new IOException("HUD 配置文件过大");
            }
            JsonObject json = JsonParser.parseString(Files.readString(path(), StandardCharsets.UTF_8)).getAsJsonObject();
            current = new Position(json.get("x").getAsDouble(), json.get("y").getAsDouble());
        }
        catch (Exception exception)
        {
            Circe.LOGGER.warn("Cannot load Circe HUD position; default retained", exception);
        }
    }

    public static void save(Position position) throws IOException
    {
        Path path = path();
        Files.createDirectories(path.getParent());
        Path pending = path.resolveSibling("hud-position.json.tmp");
        JsonObject json = new JsonObject();
        json.addProperty("x", position.x());
        json.addProperty("y", position.y());
        try
        {
            Files.writeString(pending, json.toString(), StandardCharsets.UTF_8);
            try
            {
                Files.move(pending, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            }
            catch (AtomicMoveNotSupportedException exception)
            {
                Files.move(pending, path, StandardCopyOption.REPLACE_EXISTING);
            }
            current = position;
        }
        finally
        {
            Files.deleteIfExists(pending);
        }
    }
}
