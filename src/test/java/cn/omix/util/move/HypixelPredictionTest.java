package cn.omix.util.move;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HypixelPredictionTest {
    @Test
    void repeatsTheReferenceCycle() {
        HypixelPrediction prediction = new HypixelPrediction();
        float[] expected = {1.0F, 0.85F, 0.70F, 0.63F, 0.55F, 1.70F, 1.52F, 1.50F, 1.47F, 1.44F};
        for (int cycle = 0; cycle < 3; cycle++) {
            for (float speed : expected) assertEquals(speed, prediction.tick(true));
        }
    }

    @Test
    void interruptionAtEveryPhaseRestartsFromNormalSpeed() {
        for (int phase = 0; phase < 10; phase++) {
            HypixelPrediction prediction = new HypixelPrediction();
            for (int tick = 0; tick <= phase; tick++) prediction.tick(true);
            assertEquals(1.0F, prediction.tick(false));
            assertEquals(1.0F, prediction.tick(false));
            assertEquals(1.0F, prediction.tick(true));
            assertEquals(0.85F, prediction.tick(true));
            prediction.reset();
            assertEquals(1.0F, prediction.tick(true));
        }
    }

    @Test
    void yawFollowsAllEightDirectionsAndOffsetsOnlyInAir() {
        int[][] directions = {{1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}};
        float[] ground = {0, 45, 90, 135, -180, -135, -90, -45};
        float[] air = {-45, 0, 45, 90, 135, -180, -135, -90};
        for (int i = 0; i < directions.length; i++) {
            assertEquals(ground[i], HypixelPrediction.movementYaw(0, directions[i][0], directions[i][1], true));
            assertEquals(air[i], HypixelPrediction.movementYaw(0, directions[i][0], directions[i][1], false));
        }
        assertEquals(-145, HypixelPrediction.movementYaw(170, 1, 1, true));
        assertEquals(170, HypixelPrediction.movementYaw(170, 0, 0, true));
    }
}
