# Índice de Documentación DUKPT

Esta página sirve como guía para encontrar la documentación correcta según tu necesidad.

---

## 📚 Documentos Disponibles

### 1. **DUKPT_COMPLETE_GUIDE.md** ⭐ EMPEZAR AQUÍ
- **Propósito:** Guía paso a paso completa desde cero
- **Audiencia:** Usuarios nuevos, desarrolladores
- **Contenido:**
  - Conceptos básicos de DUKPT
  - Generación de IPEK
  - Importar llaves
  - Crear perfil de inyección
  - Ejecutar inyección
  - Troubleshooting
- **Tiempo de lectura:** 15 minutos
- **Mejor para:** Primera vez usando DUKPT

### 2. **DUKPT_COMPLETE_GUIDE.md - Ejemplo Completo**
- **Sección:** "Ejemplo Completo: Paso a Paso"
- **Propósito:** Walkthrough real de 12 minutos
- **Caso:** Inyectar AES-128 DUKPT en Slot 1
- **Mejor para:** Aprender por ejemplos

---

### 3. **DUKPT_TEST_QUICKSTART.md**
- **Propósito:** Inicio rápido (2-3 minutos)
- **Audiencia:** Usuarios con prisa
- **Contenido:**
  - Comandos rápidos
  - Importación inmediata
  - Inyección de prueba
- **Mejor para:** Testing rápido

---

### 4. **DUKPT_3DES_SUMMARY.md** 🔧 REFERENCIA TÉCNICA
- **Propósito:** Detalles técnicos y decisiones de diseño
- **Audiencia:** Arquitectos, desarrolladores senior
- **Contenido:**
  - Explicación del bug de 3TDEA
  - Por qué DUKPT 3DES usa 16 bytes (no 24)
  - Validación en NewPOS
  - Confirmación con código fuente
  - Estándar ANSI X9.24-1
- **Mejor para:** Entender decisiones técnicas

---

### 5. **DUKPT_GUIDE.md**
- **Propósito:** Guía general de DUKPT
- **Contenido:**
  - Diferencias entre DUKPT y claves estáticas
  - Flujo de derivación
  - Consideraciones de seguridad
- **Mejor para:** Contexto técnico general

---

### 6. **DUKPT_KSN_IMPLEMENTATION.md**
- **Propósito:** Detalles específicos del KSN
- **Contenido:**
  - Estructura del KSN
  - Formato Futurex vs NewPOS
  - Conversión y padding
  - Incremento automático
- **Mejor para:** Trabajar con KSNs

---

## 🛠️ Archivos de Configuración

### **test_keys_dukpt.json**
Archivo con 6 llaves DUKPT listas para importar:
- 1x KEK_STORAGE (AES-256)
- 1x DUKPT IPEK AES-128
- 1x DUKPT IPEK AES-192
- 1x DUKPT IPEK AES-256
- 1x DUKPT IPEK 2TDEA
- 1x DUKPT IPEK 3TDEA

**Generado por:** `import_dukpt_test_keys.py`

### **Perfiles de Inyección**
```
dukpt_test_profile.json          # AES-128 simple (recomendado para empezar)
dukpt_2tdea_profile.json         # 2TDEA simple
dukpt_3tdea_profile.json         # 3TDEA simple
dukpt_multikey_profile.json      # Todos los algoritmos (5 slots)
```

**Generados por:** `import_dukpt_profile.py`

---

## 🐍 Scripts Python

### **import_dukpt_test_keys.py**
Genera `test_keys_dukpt.json` con 6 llaves de prueba.

```bash
python3 import_dukpt_test_keys.py
```

**Salida:** test_keys_dukpt.json

### **import_dukpt_profile.py**
Genera 4 perfiles de inyección predefinidos.

```bash
python3 import_dukpt_profile.py
```

**Salida:**
- dukpt_test_profile.json
- dukpt_2tdea_profile.json
- dukpt_3tdea_profile.json
- dukpt_multikey_profile.json

---

## 🎯 Guía de Selección

### "Soy nuevo en DUKPT"
→ Leer: **DUKPT_COMPLETE_GUIDE.md**
→ Luego: Sección "Ejemplo Completo"
→ Ejecutar: Test con `import_dukpt_test_keys.py`

### "Necesito hacer una prueba rápida"
→ Leer: **DUKPT_TEST_QUICKSTART.md**
→ Ejecutar: 3 comandos
→ Tiempo: 5 minutos

### "Tengo un error de inyección"
→ Sección: **DUKPT_COMPLETE_GUIDE.md → Verification and Troubleshooting**
→ Buscar código de error (2012, 2004, etc.)
→ Alternativa: **DUKPT_3DES_SUMMARY.md → Error 2012**

