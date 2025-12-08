# PicNet Binding

This binding integrates PicNet home automation systems with openHAB using the Sapp protocol over TCP/IP.

PicNet is a modular home automation system that uses a centralized controller to manage input/output modules, virtual addresses, and various smart home devices.

## Supported Things

This binding supports the following thing types:

- `bridge` - PicNet Bridge: Main connection to the PicNet system via Sapp protocol
- `virtual` - Virtual Address: Read/write virtual addresses (1-2500) with full control over word values, bytes, and individual bits
- `input` - Input Module: Read-only access to input module states (1-250) with word values, bytes, and individual bits as contacts
- `output` - Output Module: Read-only access to output module states (1-250) with word values, bytes, and individual bits as contacts
- `light` - Light Control: Individual light control that reads status from Input/Output/Virtual and writes commands to Virtual addresses
- `gate` - Gate Control: Gate/door control with optional status reading and pulse mode for momentary triggers
- `alarm` - Alarm Sensor: Read-only access to alarm status (1-255) using batch-optimized Sapp72Command
- `alarm-group` - Alarm Group: Master alarm monitoring a range of alarms (1-255), reports if ANY alarm is triggered

## Discovery

Auto-discovery is not currently supported.
All things must be manually configured either through the UI or via configuration files.

## Bridge Configuration

The PicNet Bridge establishes the TCP connection to your PicNet controller using the Sapp protocol.

### Bridge Parameters

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| hostname | text | yes | - | Hostname or IP address of the PicNet device |
| port | integer | yes | 7001 | TCP port of the PicNet device |
| refreshInterval | integer | no | 1000 | Polling interval in milliseconds (100-60000) |

## Thing Configuration

### Virtual Address (`virtual`)

Virtual addresses are read/write memory locations in the PicNet system (addresses 1-2500).
They can store 16-bit values and are used for internal logic and light control.

#### Configuration

| Parameter | Type | Required | Range | Description |
|-----------|------|----------|-------|-------------|
| address | integer | yes | 1-2500 | Virtual address number |

#### Channels

| Channel ID | Type | Read/Write | Description |
|------------|------|------------|-------------|
| word | Number | RW | Full 16-bit word value (0-65535) |
| signedWord | Number | RW | Signed 16-bit value (-32768 to 32767) |
| highByte | Number | R | High byte (0-255) |
| lowByte | Number | R | Low byte (0-255) |
| highByteSigned | Number | R | High byte signed (-128 to 127) |
| lowByteSigned | Number | R | Low byte signed (-128 to 127) |
| bit1 to bit16 | Switch | RW | Individual bit control (1-16) |

### Input Module (`input`)

Input modules represent physical input modules in the PicNet system (addresses 1-250).
They are read-only and typically connected to sensors, buttons, or other input devices.

#### Configuration

| Parameter | Type | Required | Range | Description |
|-----------|------|----------|-------|-------------|
| address | integer | yes | 1-250 | Input module address |

#### Channels

| Channel ID | Type | Read/Write | Description |
|------------|------|------------|-------------|
| word | Number | R | Full 16-bit word value (0-65535) |
| signedWord | Number | R | Signed 16-bit value (-32768 to 32767) |
| highByte | Number | R | High byte (0-255) |
| lowByte | Number | R | Low byte (0-255) |
| highByteSigned | Number | R | High byte signed (-128 to 127) |
| lowByteSigned | Number | R | Low byte signed (-128 to 127) |
| bit1 to bit16 | Contact | R | Individual bit status as contact (inverted by default) |

### Output Module (`output`)

Output modules represent physical output modules in the PicNet system (addresses 1-250).
They are read-only from openHAB perspective and typically control relays, lights, or other actuators.
The actual control is done through Virtual addresses.

#### Configuration

| Parameter | Type | Required | Range | Description |
|-----------|------|----------|-------|-------------|
| address | integer | yes | 1-250 | Output module address |

#### Channels

