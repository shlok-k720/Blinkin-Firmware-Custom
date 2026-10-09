# REV Blinkin LED Driver

# This is the custom firmware for the REV Blinkin LED Driver. 
This repository currently has a single `master` branch. Hard-locked `5v` and `12v` firmware variants are planned work, not branches that already exist.


## Getting Started

**Important Note:** Opening this device or using modified firmware *may or may not* change the legality for use in robotics competitions. Please refer to the rules of the specific competition you are using this device in before making modifications.

**Important Note:** Uploading custom code to the Blinkin requires opening the device. Please note that opening the device to upload code will void the warranty. REV Robotics is not liable for damage that may occur due to device modifications. Use at your own risk. For more details, please visit the REV Robotics [Warranty Page](http://www.revrobotics.com/warranty-and-returns/).

### Getting the board ready

This guide will use the programmer built into the Arduino Uno to target the Blinkin board. Be careful when following these instructions and wiring the device.

- Follow the diagram below to carefully wire the Blinkin board into an Arduino Uno
- Remove the main chip from the socket of the Arduino. This will cause the built-in Arduino programmer to target the Blinkin instead.
- Apply 12V to the Blinkin with an XT30 cable
- Plug the Arduino UNO into your computer with a USB-A to USB-B cable

<img src="https://github.com/REVrobotics/Blinkin-Firmware/blob/master/images/Wiring%20Diagram.png" width="720" height="405" />

### Setting up the Arduino IDE

- Download the [Arduino IDE](https://www.arduino.cc/en/Main/Software)
- Install and open the IDE
- In the **Tools** tab, select "Arduino/Genuino Uno" under **Board**

<img src="https://github.com/REVrobotics/Blinkin-Firmware/blob/master/images/Tools-Board%20Screenshot.png" width="720" height="405" />

- Again in the **Tools** tab, select the port the Arduino is located at

<img src="https://github.com/REVrobotics/Blinkin-Firmware/blob/master/images/Tools-Port%20Screenshot.png" width="720" height="405" />

### Uploading the Blinkin firmware to the board

- Clone or download **Blinkin-Firmware-master.zip** from this GitHub repository
- Extract the contents into a folder named **Blinkin-Firmware**
- Open **Blinkin-Firmware.ino** in the Arduino IDE
- Download [FastLED-master.zip](https://github.com/FastLED/FastLED) and [CircularBuffer-master.zip](https://github.com/rlogiacco/CircularBuffer)
- Under **Include Library**, located in the **Sketch** tab, click "Add .ZIP Library..."

<img src="https://github.com/REVrobotics/Blinkin-Firmware/blob/master/images/Sketch-Include%20Library%20Screenshot.png" width="720" height="405" />

- Locate **FastLED-master.zip** and **CircularBuffer-master.zip** and add both of them
- Press the **Upload** button at the top of the IDE to load the firmware onto the board

<img src="https://github.com/REVrobotics/Blinkin-Firmware/blob/master/images/Upload%20Screenshot.png" width="720" height="405" />

### Using the preset patterns

- Plug an LED strip into the Blinkin with the Blinkin LED cable adapter to see the output
- Hold the **Mode** button on the Blinkin board to enable setup mode
- Use the **Mode** and **Strip Select** buttons to cycle through the preset patterns
- Turn the left two potentiometers on the Blinkin to adjust the preset team colors (Used in patterns 49 through 79)
- Turn the rightmost potentiometer to adjust the length of the LED strip
- A list of the preset patterns can either be found in **Blinkin-Firmware.ino** or [here](http://www.revrobotics.com/content/docs/REV-11-1105-UM.pdf)

## FTC Java command surface

Section 1 is now implemented as a Java-side wrapper example in [FtcBlinkinCommandDriver.java](./plan/FtcBlinkinCommandDriver.java). It keeps stock PWM pattern writes available while adding a complete command API for the custom firmware's existing `gCommands[]` command surface.

### PWM protocol summary

- Normal pattern selection uses approximately **1.00 ms to 2.00 ms** pulses (`2000-4000` timer ticks at `0.5 us/tick`)
- Command entry uses approximately **2.10 ms to 2.20 ms** pulses (`4200-4400` timer ticks)
- After a command-entry pulse, the **next normal-width pulse** becomes the command payload and is decoded to `0-99`
- Pulses outside those windows are ignored by the firmware
- Command-entry pulses are ignored while the firmware is in setup mode

### Raw command slots

| Slot | Firmware handler | Meaning | Payload rules |
|---|---|---|---|
| 0 | `cmdNoStrip` | Disable output | Payload ignored |
| 1 | `cmd5VStrip` | Switch to 5V mode | Payload ignored |
| 2 | `cmd5VStrip` | Switch to 5V mode | Payload ignored |
| 3 | `cmd12VStrip` | Switch to 12V mode | Payload ignored |
| 4 | `cmd12VStrip` | Switch to 12V mode | Payload ignored |
| 5 | `cmdChangeColor1` | Update Color 1 | Use payload `78-99` |
| 6 | `cmdChangeColor2` | Update Color 2 | Use payload `78-99` |
| 7 | `cmdChangeDefaultPattern` | Update the no-signal default pattern | Use payload `0-99` |
| 8 | `cmdSetLinearBlend` | Set linear blend | Payload ignored |
| 9 | `cmdSetNoBlend` | Set no blend | Payload ignored |

### Normalized Java API behavior

- The wrapper exposes semantic `set5VMode()` and `set12VMode()` methods even though firmware slots `1/2` and `3/4` are duplicates
- The wrapper also keeps a raw API for advanced callers that need symbolic or numeric access to the exact firmware slot numbers
- Color commands stay numeric by design, but the normalized API rejects values outside `78-99` instead of relying on the firmware's coercion behavior
- `setDefaultPattern(...)` accepts only the 100 defined pattern enums
- Blend control uses a `BlendMode` enum
- Locked-mode builds are modeled by capability metadata and reject unsupported mode-switch calls before transmission
- `disableOutput()` remains sticky because the current firmware has no dedicated enable command

### Important FTC-specific transmission detail

FTC servo ports emit continuous PWM. A one-shot command cannot safely leave the servo output sitting on the command payload pulse, because the firmware would see later pulses as ordinary pattern writes after command mode exits. The wrapper therefore:

1. sends the command-entry pulse
2. sends the payload pulse
3. restores the last known live pattern pulse

If the wrapper has not been told or has not previously set a live pattern, it restores the firmware's current compile-time no-signal default pattern (`pattern 28`).

### Setup-mode guard

The firmware has no readback yet, so the wrapper cannot detect setup mode on its own. The Java API therefore tracks setup mode as host-assumed state:

```java
driver.setAssumedSetupMode(true);   // command writes now reject locally
driver.setAssumedSetupMode(false);  // command writes allowed again
```

### Example usage

```java
FtcBlinkinCommandDriver driver = hardwareMap.get(FtcBlinkinCommandDriver.class, "blinkin");

driver.setPattern(FtcBlinkinCommandDriver.Pattern.RAINBOW_PARTY).throwIfFailure();
driver.setColor1(78).throwIfFailure(); // first firmware-supported solid-color payload
driver.setBlendMode(FtcBlinkinCommandDriver.BlendMode.LINEAR).throwIfFailure();
driver.setDefaultPattern(FtcBlinkinCommandDriver.Pattern.COLOR_WAVES_PARTY).throwIfFailure();
```

### Raw command examples

```java
driver.sendRawCommand(FtcBlinkinCommandDriver.RawCommandSlot.SET_5V_MODE_SECONDARY, 0)
        .throwIfFailure();

driver.sendRawCommand(6, 99).throwIfFailure(); // raw slot 6 = Color 2, payload 99
```

### Capability inspection

```java
FtcBlinkinCommandDriver.Capabilities capabilities = driver.getCapabilities();

if (!capabilities.modeSwitchingSupported()) {
    // Future locked build: do not offer 5V/12V switching controls.
}

if (!capabilities.outputReenableWithoutModeChange()) {
    // Current firmware has no dedicated enable-output command.
}
```

## Editing the Firmware

### Using the Serial Monitor

- In **Blinkin-Firmware.ino**, add `Serial.begin(115200);` in setup()

<img src="https://github.com/REVrobotics/Blinkin-Firmware/blob/master/images/Serial-begin%20Screenshot.png" width="720" height="405" />

- Use `Serial.print();` or `Serial.println();` anywhere in the code to print to the Serial Monitor
- Press the **Serial Monitor** button at the top right corner of the IDE to open the Serial Monitor

<img src="https://github.com/REVrobotics/Blinkin-Firmware/blob/master/images/Serial%20Monitor%20Screenshot.png" width="720" height="405" />

- At the bottom right corner of the Serial Monitor, set the baud rate to 115200

<img src="https://github.com/REVrobotics/Blinkin-Firmware/blob/master/images/Serial%20Monitor-baud%20Screenshot.png" width="720" height="405" />

#### Seeing what pattern you cycle to

- Open **UserIO.ino**
- Add `Serial.println(noSignalPatternDisplay);` at the locations in the screenshots below

<img src="https://github.com/REVrobotics/Blinkin-Firmware/blob/master/images/Print%20Cycle%20Screenshot_1.png" width="720" height="405" />
<img src="https://github.com/REVrobotics/Blinkin-Firmware/blob/master/images/Print%20Cycle%20Screenshot_2.png" width="720" height="405" />

### Creating a custom function

- In **Blinkin-Firmware.ino**, there is an array of functions named `gPatterns`
- Change the name of an existing function in this list of functions

<img src="https://github.com/REVrobotics/Blinkin-Firmware/blob/master/images/gPatterns%20Change%20Function%20Screenshot.png" width="720" height="405" />

- Then, to modify what it does, open the **.ino** file the function is located in (For this example, open **PWM_1_Standard.ino**)
- Now find the function that was renamed, and change its name to the new one here
- Inside the brackets next to the function is what runs when the function is selected
- Delete the contents in the brackets and add a new method

<img src="https://github.com/REVrobotics/Blinkin-Firmware/blob/master/images/PWM_1_Standard%20Change%20Function%20Screenshot.png" width="720" height="405" />

- Below, create the new method with the desired parameters and create what the function does inside the brackets
- In this example, the method makes the LED strip blink rapidly between two colors, similar to the strobe function

<img src="https://github.com/REVrobotics/Blinkin-Firmware/blob/master/images/Add%20Method%20Screenshot.png" width="720" height="405" />

### Creating a custom palette

- Locate the folder your Arduino libraries are saved in (usually C:\Users\Username\Documents\Arduino\libraries)
- In FastLED-master, open **colorpalettes.cpp** and **colorpalettes.h**
- Add the definition for the new color palette in **colorpalettes.h** by adding `extern const TProgmemRGBPalette16 CustomPalette_p FL_PROGMEM;`

<img src="https://github.com/REVrobotics/Blinkin-Firmware/blob/master/images/Palette%20Definition%20Screenshot.png" width="720" height="405" />

- Create the palette in **colorpalettes.cpp** by adding `extern const TProgmemRGBPalette1 CustomPalette_p FL_PROGMEM = { };`, putting the desired colors in the brackets
- In this example, the palette cycles through white, red, blue, green, and yellow, then blacks out the LEDs before starting the cycle over

<img src="https://github.com/REVrobotics/Blinkin-Firmware/blob/master/images/Custom%20Palette%20Screenshot.png" width="720" height="405" />

- Save the two files and add the palettes into the Arduino code

<img src="https://github.com/REVrobotics/Blinkin-Firmware/blob/master/images/Use%20Custom%20Palette%20Screenshot.png" width="720" height="405" />