### "¿Por qué DUKPT 3DES usa 16 bytes?"
→ Leer: **DUKPT_3DES_SUMMARY.md → Aclaración ANSI X9.24-1**
→ Luego: Sección "Confirmado por"

### "Necesito entender KSN"
→ Leer: **DUKPT_KSN_IMPLEMENTATION.md**
→ Referencia: **DUKPT_COMPLETE_GUIDE.md → Conceptos: KSN**

### "Soy desarrollador, quiero detalles técnicos"
→ Leer: **DUKPT_3DES_SUMMARY.md**
→ Código: `NewposPedController.kt` (líneas 112-551)
→ Tests: `NewposKeyInjectionTests.kt`

---

## 📋 Checklist Rápida

### Antes de Primera Inyección
- [ ] Leer DUKPT_COMPLETE_GUIDE.md (Conceptos Básicos)
- [ ] Ejecutar `python3 import_dukpt_test_keys.py`
- [ ] Ejecutar `python3 import_dukpt_profile.py`
- [ ] Conectar PED por USB
- [ ] Importar llaves en Injector app
- [ ] Importar perfil en Injector app

### Durante Primera Inyección
- [ ] Abre KeyReceiver
- [ ] Verifica estado: "Conectado en protocolo FUTUREX"
- [ ] En Injector: Click "Inject All Keys"
- [ ] Monitorea logs en KeyReceiver
- [ ] Espera confirmación: "SUCCESSFUL"

### Después de Inyección
- [ ] Verifica en Key Vault → Injected Keys
- [ ] Comprueba KCV coincida
- [ ] Verifica estado: SUCCESSFUL
- [ ] Lee logs para asegurar: Sin errores

---

## 🔑 Valores de Referencia

### KCVs de Llaves de Prueba
```
AES-128:  072043
AES-192:  5D614B
AES-256:  AB1234
2TDEA:    3F8D42
3TDEA:    7B5E9C
KEK:      112A8B
```

### KSNs Iniciales
```
AES-128:  FFFF9876543210000000
AES-192:  FFFF9876543210000001
AES-256:  FFFF9876543210000002
2TDEA:    FFFF9876543210000001
3TDEA:    FFFF9876543210000002
```

---

## 📞 Preguntas Frecuentes

**P: ¿Por dónde empiezo?**
R: Lee DUKPT_COMPLETE_GUIDE.md y sigue la sección "Ejemplo Completo".

**P: ¿Cuál es la diferencia entre 2TDEA y 3TDEA?**
R: Ambos usan 16 bytes para DUKPT. Ver DUKPT_3DES_SUMMARY.md.

**P: ¿Cuántos slots tengo disponibles?**
R: Usualmente 1-10. Verifica tu dispositivo específico.

**P: ¿Puedo inyectar en todos los slots?**
R: Sí, con perfiles diferentes. Ver dukpt_multikey_profile.json.

**P: ¿Necesito KEK (Key Encryption Key)?**
R: Obligatoria para el sistema. Ver test_keys_dukpt.json (incluida).

**P: ¿Qué es EncryptionType 05?**
R: DUKPT Plaintext (solo testing). Para producción usa tipo 01.

---

## 🚀 Roadmap

### Nivel 1: Principiante
1. Leer DUKPT_COMPLETE_GUIDE.md
2. Ejecutar `import_dukpt_test_keys.py`
3. Importar en Injector
4. Inyectar en Slot 1

### Nivel 2: Intermedio
1. Leer DUKPT_KSN_IMPLEMENTATION.md
2. Crear perfil personalizado
3. Inyectar múltiples algoritmos
4. Verificar KSNs

### Nivel 3: Avanzado
1. Leer DUKPT_3DES_SUMMARY.md
2. Entender validaciones de código
3. Generar llaves propias
4. Implementar EncryptionType 01

---

## 📝 Changelog

| Fecha | Documento | Cambio |
|-------|-----------|--------|
| 2025-10-24 | DUKPT_COMPLETE_GUIDE.md | Creado - Guía completa |
| 2025-10-24 | DUKPT_3DES_SUMMARY.md | Actualizado - Aclaración ANSI X9.24-1 |
| 2025-10-24 | DUKPT_INDEX.md | Creado - Este índice |

---

## 📎 Vínculos Internos

- [Conceptos Básicos](#conceptos-básicos)
- [Generación de IPEK](#generación-de-ipek)
- [Derivación KSN](#derivación-de-claves-de-sesión)
- [Importar en App](#importar-llaves-en-la-aplicación)
- [Crear Perfil](#crear-un-perfil-de-inyección)
- [Ejecutar Inyección](#ejecutar-la-inyección)
- [Troubleshooting](#verificación-y-troubleshooting)

---

**Última actualización:** 2025-10-24
**Versión:** 1.0
**Estatus:** ✅ Completo
