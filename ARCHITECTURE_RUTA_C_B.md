# 🏗️ Architecture - RUTA C+B Triple Strategy

**Technical architecture and component interaction diagrams**

---

## System Architecture Overview

### High-Level Component Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│                    Application Layer                                │
│  ┌─────────────────────┐          ┌──────────────────────┐          │
│  │  KeyReceiver App    │          │  Injector App        │          │
│  │  (Main Thread)      │          │  (Main Thread)       │          │
│  └──────────┬──────────┘          └──────────┬───────────┘          │
│             │                               │                       │
│             └───────────────┬───────────────┘                       │
└─────────────────────────────┼───────────────────────────────────────┘
                              │
                    ┌─────────▼────────┐
                    │  Aisino Manager  │
                    └─────────┬────────┘
                              │
┌─────────────────────────────┼───────────────────────────────────────┐
│                   Communication Layer                               │
│                                                                     │
│  AisinoComController (Main Orchestrator)                           │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │                                                              │  │
│  │  open() ─┬─→ PASO 1: Virtual Ports                         │  │
│  │          │                                                  │  │
│  │          ├─→ PASO 2: USB Host API                          │  │
│  │          │   (if context != null)                          │  │
│  │          │                                                  │  │
│  │          └─→ PASO 3: Rs232Api Fallback                     │  │
│  │                                                              │  │
│  │  write() ─┬─→ USB Route                                     │  │
│  │           ├─→ Virtual Port Route                            │  │
│  │           └─→ Rs232Api Route                                │  │
│  │                                                              │  │
│  │  readData() ─┬─→ USB Route                                  │  │
│  │              ├─→ Virtual Port Route                         │  │
│  │              └─→ Rs232Api Route                             │  │
│  │                                                              │  │
│  └──────────────────────────────────────────────────────────────┘  │
│                              │                                      │
│  ┌───────────────────────────┼───────────────────────────────────┐ │
│  │                           │                                   │ │
│  │  ┌──────────────────┐     │     ┌──────────────────────────┐ │ │
│  │  │ Virtual Ports    │     │     │ AisinoUsbComController   │ │ │
│  │  │ (/dev/ttyUSB0)   │     │     │ (USB Host API)           │ │ │
│  │  │ (/dev/ttyACM0)   │     │     │ - Uses UsbManager        │ │ │
│  │  │ (/dev/ttyGS0)    │     │     │ - CDC-ACM Protocol       │ │ │
│  │  └──────────────────┘     │     └──────────────────────────┘ │ │
│  │                            │                                   │ │
│  │                            │     ┌──────────────────────────┐ │ │
│  │  (Linux Kernel)            │     │ Rs232Api Wrapper         │ │ │
│  │  Shared Access             │     │ (Proprietary SDK)        │ │ │
│  │  ✅ Multiple processes     │     │ - Exclusive access       │ │ │
│  │                            │     │ - Port 0 only            │ │ │
│  │                            │     └──────────────────────────┘ │ │
│  └───────────────────────────┼───────────────────────────────────┘ │
│                              │                                      │
└──────────────────────────────┼──────────────────────────────────────┘
                               │
┌──────────────────────────────┼──────────────────────────────────────┐
│                   I/O & Detection Layer                             │
│                                                                     │
│  ┌────────────────────┐          ┌──────────────────────────────┐ │
│  │ SerialInputOutput  │          │ AisinoPortProber            │ │
│  │ Manager            │          │ (Fallback Detection)        │ │
│  │ ─────────────────  │          │ ────────────────────────    │ │
│  │ - Async I/O        │          │ - Tests ports 0-3          │ │
│  │ - Dedicated thread │          │ - Tries multiple baudrates │ │
│  │ - Event callbacks  │          │ - Active verification      │ │
│  │ - 4KB buffer       │          │ - Coroutine-based         │ │
│  └────────────────────┘          └──────────────────────────────┘ │
│                                                                     │
│  ┌──────────────────────────────────────────────────────────────┐ │
│  │ AisinoUsbDeviceManager                                       │ │
│  │ (USB Device Enumeration)                                     │ │
│  │ ────────────────────────────────────────────────────────    │ │
│  │ - Finds Aisino devices (0x05C6:0x901D, 0x05C6:0x9020)       │ │
│  │ - Manages USB permissions                                   │ │
│  │ - Creates controllers                                       │ │
│  └──────────────────────────────────────────────────────────────┘ │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
                               │
