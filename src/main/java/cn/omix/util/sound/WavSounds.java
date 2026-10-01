package cn.omix.util.sound;

import cn.omix.Client;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineEvent;
import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Plays bundled PCM WAV files without blocking the game thread. */
public final class WavSounds {
    private static final ThreadPoolExecutor PLAYER = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(1), task -> {
                Thread thread = new Thread(task, "Omix-WavSounds");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.DiscardOldestPolicy());
    // Accessed only by PLAYER. Rapid toggles replace the previous sound.
    private static Clip activeClip;

    private WavSounds() {}

    public static void play(String resource) {
        PLAYER.execute(() -> playNow(resource));
    }

    private static void playNow(String resource) {
        if (activeClip != null) {
            activeClip.close();
            activeClip = null;
        }
        Clip clip = null;
        try {
            var url = WavSounds.class.getResource(resource);
            if (url == null) throw new IOException("Missing WAV resource: " + resource);
            try (var stream = AudioSystem.getAudioInputStream(url)) {
                clip = AudioSystem.getClip();
                Clip playing = clip;
                playing.addLineListener(event -> {
                    if (event.getType() == LineEvent.Type.STOP) playing.close();
                });
                playing.open(stream);
                activeClip = playing;
                playing.start();
            }
        } catch (Exception error) {
            if (clip != null) clip.close();
            activeClip = null;
            Client.logger.debug("Failed to play WAV {}: {}", resource, error.getMessage());
        }
    }
}
