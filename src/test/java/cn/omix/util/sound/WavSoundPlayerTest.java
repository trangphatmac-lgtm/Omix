package cn.omix.util.sound;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineListener;
import javax.sound.sampled.LineUnavailableException;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import static cn.omix.util.sound.WavSounds.Channel.KILL_EFFECT;
import static cn.omix.util.sound.WavSounds.Channel.TOGGLE;
import static org.junit.jupiter.api.Assertions.*;

class WavSoundPlayerTest {
    private static final WavSoundPlayer.Sample SAMPLE = new WavSoundPlayer.Sample(
            new AudioFormat(44_100, 16, 2, true, false), new byte[16]);

    @Test
    void rapidTogglesAndKillsOverlapWithoutInterruptingEachOther() {
        Fixture f = new Fixture();
        f.player.play(TOGGLE, "enable");
        f.player.play(KILL_EFFECT, "kill");
        f.player.play(TOGGLE, "disable");
        f.player.play(KILL_EFFECT, "kill");
        f.player.play(TOGGLE, "enable");
        assertTrue(f.clips.isEmpty(), "Resource loading and playback must stay off the caller thread");
        assertTrue(f.loads.isEmpty());

        f.audio.drain();
        assertEquals(5, f.clips.size());
        assertTrue(f.clips.stream().allMatch(clip -> clip.starts == 1 && clip.closes == 0));
        assertEquals(Map.of("enable", 1, "disable", 1, "kill", 1), f.loads);
        assertTrue(f.errors.isEmpty());
    }

    @Test
    void voiceLimitEvictsOnlyTheOldestSoundInTheSameChannel() {
        Fixture f = new Fixture();
        f.player.play(TOGGLE, "enable");
        for (int i = 0; i <= WavSoundPlayer.MAX_VOICES_PER_CHANNEL; i++) f.player.play(KILL_EFFECT, "kill");
        f.audio.drain();
        assertEquals(0, f.clips.getFirst().closes, "Kill bursts must not stop toggle sounds");
        assertEquals(1, f.clips.get(1).closes);
        assertTrue(f.clips.subList(2, f.clips.size()).stream().allMatch(clip -> clip.closes == 0));
    }

    @Test
    void stopAndCloseEventsReleaseOnceOnAudioWorkerAndAllowReplay() {
        Fixture f = new Fixture();
        f.player.play(KILL_EFFECT, "kill");
        f.audio.drain();
        FakeClip first = f.clips.getFirst();
        first.event(LineEvent.Type.START);
        assertTrue(f.audio.tasks.isEmpty());
        first.event(LineEvent.Type.STOP);
        first.event(LineEvent.Type.STOP);
        first.event(LineEvent.Type.CLOSE);
        assertEquals(0, first.closes, "The event dispatcher must never close an audio line");
        assertEquals(1, f.audio.tasks.size());
        f.player.play(KILL_EFFECT, "kill");
        f.audio.drain();
        assertEquals(1, first.closes);
        assertTrue(first.listeners.isEmpty());
        assertEquals(1, f.clips.getLast().starts);
        assertEquals(0, f.clips.getLast().closes);
        assertEquals(1, f.loads.get("kill"));
    }

    @Test
    void completedVoiceIsReapedBeforeEvictingAnOlderStillPlayingSound() {
        Fixture f = new Fixture();
        for (int i = 0; i < WavSoundPlayer.MAX_VOICES_PER_CHANNEL; i++) f.player.play(KILL_EFFECT, "kill");
        f.audio.drain();
        FakeClip finished = f.clips.getLast();
        f.player.play(KILL_EFFECT, "kill");
        finished.event(LineEvent.Type.STOP); // Cleanup is queued behind the new request.
        f.audio.drain();
        assertEquals(0, f.clips.getFirst().closes);
        assertEquals(1, finished.closes);
        assertEquals(0, f.clips.getLast().closes);
    }

    @Test
    void lateStopFromEvictedVoiceCannotCloseItsReplacement() {
        Fixture f = new Fixture();
        f.player.play(KILL_EFFECT, "kill");
        f.audio.drain();
        FakeClip old = f.clips.getFirst();
        LineListener delayedListener = old.listeners.getFirst();
        for (int i = 0; i < WavSoundPlayer.MAX_VOICES_PER_CHANNEL; i++) f.player.play(KILL_EFFECT, "kill");
        f.audio.drain();
        delayedListener.update(new LineEvent(old.clip, LineEvent.Type.STOP, 0));
        f.audio.drain();
        assertEquals(1, old.closes);
        assertEquals(0, f.clips.getLast().closes);
    }

    @Test
    void saturatedRequestsStayBoundedWithoutDiscardingCleanupOrOtherChannels() {
        Fixture f = new Fixture();
        f.player.play(KILL_EFFECT, "kill");
        f.audio.drain();
        FakeClip first = f.clips.getFirst();
        for (int i = 0; i < 10_000; i++) f.player.play(KILL_EFFECT, "kill");
        assertEquals(WavSoundPlayer.MAX_PENDING_PER_CHANNEL, f.audio.tasks.size());
        first.event(LineEvent.Type.STOP);
        f.player.play(TOGGLE, "enable");
        f.audio.drain();
        assertEquals(1, first.closes);
        assertEquals(2 + WavSoundPlayer.MAX_PENDING_PER_CHANNEL, f.clips.size());
        assertEquals(1, f.clips.getLast().starts);
        assertEquals(1 + WavSoundPlayer.MAX_VOICES_PER_CHANNEL,
                f.clips.stream().filter(clip -> clip.closes == 0).count());

        for (FakeClip clip : f.clips) clip.event(LineEvent.Type.STOP);
        f.audio.drain();
        assertTrue(f.clips.stream().allMatch(clip -> clip.closes == 1));
        f.player.play(KILL_EFFECT, "kill");
        f.audio.drain();
        assertEquals(1, f.clips.getLast().starts, "Queue permits must be returned after a burst");
        assertEquals(0, f.clips.getLast().closes);
    }

