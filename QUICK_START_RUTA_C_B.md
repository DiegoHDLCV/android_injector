# 🚀 Quick Start - RUTA C+B Deployment

**One-page guide for deploying and testing the new Aisino integration**

---

## 📦 Pre-built APKs Ready

Both APKs are compiled and ready at:
```
keyreceiver/build/outputs/apk/debug/keyreceiver-debug.apk (27 MB)
injector/build/outputs/apk/debug/injector-debug.apk (27 MB)
```

---

## ⚡ 5-Minute Setup

### Step 1: Install APKs
```bash
# On Device A (KeyReceiver)
adb install keyreceiver/build/outputs/apk/debug/keyreceiver-debug.apk

# On Device B (Injector)
adb install injector/build/outputs/apk/debug/injector-debug.apk
```

### Step 2: Connect Devices
- Connect Device A and Device B with USB cable
- Grant USB permissions when prompted
- Verify connection: `adb devices`

### Step 3: Open Apps
- Device A: Open **KeyReceiver** app
- Device B: Open **Injector** app
- Both should now attempt to connect via Aisino

### Step 4: Monitor Logs
```bash
# Terminal on your computer
adb logcat | grep -E "Aisino|USB|Serial"
```

### Step 5: Test Communication
- Device B: Click "Inyectar Llaves" (Inject Keys)
- Device A: Should receive and display keys
- Listen mode should remain active for 5+ minutes (not timeout after 30s)

---

## 🎯 What to Expect

### Success: Virtual Port Route
```
✓ Puerto virtual encontrado: /dev/ttyUSB0
✅ Puerto virtual abierto exitosamente
✅ VENTAJA: Acceso compartido permitido
```
→ **Best case** - Shared access between devices enabled

### Success: USB Host API Route
```
✓ Encontrado: /dev/bus/usb/001/015 (0x05C6:0x901D)
✓ Permiso USB verificado
✅ Usando USB Host API
```
→ **Good case** - Standard USB, non-proprietary

### Success: Rs232Api Fallback
```
✓ Puerto Rs232 0 abierto (115200bps)
⚠️ Advertencia: Usando Puerto 0 (acceso exclusivo)
```
→ **Acceptable** - Works but one-at-a-time

### Problem: All Routes Failed
```
❌ Error al abrir puerto Rs232 0: -1
```
→ Device not detected. Check:
- USB cable properly connected
- Device not in use by another app
- Device has Aisino drivers

---

## 🔍 Troubleshooting

| Issue | Symptoms | Fix |
|-------|----------|-----|
| **Device not detected** | All strategies fail, "Error -1" | Reconnect cable, restart devices |
| **Only one app can connect** | Second device gets error | Using Rs232Api (exclusive access) - normal if no virtual ports |
| **Timeout after 30s** | Still getting old behavior | Old APK may be cached, force reinstall |
| **Permission denied** | USB permission errors | Grant USB permissions when prompted |
| **No logs appearing** | Logcat empty | Filter might be wrong, try: `adb logcat | grep -i aisino` |

---

## 📊 Monitoring Logs

### All Aisino Components
```bash
adb logcat | grep -E "AisinoComController|AisinoUsbComController|AisinoPortProber|SerialIoManager"
```

### Just Connection Attempts
```bash
adb logcat | grep -E "AISINO COM OPEN|USB|Probando"
```

### Just Data Transfer
```bash
adb logcat | grep -E "📤 TX|📥 RX"
```

### Save Complete Log
```bash
adb logcat > test_log_$(date +%s).txt
# Run your test...
# Press Ctrl+C to stop
```

---

## ✨ New Features in RUTA C+B

1. **SerialInputOutputManager** (Async I/O)
   - Reads data in background thread
   - Callbacks for new data
   - Non-blocking UI operations

2. **Triple Strategy** (AisinoComController)
   - Tries virtual ports first (best: shared access)
   - Falls back to USB Host API (standard USB)
   - Falls back to Rs232Api (guaranteed)

3. **Port Probing** (AisinoPortProber)
   - Fallback detection if normal methods fail
   - Tests ports 0-3 with multiple baudrates
   - Verifies device responds

---

## 🔄 Workflow: Testing Aisino-Aisino

### Scenario A: Both Devices via Cable

**Setup:**
```
Device A (KeyReceiver)  <--[USB Cable]--  Device B (Injector)
```

**Test Steps:**
1. Open KeyReceiver on Device A
2. Open Injector on Device B
3. Click "Listen" on Device A
4. Click "Inyectar Llaves" on Device B
5. Device A should receive and display keys
6. Open log and verify no timeout (should stay connected 5+ min)

### Expected Duration
- **Old Code:** Timeout after ~22-31 seconds
- **New Code:** Should stay connected indefinitely (only closes when you click Stop)

---

## 📈 Performance Indicators

### Good Logs ✅
```
🔍 Intentando ttyUSB0...
✓ Puerto virtual encontrado
📤 TX puerto virtual: 32 bytes
📥 RX puerto virtual: 16 bytes
```
→ Everything working smoothly

### Acceptable Logs ⚠️
```
⚠️ ttyUSB0 no disponible
✓ Puerto Rs232 0 abierto
⚠️ Usando Puerto 0 (acceso exclusivo)
```
→ Fallback active but functional (one-at-a-time only)

### Bad Logs ❌
```
❌ Error al abrir puerto
```
→ Device not detected, unable to connect

---

## 🛠️ If You Need to Rebuild

```bash
# Full clean build
./gradlew clean

# Just keyreceiver
./gradlew keyreceiver:assembleDebug

# Just injector
./gradlew injector:assembleDebug

# Both apps
./gradlew keyreceiver:assembleDebug injector:assembleDebug

# With output to file (for large logs)
./gradlew keyreceiver:assembleDebug injector:assembleDebug 2>&1 | tee build.log
```

---

## 📋 Git Info

**Current Branch:** `feature/AI-75`
**Latest Commit:** `3c50bb6` - Implementación RUTA C+B: I/O Asíncrono + USB Host API
**Commits Ahead:** 13 commits from origin/feature/AI-75

### To Push to Remote
```bash
git push origin feature/AI-75
```

### To Merge to Main (after testing)
```bash
git checkout main
git merge feature/AI-75
git push origin main
```

---

## 📚 Documentation Reference

For detailed information, see:
- **IMPLEMENTATION_SUMMARY_RUTA_C_B.md** - Technical deep dive
- **EXPECTED_LOGS_RUTA_C_B.md** - Complete log scenarios
- **INTEGRATION_STRATEGY_AISINO_DEMO.md** - Architecture overview
- **PRACTICAL_EXAMPLES_INTEGRATION.md** - Code integration examples

---

## 🎯 Success Criteria

Test is **SUCCESSFUL** if:
1. ✅ Both apps connect via Aisino
2. ✅ No timeout error after 30 seconds
3. ✅ Can inject keys and receive response
4. ✅ Connection stays active for 5+ minutes
5. ✅ Logs show appropriate strategy (Virtual/USB/Rs232)

---

## ⏰ Estimated Test Time

- **Setup:** 5 minutes (install APKs)
- **Connection:** 1 minute (apps connect)
- **Test:** 10 minutes (verify shared access)
- **Verification:** 5 minutes (check logs)

**Total:** ~20 minutes for full validation

---

**Ready to deploy!** 🚀

Need more details? Check the comprehensive documentation files.
