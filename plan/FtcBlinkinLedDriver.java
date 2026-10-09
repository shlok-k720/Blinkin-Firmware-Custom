package org.firstinspires.ftc.teamcode.util.hardware.lights;

import android.graphics.Color;

import com.qualcomm.robotcore.hardware.ControlSystem;
import com.qualcomm.robotcore.hardware.HardwareDevice;
import com.qualcomm.robotcore.hardware.ServoControllerEx;
import com.qualcomm.robotcore.hardware.configuration.ServoFlavor;
import com.qualcomm.robotcore.hardware.configuration.annotations.DeviceProperties;
import com.qualcomm.robotcore.hardware.configuration.annotations.ServoType;
import com.qualcomm.robotcore.util.RobotLog;

/**
 * Minimal FTC wrapper for the custom Blinkin firmware PWM protocol.
 *
 * <p>The wrapper keeps the API small:
 * <ul>
 *   <li>Set a live pattern</li>
 *   <li>Send one of the supported semantic commands</li>
 *   <li>Send a raw command slot when debugging</li>
 * </ul>
 *
 * <p>The firmware is write-only today, so setup-mode and current strip-mode state are host-tracked
 * assumptions rather than read-back facts.
 */
@ServoType(flavor = ServoFlavor.CUSTOM, usPulseLower = 500, usPulseUpper = 2500)
@DeviceProperties(
        xmlTag = "FtcBlinkinLedDriver",
        name = "FTC Blinkin Led Driver",
        description = "Custom Blinkin firmware wrapper with pattern and command support",
        builtIn = false,
        compatibleControlSystems = ControlSystem.REV_HUB)
public class FtcBlinkinLedDriver implements HardwareDevice {

    public enum Pattern {
        RAINBOW_RGB,
        RAINBOW_PARTY,
        RAINBOW_OCEAN,
        RAINBOW_LAVA,
        RAINBOW_FOREST,
        RAINBOW_WITH_GLITTER,
        CONFETTI,
        SHOT_RED,
        SHOT_BLUE,
        SHOT_WHITE,
        SINELON_RGB,
        SINELON_PARTY,
        SINELON_OCEAN,
        SINELON_LAVA,
        SINELON_FOREST,
        BPM_RGB,
        BPM_PARTY,
        BPM_OCEAN,
        BPM_LAVA,
        BPM_FOREST,
        FIRE_2012_LOW,
        FIRE_2012_HIGH,
        TWINKLES_RGB,
        TWINKLES_PARTY,
        TWINKLES_OCEAN,
        TWINKLES_LAVA,
        TWINKLES_FOREST,
        COLOR_WAVES_RGB,
        COLOR_WAVES_PARTY,
        COLOR_WAVES_OCEAN,
        COLOR_WAVES_LAVA,
        COLOR_WAVES_FOREST,
        LARSON_SCANNER_RED,
        LARSON_SCANNER_GRAY,
        LIGHT_CHASE_RED,
        LIGHT_CHASE_BLUE,
        LIGHT_CHASE_GRAY,
        HEARTBEAT_RED,
        HEARTBEAT_BLUE,
        HEARTBEAT_WHITE,
        HEARTBEAT_GRAY,
        BREATH_RED,
        BREATH_BLUE,
        BREATH_GRAY,
        STROBE_RED,
        STROBE_BLUE,
        STROBE_GOLD,
        STROBE_WHITE,
        COLOR1_END_TO_END_STATIC_BLEND,
        COLOR1_LARSON_SCANNER,
        COLOR1_LIGHT_CHASE,
        COLOR1_HEARTBEAT_SLOW,
        COLOR1_HEARTBEAT_MEDIUM,
        COLOR1_HEARTBEAT_FAST,
        COLOR1_BREATH_SLOW,
        COLOR1_BREATH_FAST,
        COLOR1_SHOT,
        COLOR1_STROBE,
        COLOR2_END_TO_END_STATIC_BLEND,
        COLOR2_LARSON_SCANNER,
        COLOR2_LIGHT_CHASE,
        COLOR2_HEARTBEAT_SLOW,
        COLOR2_HEARTBEAT_MEDIUM,
        COLOR2_HEARTBEAT_FAST,
        COLOR2_BREATH_SLOW,
        COLOR2_BREATH_FAST,
        COLOR2_SHOT,
        COLOR2_STROBE,
        TEAM_SPARKLE,
        TEAM_SPARKLE_INVERTED,
        RAINBOW_TEAM,
        BPM_TEAM,
        END_TO_END_BLEND,
        END_TO_END_STATIC_BLEND,
        TEST_PATTERN,
        TWINKLES_TEAM,
        COLOR_WAVES_TEAM,
        SINELON_TEAM,
        HOT_PINK,
        DARK_RED,
        RED,
        RED_ORANGE,
        ORANGE,
        GOLD,
        YELLOW,
        LAWN_GREEN,
        LIME,
        DARK_GREEN,
        GREEN,
        BLUE_GREEN,
        AQUA,
        SKY_BLUE,
        DARK_BLUE,
        BLUE,
        BLUE_VIOLET,
        VIOLET,
        WHITE,
        GRAY,
        DARK_GRAY,
        BLACK;

