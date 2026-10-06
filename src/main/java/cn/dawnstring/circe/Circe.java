package cn.dawnstring.circe;

import cn.dawnstring.circe.network.QuestNetwork;
import cn.dawnstring.circe.quest.QuestEvents;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(Circe.MODID)
public class Circe
{
    public static final String MODID = "circe";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Circe(IEventBus modEventBus)
    {
        modEventBus.addListener(QuestNetwork::register);
        NeoForge.EVENT_BUS.register(new QuestEvents());
    }
}