┌──────────────────────────────┼──────────────────────────────────────┐
│                   Hardware Layer                                    │
│                                                                     │
│  ┌────────────────────┐              ┌──────────────────────────┐ │
│  │  Aisino A90 Pro    │──[USB Cable]─│  Aisino A90 Pro        │ │
│  │  Device A          │              │  Device B              │ │
│  │  (Port 0: HW)      │              │  (Port 0: HW)          │ │
│  └────────────────────┘              └──────────────────────────┘ │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Strategy Selection Flowchart

```
┌────────────────────────────────────────┐
│   AisinoComController.open() called    │
│   (comport=0, context=?)               │
└──────────────────┬─────────────────────┘
                   │
        ┌──────────▼──────────┐
        │  Check Virtual Ports│
        │ (ttyUSB0/ACM0/GS0)  │
        └────┬──────────┬─────┘
            YES│        │NO
         ┌─────▼┐    ┌──▼────────────────┐
         │OPEN! │    │ Check Context     │
         │ ✅   │    │ for USB Host API  │
         └─────┘    └────┬────────────┬──┘
                        YES│         │NO
                    ┌────▼────────┐  │
                    │Find USB Dev?│  │
                    │(0x05C6)     │  │
                    └─┬──────────┬┘  │
                   YES│         │NO  │
              ┌───────▼┐     ┌──▼──────────┐
              │OPEN!   │     │Try Rs232Api │
              │✅ USB  │     │(Fallback)   │
              └────────┘     └──┬────────┬─┘
                            SUCCESS│   │FAIL
                           ┌────▼─┐ ┌──▼──┐
                           │OPEN! │ │❌   │
                           │✅    │ │ERR  │
                           └──────┘ └─────┘
```

---

## Data Flow Diagram - Write Operation

```
Application
   │
   ├─→ MainViewModel.sendKeys(data: ByteArray)
   │
   └─→ KeyInjectionViewModel.injectKeys(keys)
       │
       └─→ AisinoComController.write(data, timeout)
           │
           ├──→ if (usingUsbHost)
           │    └─→ AisinoUsbComController.write()
           │        └─→ UsbManager.bulkTransfer()
           │            └─→ [USB Cable] ──→ Device B Hardware
           │
           ├──→ else if (usingVirtualPort)
           │    └─→ OutputStream.write(data)
           │        └─→ [Linux Kernel Buffer] ──→ /dev/ttyUSB0
           │            └─→ [USB Cable] ──→ Device B Hardware
           │
           └──→ else
                └─→ Rs232Api.PortSends_Api()
                    └─→ [Proprietary SDK] ──→ [USB Cable]
                        └─→ Device B Hardware

           Return: data.size (bytes written) or ERROR_CODE
```

---

## Data Flow Diagram - Read Operation

