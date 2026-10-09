package org.firstinspires.ftc.teamcode.util.hardware.lights;

import com.qualcomm.robotcore.hardware.ControlSystem;
import com.qualcomm.robotcore.hardware.HardwareDevice;
import com.qualcomm.robotcore.hardware.ServoControllerEx;
import com.qualcomm.robotcore.hardware.configuration.ServoFlavor;
import com.qualcomm.robotcore.hardware.configuration.annotations.DeviceProperties;
import com.qualcomm.robotcore.hardware.configuration.annotations.ServoType;
import com.qualcomm.robotcore.util.RobotLog;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * FTC-side wrapper for the custom Blinkin firmware's PWM pattern and command protocol.
 *
 * <p>This class intentionally models Section 1 of the repository plan:
 * <ul>
 *   <li>100 stock/live pattern IDs remain addressable as a typed enum</li>
 *   <li>All 10 firmware command slots are exposed through a raw API</li>
 *   <li>Duplicate strip-mode slots are normalized into semantic 5V and 12V methods</li>
 *   <li>Mode-switch support can be disabled up front for future locked builds</li>
 *   <li>Command writes restore a live pattern pulse after the payload pulse so a continuous FTC
 *       servo signal does not leave the device stuck repeating the payload as a normal pattern</li>
 * </ul>
 *
 * <p>The current firmware is receive-only. There is no readback path yet, so any setup-mode or
 * strip-mode state enforced here is host-assumed state tracked by the wrapper.
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
        RAINBOW_RGB(0),
        RAINBOW_PARTY(1),
        RAINBOW_OCEAN(2),
        RAINBOW_LAVA(3),
        RAINBOW_FOREST(4),
        RAINBOW_WITH_GLITTER(5),
        CONFETTI(6),
        SHOT_RED(7),
        SHOT_BLUE(8),
        SHOT_WHITE(9),
        SINELON_RGB(10),
        SINELON_PARTY(11),
        SINELON_OCEAN(12),
        SINELON_LAVA(13),
        SINELON_FOREST(14),
        BPM_RGB(15),
        BPM_PARTY(16),
        BPM_OCEAN(17),
        BPM_LAVA(18),
        BPM_FOREST(19),
        FIRE_2012_LOW(20),
        FIRE_2012_HIGH(21),
        TWINKLES_RGB(22),
        TWINKLES_PARTY(23),
        TWINKLES_OCEAN(24),
        TWINKLES_LAVA(25),
        TWINKLES_FOREST(26),
        COLOR_WAVES_RGB(27),
        COLOR_WAVES_PARTY(28),
        COLOR_WAVES_OCEAN(29),
        COLOR_WAVES_LAVA(30),
        COLOR_WAVES_FOREST(31),
        LARSON_SCANNER_RED(32),
        LARSON_SCANNER_GRAY(33),
        LIGHT_CHASE_RED(34),
        LIGHT_CHASE_BLUE(35),
        LIGHT_CHASE_GRAY(36),
        HEARTBEAT_RED(37),
        HEARTBEAT_BLUE(38),
        HEARTBEAT_WHITE(39),
        HEARTBEAT_GRAY(40),
        BREATH_RED(41),
        BREATH_BLUE(42),
        BREATH_GRAY(43),
        STROBE_RED(44),
        STROBE_BLUE(45),
        STROBE_GOLD(46),
        STROBE_WHITE(47),
        COLOR1_END_TO_END_STATIC_BLEND(48),
        COLOR1_LARSON_SCANNER(49),
        COLOR1_LIGHT_CHASE(50),
        COLOR1_HEARTBEAT_SLOW(51),
        COLOR1_HEARTBEAT_MEDIUM(52),
        COLOR1_HEARTBEAT_FAST(53),
        COLOR1_BREATH_SLOW(54),
        COLOR1_BREATH_FAST(55),
        COLOR1_SHOT(56),
        COLOR1_STROBE(57),
        COLOR2_END_TO_END_STATIC_BLEND(58),
        COLOR2_LARSON_SCANNER(59),
        COLOR2_LIGHT_CHASE(60),
        COLOR2_HEARTBEAT_SLOW(61),
        COLOR2_HEARTBEAT_MEDIUM(62),
        COLOR2_HEARTBEAT_FAST(63),
        COLOR2_BREATH_SLOW(64),
        COLOR2_BREATH_FAST(65),
        COLOR2_SHOT(66),
        COLOR2_STROBE(67),
        TEAM_SPARKLE(68),
        TEAM_SPARKLE_INVERTED(69),
        RAINBOW_TEAM(70),
        BPM_TEAM(71),
        END_TO_END_BLEND(72),
        END_TO_END_STATIC_BLEND(73),
        TEST_PATTERN(74),
        TWINKLES_TEAM(75),
        COLOR_WAVES_TEAM(76),
        SINELON_TEAM(77),
        HOT_PINK(78),
        DARK_RED(79),
        RED(80),
        RED_ORANGE(81),
        ORANGE(82),
        GOLD(83),
        YELLOW(84),
        LAWN_GREEN(85),
        LIME(86),
        DARK_GREEN(87),
        GREEN(88),
        BLUE_GREEN(89),
        AQUA(90),
        SKY_BLUE(91),
        DARK_BLUE(92),
        BLUE(93),
        BLUE_VIOLET(94),
        VIOLET(95),
        WHITE(96),
        GRAY(97),
        DARK_GRAY(98),
        BLACK(99);

        private static final Pattern[] ELEMENTS = values();
        private final int patternId;

        Pattern(int patternId) {
            this.patternId = patternId;
        }

        public int patternId() {
            return patternId;
        }

        public static Pattern fromId(int patternId) {
            if (patternId < 0 || patternId >= ELEMENTS.length) {
                throw new IllegalArgumentException("Pattern ID must be in [0, 99]: " + patternId);
            }
            return ELEMENTS[patternId];
        }
    }

    public enum BlendMode {
        LINEAR(RawCommandSlot.SET_LINEAR_BLEND),
        NO_BLEND(RawCommandSlot.SET_NO_BLEND);

        private final RawCommandSlot slot;

        BlendMode(RawCommandSlot slot) {
            this.slot = slot;
        }

        RawCommandSlot slot() {
            return slot;
        }
    }

    public enum NormalizedCommand {
        DISABLE_OUTPUT(RawCommandSlot.DISABLE_OUTPUT),
        SET_5V_MODE(RawCommandSlot.SET_5V_MODE_PRIMARY),
        SET_12V_MODE(RawCommandSlot.SET_12V_MODE_PRIMARY),
        SET_COLOR1(RawCommandSlot.SET_COLOR1),
        SET_COLOR2(RawCommandSlot.SET_COLOR2),
        SET_DEFAULT_PATTERN(RawCommandSlot.SET_DEFAULT_PATTERN),
        SET_LINEAR_BLEND(RawCommandSlot.SET_LINEAR_BLEND),
        SET_NO_BLEND(RawCommandSlot.SET_NO_BLEND);

        private final RawCommandSlot canonicalSlot;

        NormalizedCommand(RawCommandSlot canonicalSlot) {
            this.canonicalSlot = canonicalSlot;
        }

        public RawCommandSlot canonicalSlot() {
            return canonicalSlot;
        }
    }

    public enum RawCommandSlot {
        DISABLE_OUTPUT(0, "Disable output"),
        SET_5V_MODE_PRIMARY(1, "Select 5V mode"),
        SET_5V_MODE_SECONDARY(2, "Select 5V mode"),
        SET_12V_MODE_PRIMARY(3, "Select 12V mode"),
        SET_12V_MODE_SECONDARY(4, "Select 12V mode"),
        SET_COLOR1(5, "Change Color 1"),
        SET_COLOR2(6, "Change Color 2"),
        SET_DEFAULT_PATTERN(7, "Change no-signal default pattern"),
        SET_LINEAR_BLEND(8, "Use linear blend"),
        SET_NO_BLEND(9, "Use no blend");

        private static final RawCommandSlot[] ELEMENTS = values();
        private final int slotNumber;
        private final String description;

        RawCommandSlot(int slotNumber, String description) {
            this.slotNumber = slotNumber;
            this.description = description;
        }

        public int slotNumber() {
            return slotNumber;
        }

        public String description() {
            return description;
        }

        public static RawCommandSlot fromSlotNumber(int slotNumber) {
            for (RawCommandSlot slot : ELEMENTS) {
                if (slot.slotNumber == slotNumber) {
                    return slot;
                }
            }
            throw new IllegalArgumentException("Command slot must be in [0, 9]: " + slotNumber);
        }
    }

    public enum ModeLock {
        UNLOCKED,
        FORCE_5V,
        FORCE_12V
    }

    public enum KnownStripMode {
        UNKNOWN,
        MODE_5V,
        MODE_12V
    }

    public enum Outcome {
        APPLIED,
        NO_CHANGE,
        UNSUPPORTED,
        REJECTED
    }

    public static final class Capabilities {
        private final boolean normalizedCommandsSupported;
        private final boolean rawCommandsSupported;
        private final boolean asyncCommandsSupported;
        private final boolean modeSwitchingSupported;
        private final boolean readbackSupported;
        private final boolean outputReenableWithoutModeChange;
        private final boolean setupModeWritesAllowed;
        private final ModeLock modeLock;

        private Capabilities(ModeLock modeLock) {
            this.normalizedCommandsSupported = true;
            this.rawCommandsSupported = true;
            this.asyncCommandsSupported = true;
            this.modeSwitchingSupported = modeLock == ModeLock.UNLOCKED;
            this.readbackSupported = false;
            this.outputReenableWithoutModeChange = false;
            this.setupModeWritesAllowed = false;
            this.modeLock = modeLock;
        }

        public boolean normalizedCommandsSupported() {
            return normalizedCommandsSupported;
        }

        public boolean rawCommandsSupported() {
            return rawCommandsSupported;
        }

        public boolean asyncCommandsSupported() {
            return asyncCommandsSupported;
        }

        public boolean modeSwitchingSupported() {
            return modeSwitchingSupported;
        }

        public boolean readbackSupported() {
            return readbackSupported;
        }

        public boolean outputReenableWithoutModeChange() {
            return outputReenableWithoutModeChange;
        }

        public boolean setupModeWritesAllowed() {
            return setupModeWritesAllowed;
        }

        public ModeLock modeLock() {
            return modeLock;
        }
    }

    public static final class CommandResult {
        private final Outcome outcome;
        private final String operation;
        private final String detail;

        private CommandResult(Outcome outcome, String operation, String detail) {
            this.outcome = outcome;
            this.operation = operation;
            this.detail = detail;
        }

        public Outcome outcome() {
            return outcome;
        }

        public String operation() {
            return operation;
        }

        public String detail() {
            return detail;
        }

        public boolean isSuccess() {
            return outcome == Outcome.APPLIED || outcome == Outcome.NO_CHANGE;
        }

        public void throwIfFailure() {
            if (!isSuccess()) {
                throw new CommandFailureException(this);
            }
        }
    }

    public static final class CommandFailureException extends IllegalStateException {
        private final CommandResult result;

        public CommandFailureException(CommandResult result) {
            super(result.operation() + ": " + result.detail());
            this.result = result;
        }

        public CommandResult result() {
            return result;
        }
    }

    private interface CommandAction {
        CommandResult run() throws InterruptedException;
    }

    public static final int MIN_PATTERN_ID = 0;
    public static final int MAX_PATTERN_ID = 99;
    public static final int MIN_COLOR_PAYLOAD = 78;
    public static final int MAX_COLOR_PAYLOAD = 99;
    public static final int DEFAULT_FRAME_DURATION_MS = 25;
    public static final Pattern DEFAULT_FALLBACK_PATTERN = Pattern.BLACK;

    private static final String TAG = "FtcBlinkinLedDriver";
    private static final double PULSE_WIDTH_INCREMENTOR = 0.0005;
    private static final double BASE_SERVO_POSITION = 505 * PULSE_WIDTH_INCREMENTOR;
    private static final int PATTERN_OFFSET_US = 10;
    private static final int COMMAND_ENTRY_BASE_US = 2105;
    private static final int COMMAND_ENTRY_OFFSET_US = 10;

    private ServoControllerEx controller;
    private int port;
    private ExecutorService commandExecutor;
    private Capabilities capabilities;
    private Pattern fallbackRestorePattern;
    private int frameDurationMs;

    private volatile boolean assumedSetupMode;
    private volatile KnownStripMode knownStripMode;
    private volatile Pattern lastKnownPattern;

    public FtcBlinkinLedDriver(ServoControllerEx controller, int port) {
        this.controller = controller;
        this.port = port;
        initialize(ModeLock.UNLOCKED, DEFAULT_FALLBACK_PATTERN, DEFAULT_FRAME_DURATION_MS);
    }

    public void initialize(ModeLock modeLock, Pattern fallbackRestorePattern, int frameDurationMs) {
        if (modeLock == null) {
            throw new IllegalArgumentException("modeLock must not be null");
        }
        if (fallbackRestorePattern == null) {
            throw new IllegalArgumentException("fallbackRestorePattern must not be null");
        }
        if (frameDurationMs <= 0) {
            throw new IllegalArgumentException("frameDurationMs must be > 0");
        }

        this.capabilities = new Capabilities(modeLock);
        this.fallbackRestorePattern = fallbackRestorePattern;
        this.frameDurationMs = frameDurationMs;
        this.commandExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "BlinkinLedDriver-" + port);
            thread.setDaemon(true);
            return thread;
        });
        this.assumedSetupMode = false;
        this.knownStripMode = modeLock == ModeLock.FORCE_5V
                ? KnownStripMode.MODE_5V
                : modeLock == ModeLock.FORCE_12V ? KnownStripMode.MODE_12V : KnownStripMode.UNKNOWN;
        this.lastKnownPattern = null;
    }

    public Capabilities getCapabilities() {
        return capabilities;
    }

    public boolean isAssumedSetupMode() {
        return assumedSetupMode;
    }

    public void setAssumedSetupMode(boolean assumedSetupMode) {
        this.assumedSetupMode = assumedSetupMode;
    }

    public KnownStripMode getKnownStripMode() {
        return knownStripMode;
    }

    public void setKnownStripMode(KnownStripMode knownStripMode) {
        if (knownStripMode == null) {
            throw new IllegalArgumentException("knownStripMode must not be null");
        }
        this.knownStripMode = knownStripMode;
    }

    public Pattern getLastKnownPattern() {
        return lastKnownPattern;
    }

    public void setLastKnownPattern(Pattern pattern) {
        if (pattern == null) {
            throw new IllegalArgumentException("pattern must not be null");
        }
        this.lastKnownPattern = pattern;
    }

    public CommandResult setPattern(Pattern pattern) {
        return executeSync("setPattern(" + pattern + ")", () -> doSetPattern(pattern));
    }

    public CompletableFuture<CommandResult> setPatternAsync(Pattern pattern) {
        return executeAsync("setPattern(" + pattern + ")", () -> doSetPattern(pattern));
    }

    public void setPatternOrThrow(Pattern pattern) {
        setPattern(pattern).throwIfFailure();
    }

    public CommandResult disableOutput() {
        return executeSync("disableOutput", () -> doSendRawCommand(RawCommandSlot.DISABLE_OUTPUT, 0));
    }

    public CompletableFuture<CommandResult> disableOutputAsync() {
        return executeAsync("disableOutput", () -> doSendRawCommand(RawCommandSlot.DISABLE_OUTPUT, 0));
    }

    public void disableOutputOrThrow() {
        disableOutput().throwIfFailure();
    }

    public CommandResult set5VMode() {
        return executeSync("set5VMode", this::doSet5VMode);
    }

    public CompletableFuture<CommandResult> set5VModeAsync() {
        return executeAsync("set5VMode", this::doSet5VMode);
    }

    public void set5VModeOrThrow() {
        set5VMode().throwIfFailure();
    }

    public CommandResult set12VMode() {
        return executeSync("set12VMode", this::doSet12VMode);
    }

    public CompletableFuture<CommandResult> set12VModeAsync() {
        return executeAsync("set12VMode", this::doSet12VMode);
    }

    public void set12VModeOrThrow() {
        set12VMode().throwIfFailure();
    }

    public CommandResult setColor1(int payload) {
        validateColorPayload(payload, "setColor1");
        return executeSync("setColor1(" + payload + ")", () -> doSendRawCommand(RawCommandSlot.SET_COLOR1, payload));
    }

    public CompletableFuture<CommandResult> setColor1Async(int payload) {
        validateColorPayload(payload, "setColor1Async");
        return executeAsync("setColor1(" + payload + ")", () -> doSendRawCommand(RawCommandSlot.SET_COLOR1, payload));
    }

    public void setColor1OrThrow(int payload) {
        setColor1(payload).throwIfFailure();
    }

    public CommandResult setColor2(int payload) {
        validateColorPayload(payload, "setColor2");
        return executeSync("setColor2(" + payload + ")", () -> doSendRawCommand(RawCommandSlot.SET_COLOR2, payload));
    }

    public CompletableFuture<CommandResult> setColor2Async(int payload) {
        validateColorPayload(payload, "setColor2Async");
        return executeAsync("setColor2(" + payload + ")", () -> doSendRawCommand(RawCommandSlot.SET_COLOR2, payload));
    }

    public void setColor2OrThrow(int payload) {
        setColor2(payload).throwIfFailure();
    }

    public CommandResult setDefaultPattern(Pattern pattern) {
        return executeSync(
                "setDefaultPattern(" + pattern + ")",
                () -> doSendRawCommand(RawCommandSlot.SET_DEFAULT_PATTERN, pattern.patternId()));
    }

    public CompletableFuture<CommandResult> setDefaultPatternAsync(Pattern pattern) {
        return executeAsync(
                "setDefaultPattern(" + pattern + ")",
                () -> doSendRawCommand(RawCommandSlot.SET_DEFAULT_PATTERN, pattern.patternId()));
    }

    public void setDefaultPatternOrThrow(Pattern pattern) {
        setDefaultPattern(pattern).throwIfFailure();
    }

    public CommandResult setBlendMode(BlendMode blendMode) {
        if (blendMode == null) {
            throw new IllegalArgumentException("blendMode must not be null");
        }
        return executeSync("setBlendMode(" + blendMode + ")", () -> doSendRawCommand(blendMode.slot(), 0));
    }

    public CompletableFuture<CommandResult> setBlendModeAsync(BlendMode blendMode) {
        if (blendMode == null) {
            throw new IllegalArgumentException("blendMode must not be null");
        }
        return executeAsync("setBlendMode(" + blendMode + ")", () -> doSendRawCommand(blendMode.slot(), 0));
    }

    public void setBlendModeOrThrow(BlendMode blendMode) {
        setBlendMode(blendMode).throwIfFailure();
    }

    public CommandResult sendRawCommand(RawCommandSlot slot, int payload) {
        if (slot == null) {
            throw new IllegalArgumentException("slot must not be null");
        }
        validateUniversalPayload(payload);
        return executeSync("sendRawCommand(" + slot + ", " + payload + ")", () -> doSendRawCommand(slot, payload));
    }

    public CompletableFuture<CommandResult> sendRawCommandAsync(RawCommandSlot slot, int payload) {
        if (slot == null) {
            throw new IllegalArgumentException("slot must not be null");
        }
        validateUniversalPayload(payload);
        return executeAsync("sendRawCommand(" + slot + ", " + payload + ")", () -> doSendRawCommand(slot, payload));
    }

    public void sendRawCommandOrThrow(RawCommandSlot slot, int payload) {
        sendRawCommand(slot, payload).throwIfFailure();
    }

    public CommandResult sendRawCommand(int slotNumber, int payload) {
        validateUniversalPayload(payload);
        return sendRawCommand(RawCommandSlot.fromSlotNumber(slotNumber), payload);
    }

    public CompletableFuture<CommandResult> sendRawCommandAsync(int slotNumber, int payload) {
        validateUniversalPayload(payload);
        return sendRawCommandAsync(RawCommandSlot.fromSlotNumber(slotNumber), payload);
    }

    public void sendRawCommandOrThrow(int slotNumber, int payload) {
        sendRawCommand(slotNumber, payload).throwIfFailure();
    }

    private CommandResult doSetPattern(Pattern pattern) {
        if (pattern == null) {
            throw new IllegalArgumentException("pattern must not be null");
        }
        lastKnownPattern = pattern;
        controller.setServoPosition(port, patternToServoPosition(pattern));
        RobotLog.vv(TAG, "Applied live pattern %s (%d)", pattern, pattern.patternId());
        return applied("setPattern(" + pattern + ")", "Live pattern pulse updated");
    }

    private CommandResult doSet5VMode() throws InterruptedException {
        if (capabilities.modeLock() == ModeLock.FORCE_5V || knownStripMode == KnownStripMode.MODE_5V) {
            knownStripMode = KnownStripMode.MODE_5V;
            return noChange("set5VMode", "Device is already in 5V mode");
        }
        if (!capabilities.modeSwitchingSupported()) {
            return unsupported("set5VMode", "Mode switching is disabled for this locked build");
        }
        CommandResult result = doSendRawCommand(RawCommandSlot.SET_5V_MODE_PRIMARY, 0);
        if (result.isSuccess()) {
            knownStripMode = KnownStripMode.MODE_5V;
        }
        return result;
    }

    private CommandResult doSet12VMode() throws InterruptedException {
        if (capabilities.modeLock() == ModeLock.FORCE_12V || knownStripMode == KnownStripMode.MODE_12V) {
            knownStripMode = KnownStripMode.MODE_12V;
            return noChange("set12VMode", "Device is already in 12V mode");
        }
        if (!capabilities.modeSwitchingSupported()) {
            return unsupported("set12VMode", "Mode switching is disabled for this locked build");
        }
        CommandResult result = doSendRawCommand(RawCommandSlot.SET_12V_MODE_PRIMARY, 0);
        if (result.isSuccess()) {
            knownStripMode = KnownStripMode.MODE_12V;
        }
        return result;
    }

    private CommandResult doSendRawCommand(RawCommandSlot slot, int payload) throws InterruptedException {
        CommandResult preflight = preflight(slot, payload);
        if (preflight != null) {
            return preflight;
        }

        controller.setServoPosition(port, commandEntryToServoPosition(slot));
        sleepFrame();
        controller.setServoPosition(port, payloadToServoPosition(payload));
        sleepFrame();

        Pattern restorePattern = lastKnownPattern != null ? lastKnownPattern : fallbackRestorePattern;
        controller.setServoPosition(port, patternToServoPosition(restorePattern));
        sleepFrame();

        if (slot == RawCommandSlot.SET_5V_MODE_PRIMARY || slot == RawCommandSlot.SET_5V_MODE_SECONDARY) {
            knownStripMode = KnownStripMode.MODE_5V;
        } else if (slot == RawCommandSlot.SET_12V_MODE_PRIMARY || slot == RawCommandSlot.SET_12V_MODE_SECONDARY) {
            knownStripMode = KnownStripMode.MODE_12V;
        }

        RobotLog.vv(
                TAG,
                "Applied raw command %s (slot=%d, payload=%d, restore=%s)",
                slot,
                slot.slotNumber(),
                payload,
                restorePattern);
        return applied(
                "sendRawCommand(" + slot + ", " + payload + ")",
                "Command entry, payload, and restore pattern pulses sent");
    }

    private CommandResult preflight(RawCommandSlot slot, int payload) {
        validateUniversalPayload(payload);

        if (assumedSetupMode) {
            return rejected(
                    "sendRawCommand(" + slot + ", " + payload + ")",
                    "Command writes are rejected while the wrapper is marked in setup mode");
        }

        if (!capabilities.modeSwitchingSupported() && isModeSwitchSlot(slot)) {
            return unsupported(
                    "sendRawCommand(" + slot + ", " + payload + ")",
                    "Mode switching is disabled for this locked build");
        }

        if (capabilities.modeLock() == ModeLock.FORCE_5V && is12VSlot(slot)) {
            return unsupported(
                    "sendRawCommand(" + slot + ", " + payload + ")",
                    "12V mode requests are not supported by a locked 5V build");
        }

        if (capabilities.modeLock() == ModeLock.FORCE_12V && is5VSlot(slot)) {
            return unsupported(
                    "sendRawCommand(" + slot + ", " + payload + ")",
                    "5V mode requests are not supported by a locked 12V build");
        }

        return null;
    }

    private boolean isModeSwitchSlot(RawCommandSlot slot) {
        return is5VSlot(slot) || is12VSlot(slot);
    }

    private boolean is5VSlot(RawCommandSlot slot) {
        return slot == RawCommandSlot.SET_5V_MODE_PRIMARY || slot == RawCommandSlot.SET_5V_MODE_SECONDARY;
    }

    private boolean is12VSlot(RawCommandSlot slot) {
        return slot == RawCommandSlot.SET_12V_MODE_PRIMARY || slot == RawCommandSlot.SET_12V_MODE_SECONDARY;
    }

    private void validateColorPayload(int payload, String operation) {
        if (payload < MIN_COLOR_PAYLOAD || payload > MAX_COLOR_PAYLOAD) {
            throw new IllegalArgumentException(
                    operation
                            + " payload must be in ["
                            + MIN_COLOR_PAYLOAD
                            + ", "
                            + MAX_COLOR_PAYLOAD
                            + "] to match the firmware's color command contract: "
                            + payload);
        }
    }

    private void validateUniversalPayload(int payload) {
        if (payload < MIN_PATTERN_ID || payload > MAX_PATTERN_ID) {
            throw new IllegalArgumentException("Payload must be in [0, 99]: " + payload);
        }
    }

    private double patternToServoPosition(Pattern pattern) {
        return BASE_SERVO_POSITION + (pattern.patternId() * PATTERN_OFFSET_US * PULSE_WIDTH_INCREMENTOR);
    }

    private double payloadToServoPosition(int payload) {
        return BASE_SERVO_POSITION + (payload * PATTERN_OFFSET_US * PULSE_WIDTH_INCREMENTOR);
    }

    private double commandEntryToServoPosition(RawCommandSlot slot) {
        int pulseWidthMicros = COMMAND_ENTRY_BASE_US + (slot.slotNumber() * COMMAND_ENTRY_OFFSET_US);
        return pulseWidthMicros * PULSE_WIDTH_INCREMENTOR;
    }

    private void sleepFrame() throws InterruptedException {
        Thread.sleep(frameDurationMs);
    }

    private CommandResult executeSync(String operation, CommandAction action) {
        try {
            return executeAsync(operation, action).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for " + operation, e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new IllegalStateException("Unexpected failure while running " + operation, cause);
        }
    }

    private CompletableFuture<CommandResult> executeAsync(String operation, CommandAction action) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return action.run();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CompletionException(new IllegalStateException("Interrupted while running " + operation, e));
            }
        }, commandExecutor);
    }

    private CommandResult applied(String operation, String detail) {
        return new CommandResult(Outcome.APPLIED, operation, detail);
    }

    private CommandResult noChange(String operation, String detail) {
        return new CommandResult(Outcome.NO_CHANGE, operation, detail);
    }

    private CommandResult unsupported(String operation, String detail) {
        return new CommandResult(Outcome.UNSUPPORTED, operation, detail);
    }

    private CommandResult rejected(String operation, String detail) {
        return new CommandResult(Outcome.REJECTED, operation, detail);
    }

    @Override
    public Manufacturer getManufacturer() {
        return Manufacturer.Lynx;
    }

    @Override
    public String getDeviceName() {
        return TAG;
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
    public void resetDeviceConfigurationForOpMode() {
        assumedSetupMode = false;
        knownStripMode = capabilities.modeLock() == ModeLock.FORCE_5V
                ? KnownStripMode.MODE_5V
                : capabilities.modeLock() == ModeLock.FORCE_12V
                  ? KnownStripMode.MODE_12V
                  : KnownStripMode.UNKNOWN;
    }

    @Override
    public void close() {
        commandExecutor.shutdownNow();
    }
}
