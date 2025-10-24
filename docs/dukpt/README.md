# Documentación DUKPT

Guías completas para entender e implementar DUKPT en NewPOS.

## Documentos

### 🌟 Comienza Aquí

**[DUKPT_INDEX.md](DUKPT_INDEX.md)**
- Índice central de toda la documentación
- Guía para seleccionar el documento correcto según tu necesidad
- Checklist, referencias rápidas y FAQs

### 📚 Guías Principales

**[DUKPT_COMPLETE_GUIDE.md](DUKPT_COMPLETE_GUIDE.md)** ⭐ RECOMENDADO
- Guía paso a paso desde cero hasta inyección (722 líneas)
- Conceptos básicos, generación, derivación, importación, perfiles
- Ejemplo completo: AES-128 en ~12 minutos
- Troubleshooting detallado

**[DUKPT_TEST_QUICKSTART.md](DUKPT_TEST_QUICKSTART.md)**
- Inicio rápido (2-3 minutos)
- Para usuarios con prisa
- Comandos directos

### 🔧 Referencia Técnica

**[DUKPT_3DES_SUMMARY.md](DUKPT_3DES_SUMMARY.md)**
- Detalles técnicos y decisiones de diseño
- Por qué DUKPT 3TDEA usa 16 bytes (no 24 bytes)
- Validación con código NewPOS
- Estándar ANSI X9.24-1

**[DUKPT_GUIDE.md](DUKPT_GUIDE.md)**
- Contexto general de DUKPT
- Diferencias entre DUKPT y claves estáticas
- Flujo de derivación
- Consideraciones de seguridad

**[DUKPT_KSN_IMPLEMENTATION.md](DUKPT_KSN_IMPLEMENTATION.md)**
- Detalles específicos del KSN
- Estructura y formato
- Conversión Futurex ↔ NewPOS
- Incremento automático

## Estructura Recomendada de Lectura

### Para Principiantes
1. DUKPT_INDEX.md (5 min)
2. DUKPT_COMPLETE_GUIDE.md → "Conceptos Básicos" (10 min)
3. DUKPT_COMPLETE_GUIDE.md → "Generación de IPEK" (5 min)
4. DUKPT_COMPLETE_GUIDE.md → "Ejemplo Completo" (10 min)

### Para Usuarios con Prisa
1. DUKPT_INDEX.md → "Guía de Selección" (3 min)
2. DUKPT_TEST_QUICKSTART.md (3 min)
3. Ejecutar scripts

### Para Desarrolladores
1. DUKPT_3DES_SUMMARY.md (15 min)
2. DUKPT_COMPLETE_GUIDE.md → "Derivación de Claves" (10 min)
3. DUKPT_KSN_IMPLEMENTATION.md (10 min)
4. Código: MainViewModel.kt (líneas 754-770)

## Temas Cubiertos

### Conceptos
- ✅ Qué es DUKPT
- ✅ Estándar ANSI X9.24-1
- ✅ Diferencia entre 2TDEA y 3TDEA
- ✅ KSN y derivación de claves

### Implementación
- ✅ Generación de IPEK
- ✅ Cálculo de KCV
- ✅ Importación en aplicación
- ✅ Creación de perfiles
- ✅ Ejecución de inyección

### Troubleshooting
- ✅ Error 2012 (KEY_LEN_ERR)
- ✅ Error 2004 (KEY_INDEX_ERR)
- ✅ Error 2001 (KEY_FULL)
- ✅ KSN inválido
- ✅ Verificación de inyección

## Algoritmos Soportados

| Algoritmo | IPEK | Documento |
|-----------|------|-----------|
| 3DES 2TDEA | 16 bytes | DUKPT_COMPLETE_GUIDE.md |
| 3DES 3TDEA | 16 bytes | DUKPT_3DES_SUMMARY.md |
| AES-128 | 16 bytes | DUKPT_COMPLETE_GUIDE.md |
| AES-192 | 24 bytes | DUKPT_COMPLETE_GUIDE.md |
| AES-256 | 32 bytes | DUKPT_COMPLETE_GUIDE.md |

## Referencias Rápidas

### Valores de Prueba
```
AES-128:  KCV: 072043  |  KSN: FFFF9876543210000000
AES-192:  KCV: 5D614B  |  KSN: FFFF9876543210000001
AES-256:  KCV: AB1234  |  KSN: FFFF9876543210000002
2TDEA:    KCV: 3F8D42  |  KSN: FFFF9876543210000001
3TDEA:    KCV: 7B5E9C  |  KSN: FFFF9876543210000002
KEK:      KCV: 112A8B
```

### Cambio Importante en 3TDEA
- **Antes:** 24 bytes (incorrecto)
- **Ahora:** 16 bytes (correcto según ANSI X9.24-1)
- **Razón:** Ver DUKPT_3DES_SUMMARY.md
- **Error:** NewPOS rechaza con 2012 si no se respeta

## Archivos Relacionados

- **Datos:** `/data/dukpt/` (llaves, perfiles)
- **Scripts:** `/scripts/dukpt/` (generadores Python)
- **Código:** `MainViewModel.kt` (líneas 754-770)
- **Tests:** `NewposKeyInjectionTests.kt`

## Estructura de Archivos

```
docs/dukpt/
├── README.md (este archivo)
├── DUKPT_INDEX.md                 ← COMIENZA AQUÍ
├── DUKPT_COMPLETE_GUIDE.md        ← RECOMENDADO
├── DUKPT_TEST_QUICKSTART.md
├── DUKPT_3DES_SUMMARY.md
├── DUKPT_GUIDE.md
└── DUKPT_KSN_IMPLEMENTATION.md
```

## Contribuir

Si encuentras errores o tienes sugerencias:
1. Abre un issue describiendo el problema
2. Proporciona el documento y sección afectada
3. Incluye pasos para reproducir si aplica

## Versionado

| Versión | Fecha | Cambios |
|---------|-------|---------|
| 1.0 | 2025-10-24 | Documentación inicial completa |

---

**Última actualización:** 2025-10-24
**Estado:** ✅ Completo
