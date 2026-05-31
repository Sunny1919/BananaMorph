package xyz.nifeather.morph.backends.server.renderer.network;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import xiamomc.pluginbase.Annotations.Initializer;
import xyz.nifeather.morph.MorphPluginObject;
import xyz.nifeather.morph.backends.server.renderer.network.listeners.*;

import java.util.List;

public class ProtocolHandler extends MorphPluginObject
{
    public ProtocolHandler()
    {
        registerRange(
                new SpawnPacketHandler(),
                new MetaPacketListener(),
                new EquipmentPacketListener(),
                new PlayerLookPacketListener(),
                new SoundListener(),
                new AnimationPacketListener()
        );
    }

    private final List<ProtocolListener> listeners = new ObjectArrayList<>();

    private void throwIfDisposed()
    {
        if (disposed)
            throw new IllegalStateException(
                    "This instance of ProtocolHandler(%s) is disposed and cannot be used."
                            .formatted(this)
            );
    }

    public boolean contains(ProtocolListener listener)
    {
        throwIfDisposed();

        return contains(listener.getIdentifier());
    }

    public boolean contains(String id)
    {
        throwIfDisposed();

        return listeners.stream().anyMatch(l -> l.getIdentifier().equalsIgnoreCase(id));
    }

    public boolean register(ProtocolListener listener)
    {
        throwIfDisposed();

        if (this.contains(listener))
            return false;

        listeners.add(listener);

        return true;
    }

    public boolean registerRange(ProtocolListener... listeners)
    {
        throwIfDisposed();

        boolean allSuccess = true;

        for (ProtocolListener listener : listeners)
        {
            allSuccess = register(listener) && allSuccess;
        }

        return allSuccess;
    }

    public boolean unregister(ProtocolListener listener)
    {
        throwIfDisposed();

        listeners.remove(listener);

        return true;
    }

    private boolean loadReady;
    private PacketListenerCommon packetEventsListener;

    @Initializer
    private void load()
    {
        if (disposed) return;

        packetEventsListener = new PacketListenerAbstract() {
            @Override
            public void onPacketSend(PacketSendEvent event) {
                if (disposed) return;
                for (var listener : listeners)
                {
                    try
                    {
                        listener.onPacketSend(event);
                    }
                    catch (Throwable t)
                    {
                        logger.error("Error in packet listener '" + listener.getIdentifier() + "': " + t.getMessage());
                        t.printStackTrace();
                    }
                }
            }
        };

        PacketEvents.getAPI().getEventManager().registerListener(packetEventsListener);
        loadReady = true;
    }

    private boolean disposed;

    public boolean disposed()
    {
        return disposed;
    }

    @Override
    public void dispose()
    {
        if (packetEventsListener != null)
        {
            try
            {
                PacketEvents.getAPI().getEventManager().unregisterListener(packetEventsListener);
            }
            catch (Throwable t)
            {
                logger.error("Error unregistering PacketEvents listener: " + t.getMessage());
            }
        }

        disposed = true;
    }
}
