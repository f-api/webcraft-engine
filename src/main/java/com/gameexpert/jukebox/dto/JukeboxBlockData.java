package com.gameexpert.jukebox.dto;

import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.engine.jukebox.JukeboxRules;

/**
 * [JUKEBOX] Immutable complete state of one jukebox block entity that holds a disc: the exact
 * one-disc stack (every component, like vanilla {@code ContainerSingleItem}) and
 * {@code JukeboxSongPlayer.ticksSinceSongStarted}. {@code null} ticks means no song is playing
 * (vanilla writes {@code ticks_since_song_started} only while {@code getSong() != null}). An empty
 * jukebox has no row.
 */
public final class JukeboxBlockData {
    private final int x;
    private final int y;
    private final int z;
    private final PlayerInventory.StackSnapshot disc;
    private final Long ticksSinceSongStarted;

    public JukeboxBlockData(int x, int y, int z, PlayerInventory.StackSnapshot disc,
            Long ticksSinceSongStarted) {
        if (disc == null || disc.count() != 1 || !JukeboxRules.isMusicDisc(disc.itemType())) {
            throw new IllegalArgumentException("jukebox requires exactly one music disc");
        }
        if (ticksSinceSongStarted != null && ticksSinceSongStarted < 0) {
            throw new IllegalArgumentException("jukebox song clock is negative");
        }
        this.x = x;
        this.y = y;
        this.z = z;
        this.disc = disc;
        this.ticksSinceSongStarted = ticksSinceSongStarted;
    }

    public int x() { return x; }
    public int y() { return y; }
    public int z() { return z; }
    public PlayerInventory.StackSnapshot disc() { return disc; }
    public Long ticksSinceSongStarted() { return ticksSinceSongStarted; }

    public JukeboxBlockData withTicks(Long ticks) {
        return new JukeboxBlockData(x, y, z, disc, ticks);
    }
}
