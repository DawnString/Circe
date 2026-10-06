package cn.dawnstring.circe.quest;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class QuestBookStore
{
    private static final int MAX_BOOK_BYTES = 1_048_576;

    private QuestBookStore()
    {
    }

    public static Path path()
    {
        return FMLPaths.CONFIGDIR.get().resolve("circe/quest-book.json");
    }

    public static JsonObject read() throws IOException
    {
        Path path = path();
        if (!Files.exists(path))
        {
            return null;
        }
        if (Files.size(path) > MAX_BOOK_BYTES)
        {
            throw new IOException("Quest book exceeds 1 MiB");
        }
        return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    public static void write(JsonObject book) throws IOException
    {
        Path path = path();
        Files.createDirectories(path.getParent());
        Path pendingPath = path.resolveSibling("quest-book.json.tmp");
        try
        {
            String contents = new GsonBuilder().setPrettyPrinting().create().toJson(book);
            if (contents.getBytes(StandardCharsets.UTF_8).length > MAX_BOOK_BYTES)
            {
                throw new IOException("任务书文件超过 1 MiB，无法保存");
            }
            Files.writeString(pendingPath, contents, StandardCharsets.UTF_8);
            if (Files.exists(path))
            {
                Files.copy(path, path.resolveSibling("quest-book.json.bak"), StandardCopyOption.REPLACE_EXISTING);
            }
            try
            {
                Files.move(pendingPath, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            }
            catch (AtomicMoveNotSupportedException exception)
            {
                Files.move(pendingPath, path, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        finally
        {
            Files.deleteIfExists(pendingPath);
        }
    }
}