```
Device A Hardware
   │
   ├──→ [USB Cable] ──→ [Virtual Port] ──→ InputStream.read()
   │    (if virtual port available)      └─→ AisinoComController.readData()
   │                                        └─→ Application receives bytes
   │
   ├──→ [USB Cable] ──→ [USB Host API] ──→ AisinoUsbComController.readData()
   │    (if USB available)                  └─→ AisinoComController.readData()
   │                                          └─→ Application receives bytes
   │
   └──→ [USB Cable] ──→ [Rs232Api] ──→ Rs232Api.PortRecv_Api()
        (fallback)                      └─→ AisinoComController.readData()
                                          └─→ Application receives bytes

SerialInputOutputManager
   │
   ├─→ Dedicated Thread (AisinoSerialIoManager)
   │   ├─→ While(state == RUNNING)
   │   │   └─→ port.readData(4096, buffer, 100ms timeout)
   │   │       │
   │   │       ├──→ if (bytesRead > 0)
   │   │       │    └─→ listener.onNewData(data)
   │   │       │        └─→ ViewModel updates UI
   │   │       │
   │   │       ├──→ if (bytesRead < 0 && != -6)
   │   │       │    └─→ listener.onRunError(exception)
   │   │       │        └─→ ViewModel handles error
   │   │       │
   │   │       └──→ if (bytesRead == -6)
   │   │            └─→ Timeout (normal), continue loop
```

---

## Component Interaction Sequence

### Scenario: Aisino-Aisino Full Communication Cycle

```
Timeline:

T0: App Startup
    ├─→ Device A: MainActivity.onCreate()
    │   └─→ AisinoComController(comport=0, context=appContext)
    │
    └─→ Device B: MainActivity.onCreate()
        └─→ AisinoComController(comport=0, context=appContext)

T1: User clicks "Listen" (Device A)
    ├─→ AisinoComController.init(115200, NOPAR, DB_8)
    ├─→ AisinoComController.open()
    │   ├─→ PASO 1: Check virtual ports
    │   │   ├─→ Try /dev/ttyUSB0 ✓ FOUND
    │   │   └─→ isOpen = true, usingVirtualPort = true
    │   │
    │   └─→ SerialInputOutputManager.start()
    │       └─→ New Thread("AisinoSerialIoManager")
    │           └─→ While loop: readData() every 100ms
    │
    └─→ Log: "✅ Puerto virtual abierto exitosamente"

T2: User clicks "Inyectar Llaves" (Device B)
    ├─→ AisinoComController.init(115200, NOPAR, DB_8)
    ├─→ AisinoComController.open()
    │   ├─→ PASO 1: Check virtual ports
    │   │   ├─→ Try /dev/ttyUSB0 ✓ FOUND (same kernel buffer)
    │   │   └─→ isOpen = true, usingVirtualPort = true
    │   │
    │   └─→ Log: "✅ Puerto virtual abierto exitosamente"
    │
    └─→ AisinoComController.write(keyData, timeout=5000)
        ├─→ Since usingVirtualPort: true
        ├─→ virtualPortOutputStream.write(keyData)
        ├─→ virtualPortOutputStream.flush()
        │
        └─→ Log: "📤 TX puerto virtual: 256 bytes"

T3: Device A receives data
    ├─→ SerialInputOutputManager thread detects data
    ├─→ port.readData() returns 256 bytes
    ├─→ listener.onNewData(data)
    ├─→ ViewModel.updateReceivedKeys(data)
    │
    └─→ Log: "📥 RX puerto virtual: 256 bytes - [hex data]"

T4: Device B verifies reception
    ├─→ AisinoComController.readData(expectedLen=100, timeout=2000)
    ├─→ Since usingVirtualPort: true
    ├─→ virtualPortInputStream.read() returns 100 bytes (ACK)
    │
    └─→ Log: "📥 RX puerto virtual: 100 bytes - [hex ACK]"

T5: Connection remains open
    ├─→ Device A: SerialInputOutputManager continues reading (every 100ms)
    ├─→ Device B: Connection stays open (no timeout)
    │
    └─→ Both devices can exchange data indefinitely

T6: User clicks "Stop" (Device A)
    ├─→ SerialInputOutputManager.stop()
    │   └─→ state = STOPPING, thread joins
    ├─→ AisinoComController.close()
    │   ├─→ virtualPortInputStream.close()
    │   └─→ virtualPortOutputStream.close()
    │
    └─→ Log: "✅ Puerto cerrado"
```

