package cn.omix.util.opai.island;

public final class ScaffoldBpsTracker {
      private Object player;
      private Object world;
      private long lastTick;
      private double lastX;
      private double lastZ;
      private double blocksPerSecond;

      public void sample(Object player, Object world, long tick, double x, double z, double timerMultiplier) {
         if (player == null || world == null || !Double.isFinite(x) || !Double.isFinite(z)
            || !Double.isFinite(timerMultiplier) || timerMultiplier < 0) {
            reset();
            return;
         }
         boolean sameSession = this.player == player && this.world == world;
         if (sameSession && tick == this.lastTick) {
            return;
         }
         // BPS = horizontal distance per tick * 20 TPS * timer multiplier.
         // Prime after enable, respawn, dimension changes or missing ticks; never measure from the origin.
         this.blocksPerSecond = sameSession && tick - this.lastTick == 1
            ? Math.hypot(x - this.lastX, z - this.lastZ) * 20 * timerMultiplier : 0;
         this.player = player;
         this.world = world;
         this.lastTick = tick;
         this.lastX = x;
         this.lastZ = z;
      }

      /** Reads do not decay the value: rendering at 30 or 240 FPS yields the same tick sample. */
      public double blocksPerSecond(Object player, Object world) {
         return this.player == player && this.world == world ? this.blocksPerSecond : 0;
      }

      public void reset() {
         this.player = null;
         this.world = null;
         this.blocksPerSecond = 0;
      }
   }