        private static final Pattern[] VALUES = values();

        public int id() {
            return ordinal();
        }

        public static Pattern fromId(int id) {
            if (id < 0 || id >= VALUES.length) {
                throw new IllegalArgumentException("Pattern ID must be in [0, 99]: " + id);
            }
            return VALUES[id];
        }
    }

    public enum StripMode {
        MODE_5V,
        MODE_12V
    }

    public enum BlendMode {
        LINEAR,
        NO_BLEND
    }

    public enum ModeLock {
        UNLOCKED,
        LOCKED_5V,
        LOCKED_12V
    }

    public static final int SLOT_DISABLE_OUTPUT = 0;
    public static final int SLOT_SET_5V_MODE_PRIMARY = 1;
    public static final int SLOT_SET_5V_MODE_SECONDARY = 2;
    public static final int SLOT_SET_12V_MODE_PRIMARY = 3;
    public static final int SLOT_SET_12V_MODE_SECONDARY = 4;
    public static final int SLOT_SET_COLOR1 = 5;
    public static final int SLOT_SET_COLOR2 = 6;
    public static final int SLOT_SET_DEFAULT_PATTERN = 7;
    public static final int SLOT_SET_LINEAR_BLEND = 8;
    public static final int SLOT_SET_NO_BLEND = 9;

    public static final int MIN_PAYLOAD = 0;
    public static final int MAX_PAYLOAD = 99;
    public static final int FIRST_COLOR_PATTERN_ID = Pattern.HOT_PINK.id();
    public static final int LAST_COLOR_PATTERN_ID = Pattern.BLACK.id();
    public static final int DEFAULT_FRAME_DURATION_MS = 25;
    public static final Pattern DEFAULT_RESTORE_PATTERN = Pattern.BLACK;

    private static final String TAG = "FtcBlinkinLedDriver";
    private static final double SERVO_POSITION_PER_MICROSECOND = 0.0005;
    private static final double BASE_SERVO_POSITION = 505 * SERVO_POSITION_PER_MICROSECOND;
    private static final int NORMAL_PULSE_STEP_US = 10;
    private static final int COMMAND_ENTRY_BASE_US = 2105;

    private static final int[] FIRMWARE_COLORS = {
            0xFF00AA, 0x990000, 0xFF0000, 0xFF6A00, 0xFF8C00, 0xFFEA00, 0xFFFF00, 0xBFFF00,
            0x80FF00, 0x009900, 0x00FF00, 0x00FFAA, 0x00FFFF, 0x0080FF, 0x000099, 0x0000FF,
            0x8000FF, 0xAA00FF, 0xFFFFFF, 0x4D4D4D, 0x1A1A1A, 0x000000
    };

    private final ServoControllerEx controller;
    private final int port;
    private final ModeLock modeLock;
    private final Pattern restorePatternWhenUnknown;
    private final int frameDurationMs;

    private boolean assumedSetupMode;
    private StripMode knownStripMode;
    private Pattern lastPattern;

    public FtcBlinkinLedDriver(ServoControllerEx controller, int port) {
        this(controller, port, ModeLock.UNLOCKED, DEFAULT_RESTORE_PATTERN, DEFAULT_FRAME_DURATION_MS);
    }

