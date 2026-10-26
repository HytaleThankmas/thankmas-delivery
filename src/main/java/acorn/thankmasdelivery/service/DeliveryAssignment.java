package acorn.thankmasdelivery.service;

import acorn.thankmasdelivery.model.DeliverySession;
import acorn.thankmasdelivery.persistence.DeliveryStore;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Runtime state for one equipped delivery. Nothing in this class is saved or resumed. */
final class DeliveryAssignment {
    final DeliverySession session;
    final PlayerRef player;
    final DeliveryStore.Run run;
    final World world;
    final Set<Ref<EntityStore>> parcelEntities = ConcurrentHashMap.newKeySet();
    volatile Vector3d lastParcelPosition;
    long missingCarriedSince;

    DeliveryAssignment(DeliverySession session, PlayerRef player, DeliveryStore.Run run, World world) {
        this.session = session;
        this.player = player;
        this.run = run;
        this.world = world;
    }

    void observeParcelPosition(Vector3dc position) {
        // A resting parcel is checked every tick. Allocate a snapshot only when it actually moves.
        Vector3d previous = lastParcelPosition;
        if (previous == null || !previous.equals(position)) {
            lastParcelPosition = new Vector3d(position);
        }
    }
}
