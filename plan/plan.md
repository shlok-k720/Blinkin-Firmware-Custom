# Blinkin customization plan

## Goal

Plan the repository changes needed to:
- expose the existing Blinkin command surface to FTC Java
- add a readback path without committing yet to one transport
- support hard-locked 5V and 12V firmware variants
- provide an FTC hardware wrapper that matches the final firmware interface

The implementation should preserve existing pattern behavior where possible, keep the work sectioned so it can be done at different times, and align firmware, FTC integration, and documentation.

## Codebase facts that drive the plan

- Firmware entry points are [BlinkinFirmware.ino](/Users/kshlok/Downloads/Blinkin-Firmware-Custom/BlinkinFirmware.ino), [interrupt.ino](/Users/kshlok/Downloads/Blinkin-Firmware-Custom/interrupt.ino), [UserIO.ino](/Users/kshlok/Downloads/Blinkin-Firmware-Custom/UserIO.ino), [PWM_0_Command.ino](/Users/kshlok/Downloads/Blinkin-Firmware-Custom/PWM_0_Command.ino), and the pattern files.
- Host control today is one-way PWM on D2; there is no existing I2C, SPI, or other readback channel in the firmware.
- `gCommands[]` already defines 10 command slots, including output disable, strip-mode switching, color changes, default-pattern updates, and blend-mode updates.
- Most state needed for readback already exists in globals, but some values are not persisted after decode, especially raw PWM timing and command payload details.
- Strip mode can currently change from button input, PWM commands, direct setters, and EEPROM reloads.
- [RevBlinkinLedDriver.java](/Users/kshlok/Downloads/Blinkin-Firmware-Custom/plan/RevBlinkinLedDriver.java) is reference code only; it writes pattern PWM values but does not support command sequencing or readback.
- [README.md](/Users/kshlok/Downloads/Blinkin-Firmware-Custom/README.md) claims `5v` and `12v` branches exist, but only `master` exists today.

## Section 1 — Expose all commands to FTC Java

Status: complete

### Outcome
Define a complete, documented Java-facing command surface for everything the current firmware already supports safely.

### Related methods and facts
- `gCommands[]` in [BlinkinFirmware.ino](/Users/kshlok/Downloads/Blinkin-Firmware-Custom/BlinkinFirmware.ino) exposes 10 command slots.
- Commands are decoded in `ISRfalling()` in [interrupt.ino](/Users/kshlok/Downloads/Blinkin-Firmware-Custom/interrupt.ino).
- 4200-4400 timer ticks enter command mode; the next normal-width pulse supplies the payload.
- Pattern selection is decoded from 2000-4000 timer ticks and mapped to pattern IDs 0-99.
- `cmdChangeColor1` and `cmdChangeColor2` map payload values 78-99 into color indexes 0-21.
- Strip-mode commands currently call `setStripSelect(true/false)`.
- [RevBlinkinLedDriver.java](/Users/kshlok/Downloads/Blinkin-Firmware-Custom/plan/RevBlinkinLedDriver.java) currently exposes only PWM pattern writes.

### Implemented work
- documented the exact PWM command protocol:
  - command entry pulse range
  - slot-to-command mapping
  - payload encoding rules
  - ignored or invalid cases
- normalized the duplicate strip-mode slots into one semantic 5V command and one semantic 12V command at the FTC API level
- defined Java enums/constants for:
  - live patterns
  - normalized commands
  - raw command slots
  - blend mode
  - mode-lock capability
- defined locked-build behavior so unsupported mode-switch requests are rejected before sending
- added a new FTC wrapper example in [FtcBlinkinLedDriver.java](/Users/kshlok/Downloads/Blinkin-Firmware-Custom/plan/FtcBlinkinLedDriver.java)
- added developer-facing documentation and Java usage examples in [README.md](/Users/kshlok/Downloads/Blinkin-Firmware-Custom/README.md)

### Implemented API decisions
- normalized API:
  - uses semantic 5V/12V methods instead of exposing the duplicated strip-mode slots directly
  - uses typed pattern enums for live-pattern and default-pattern methods
  - uses a blend enum for blend control
  - keeps color writes numeric, but validates them to the firmware-supported `78-99` range
- raw API:
  - remains public as an advanced compatibility layer
  - supports both symbolic raw-slot enums and numeric slot access
  - preserves duplicate raw strip-mode slots `1/2` and `3/4`
  - validates only the transport-level `0-99` payload range
- command execution:
  - offers both synchronous and asynchronous forms
  - hides the two-pulse sequence behind one call
  - restores the last known live pattern after command transmission so continuous FTC PWM does not leave the payload repeating as a normal pattern
  - falls back to the firmware's default no-signal pattern if no live pattern has been set yet
- capability reporting:
  - exposes that readback does not exist yet
  - exposes whether raw commands and mode switching are supported
  - exposes that `disableOutput()` is not reversible without a mode change on current firmware
  - treats setup mode as a host-tracked precondition that rejects command writes when marked active

## Section 2 — Read values back from the firmware

