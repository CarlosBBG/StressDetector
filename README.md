# StressDetector

Aplicacion Android para deteccion de estres en estudiantes, basada en el analisis de senales fisiologicas y un modelo TFLite. La app permite cargar archivos CSV, procesar la senal, estimar el nivel de estres y mostrar recomendaciones.

## Funcionalidades principales

- Inicio de sesion y registro de usuario.
- Carga y analisis de archivos CSV.
- Resultados del nivel de estres y analisis detallado.
- Recomendaciones segun el nivel de estres.
- Historial de mediciones.
- Modo offline para analisis local.

## Flujo de la aplicacion

1. El usuario inicia sesion o se registra.
2. Carga un archivo CSV con senales fisiologicas.
3. Se ejecuta el preprocesamiento y la extraccion de caracteristicas.
4. El modelo TFLite clasifica el nivel de estres.
5. Se muestran resultados y recomendaciones; se guarda en el historial.

## Senales fisiologicas utilizadas

- Electrocardiograma (ECG).
- Pulso de Volumen Sanguineo (BVP).

### Indicadores derivados

- Intervalos RR (tiempo entre picos R consecutivos en ECG).
- Intervalos entre latidos (IBI).
- Variabilidad de la Frecuencia Cardiaca (HRV) como indicador de estres.

## Modelo de red neuronal

- Modelo TFLite entrenado con senales ECG y BVP.
- Arquitectura basada en una CNN 1D para clasificacion de nivel de estres.
- Entrenado con un dataset obtenido en estudiantes de la FIEE usando el modulo MAX86150 EVSYS_EVKIT.

## Estructura de codigo (app/src/main/java)

```text
app/src/main/java/
└── com/example/stressdetector/
    ├── MainActivity.kt
    ├── TFLiteRunner.kt
    ├── analysis/
    │   ├── StressAnalyzer.kt
    │   └── StressRecommendations.kt
    ├── api/
    │   ├── ApiConfig.kt
    │   ├── ApiService.kt
    │   ├── AuthInterceptor.kt
    │   └── UnauthorizedInterceptor.kt
    ├── auth/
    │   └── SessionManager.kt
    ├── models/
    │   ├── ApiError.kt
    │   ├── AuthModels.kt
    │   └── MeasurementModels.kt
    ├── preprocessing/
    │   ├── ButterworthFilter.kt
    │   ├── CSVLoader.kt
    │   ├── QualityChecker.kt
    │   ├── RPeakDetector.kt
    │   └── SignalPreprocessor.kt
    ├── ui/
    │   ├── AboutAppFragment.kt
    │   ├── AnalysisFragment.kt
    │   ├── HistoryFragment.kt
    │   ├── HomeFragment.kt
    │   ├── LoginActivity.kt
    │   ├── MeasurementAdapter.kt
    │   ├── MeasurementDetailActivity.kt
    │   ├── ProfileFragment.kt
    │   └── RegisterActivity.kt
    └── util/
```

## Gradle (root + app)

```text
./
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── app/
    └── build.gradle.kts
```

## Modelo y assets

- Modelo TFLite: app/src/main/assets/stress_detector.tflite

## Ejecutar la app

1. Abrir el proyecto en Android Studio.
2. Sincronizar Gradle.
3. Ejecutar en emulador o dispositivo.
