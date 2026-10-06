package cn.omix.util.sound;

import cn.omix.Client;

import javax.sound.sampled.AudioSystem;
import java.util.concurrent.Executors;

/** Plays bundled PCM WAV files without blocking the game thread. */
public final class WavSounds {
    public enum Channel { GENERAL, TOGGLE, KILL_EFFECT }

    private static final WavSoundPlayer PLAYER = new WavSoundPlayer(
            Executors.newSingleThreadExecutor(task -> {
                Thread thread = new Thread(task, "Omix-WavSounds");
                thread.setDaemon(true);
                return thread;
            }), AudioSystem::getClip, WavSoundPlayer::load,
            (resource, error) -> Client.logger.debug("Failed to play WAV {}: {}", resource, error.getMessage()));

    private WavSounds() {}

    public static void play(String resource) {
        play(Channel.GENERAL, resource);
    }

    public static void play(Channel channel, String resource) {
        PLAYER.play(channel, resource);
    }
}
