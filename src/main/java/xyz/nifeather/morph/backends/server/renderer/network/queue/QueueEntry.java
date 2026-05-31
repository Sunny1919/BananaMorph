package xyz.nifeather.morph.backends.server.renderer.network.queue;

import net.minecraft.network.protocol.Packet;
import org.jetbrains.annotations.Nullable;

public final class QueueEntry
{
    @Nullable
    public final Runnable action;
    public final Packet<?> packet;
    public final int delay;

    long targetTick;

    public QueueEntry(Packet<?> packetContainer, int delay)
    {
        this(packetContainer, null, delay);
    }

    public QueueEntry(Packet<?> packet, @Nullable Runnable action, int delay)
    {
        this.packet = packet;
        this.action = action;
        this.delay = delay;
    }

    public static QueueEntry from(Packet<?> packet)
    {
        return from(packet, 0);
    }

    public static QueueEntry from(Packet<?> packet, int delay)
    {
        return new QueueEntry(packet, delay);
    }

    public static QueueEntry fromAction(Runnable action)
    {
        return fromAction(action, 0);
    }

    public static QueueEntry fromAction(Runnable action, int delay)
    {
        return new QueueEntry(null, action, delay);
    }
}