    @ParameterizedTest
    @ValueSource(strings = {"open", "start", "close"})
    void deviceFailureDoesNotBreakSubsequentPlaybackOrDoubleClose(String operation) {
        Fixture f = new Fixture();
        f.nextFailure = operation;
        f.player.play(KILL_EFFECT, "kill");
        f.audio.drain();
        if (operation.equals("close")) {
            f.clips.getFirst().event(LineEvent.Type.STOP);
            f.audio.drain();
        }
        assertEquals(1, f.errors.size());
        assertEquals(1, f.clips.getFirst().closes);
        assertTrue(f.clips.getFirst().listeners.isEmpty());
        f.player.play(KILL_EFFECT, "kill");
        f.audio.drain();
        assertEquals(1, f.clips.getLast().starts);
        assertEquals(0, f.clips.getLast().closes);
    }

    @Test
    void failedLoadsPreserveActiveSoundsAndDoNotLeakRequestPermits() {
        Fixture f = new Fixture();
        f.player.play(KILL_EFFECT, "kill");
        f.audio.drain();
        for (int i = 0; i < WavSoundPlayer.MAX_PENDING_PER_CHANNEL + 1; i++) {
            f.player.play(KILL_EFFECT, "missing");
            f.audio.drain();
        }
        assertEquals(WavSoundPlayer.MAX_PENDING_PER_CHANNEL + 1, f.errors.size());
        assertEquals(0, f.clips.getFirst().closes);
        f.player.play(KILL_EFFECT, "kill");
        f.audio.drain();
        assertEquals(2, f.clips.size());
        assertTrue(f.clips.stream().allMatch(clip -> clip.starts == 1 && clip.closes == 0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"xinxin/enable.wav", "xinxin/disable.wav", "xinxin/kill.wav",
            "killeffect/bing-bing-bing.wav"})
    void bundledWavsDecodeAsCompletePcmFramesWithoutAnAudioDevice(String name) throws Exception {
        var sample = WavSoundPlayer.load("/assets/omix/sounds/" + name);
        assertEquals(AudioFormat.Encoding.PCM_SIGNED, sample.format().getEncoding());
        assertEquals(16, sample.format().getSampleSizeInBits());
        assertEquals(2, sample.format().getChannels());
        assertTrue(sample.data().length > 0);
        assertEquals(0, sample.data().length % sample.format().getFrameSize());
        assertTrue(sample.format().getFrameRate() > 0);
    }

    @Test
    void missingBundledWavHasAUsefulError() {
        var error = assertThrows(IOException.class, () -> WavSoundPlayer.load("/missing.wav"));
        assertTrue(error.getMessage().contains("/missing.wav"));
    }

    private static final class Fixture {
        final ManualExecutor audio = new ManualExecutor();
        final List<FakeClip> clips = new ArrayList<>();
        final Map<String, Integer> loads = new HashMap<>();
        final List<Exception> errors = new ArrayList<>();
        String nextFailure;
        final WavSoundPlayer player = new WavSoundPlayer(audio, () -> {
            assertTrue(audio.running);
            FakeClip clip = new FakeClip(audio, nextFailure);
            nextFailure = null;
            clips.add(clip);
            return clip.clip;
        }, resource -> {
            assertTrue(audio.running);
            loads.merge(resource, 1, Integer::sum);
            if (resource.equals("missing")) throw new IOException("Missing WAV");
            return SAMPLE;
        }, (resource, error) -> errors.add(error));
    }

    private static final class ManualExecutor implements Executor {
        final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        boolean running;

        @Override
        public void execute(Runnable command) {
            tasks.addLast(command);
        }

        void drain() {
            running = true;
            try {
                while (!tasks.isEmpty()) tasks.removeFirst().run();
            } finally {
                running = false;
            }
        }
    }

    /** A hardware-free Clip that also rejects audio operations on callback/caller threads. */
    private static final class FakeClip {
        final List<LineListener> listeners = new ArrayList<>();
        final Clip clip;
        int starts;
        int closes;

        FakeClip(ManualExecutor audio, String failure) {
            clip = (Clip) Proxy.newProxyInstance(Clip.class.getClassLoader(), new Class<?>[]{Clip.class},
                    (proxy, method, args) -> {
                        assertTrue(audio.running, "Clip operation outside audio worker: " + method.getName());
                        switch (method.getName()) {
                            case "addLineListener" -> listeners.add((LineListener) args[0]);
                            case "removeLineListener" -> listeners.remove(args[0]);
                            case "open" -> {
                                assertEquals(SAMPLE.format(), args[0]);
                                assertArrayEquals(SAMPLE.data(), (byte[]) args[1]);
                            }
                            case "start" -> starts++;
                            case "close" -> {
                                closes++;
                                event(LineEvent.Type.STOP);
                                event(LineEvent.Type.CLOSE);
                            }
                            default -> throw new AssertionError("Unexpected Clip operation: " + method.getName());
                        }
                        if (method.getName().equals(failure)) {
                            if (failure.equals("open")) throw new LineUnavailableException("Device busy");
                            throw new IllegalStateException("Device failure: " + failure);
                        }
                        return null;
                    });
        }

        void event(LineEvent.Type type) {
            for (LineListener listener : List.copyOf(listeners)) listener.update(new LineEvent(clip, type, 0));
        }
    }
}