| Channel ID | Type | Read/Write | Description |
|------------|------|------------|-------------|
| word | Number | R | Full 16-bit word value (0-65535) |
| signedWord | Number | R | Signed 16-bit value (-32768 to 32767) |
| highByte | Number | R | High byte (0-255) |
| lowByte | Number | R | Low byte (0-255) |
| highByteSigned | Number | R | High byte signed (-128 to 127) |
| lowByteSigned | Number | R | Low byte signed (-128 to 127) |
| bit1 to bit16 | Contact | R | Individual bit status as contact (inverted by default) |

### Light (`light`)

The Light thing provides a simple ON/OFF switch control for individual lights.
It reads the light status from an Input/Output/Virtual address and writes control commands to a Virtual address.

#### Configuration

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| readType | text | yes | output | Type of address to read status from: `input`, `output`, or `virtual` |
| readAddress | integer | yes | - | Address to read the light status from (1-250 for Input/Output, 1-2500 for Virtual) |
| readBit | integer | yes | 1 | Bit number to read light status from (1-16) |
| virtualAddress | integer | yes | - | Virtual address to write light commands to (1-2500) |
| writeBit | integer | yes | 1 | Bit number to write light commands to (1-16) |
| pulseMode | boolean | no | false | Send a pulse (1) for both ON and OFF commands, like a momentary push button |

#### Channels

| Channel ID | Type | Description |
|------------|------|-------------|
| switch | Switch | Turn the light on or off |

### Gate (`gate`)

The Gate thing provides control for gates, doors, or similar devices.
It can **optionally** read status from an Input/Output/Virtual address and writes trigger commands to a Virtual address.
If status reading is not configured, it operates in **command-only mode** (useful when you only need to trigger the gate without feedback).

Typically uses **pulse mode** (momentary trigger) which is ideal for gate controllers.

#### Configuration

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| readType | text | no | (empty) | Type of address to read status from: `input`, `output`, `virtual`, or empty for command-only |
| readAddress | integer | no | 0 | Address to read the gate status from (1-250 for Input/Output, 1-2500 for Virtual, 0 if not used) |
| readBit | integer | no | 1 | Bit number to read gate status from (1-16) |
| virtualAddress | integer | yes | - | Virtual address to write gate commands to (1-2500) |
| writeBit | integer | yes | 1 | Bit number to write gate commands to (1-16) |
| pulseMode | boolean | no | true | Send a momentary pulse for gate trigger (typical for gates) |

#### Channels

| Channel ID | Type | Description |
|------------|------|-------------|
| switch | Switch | Trigger the gate (ON command triggers the gate) |

#### Use Cases

- **Command-only gate**: Leave `readType` empty - only sends trigger commands without status feedback
- **Gate with status**: Configure `readType`, `readAddress`, and `readBit` to monitor gate position/status
- **Pulse trigger**: Default `pulseMode=true` sends momentary pulse (set bit, wait 200ms, clear bit)
- **Toggle mode**: Set `pulseMode=false` for persistent ON/OFF control

### Alarm (`alarm`)

The Alarm thing provides read-only access to alarm sensors in the PicNet system.
Alarm status is read using **Sapp72Command** for batch efficiency (up to 32 alarms per request) and represented as a Contact channel.

#### Configuration

| Parameter | Type | Required | Range | Description |
|-----------|------|----------|-------|-------------|
| alarmNumber | integer | yes | 1-255 | Alarm number to monitor |

#### Channels

| Channel ID | Type | Read/Write | Description |
|------------|------|------------|-------------|
| alarm | Contact | R | Alarm status (CLOSED=OK, OPEN=TRIGGERED) |

### Alarm Group (`alarm-group`)

The Alarm Group thing provides a master alarm that monitors a **range** of alarms and reports if **ANY** alarm in that range is triggered.
This is useful for monitoring the overall system status with a single channel.
Alarm statuses are read using **Sapp72Command** for batch efficiency.

#### Configuration

| Parameter | Type | Required | Range | Description |
|-----------|------|----------|-------|-------------|
| startAlarm | integer | yes | 1-255 | First alarm number to monitor |
| endAlarm | integer | yes | 1-255 | Last alarm number to monitor |

#### Channels

