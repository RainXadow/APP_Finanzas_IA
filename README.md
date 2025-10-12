# 📱 Guía de Uso - Finanzas IA (Versión PDF)

## 🎯 ¿Cómo Funciona la App?

### Flujo de Funcionamiento:

```
1. Descargas PDF de Santander → 
2. Importas PDF a la app → 
3. App extrae transacciones → 
4. Categorización automática/manual → 
5. Guarda sin duplicados → 
6. Exportas a Excel cuando quieras
```

---

## 📋 CARACTERÍSTICAS PRINCIPALES

### ✨ Importación de PDFs
- Importa extractos bancarios del Santander en PDF
- Extrae automáticamente: fecha, concepto, importe, saldo
- **Anti-duplicados**: Si importas el mismo PDF dos veces, no se duplican los movimientos

### 🏷️ Categorización Inteligente
- **Primera vez**: Te pregunta la categoría para cada nuevo tipo de gasto
- **Aprendizaje**: Guarda tu elección para futuros gastos similares
- **Automática**: Los siguientes gastos con el mismo concepto se categorizan solos
- **Personalizable**: Crea tus propias categorías

### 📊 Gestión Completa
- Ver todas tus transacciones
- Estadísticas en tiempo real
- Exportar a Excel con categorías
- Eliminar transacciones individuales

---

## 🚀 CÓMO USAR LA APP

### Paso 1: Descargar PDF del Banco

1. Entra en la app/web de Santander
2. Ve a tu cuenta
3. Busca "Movimientos" o "Extracto"
4. Descarga el PDF (puede ser "Listado de movimientos.pdf")
5. Guárdalo en tu móvil

### Paso 2: Importar PDF

1. Abre **Finanzas IA**
2. Pulsa **"📄 Importar PDF"**
3. Selecciona el PDF descargado
4. Espera a que se procese (puede tardar unos segundos)

### Paso 3: Categorizar Transacciones

**Primera importación:**
- Te aparecerá un diálogo para categorizar cada tipo de gasto nuevo
- Puedes elegir una categoría existente o crear una nueva

**Importaciones posteriores:**
- Los gastos ya conocidos se categorizan automáticamente
- Solo te preguntará por los conceptos nuevos

### Paso 4: Revisar y Gestionar

- **Ver detalles**: Toca una transacción
- **Cambiar categoría**: Toca una transacción y elige nueva categoría
- **Eliminar**: Mantén presionada una transacción

### Paso 5: Exportar a Excel

1. Pulsa **"📊 Exportar a Excel"**
2. Comparte el archivo por WhatsApp, Email, Drive, etc.
3. Abre el Excel en tu ordenador o móvil

---

## 🏷️ GESTIÓN DE CATEGORÍAS

### Ver Categorías

1. Pulsa **"🏷️ Categorías"**
2. Verás todas tus categorías con el número de transacciones

### Crear Nueva Categoría

**Opción 1: Al categorizar una transacción**
1. Cuando te pida categorizar un gasto
2. Pulsa **"Nueva Categoría"**
3. Escribe el nombre (ej: "Mascotas", "Regalos", "Viajes")

**Opción 2: Desde el gestor**
1. Pulsa **"🏷️ Categorías"**
2. Pulsa **"Nueva Categoría"**
3. Escribe el nombre

### Eliminar Categoría

1. Pulsa **"🏷️ Categorías"**
2. Toca la categoría que quieres eliminar
3. Pulsa **"Eliminar"**
4. Las transacciones se marcarán como "Sin categoría"

---

## 🎯 CATEGORÍAS PREDETERMINADAS

La app viene con estas categorías por defecto:

| Categoría | Se detecta automáticamente en |
|-----------|-------------------------------|
| 🍎 Alimentación | Mercadona, Carrefour, Lidl, Consum |
| 🍽️ Restaurantes | Pizzeria, Restaurante, Bar, Domino's |
| 🚗 Transporte | Gasolina, Repsol, Cepsa, Plenoil |
| 🎮 Ocio | Amazon, Netflix, Spotify, Cine |
| 💡 Servicios | Recibos, Digi, Movistar, Endesa |
| 💸 Transferencias | Transferencias, Traspasos |
| 📱 Bizum | Bizum |
| 🛒 Compras Online | PayPal, Amazon, AliExpress |
| 🏧 Cajero | Ingresos y retiradas de efectivo |
| 💰 Nómina | Nóminas |

---

