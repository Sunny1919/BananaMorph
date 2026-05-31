package xyz.nifeather.morph.backends.server.renderer.network.queue;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.bukkit.entity.Player;
import xiamomc.pluginbase.Annotations.Initializer;
import xyz.nifeather.morph.MorphPluginObject;
import xyz.nifeather.morph.misc.NmsRecord;

import java.util.List;
import java.util.Map;

public class PacketQueue extends MorphPluginObject
{
    private final Map<Player, List<QueueEntry>> playerPackets = new Object2ObjectOpenHashMap<>();

    public void clearQueue(Player bindingPlayer)
    {
        playerPackets.remove(bindingPlayer);
    }

    public void pushQueue(Player bindingPlayer, List<QueueEntry> queue)
    {
        var targetList = playerPackets.getOrDefault(bindingPlayer, null);
        if (targetList == null)
            playerPackets.put(bindingPlayer, targetList = new ObjectArrayList<>());

        List<QueueEntry> finalTargetList = targetList;

        var currentTick = plugin.getCurrentTick();
        queue.forEach(qs ->
        {
            if (qs.delay <= 0)
            {
                applyPacket(bindingPlayer, qs);
            }
            else
            {
                qs.targetTick = currentTick + qs.delay;
                finalTargetList.add(qs);
            }
        });
    }

    @Initializer
    private void load()
    {
        this.addSchedule(this::update);
    }

    private void update()
    {
        this.addSchedule(this::update);

        if (playerPackets.isEmpty())
            return;

        var currentTick = plugin.getCurrentTick();
        
        var iterator = playerPackets.entrySet().iterator();
        while (iterator.hasNext())
        {
            var entry = iterator.next();
            var player = entry.getKey();
            if (!player.isOnline())
            {
                iterator.remove();
                continue;
            }

            var queue = entry.getValue();
            if (queue.isEmpty())
                continue;

            var listIterator = queue.iterator();
            while (listIterator.hasNext())
            {
                var qs = listIterator.next();
                if (currentTick >= qs.targetTick)
                {
                    this.applyPacket(player, qs);
                    listIterator.remove();
                }
            }
        }
    }

    private void applyPacket(Player bindingPlayer, QueueEntry single)
    {
        if (single.packet != null)
        {
            var nmsPlayer = NmsRecord.ofPlayer(bindingPlayer);
            if (nmsPlayer.connection != null)
                nmsPlayer.connection.sendPacket(single.packet);
        }

        if (single.action != null)
        {
            try
            {
                single.action.run();
            }
            catch (Throwable t)
            {
                logger.error("Error occurred while performing action from queue: " + t.getMessage());
                t.printStackTrace();
            }
        }
    }
}
