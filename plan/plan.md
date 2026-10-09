# Blinkin customization plan

## Problem and proposed approach

Add a structured customization layer around the REV Blinkin firmware so FTC Java code can:
- send every supported command intentionally and predictably
- read device state back from the Blinkin over a bidirectional interface
- use hard-locked 5V and 12V firmware variants that cannot switch modes at runtime
- integrate through a custom FTC Java hardware wrapper that is configurable and discoverable from `hardwareMap.get()`

The implementation should preserve existing pattern behavior where possible, remove unsafe/random mode-switch paths where requested, and document both firmware and FTC SDK integration.

## Section 1 — Expose all commands to FTC Java (incomplete)

Status: incomplete

### Goal
Expose every current firmware command that is not already conveniently represented in the FTC Java side, and document exactly how Java code should send them.

### Related methods and facts
- `gCommands[]` in `BlinkinFirmware.ino` currently exposes 10 command slots:
  - `cmdNoStrip`
  - `cmd5VStrip`
  - `cmd5VStrip` (duplicate slot)
  - `cmd12VStrip`
  - `cmd12VStrip` (duplicate slot)
  - `cmdChangeColor1`
  - `cmdChangeColor2`
  - `cmdChangeDefaultPattern`
  - `cmdSetLinearBlend`
  - `cmdSetNoBlend`
- Commands are decoded in `ISRfalling()` in `interrupt.ino`
- Command mode is entered by a pulse-width window around 4200–4400 timer ticks, then the next normal pulse is used as the command payload
- Pattern selection is decoded from 2000–4000 timer ticks and mapped to pattern IDs 0–99
- `cmdChangeColor1` and `cmdChangeColor2` currently map payload values 78–99 into color indexes 0–21
- `cmdChangeDefaultPattern` writes to `noSignalPatternDisplay`
- Voltage mode commands currently call `setStripSelect(true/false)`
- The stock/example FTC Java file in `plan/RevBlinkinLedDriver.java` only exposes pattern writes via servo PWM and does not expose command mode helpers

### Planned edits
- Audit and document the exact pulse protocol required to issue each command from FTC Java
- Decide whether command writes remain PWM-based or are additionally surfaced over the new bidirectional interface
- Extend the Java wrapper with explicit methods for every safe command:
  - disable output
  - set color1/color2
  - set default pattern
  - set blend mode
  - set voltage mode only on branches where it is allowed
- Add developer-facing documentation showing:
  - command IDs
  - payload expectations
  - any color-index mapping quirks
  - Java usage examples
- Update README content that currently implies branches already exist

## Section 2 — Read values back from Blinkin firmware (incomplete)

Status: incomplete

### Goal
Design and implement a robust telemetry/readback interface so FTC code can query state such as current mode, current pattern, colors, default pattern, flags, and possibly last raw PWM input.

### Related methods and facts
- The current firmware is effectively receive-only on control input pin D2
- No existing serial, I2C, SPI, or other host-readable status path exists in this codebase
- Incoming pulse width is measured in `ISRfalling()` as local variable `pwm_value`
- The raw input pulse width is not currently persisted globally
- Current decoded runtime state already exists in globals such as:
  - `addressableStrip`
  - `currentPattern`
  - `noSignalPatternDisplay`
  - `COLOR1`
  - `COLOR2`
  - `currentBlending`
  - `cmdDisableOutput`
  - `inSetup`
  - `noSignal`
- Stable pattern resolution happens in `ledUpdate()`
- Defaults are stored in EEPROM via:
  - `SS_EE`
  - `COLOR1_EE`
  - `COLOR2_EE`
  - `LED_EE`
  - `PATTERN_EE`
- `saveDefaults()` and `initEEPROM()` handle persisted configuration
- The likely clean FTC-side read path is a register-style interface consumed from Java, potentially via `I2cDeviceSynchSimple`

### Planned edits
- Compare and document two transport options for readback/control:
  - I2C as the primary bidirectional interface, with PWM retained only for compatibility
  - PWM retained as the primary control path, with minimal added readback
- Define a versioned register map for:
  - firmware version
  - mode
  - current pattern
  - default/no-signal pattern
  - color1/color2
  - blend mode
  - flags
  - optional raw PWM ticks / last command / strip length
- Add firmware-side state capture for values not currently stored, especially raw input measurements if needed
- Implement firmware handlers for register reads and, if approved, selective writes
- Document threading/ISR safety for shared state read from interrupt context
- Define Java-side polling/access semantics and error handling
- Document hardware assumptions and board-pin constraints needed to make a bidirectional interface physically possible