## 🔄 PREVENCIÓN DE DUPLICADOS

### ¿Cómo Funciona?

La app crea una "huella digital" de cada transacción usando:
- Fecha exacta
- Concepto (primeros 30 caracteres)
- Importe exacto

### Ejemplo:
```
Primera importación:
✅ "29 ago 2025 - Mercadona - 14,39€" → Se guarda

Segunda importación (mismo PDF):
⚠️ "29 ago 2025 - Mercadona - 14,39€" → Se detecta como duplicado, se ignora

Tercera importación (PDF nuevo mes):
✅ "15 sep 2025 - Mercadona - 18,50€" → Se guarda (fecha diferente)
```

### Resultado:
- Puedes importar el mismo PDF múltiples veces sin problema
- Solo se añaden transacciones nuevas

---

## 📊 EJEMPLO DE USO COMPLETO

### Escenario: Primera vez usando la app

**Mes 1 (Agosto):**
1. Descargas PDF de agosto del Santander
2. Importas el PDF → 45 transacciones encontradas
3. La app te pide categorizar:
    - "Mercadona" → Eliges "Alimentación"
    - "Domino's Pizza" → Eliges "Restaurantes"
    - "Repsol" → Eliges "Transporte"
    - ...continúas para los 10-15 conceptos diferentes
4. Total: 45 transacciones guardadas

**Mes 2 (Septiembre):**
1. Descargas PDF de septiembre
2. Importas el PDF → 50 transacciones encontradas
3. La app categoriza automáticamente:
    - ✅ "Mercadona" → Alimentación (ya guardado)
    - ✅ "Domino's Pizza" → Restaurantes (ya guardado)
    - ✅ "Repsol" → Transporte (ya guardado)
    - ❓ "Netflix" → Te pregunta (nuevo concepto)
4. Solo te pregunta por 2-3 conceptos nuevos

**Mes 3 en adelante:**
- Cada vez menos preguntas
- La app ya conoce tus gastos habituales
- Categorización casi 100% automática

---

## 📈 ESTADÍSTICAS Y ANÁLISIS

### Panel Principal

Muestra en tiempo real:
- 💰 **Total Ingresos**: Suma de todos los ingresos
- 💸 **Total Gastos**: Suma de todos los gastos
- 📈 **Balance**: Diferencia entre ingresos y gastos

### Excel Exportado

Incluye:
- Fecha de cada transacción
- Concepto completo
- Categoría asignada
- Importe
- Tipo (Ingreso/Gasto)
- Saldo después de la transacción

---

## ⚠️ PROBLEMAS COMUNES Y SOLUCIONES

### ❌ "No se importa el PDF"

**Posibles causas:**
1. El PDF no es del Santander
2. El formato del PDF ha cambiado
3. El PDF está protegido o encriptado

**Solución:**
- Asegúrate de que el PDF es el extracto oficial del Santander
- Descarga un PDF nuevo desde la app/web del banco

### ❌ "Se importan pocas transacciones"

**Causa:**
- El PDF solo contiene algunas páginas

**Solución:**
- Descarga el PDF completo con todos los movimientos

### ❌ "Aparecen transacciones duplicadas"

**Causa:**
- Muy raro, pero puede pasar si cambias la hora del móvil

**Solución:**
- Mantén presionada la transacción duplicada y elimínala

---

## 🎓 CONSEJOS PRO

### 1. Importa Regularmente
- Importa el PDF cada mes
- Así mantienes tu registro actualizado

### 2. Revisa las Categorías
- De vez en cuando, revisa si las categorías son correctas
- Toca una transacción para cambiar su categoría

### 3. Crea Categorías Específicas
- En vez de solo "Ocio", crea "Streaming", "Videojuegos", "Deportes"
- Así tendrás análisis más detallados

### 4. Exporta Mensualmente
- Exporta un Excel al final de cada mes
- Guárdalo como backup

### 5. Usa el Excel para Análisis
- Abre el Excel en tu ordenador
- Crea tablas dinámicas
- Genera gráficos personalizados

---

## 🔐 PRIVACIDAD

✅ **Todos tus datos están SOLO en tu móvil**
✅ No hay conexión a internet
✅ No se suben datos a ningún servidor
✅ Tú controlas tus datos 100%

---

## 📱 REQUISITOS

- Android 13 o superior
- Espacio: ~50 MB
- PDFs del Santander en formato estándar

---

**¡Empieza a gestionar tus finanzas de forma inteligente! 💰📊**