Status: incomplete

### Outcome
Define a readback strategy that FTC code can rely on, while keeping both transport options open until implementation time.

### Related methods and facts
- The firmware is currently receive-only on the control input pin.
- No existing serial, I2C, SPI, or other host-readable status path exists in the codebase.
- Incoming pulse width is measured in `ISRfalling()` as local variable `pwm_value`.
- Current runtime state already exists in globals such as:
  - `addressableStrip`
  - `currentPattern`
  - `noSignalPatternDisplay`
  - `COLOR1`
  - `COLOR2`
  - `currentBlending`
  - `cmdDisableOutput`
  - `inSetup`
  - `noSignal`
- EEPROM-backed defaults are handled by `saveDefaults()` and `initEEPROM()`.

### Planned work
- keep both implementation paths open for now:
  - PWM remains primary, with minimal readback if hardware allows it
  - PWM remains compatible, but a new bidirectional interface carries readback and optional writes
- inventory the state that should be readable:
  - current mode
  - current pattern
  - default pattern
  - color1 / color2
  - blend mode
  - output-disabled flag
  - setup / no-signal flags
  - low-level diagnostics such as last PWM ticks and last decoded command
- identify which fields need new cached state or ISR-safe access rules
- leave the final register/API shape as a later decision tied to the chosen transport

## Section 3 — Hard-locked 5V and 12V variants

Status: incomplete

### Outcome
Prepare the codebase so 5V-only and 12V-only firmware variants can be created later without scattered one-off edits.

### Related methods and facts
- `addressableStrip` is the runtime mode flag:
  - `true` = 5V/addressable behavior
  - `false` = 12V/analog behavior
- Mode changes currently flow through:
  - `toggleStripSelect()` / `setStripSelect()`
  - button handling in [UserIO.ino](/Users/kshlok/Downloads/Blinkin-Firmware-Custom/UserIO.ino)
  - PWM commands in [PWM_0_Command.ino](/Users/kshlok/Downloads/Blinkin-Firmware-Custom/PWM_0_Command.ino)
  - EEPROM reload in `initEEPROM()`
- [README.md](/Users/kshlok/Downloads/Blinkin-Firmware-Custom/README.md) currently implies these locked branches already exist.

### Planned work
- trace every path that can change `addressableStrip`
- centralize strip-mode policy before any branch work starts
- define locked variants so unsupported mode-switch requests become no-ops that preserve the fixed mode
- keep readback truthful so the host can still learn the fixed mode
- update documentation so the repo stops implying these branches already exist

## Section 4 — FTC Java wrapper / configurable hardware device

Status: incomplete

### Outcome
Replace the example driver with a real FTC-facing API that matches the eventual firmware interface.

### Related methods and facts
- The repository already includes a reference implementation in [RevBlinkinLedDriver.java](/Users/kshlok/Downloads/Blinkin-Firmware-Custom/plan/RevBlinkinLedDriver.java).
- The example class:
  - implements `HardwareDevice`
  - uses `@DeviceProperties`
  - uses `@ServoType`
  - depends on `ServoControllerEx`
  - maps enum ordinals to servo pulse widths for pattern writes
- The current example does not expose command sequencing, readback, or locked-mode capability handling.

### Planned work
- keep the current file as prior art for configuration annotations and PWM pattern mapping
- treat the wrapper as both:
  - a short-term maintained artifact/example in this repository
  - code intended to be copied into a separate FTC app once the interface settles
- define the wrapper around the final transport rather than forcing the transport to follow the wrapper
- include a minimum API for:
  - pattern selection
  - command writes
  - mode reads
  - color reads/writes
  - default-pattern reads/writes
  - blend-mode reads/writes
  - capability reporting for locked variants
- ensure the device remains discoverable from `hardwareMap.get()`
- include a sample configuration and OpMode usage flow once the final wrapper shape is chosen

## Shared work

- update [README.md](/Users/kshlok/Downloads/Blinkin-Firmware-Custom/README.md) to match the real repository state and final integration design
- keep the plan sectioned because each area can be implemented independently and at different times
- avoid promising a branch layout or transport API until that section is actively selected for implementation
- center validation on manual hardware checks and documented protocol test cases rather than assuming automated repo-local tests

## Todo list

- `section-1-command-surface`: Finalize the command-surface inventory for Section 1
- `section-2-readback-options`: Keep both readback transport options open in Section 2
- `section-3-locked-variants`: Inventory every strip-mode mutation point for Section 3
- `section-4-ftc-wrapper`: Define the FTC wrapper surface for Section 4 without locking transport too early
- `shared-docs`: Update docs only after the relevant section design is settled

## Notes and considerations

- Section 2 is the main architecture fork, so it remains intentionally open.
- The example Java driver should be treated as prior art, not a finished solution.
- Branch creation should happen only after strip-mode policy is centralized enough to keep locked variants small and maintainable.
- Documentation must clearly distinguish:
  - stock PWM pattern selection
  - command-mode writes
  - any future readback/register interface