    public FtcBlinkinLedDriver(
            ServoControllerEx controller,
            int port,
            ModeLock modeLock,
            Pattern restorePatternWhenUnknown,
            int frameDurationMs) {
        if (controller == null) {
            throw new IllegalArgumentException("controller must not be null");
        }
        if (modeLock == null) {
            throw new IllegalArgumentException("modeLock must not be null");
        }
        if (restorePatternWhenUnknown == null) {
            throw new IllegalArgumentException("restorePatternWhenUnknown must not be null");
        }
        if (frameDurationMs <= 0) {
            throw new IllegalArgumentException("frameDurationMs must be > 0");
        }

        this.controller = controller;
        this.port = port;
        this.modeLock = modeLock;
        this.restorePatternWhenUnknown = restorePatternWhenUnknown;
        this.frameDurationMs = frameDurationMs;
        this.knownStripMode = initialKnownMode(modeLock);
    }

    public synchronized void setPattern(Pattern pattern) {
        requirePattern(pattern);
        lastPattern = pattern;
        controller.setServoPosition(port, positionForPattern(pattern));
        RobotLog.vv(TAG, "Set live pattern %s (%d)", pattern, pattern.id());
    }

    public synchronized void disableOutput() {
        sendCommandInternal(SLOT_DISABLE_OUTPUT, 0, "disableOutput");
    }

    public synchronized void setMode(StripMode mode) {
        if (mode == null) {
            throw new IllegalArgumentException("mode must not be null");
        }
        if (knownStripMode == mode) {
            RobotLog.vv(TAG, "Ignoring setMode(%s): already selected", mode);
            return;
        }
        if (modeLock == ModeLock.LOCKED_5V && mode == StripMode.MODE_12V) {
            throw new UnsupportedOperationException("12V mode is not supported by a locked 5V build");
        }
        if (modeLock == ModeLock.LOCKED_12V && mode == StripMode.MODE_5V) {
            throw new UnsupportedOperationException("5V mode is not supported by a locked 12V build");
        }

        int slot = mode == StripMode.MODE_5V ? SLOT_SET_5V_MODE_PRIMARY : SLOT_SET_12V_MODE_PRIMARY;
        sendCommandInternal(slot, 0, "setMode(" + mode + ")");
        knownStripMode = mode;
    }

    public synchronized void setColor1(int androidColor) {
        int payload = colorPayloadFor(androidColor);
        sendCommandInternal(SLOT_SET_COLOR1, payload, "setColor1");
    }

    public synchronized void setColor2(int androidColor) {
        int payload = colorPayloadFor(androidColor);
        sendCommandInternal(SLOT_SET_COLOR2, payload, "setColor2");
    }

    public synchronized void setDefaultPattern(Pattern pattern) {
        requirePattern(pattern);
        sendCommandInternal(SLOT_SET_DEFAULT_PATTERN, pattern.id(), "setDefaultPattern(" + pattern + ")");
    }

    public synchronized void setBlend(BlendMode blendMode) {
        if (blendMode == null) {
            throw new IllegalArgumentException("blendMode must not be null");
        }
        int slot = blendMode == BlendMode.LINEAR ? SLOT_SET_LINEAR_BLEND : SLOT_SET_NO_BLEND;
        sendCommandInternal(slot, 0, "setBlend(" + blendMode + ")");
    }

    public synchronized void sendCommand(int slot, int payload) {
        sendCommandInternal(slot, payload, "sendCommand(" + slot + ", " + payload + ")");
    }

    public synchronized void setAssumedSetupMode(boolean assumedSetupMode) {
        this.assumedSetupMode = assumedSetupMode;
    }

    public synchronized boolean isAssumedSetupMode() {
        return assumedSetupMode;
    }

    public synchronized StripMode getKnownStripMode() {
        return knownStripMode;
    }

    public synchronized void setKnownStripMode(StripMode knownStripMode) {
        this.knownStripMode = knownStripMode;
    }

    public synchronized Pattern getLastPattern() {
        return lastPattern;
    }

    public static int colorPayloadFor(int androidColor) {
        int bestIndex = 0;
        long bestDistance = Long.MAX_VALUE;

        int targetRed = Color.red(androidColor);
        int targetGreen = Color.green(androidColor);
        int targetBlue = Color.blue(androidColor);

        for (int i = 0; i < FIRMWARE_COLORS.length; i++) {
            int candidate = FIRMWARE_COLORS[i];
            int red = (candidate >> 16) & 0xFF;
            int green = (candidate >> 8) & 0xFF;
            int blue = candidate & 0xFF;

            long distance = square(targetRed - red) + square(targetGreen - green) + square(targetBlue - blue);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestIndex = i;
            }
        }

