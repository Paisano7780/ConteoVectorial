BLUEPRINT TÉCNICO: SMVP LITE (Fase de Validación y Active Learning)
Objetivo: Desarrollar una Prueba de Concepto (Mockup) funcional para validar la lógica matemática vectorial y actuar como un Data-Logger de entrenamiento asistido por humanos (HITL).
Plataforma de Hardware: DJI Mini 3 (Estándar) + Control RC-N1 + Xiaomi Redmi Note 13 Pro (Procesamiento Edge).
Framework Principal: DJI Mobile SDK V5 (MSDK V5) para Android.
1. Stack Tecnológico y Lenguajes
•	Aplicación Principal y Conectividad: Desarrollada en Kotlin (Android Studio). Kotlin gestionará la interfaz de usuario (UI), la telemetría del MSDK V5 y el almacenamiento de datos en el Xiaomi.
•	Motor de Visión Computacional: Desarrollado en C++ y comunicado con Kotlin a través de JNI (Java Native Interface). Esto es vital para evitar cuellos de botella en el procesador del Redmi Note 13 Pro.
•	Geometría y Matrices: Implementación de OpenCV (versión Android/C++) para manipular las imágenes y trazar los polígonos (Convex Hull) y el cálculo de vectores.
•	Inferencia AI: Uso exclusivo del framework NCNN para correr el modelo YOLOv8-Pose base, garantizando máxima optimización en la CPU/GPU de arquitectura ARM del teléfono sin saturar la memoria RAM.
•	Almacenamiento de Datos: Uso de Room Database (SQLite) para registrar la telemetría y el sistema de archivos nativo de Android para guardar las imágenes y etiquetas.
2. Módulo de Captura en Vuelo (El Data-Logger)
Lógica de Funcionamiento: Durante el vuelo manual con el RC-N1, el software no realiza correcciones de vuelo, actúa puramente como un sistema de extracción y marcado en segundo plano.
•	Extracción de Video: Kotlin se suscribe al ICameraStreamManager del MSDK V5 para obtener el flujo de video en vivo de la cámara RGB del Mini 3.
•	Inferencia Discreta: El frame de video se envía al módulo de C++. YOLOv8-Pose detecta las vacas e identifica los Keypoints articulares críticos (Cola y Cabeza).
•	Trazado Vectorial en Pantalla (Vector Overlay): OpenCV procesa los keypoints y dibuja vectores direccionales sobre el video en vivo en la pantalla del celular, permitiendo al piloto ver la estimación de orientación de la IA en tiempo real.
•	Extracción de Dataset (Gatillo de Guardado): Se programa un bucle en Kotlin que, cada 1 segundo (o al presionar un botón virtual en la app), "congela" los datos de ese instante exacto:
o	Guarda un frame .jpg en alta resolución en el almacenamiento del Xiaomi.
o	Crea un archivo de texto .txt homónimo (formato estándar YOLO-Pose) con las coordenadas $x, y$ de los Keypoints detectados en ese frame.
o	Registra en la base de datos la altura actual, latitud, longitud y ángulo del gimbal obtenidos de la telemetría del dron.
3. Módulo de "Human-In-The-Loop" (Validación Post-Vuelo)
Lógica de Funcionamiento: Una interfaz secundaria en la aplicación Android diseñada para ejecutarse de forma asíncrona una vez que el Mini 3 ha aterrizado. Transforma al piloto en el entrenador de la red neuronal directamente desde la pantalla táctil del celular.
•	Interfaz de Auditoría: La app carga las imágenes .jpg capturadas durante el vuelo con los vectores pre-dibujados (los pre-etiquetados que hizo la IA).
•	Mecánica de Etiquetado Ágil (Gestos Táctiles):
o	Aprobación (Deslizar a la derecha): El usuario confirma que el vector es perfecto. El archivo .txt se bloquea como True Positive (Dato limpio para entrenamiento).
o	Rechazo (Deslizar a la izquierda): La IA detectó un objeto erróneo (False Positive). El software elimina automáticamente las coordenadas de ese objeto del archivo .txt.
o	Corrección Táctil (Tap & Drag): Si los puntos están invertidos o ligeramente descentrados, el usuario toca la pantalla del Xiaomi y arrastra el Keypoint de la cabeza o la cola a su posición real. El software recalcula el vector y reescribe las coordenadas en el archivo .txt.
4. Estructuración del Dataset y Exportación
Lógica de Funcionamiento: Preparar el paquete de datos estructurados para migrar el trabajo del celular a la computadora de escritorio.
•	Empaquetado: Un script en Kotlin comprime todas las imágenes validadas y sus respectivos archivos .txt en un archivo .zip.
•	Sincronización: El archivo se exporta mediante transferencia USB directa o sincronización en la nube (Google Drive/AWS) desde el Redmi Note 13 Pro hacia la PC de desarrollo.
•	Integración Continua: Estos datos pre-etiquetados y corregidos se inyectan en el script de entrenamiento en la PC para recalibrar los pesos de la red neuronal.
5. Limitaciones Intencionales de esta Fase (Scope)
Para garantizar la viabilidad inmediata y la fluidez del código en el hardware inicial, este SMVP debe excluir explícitamente las siguientes funciones hasta migrar a la Fase Enterprise:
•	Vuelo Autónomo / Waypoints: El vuelo se mantiene 100% manual vía palancas del RC-N1. El MSDK V5 en drones de consumo tiene restricciones severas para misiones automatizadas complejas.
•	Fusión Térmica y Láser: El Mini 3 carece de LRF y sensor infrarrojo. La biometría se validará estimando la escala mediante altimetría GPS estándar y el ángulo del gimbal.
•	Deduplicación Espacial Compleja: No se implementará el Filtro de Kalman de memoria persistente en este mockup, ya que el objetivo principal es recolectar datos y medir el rendimiento del reconocimiento del individuo, no auditar el conteo de un lote completo.

