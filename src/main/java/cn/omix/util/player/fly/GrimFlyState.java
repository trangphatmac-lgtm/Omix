package cn.omix.util.player.fly;

/** Height selection and PRE-motion cadence ported from Southside's GrimFly. */
public final class GrimFlyState {
    private int ticks;
    private boolean shouldFly;
    private boolean heightCaptured;
    private double height;
    private boolean jumpWasPressed;
    private boolean sneakWasPressed;
    private boolean waitingForApex;
    private double maxY;
    private boolean hasValidYVelocity;

    public static String migrateMode(String mode) {
        return switch (mode) {
            case "1平" -> "Flat";
            case "2随意" -> "Free";
            case "3一直" -> "Always";
            default -> mode;
        };
    }

    public boolean onMotion(String mode, boolean selectMaxVelocity, double y, double velocityY,
                            boolean jumpPressed, boolean sneakPressed) {
        switch (mode) {
            case "Flat" -> {
                if (Math.abs(y % 1.0 - 0.24919) < 1.0E-4) {
                    shouldFly = true;
                }
            }
            case "Free" -> {
                updateHeight(selectMaxVelocity, y, velocityY, jumpPressed, sneakPressed);
                shouldFly = jumpPressed || heightCaptured && !waitingForApex && y <= height;
                jumpWasPressed = jumpPressed;
                sneakWasPressed = sneakPressed;
            }
            case "Always" -> shouldFly = true;
        }

        if (sneakPressed) {
            shouldFly = false;
        }
        return ++ticks % 2 == 0 && shouldFly;
    }

    private void updateHeight(boolean selectMaxVelocity, double y, double velocityY,
                              boolean jumpPressed, boolean sneakPressed) {
        if (selectMaxVelocity) {
            if (jumpPressed && !jumpWasPressed) {
                maxY = y;
                hasValidYVelocity = false;
            }

            if (jumpPressed) {
                if (!hasValidYVelocity && Math.abs(velocityY) >= 0.4) {
                    hasValidYVelocity = true;
                }
                if (hasValidYVelocity) {
                    maxY = Math.max(maxY, y);
                    if (y > height) {
                        height = y;
                        heightCaptured = true;
                    }
                }
            }

            if (!jumpPressed && jumpWasPressed && hasValidYVelocity) {
                waitingForApex = true;
            }
            if (waitingForApex) {
                maxY = Math.max(maxY, y);
                if (velocityY <= 0.0) {
                    height = maxY;
                    heightCaptured = true;
                    waitingForApex = false;
                }
            }
        } else if ((jumpPressed && y > height) || (!jumpPressed && jumpWasPressed)) {
            height = y;
            heightCaptured = true;
        }

        if (!sneakPressed && sneakWasPressed) {
            height = y;
            heightCaptured = true;
            waitingForApex = false;
        }
    }

    public void captureCorrectionHeight(double y) {
        if (!heightCaptured) {
            height = y;
            heightCaptured = true;
        }
    }

    public void reset() {
        ticks = 0;
        shouldFly = false;
        heightCaptured = false;
        height = 0.0;
        jumpWasPressed = false;
        sneakWasPressed = false;
        waitingForApex = false;
        maxY = 0.0;
        hasValidYVelocity = false;
    }
}
