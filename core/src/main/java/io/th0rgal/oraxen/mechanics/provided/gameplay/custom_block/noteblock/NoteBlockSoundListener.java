package io.th0rgal.oraxen.mechanics.provided.gameplay.custom_block.noteblock;

import io.th0rgal.oraxen.OraxenPlugin;
import io.th0rgal.oraxen.api.OraxenBlocks;
import io.th0rgal.oraxen.api.events.custom_block.noteblock.OraxenNoteBlockBreakEvent;
import io.th0rgal.oraxen.api.events.custom_block.noteblock.OraxenNoteBlockPlaceEvent;
import io.th0rgal.oraxen.utils.BlockHelpers;
import io.th0rgal.oraxen.utils.blocksounds.BlockSounds;
import io.th0rgal.protectionlib.ProtectionLib;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageAbortEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.world.GenericGameEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;

import static io.th0rgal.oraxen.utils.BlockHelpers.isLoaded;
import static io.th0rgal.oraxen.utils.blocksounds.BlockSounds.*;

public class NoteBlockSoundListener implements Listener {
    private final Map<Location, BukkitTask> breakerPlaySound = new HashMap<>();

    @EventHandler
    public void onWorldUnload(WorldUnloadEvent event) {
        for (Map.Entry<Location, BukkitTask> entry : breakerPlaySound.entrySet()) {
            if (entry.getKey().isWorldLoaded() || entry.getValue().isCancelled()) continue;
            entry.getValue().cancel();
            breakerPlaySound.remove(entry.getKey());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlacingWood(final BlockPlaceEvent event) {
        Block placed = event.getBlockPlaced();
        if (placed.getBlockData().getSoundGroup().getPlaceSound() != Sound.BLOCK_WOOD_PLACE) return;
        if (OraxenBlocks.isOraxenNoteBlock(placed)) return;

        // Play sound for wood
        BlockHelpers.playCustomBlockSound(placed.getLocation(), VANILLA_WOOD_PLACE, VANILLA_PLACE_VOLUME, VANILLA_PLACE_PITCH);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBreakingWood(final BlockBreakEvent event) {
        final Block block = event.getBlock();
        Location location = block.getLocation();

        if (breakerPlaySound.containsKey(location)) {
            breakerPlaySound.get(location).cancel();
            breakerPlaySound.remove(location);
        }
        if (block.getBlockData().getSoundGroup().getBreakSound() != Sound.BLOCK_WOOD_BREAK) return;
        if (OraxenBlocks.isOraxenNoteBlock(block) || block.getType() == Material.MUSHROOM_STEM) return;

        if (!event.isCancelled() && ProtectionLib.canBreak(event.getPlayer(), location))
            BlockHelpers.playCustomBlockSound(location, VANILLA_WOOD_BREAK, VANILLA_BREAK_VOLUME, VANILLA_BREAK_PITCH);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHitWood(final BlockDamageEvent event) {
        Block block = event.getBlock();
        Location location = block.getLocation();
        SoundGroup soundGroup = block.getBlockData().getSoundGroup();

        if (block.getType() == Material.NOTE_BLOCK || block.getType() == Material.MUSHROOM_STEM) {
            if (event.getInstaBreak()) {
                Bukkit.getScheduler().runTaskLater(OraxenPlugin.get(), () ->
                        block.setType(Material.AIR, false), 1);
                return;
            }
        }
        if (soundGroup.getHitSound() != Sound.BLOCK_WOOD_HIT) return;
        if (breakerPlaySound.containsKey(location)) return;

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(OraxenPlugin.get(), () ->
                BlockHelpers.playCustomBlockSound(location, VANILLA_WOOD_HIT, VANILLA_HIT_VOLUME, VANILLA_HIT_PITCH), 2L, 4L);
        breakerPlaySound.put(location, task);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onStopHittingWood(final BlockDamageAbortEvent event) {
        Location location = event.getBlock().getLocation();
        if (breakerPlaySound.containsKey(location)) {
            breakerPlaySound.get(location).cancel();
            breakerPlaySound.remove(location);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onStepFall(final GenericGameEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof LivingEntity)) return;
        if (!isLoaded(entity.getLocation())) return;

        GameEvent gameEvent = event.getEvent();
        Block block = BlockHelpers.getBlockStandingOn(entity);
        EntityDamageEvent cause = entity.getLastDamageCause();

        if (gameEvent == GameEvent.HIT_GROUND && cause != null && cause.getCause() != EntityDamageEvent.DamageCause.FALL)
            return;
        if (block == null || block.getType().isAir() || block.getBlockData().getSoundGroup().getStepSound() != Sound.BLOCK_WOOD_STEP)
            return;

        NoteBlockMechanic mechanic = OraxenBlocks.getNoteBlockMechanic(block);
        String sound;
        float volume;
        float pitch;
        if (gameEvent == GameEvent.STEP) {
            boolean check = block.getType() == Material.NOTE_BLOCK && mechanic != null && mechanic.hasBlockSounds() && mechanic.blockSounds().hasStepSound();
            sound = (check) ? mechanic.blockSounds().stepSound() : VANILLA_WOOD_STEP;
            volume = (check) ? mechanic.blockSounds().stepVolume() : VANILLA_STEP_VOLUME;
            pitch = (check) ? mechanic.blockSounds().stepPitch() : VANILLA_STEP_PITCH;
        } else if (gameEvent == GameEvent.HIT_GROUND) {
            boolean check = (block.getType() == Material.NOTE_BLOCK && mechanic != null && mechanic.hasBlockSounds() && mechanic.blockSounds().hasFallSound());
            sound = (check) ? mechanic.blockSounds().fallSound() : VANILLA_WOOD_FALL;
            volume = (check) ? mechanic.blockSounds().fallVolume() : VANILLA_FALL_VOLUME;
            pitch = (check) ? mechanic.blockSounds().fallPitch() : VANILLA_FALL_PITCH;
        } else return;

        BlockHelpers.playCustomBlockSound(entity.getLocation(), sound, SoundCategory.PLAYERS, volume, pitch);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlacing(final OraxenNoteBlockPlaceEvent event) {
        BlockSounds blockSounds = event.getMechanic().blockSounds();
        if (blockSounds == null || !blockSounds.hasPlaceSound()) return;
        BlockHelpers.playCustomBlockSound(event.getBlock().getLocation(), blockSounds.placeSound(), blockSounds.placeVolume(), blockSounds.placePitch());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreaking(final OraxenNoteBlockBreakEvent event) {
        BlockSounds blockSounds = event.getMechanic().blockSounds();
        if (blockSounds == null || !blockSounds.hasBreakSound()) return;
        BlockHelpers.playCustomBlockSound(event.getBlock().getLocation(), blockSounds.breakSound(), blockSounds.breakVolume(), blockSounds.breakPitch());
    }
}