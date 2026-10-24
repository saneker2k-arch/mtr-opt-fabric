# Проверки вне Minecraft

Запускаются обычной JVM (Java 17+) с jar MTR 4.0.5 в classpath. Минимальные заглушки Mixin, Fabric, LWJGL, slf4j и Gson
нужны только для компиляции исходников аддона вне Gradle (см. docs/OPTIMIZATION_REPORT.md).

| Файл | Что проверяет |
|---|---|
| `LeakProof` | Запускает настоящий `CachedResource` из MTR: 100 000 ресурсов, которыми никто не владеет, остаются в статическом реестре навсегда, а сырые данные, захваченные загрузчиками, остаются в памяти (около 196 МБ). Это доказательство утечки |
| `InitProbe` | Все рефлексивные обращения `VehiclePrewarm` находят поля и методы в настоящих классах MTR |
| `GpuProbe` | То же для `GpuRelease` (`OptimizedModelWrapper.optimizedModel`, `OptimizedModel.close()`, 4 поля `VehicleResourceCacheHolder`) и для `CachedResource.CACHED_RESOURCES` |
| `SafetyTest` | Значения по умолчанию, аварийное отключение (`Safety.trip`) и вывод статистики |
| `RegistryTest` | Логика `ResourceRegistry`: слабые ссылки, срок хранения, отложенное освобождение, `disposeAll`, бюджет сборки |

Что эти проверки НЕ покрывают: применение Mixin-хуков и работу в самой игре.
