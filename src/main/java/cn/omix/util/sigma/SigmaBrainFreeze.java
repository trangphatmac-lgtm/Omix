package cn.omix.util.sigma;

import net.minecraft.client.gui.DrawContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Original snow particle sizes, opacity and changing horizontal wind, with elapsed-time motion. */
public final class SigmaBrainFreeze {
    private final List<Particle> particles = new ArrayList<>();
    private final Random random = new Random();
    private long last, nextWind;
    private float wind = random.nextFloat(), targetWind = wind;

    public void draw(DrawContext context, float opacity) {
        int width = SigmaDraw.width(), height = SigmaDraw.height();
        int count = Math.min(512, width / 4);
        while (particles.size() < count) particles.add(new Particle(random.nextFloat() * width, random.nextFloat() * height,
                (1 + random.nextInt(2) + random.nextFloat()) * 2, random.nextFloat() * 2, (random.nextFloat() - .5f) * 2));
        if (particles.size() > count) particles.subList(count, particles.size()).clear();
        long now = System.nanoTime();
        float dt = last == 0 ? 0 : Math.clamp((now - last) / 1_000_000_000f, 0, .1f); last = now;
        if (now >= nextWind) {
            targetWind = (random.nextFloat() + .75f) * (random.nextBoolean() ? 1 : -1);
            nextWind = now + (8000 + random.nextInt(2001)) * 1_000_000L;
        }
        wind += Math.clamp(targetWind - wind, -2 * dt, 2 * dt);
        for (Particle particle : particles) {
            particle.x = (particle.x + (wind * 2 + particle.drift) * dt * 60 + width) % width;
            particle.y = (particle.y + particle.speed * dt * 60) % height;
            SigmaShape.rounded(context, particle.x - particle.size, particle.y - particle.size, particle.size * 2, particle.size * 2,
                    particle.size, SigmaColors.alpha(0x80ffffff, opacity * .7f));
        }
    }

    private static final class Particle {
        float x, y;
        final float size, speed, drift;
        Particle(float x, float y, float size, float speed, float drift) { this.x=x; this.y=y; this.size=size; this.speed=speed; this.drift=drift; }
    }
}