---

## Class Hierarchy and Dependencies

```
IComController (Interface)
├── AisinoComController
│   ├── Uses: AisinoUsbDeviceManager (optional)
│   ├── Uses: AisinoUsbComController (optional)
│   ├── Uses: Rs232Api (always fallback)
│   └── Composition: SerialInputOutputManager (optional)
│
└── AisinoUsbComController
    ├── Implements: IComController
    ├── Uses: UsbManager
    ├── Uses: UsbDevice
    └── Protocol: CDC-ACM

AisinoUsbDeviceManager
├── Uses: UsbManager
├── Creates: AisinoUsbComController
└── Constants: AISINO_VENDOR_ID (0x05C6)

AisinoPortProber
├── Object: Singleton
├── Suspends: probePort()
├── Suspends: probeSpecificPort()
└── Coroutines: Dispatchers.Default

SerialInputOutputManager
├── Implements: Runnable
├── Listener: Interface
├── State: Enum {STOPPED, RUNNING, STOPPING}
└── Thread: AisinoSerialIoManager
```

---

## State Machine - AisinoComController

```
                    ┌──────────────┐
                    │   CREATED    │
                    │  (isOpen=F)  │
                    └──────┬───────┘
                           │
                   ┌───────▼────────┐
                   │ init() called  │
                   │  Store params  │
                   └───────┬────────┘
                           │
                   ┌───────▼────────┐
                   │ open() called  │
                   └───────┬────────┘
                           │
          ┌────────────────┼────────────────┐
          │                │                │
    ┌─────▼──────┐ ┌──────▼─────┐ ┌─────┘──────┐
    │ VIRTUAL    │ │   USB      │ │  RS232     │
    │ OPENED     │ │   OPENED   │ │  OPENED    │
    │(isOpen=T)  │ │(isOpen=T)  │ │(isOpen=T)  │
    └─────┬──────┘ └──────┬─────┘ └──────┬─────┘
          │               │              │
          │    ┌──────────┼──────────┐   │
          │    │          │          │   │
    ┌─────▼────▼──┬───────▼──┬──────▼──┐│
    │ write()     │ readData()  close()  │
    │ read data   │ write data            │
    └────┬────────┴─────────────┬────────┘
         │                      │
         │ (routes by type)     │
         │                      │
    ┌────▼──────────────────────▼────┐
    │   CLOSED                        │
    │  (isOpen=F)                     │
    │  (all streams closed)           │
    └────────────────────────────────┘
```

---

## Error Handling Flow

```
┌─────────────────────────────────────┐
│  Operation (open/write/read)        │
└──────────────┬──────────────────────┘
               │
        ┌──────▼────────┐
        │  Try Execute  │
        └────┬──┬───────┘
            OK │FAIL
         ┌─────┴──────────┐
         │                │
    ┌────▼────┐     ┌─────▼──────────┐
    │ Return  │     │ Catch Exception│
    │ SUCCESS │     └─────┬──────────┘
    │ or      │           │
    │ bytesRead           │
    │ (0..max)│     ┌─────▼──────────────────┐
    └────────┘     │ Log error + stacktrace │
                   └─────┬──────────────────┘
                         │
                    ┌────▼────────────────┐
                    │ Return Error Code   │
                    │ -1: ALREADY_OPEN    │
                    │ -3: OPEN_FAILED     │
                    │ -4: NOT_OPEN        │
                    │ -5: WRITE_FAILED    │
                    │ -6: READ_TIMEOUT    │
                    │ -8: CLOSE_FAILED    │
                    │ -10: SET_BAUD_FAIL  │
                    │ -99: GENERAL_EXCEPT │
                    └─────────────────────┘
```

---

## Resource Management Timeline

