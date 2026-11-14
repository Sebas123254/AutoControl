# 🚗 AutoControl

**Tu auto. Tus gastos. Bajo control.**

**AutoControl** es una aplicación Android nativa diseñada para dueños de vehículos que quieren tomar el control real de sus finanzas automotrices. Registra gasolina, mantenimientos, gastos generales y documentos, mientras la app calcula automáticamente el rendimiento, el costo por kilómetro y el costo real del vehículo.

## ✨ Características Principales

### 📊 Dashboard Inteligente
- Resumen financiero por periodos (Este mes, Mes pasado, Este año, Histórico).
- Cálculo de costo por kilómetro y costo total acumulado.
- Estado del próximo mantenimiento con barras de progreso.
- Rendimiento del vehículo (km/L, L/100km o MPG).

### ⛽ Captura Rápida y Precisa
- **Integración con cámara:** Toma fotos del ticket directamente desde la app sin salir a la galería.
- **Fechas editables:** Registra gastos de días anteriores sin problemas.
- **CurrencyField:** Formateo automático de montos para evitar errores humanos.
- **Validaciones estrictas:** La app evita que ingreses fechas invertidas, años fuera de rango o montos ilógicos.

### 📈 Estadísticas y Reportes
- Gráfica interactiva de evolución mensual de gastos (con soporte para TalkBack).
- Distribución de gastos por categorías mediante barras de progreso.
- Historial de rendimiento de combustible basado en cargas de "Tanque lleno".

### 📁 Documentación y Recordatorios
- Gestión de documentos (Pólizas, Verificación, Facturas) con adjuntos PDF o imágenes.
- Recordatorios automáticos por fechas de vencimiento o kilometraje.
- Posibilidad de **posponer** recordatorios (1 día o 1 semana).

### ☁️ Respaldo y Privacidad
- **Respaldo en la Nube (Auto Backup):** Integración con Google Drive para transferir datos automáticamente al cambiar de dispositivo.
- **Respaldo Manual (.zip):** Exporta e importa un archivo comprimido que incluye toda tu base de datos y las fotos de tus recibos.
- **Privacidad Total:** Los datos se quedan en tu teléfono. No hay servidores externos ni rastreo.

## 🛠️ Stack Tecnológico

- **Lenguaje:** [Kotlin](https://kotlinlang.org/)
- **UI:** [Jetpack Compose](https://developer.android.com/jetpack/compose) con **Material Design 3** (soporte nativo para modo oscuro y color dinámico).
- **Arquitectura:** MVVM + Clean Architecture simplificada.
- **Base de Datos:** [Room](https://developer.android.com/training/data-storage/room) (con migraciones y triggers).
- **Inyección de Dependencias:** [Hilt](https://dagger.dev/hilt/).
- **Preferencias:** [DataStore](https://developer.android.com/topic/libraries/architecture/datastore).
- **Tareas en Segundo Plano:** [WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager).
- **Carga de Imágenes:** [Coil](https://coil-kt.github.io/coil/).
- **Serialización:** Kotlinx Serialization.

## 🏗️ Arquitectura del Proyecto

El proyecto sigue una estructura modular por características (Feature-based):

```text
app/src/main/java/com/sierracode/autocontrol/
│
├── core/           # Tema, componentes UI compartidos, utilidades, DI.
├── data/           # Room (DAOs, Entities), Repositorios, DataStore, Backup.
├── domain/         # Lógica de negocio pura (Cálculos, Filtros, Conversores).
├── feature/        # Pantallas y ViewModels agrupados por módulo.
│   ├── dashboard/
│   ├── expenses/
│   ├── fuel/
│   ├── maintenance/
│   ├── documents/
│   ├── goals/
│   ├── stats/
│   └── ...
└── navigation/     # NavHost y rutas.