        return FIRST_COLOR_PATTERN_ID + bestIndex;
    }

    private void sendCommandInternal(int slot, int payload, String operation) {
        ensureCommandAllowed(slot, payload, operation);

        Pattern restorePattern = lastPattern != null ? lastPattern : restorePatternWhenUnknown;

        controller.setServoPosition(port, positionForCommandSlot(slot));
        sleepFrame();
        controller.setServoPosition(port, positionForPayload(payload));
        sleepFrame();
        controller.setServoPosition(port, positionForPattern(restorePattern));
        sleepFrame();

        if (slot == SLOT_SET_5V_MODE_PRIMARY || slot == SLOT_SET_5V_MODE_SECONDARY) {
            knownStripMode = StripMode.MODE_5V;
        } else if (slot == SLOT_SET_12V_MODE_PRIMARY || slot == SLOT_SET_12V_MODE_SECONDARY) {
            knownStripMode = StripMode.MODE_12V;
        }

        RobotLog.vv(
                TAG,
                "%s sent slot=%d payload=%d restore=%s",
                operation,
                slot,
                payload,
                restorePattern);
    }

    private void ensureCommandAllowed(int slot, int payload, String operation) {
        validateSlot(slot);
        validatePayload(payload);

        if (assumedSetupMode) {
            throw new IllegalStateException(operation + " rejected because the wrapper is marked in setup mode");
        }

        if (modeLock == ModeLock.LOCKED_5V && is12VSlot(slot)) {
            throw new UnsupportedOperationException(operation + " is not supported by a locked 5V build");
        }

        if (modeLock == ModeLock.LOCKED_12V && is5VSlot(slot)) {
            throw new UnsupportedOperationException(operation + " is not supported by a locked 12V build");
        }
    }

    private static void requirePattern(Pattern pattern) {
        if (pattern == null) {
            throw new IllegalArgumentException("pattern must not be null");
        }
    }

    private static void validateSlot(int slot) {
        if (slot < SLOT_DISABLE_OUTPUT || slot > SLOT_SET_NO_BLEND) {
            throw new IllegalArgumentException("Command slot must be in [0, 9]: " + slot);
        }
    }

    private static void validatePayload(int payload) {
        if (payload < MIN_PAYLOAD || payload > MAX_PAYLOAD) {
            throw new IllegalArgumentException("Payload must be in [0, 99]: " + payload);
        }
    }

    private static boolean is5VSlot(int slot) {
        return slot == SLOT_SET_5V_MODE_PRIMARY || slot == SLOT_SET_5V_MODE_SECONDARY;
    }

    private static boolean is12VSlot(int slot) {
        return slot == SLOT_SET_12V_MODE_PRIMARY || slot == SLOT_SET_12V_MODE_SECONDARY;
    }

    private static long square(int value) {
        return (long) value * value;
    }

    private static StripMode initialKnownMode(ModeLock modeLock) {
        if (modeLock == ModeLock.LOCKED_5V) {
            return StripMode.MODE_5V;
        }
        if (modeLock == ModeLock.LOCKED_12V) {
            return StripMode.MODE_12V;
        }
        return null;
    }

    private static double positionForPattern(Pattern pattern) {
        return BASE_SERVO_POSITION + (pattern.id() * NORMAL_PULSE_STEP_US * SERVO_POSITION_PER_MICROSECOND);
    }

    private static double positionForPayload(int payload) {
        return BASE_SERVO_POSITION + (payload * NORMAL_PULSE_STEP_US * SERVO_POSITION_PER_MICROSECOND);
    }

    private static double positionForCommandSlot(int slot) {
        return (COMMAND_ENTRY_BASE_US + (slot * NORMAL_PULSE_STEP_US)) * SERVO_POSITION_PER_MICROSECOND;
    }

    private void sleepFrame() {
        try {
            Thread.sleep(frameDurationMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while sending a Blinkin command", e);
        }
    }

    @Override
    public Manufacturer getManufacturer() {
        return Manufacturer.Lynx;
    }

    @Override
    public String getDeviceName() {
        return "FTC Blinkin Led Driver";
    }

    @Override
    public String getConnectionInfo() {
        return controller.getConnectionInfo() + "; port " + port;
    }

    @Override
    public int getVersion() {
        return 1;
    }

    @Override
    public synchronized void resetDeviceConfigurationForOpMode() {
        assumedSetupMode = false;
        knownStripMode = initialKnownMode(modeLock);
    }

    @Override
    public void close() {
        // No resources to release.
    }
}