## Section 3 — Hard-locked 5V and 12V branches (incomplete)

Status: incomplete

### Goal
Create real `5v` and `12v` branches that hardcode the voltage mode so it cannot be changed, while preserving all other supported behaviors and allowing mode getters/readback to continue working.

### Related methods and facts
- `addressableStrip` is the runtime mode flag
  - `true` means 5V/addressable behavior
  - `false` means 12V/analog behavior
- `toggleStripSelect()` actively flips `addressableStrip`
- `setStripSelect(bool)` calls `toggleStripSelect()` when a change is requested
- Physical long-press switching happens in `buttonHandler()`
- PWM command switching happens through:
  - `cmd5VStrip`
  - `cmd12VStrip`
  - command slots 1–4 in `gCommands[]`
- EEPROM can reload mode from `SS_EE` in `initEEPROM()`
- Setup/startup indicator behavior depends on `addressableStrip`
- README currently claims `5v` and `12v` branches already exist, but they do not

### Planned edits
- Define a branch strategy:
  - shared mainline refactor first
  - then branch-specific hard-lock changes for `5v`
  - then branch-specific hard-lock changes for `12v`
- Refactor firmware so mode policy is centralized instead of scattered
- Ensure voltage setters become no-ops or explicit failures on hard-locked branches
- Preserve mode getters/readback so FTC Java can still learn the branch’s locked mode
- Remove or neutralize all mode-switch entry points on locked branches:
  - button path
  - PWM command path
  - EEPROM reload/write path for mode
  - direct setter behavior
- Update README and branch-specific documentation so the workflow matches the actual repository state

## Section 4 — FTC Java wrapper / configurable hardware device (incomplete)

Status: incomplete

### Goal
Create a Java wrapper class that integrates with the FTC SDK configuration system, is discoverable through `hardwareMap.get()`, and exposes the custom firmware capabilities cleanly from robot code.

### Related methods and facts
- The repository already includes a reference implementation in `plan/RevBlinkinLedDriver.java`
- The example class:
  - implements `HardwareDevice`
  - uses `@DeviceProperties`
  - uses `@ServoType`
  - depends on `ServoControllerEx`
  - maps enum ordinals to servo pulse widths for pattern writes
- The existing example is PWM-write oriented and does not expose:
  - command mode sequencing
  - readback/telemetry
  - branch-aware voltage restrictions
- The requested design should consider switching from a servo-only device abstraction to `I2cDeviceSynchSimple` if the firmware adds a bidirectional register interface
- The wrapper must remain configurable on REV hubs and usable through `hardwareMap.get()`

### Planned edits
- Decide whether the Java wrapper should be:
  - a servo/PWM driver with supplemental I2C companion access, or
  - a primary I2C device abstraction that also knows how to command patterns
- Include an explicit tradeoff comparison so the wrapper architecture follows the selected transport plan from Section 2
- Create a public API surface for:
  - pattern selection
  - command writes
  - state reads
  - mode getter
  - guarded mode setter
  - color setters/getters
  - default pattern getter/setter
  - blend mode getter/setter
- Add enums/constants mirroring firmware command IDs and register IDs
- Add branch-awareness or capability reporting so Java can disable unsupported setters on hard-locked firmware
- Provide example OpMode snippets and configuration instructions

## Todo list

- `section-1-command-surface`: Document and expose all command-mode features to FTC Java
- `section-2-readback-interface`: Design and implement a bidirectional firmware-to-FTC readback interface
- `section-3-locked-mode-branches`: Create real `5v` and `12v` branches with unswitchable mode behavior
- `section-4-java-wrapper`: Build a configurable FTC SDK wrapper for the custom firmware
- `shared-docs-and-compatibility`: Update README/docs and preserve compatibility behavior across firmware and Java surfaces

## Notes and considerations

- The highest-risk design choice is Section 2: the bidirectional transport and physical wiring assumptions drive both firmware and Java architecture
- The plan should explicitly compare the two interface strategies before implementation is approved:
  - I2C-primary, PWM-compatible
  - PWM-primary with minimal readback
- The example Java driver in `plan/RevBlinkinLedDriver.java` should be treated as prior art, not a finished solution
- Branch creation should happen only after the shared firmware surface is refactored enough to make the locked-mode variants small and maintainable
- Documentation must distinguish:
  - stock PWM pattern selection
  - command-mode writes
  - any new readback/register interface
