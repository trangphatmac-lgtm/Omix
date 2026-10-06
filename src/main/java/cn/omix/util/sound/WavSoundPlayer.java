package cn.omix.util.sound;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineListener;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

/** All clip operations and cache access belong to the supplied serial audio executor. */
final class WavSoundPlayer {
    static final int MAX_VOICES_PER_CHANNEL = 8;
    static final int MAX_PENDING_PER_CHANNEL = 32;
    private static final int MAX_CACHED_SOUNDS = 16;

    private final Executor executor;
    private final ClipFactory clips;
    private final SampleLoader loader;
    private final BiConsumer<String, Exception> errors;
    private final Map<WavSounds.Channel, ChannelState> channels = new EnumMap<>(WavSounds.Channel.class);
    private final Map<String, Sample> samples = new LinkedHashMap<>(16, .75F, true);

    WavSoundPlayer(Executor executor, ClipFactory clips, SampleLoader loader,
                   BiConsumer<String, Exception> errors) {
        this.executor = executor;
        this.clips = clips;
        this.loader = loader;
        this.errors = errors;
        for (var channel : WavSounds.Channel.values()) channels.put(channel, new ChannelState());
    }

    void play(WavSounds.Channel channel, String resource) {
        Objects.requireNonNull(resource, "resource");
        ChannelState state = channels.get(Objects.requireNonNull(channel, "channel"));
        // Bound play requests separately from cleanup: a burst must never discard a close task.
        if (!state.pending.tryAcquire()) return;
        try {
            executor.execute(() -> {
                try {
                    playNow(state, resource);
                } finally {
                    state.pending.release();
                }
            });
        } catch (RuntimeException error) {
            state.pending.release();
            throw error;
        }
    }

    private void playNow(ChannelState state, String resource) {
        Voice voice = null;
        try {
            Sample sample = samples.get(resource);
            if (sample == null) {
                sample = loader.load(resource);
                samples.put(resource, sample);
                if (samples.size() > MAX_CACHED_SOUNDS) samples.remove(samples.keySet().iterator().next());
            }
            // Finished clips can be awaiting a queued STOP callback behind this play request.
            for (Voice previous : state.active.toArray(Voice[]::new)) {
                if (previous.finished.get()) close(previous);
            }
            if (state.active.size() >= MAX_VOICES_PER_CHANNEL) close(state.active.getFirst());

            voice = new Voice(state, resource, clips.create());
            voice.clip.addLineListener(voice.listener);
            voice.clip.open(sample.format(), sample.data(), 0, sample.data().length);
            state.active.addLast(voice);
            voice.clip.start();
        } catch (Exception error) {
            if (voice != null) close(voice);
            errors.accept(resource, error);
        }
    }

    private void close(Voice voice) {
        if (voice.closed) return;
        voice.closed = true;
        voice.state.active.remove(voice);
        voice.clip.removeLineListener(voice.listener);
        try {
            voice.clip.close();
        } catch (Exception error) {
            errors.accept(voice.resource, error);
        }
    }

    static Sample load(String resource) throws Exception {
        var url = WavSoundPlayer.class.getResource(resource);
        if (url == null) throw new IOException("Missing WAV resource: " + resource);
        try (var stream = AudioSystem.getAudioInputStream(url)) {
            return new Sample(stream.getFormat(), stream.readAllBytes());
        }
    }

    record Sample(AudioFormat format, byte[] data) {}

    @FunctionalInterface
    interface ClipFactory {
        Clip create() throws Exception;
    }

    @FunctionalInterface
    interface SampleLoader {
        Sample load(String resource) throws Exception;
    }

    private static final class ChannelState {
        final Semaphore pending = new Semaphore(MAX_PENDING_PER_CHANNEL);
        final ArrayDeque<Voice> active = new ArrayDeque<>();
    }

    private final class Voice {
        final ChannelState state;
        final String resource;
        final Clip clip;
        final AtomicBoolean finished = new AtomicBoolean();
        final LineListener listener;
        boolean closed;

        Voice(ChannelState state, String resource, Clip clip) {
            this.state = state;
            this.resource = resource;
            this.clip = clip;
            listener = event -> {
                if ((event.getType() == LineEvent.Type.STOP || event.getType() == LineEvent.Type.CLOSE)
                        && finished.compareAndSet(false, true)) {
                    // Java Sound dispatches events on another thread. Never close a line there.
                    executor.execute(() -> close(this));
                }
            };
        }
    }
}