| Channel ID | Type | Read/Write | Description |
|------------|------|------------|-------------|
| master-alarm | Contact | R | Master alarm status (CLOSED=all OK in range, OPEN=at least one triggered) |

#### Use Cases

- **Full System Monitoring**: Monitor all 255 alarms with `startAlarm=1, endAlarm=255`
- **Floor/Zone Monitoring**: Monitor specific zones (e.g., `startAlarm=1, endAlarm=32` for first floor)
- **Critical Alarms**: Monitor only critical alarms (e.g., `startAlarm=200, endAlarm=210`)
- **Multiple Groups**: Create multiple alarm groups for different areas

## Full Example

### Thing Configuration

```java
Bridge picnet:bridge:controller "PicNet Controller" [ hostname="192.168.1.100", port=7001, refreshInterval=1000 ] {
    // Virtual addresses for light control
    Thing virtual virtual200 "Living Room Light Virtual" [ address=200 ]
    Thing virtual virtual201 "Kitchen Light Virtual" [ address=201 ]

    // Input modules
    Thing input input1 "Main Input Module" [ address=1 ]
    Thing input input2 "Second Floor Inputs" [ address=2 ]

    // Output modules
    Thing output output1 "Main Output Module" [ address=1 ]
    Thing output output2 "Second Floor Outputs" [ address=2 ]

    // Individual lights
    Thing light livingRoomLight "Living Room Light" [
        readType="output",
        readAddress=1,
        readBit=1,
        virtualAddress=200,
        writeBit=1,
        pulseMode=false
    ]

    Thing light kitchenLight "Kitchen Light" [
        readType="output",
        readAddress=1,
        readBit=2,
        virtualAddress=201,
        writeBit=1,
        pulseMode=true
    ]

    // Gates
    Thing gate mainGate "Main Gate" [
        readType="input",
        readAddress=3,
        readBit=1,
        virtualAddress=300,
        writeBit=1,
        pulseMode=true
    ]

    Thing gate garageGate "Garage Gate (command only)" [
        virtualAddress=301,
        writeBit=1,
        pulseMode=true
    ]

    // Alarm sensors
    Thing alarm frontDoorAlarm "Front Door Alarm" [ alarmNumber=1 ]
    Thing alarm motionAlarm "Motion Detector Alarm" [ alarmNumber=2 ]
    Thing alarm fireAlarm "Fire Alarm" [ alarmNumber=3 ]

    // Alarm groups
    Thing alarm-group masterAlarm "Master Alarm - All System" [ startAlarm=1, endAlarm=255 ]
    Thing alarm-group firstFloorAlarms "First Floor Alarms" [ startAlarm=1, endAlarm=50 ]
    Thing alarm-group criticalAlarms "Critical Alarms Only" [ startAlarm=200, endAlarm=210 ]
}
```

### Item Configuration

```java
// Light switches
Switch LivingRoomLight "Living Room" { channel="picnet:light:controller:livingRoomLight:switch" }
Switch KitchenLight "Kitchen" { channel="picnet:light:controller:kitchenLight:switch" }

// Gates
Switch MainGate "Main Gate" { channel="picnet:gate:controller:mainGate:switch" }
Switch GarageGate "Garage Gate" { channel="picnet:gate:controller:garageGate:switch" }

// Virtual address - full word control
Number Virtual200_Word "Virtual 200 Word" { channel="picnet:virtual:controller:virtual200:word" }
Switch Virtual200_Bit1 "Virtual 200 Bit 1" { channel="picnet:virtual:controller:virtual200:bit1" }
Switch Virtual200_Bit2 "Virtual 200 Bit 2" { channel="picnet:virtual:controller:virtual200:bit2" }

// Input module - sensors
Contact FrontDoorSensor "Front Door [MAP(contact.map):%s]" { channel="picnet:input:controller:input1:bit1" }
Contact MotionSensor "Motion Detected [MAP(contact.map):%s]" { channel="picnet:input:controller:input1:bit2" }
Number Input1_Word "Input 1 Full Word" { channel="picnet:input:controller:input1:word" }

// Output module - status monitoring
Contact RelayStatus "Main Relay [MAP(contact.map):%s]" { channel="picnet:output:controller:output1:bit1" }
Number Output1_HighByte "Output 1 High Byte" { channel="picnet:output:controller:output1:highByte" }

// Alarm sensors
Contact FrontDoorAlarm "Front Door Alarm [MAP(alarm.map):%s]" { channel="picnet:alarm:controller:frontDoorAlarm:alarm" }
Contact MotionAlarm "Motion Detector [MAP(alarm.map):%s]" { channel="picnet:alarm:controller:motionAlarm:alarm" }
Contact FireAlarm "Fire Alarm [MAP(alarm.map):%s]" { channel="picnet:alarm:controller:fireAlarm:alarm" }

// Alarm groups
Contact MasterAlarm "System Master Alarm [MAP(alarm.map):%s]" { channel="picnet:alarm-group:controller:masterAlarm:master-alarm" }
Contact FirstFloorAlarm "First Floor Status [MAP(alarm.map):%s]" { channel="picnet:alarm-group:controller:firstFloorAlarms:master-alarm" }
Contact CriticalAlarm "Critical Systems [MAP(alarm.map):%s]" { channel="picnet:alarm-group:controller:criticalAlarms:master-alarm" }
```