### Virtual Port Route
```
T0: open()
    └─→ File("/dev/ttyUSB0")
        ├─→ portFile.inputStream()  [ALLOCATED]
        └─→ portFile.outputStream() [ALLOCATED]

T1..Tn: write/read operations
    ├─→ outputStream.write()
    └─→ inputStream.read()

Tn+1: close()
    ├─→ inputStream.close()   [RELEASED]
    └─→ outputStream.close()  [RELEASED]
```

### USB Route
```
T0: open()
    └─→ UsbManager.openDevice(device)
        └─→ UsbDeviceConnection [ALLOCATED]

T1..Tn: write/read operations
    ├─→ connection.bulkTransfer() (write)
    └─→ connection.bulkTransfer() (read)

Tn+1: close()
    └─→ connection.close() [RELEASED]
```

### Rs232Api Route
```
T0: open()
    ├─→ Rs232Api.PortOpen_Api(0)     [PORT ALLOCATED]
    └─→ Rs232Api.PortSetBaud_Api(...) [CONFIGURED]

T1..Tn: write/read operations
    ├─→ Rs232Api.PortSends_Api()
    └─→ Rs232Api.PortRecv_Api()

Tn+1: close()
    ├─→ Rs232Api.PortClose_Api()  [PORT RELEASED]
    └─→ Rs232Api.PortReset_Api()  [RESET]
```

---

## Thread Safety

```
AisinoComController
├── isOpen: Boolean (synchronized via init/open/close)
├── usingVirtualPort: Boolean (write once in open)
├── usingUsbHost: Boolean (write once in open)
└── Streams: InputOutput (only accessed after open succeeds)

AisinoUsbComController
└── writeLock: Any()
    └─→ synchronized(writeLock) for bulkTransfer

SerialInputOutputManager
├── readBuffer: ByteArray (thread-local)
├── state: State
│   └─→ synchronized for state transitions
├── thread: Thread (single dedicated thread)
└── listener: Callbacks (thread-safe event dispatch)

AisinoPortProber
├── Single-threaded (Dispatchers.Default)
└── Each port attempt is sequential
```

---

## Performance Characteristics

### Virtual Port Route (Best Case)
```
Open:     10-50ms (file system access)
Write:    5-20ms  (kernel buffering)
Read:     100-200ms per cycle (100ms timeout)
Close:    1-5ms   (file close)
Latency:  ~150ms per full cycle (TX→RX)
```

### USB Route
```
Open:     50-100ms (USB enumeration + claim)
Write:    20-50ms  (USB transfer)
Read:     100-200ms per cycle (100ms timeout + transfer)
Close:    10-20ms  (USB release)
Latency:  ~200ms per full cycle (TX→RX)
```

### Rs232Api Route
```
Open:     100-200ms (proprietary init)
Write:    20-50ms   (serial transmit)
Read:     100-2000ms (configurable timeout)
Close:    50-100ms  (serial close + reset)
Latency:  ~200ms per full cycle (TX→RX)
```

---

## Design Decisions

### 1. Triple Strategy Pattern
**Why:** Maximize compatibility and robustness
- Virtual ports: Best performance, shared access
- USB Host API: Standard, alternative
- Rs232Api: Guaranteed fallback

### 2. SerialInputOutputManager for I/O
**Why:** Non-blocking, event-driven architecture
- Dedicated thread prevents UI blocking
- Callbacks decouple I/O from application logic
- Configurable state management

### 3. Port Probing as Fallback
**Why:** Handle edge cases when normal detection fails
- Active verification ensures device is responsive
- Tests multiple port/baudrate combinations
- Coroutine-based for non-blocking execution

### 4. Separate USB Classes
**Why:** Clean separation of concerns
- AisinoUsbDeviceManager: Hardware enumeration
- AisinoUsbComController: Communication protocol
- Main controller: Strategy orchestration

---

**Generated:** 27 de octubre de 2025
**Version:** RUTA C+B Implementation
**Status:** Architecture finalized and implemented
