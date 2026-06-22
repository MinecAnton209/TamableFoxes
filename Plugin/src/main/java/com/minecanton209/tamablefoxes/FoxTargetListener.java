package com.minecanton209.tamablefoxes;

import com.minecanton209.tamablefoxes.util.TamableFoxLogic;
import org.bukkit.entity.Fox;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetEvent;

import java.util.Set;
import java.util.UUID;

public class FoxTargetListener implements Listener {

    @EventHandler
    public void onEntityTarget(EntityTargetEvent event) {
        if (!(event.getEntity() instanceof Fox fox)) return;
        UUID uuid = fox.getUniqueId();

        if (!TamableFoxLogic.isRegisteredFox(uuid)) return;
        if (event.getTarget() == null) return;

        if (!TamableFoxLogic.isAggressive(uuid)) {
            event.setCancelled(true);
            fox.setTarget(null);
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Fox fox)) return;
        UUID uuid = fox.getUniqueId();

        if (!TamableFoxLogic.isRegisteredFox(uuid)) return;

        if (!TamableFoxLogic.isAggressive(uuid)) {
            event.setCancelled(true);
        }
    }
}