### Sitemap Configuration

```perl
sitemap picnet label="PicNet Control" {
    Frame label="Lights" {
        Switch item=LivingRoomLight
        Switch item=KitchenLight
    }

    Frame label="Gates" {
        Switch item=MainGate icon="gate"
        Switch item=GarageGate icon="garage"
    }

    Frame label="Sensors" {
        Text item=FrontDoorSensor
        Text item=MotionSensor
    }

    Frame label="Alarms" {
        Text item=MasterAlarm icon="alarm" valuecolor=[OPEN="red", CLOSED="green"]
        Text item=FrontDoorAlarm
        Text item=MotionAlarm
        Text item=FireAlarm
    }

    Frame label="Alarm Groups" {
        Text item=FirstFloorAlarm
        Text item=CriticalAlarm
    }

    Frame label="Virtual Addresses" {
        Setpoint item=Virtual200_Word minValue=0 maxValue=65535 step=1
        Switch item=Virtual200_Bit1
        Switch item=Virtual200_Bit2
    }

    Frame label="Status" {
        Text item=RelayStatus
        Text item=Output1_HighByte
    }
}
```

## Features

### Automatic Reconnection

The binding automatically reconnects to the PicNet controller if the connection is lost.
Reconnection attempts are made every 30 seconds until the connection is restored.

### Batch Reading Optimization

For better performance, the binding uses batch reading when polling consecutive Virtual addresses.
This reduces the number of network requests and improves overall system responsiveness.

### Bridge Status Propagation

When the bridge goes offline, all child things (Virtual, Input, Output, Light) automatically update their status to OFFLINE with the BRIDGE_OFFLINE detail.
When the bridge comes back online, child things automatically reinitialize and go back online.

### Pulse Mode for Lights

The Light thing supports pulse mode, which sends a momentary pulse (set bit to 1, then back to 0) for both ON and OFF commands.
This is useful for controlling lights that use toggle switches or require a pulse to change state.

## Technical Details

### Protocol

This binding uses the Sapp protocol over TCP/IP to communicate with PicNet controllers.
The implementation is based on the [jsapp-core](https://github.com/paolodenti/jsapp-core) library.

### Polling

The binding polls the PicNet controller at the configured refresh interval (default 1000ms).
Polling is optimized to minimize network traffic:

- Virtual addresses: Batch reading of consecutive addresses (up to 250 addresses per request)
- Light things: Grouped by read address to avoid duplicate reads
- Input/Output modules: Individual reads per module
- Alarm sensors: Batch reading of up to 32 consecutive alarms per request using Sapp72Command

### Address Ranges

- Virtual addresses: 1-2500
- Input module addresses: 1-250
- Output module addresses: 1-250
- Alarm numbers: 1-255
- Bit positions: 1-16 (for 16-bit words)

## Support

For issues, questions, or contributions, please visit the [openHAB Community Forum](https://community.openhab.org/).
