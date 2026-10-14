# Отчёт по переносу оптимизаций MTR-34 → MTR 4.0.5

## Как проводился анализ

- Исходники MTR 4.0.5 прочитаны напрямую (рендер, `WorkerThread`, `MainRenderer`, `RenderRails`, `RenderVehicles`, сборка).
- Мод MTR-34 (2.2.1-DEV-106, MIT) — это аддон для **MTR 3.1.12+**. Его jar был разобран только по списку миксинов,
  структуре пакетов и строкам констант в `.class`-файлах. Декомпиляции кода не было (в среде нет JDK/декомпилятора),
  поэтому отдельные пункты ниже помечены как «по названию и сигнатурам».
- Код не компилировался и не запускался: в среде нет `javac` и доступа в сеть. Проверка сборки — первый запуск CI на GitHub.

## Главный вывод

MTR-34 написан под внутренности MTR 3 (`mtr.data.RailwayData`, `mtr.data.Siding`, `mtr.data.Rail` и т.д.).
В MTR 4 всё это переписано: симуляция вынесена в отдельное ядро (`Transport-Simulation-Core`), рендер использует
собственный слой `org.mtr.mapping` с `OptimizedRenderer`. Миксины MTR-34 напрямую перенести нельзя,
переносить можно только идеи.

## Таблица

| Компонент MTR-34 | Статус в MTR 4 |
|---|---|
| `sowcer` (батчинг, instancing, VAO/InstanceBuf) | **Уже есть** в MTR 4 (`OptimizedRenderer`, `QueuedRenderLayer`, батчи по `Identifier`) |
| Occlusion/дальность отрисовки рельсов (`RailDistanceRenderer`, `RenderTrainsMixin`) | **Перенесено** как `maxRailRenderDistanceBlocks` |
| Снижение нагрузки при работе с рельсами (идея MTR-34: не делать лишних проверок мира каждый кадр) | **Новое для MTR 4**: `nodeCheckRadiusBlocks` (в MTR 4 куб 33x33x33 блоков проверяется каждый кадр) |
| Кэш генерируемых текстур (идея MTR-34: контроль памяти динамических ресурсов) | **Новое для MTR 4**: `dynamicTextureLifetimeMillis` |
| «Запоминание» поезда: не пересобирать модель при каждом возвращении в поле зрения (в MTR-34 это делал слой `sowcer`/`DynamicTrainModelLoader`; назначение определено по названиям классов, без декомпиляции) | **Перенесено** как `vehicleModelCacheMillis`: в MTR 4 модели уже собираются в оптимизированные буферы, но через 60 с без обращений кэш `CachedResource` сбрасывается; аддон продлевает срок жизни |
| Меньше сетевого трафика для табло | **Новое для MTR 4**: `arrivalsRefreshMillis` |
| Асинхронная работа с видимостью | **Уже есть** (`WorkerThread` с occlusion culling); добавлены настройки радиуса и паузы |
| `DynamicTrainModelLoader`, `ModelBakeryMixin`, `TrainModelCapture` | Не применимо: модели поездов в MTR 4 грузятся через `CustomResourceLoader`/`VehicleResource` |
| `BetterPathFinder` (`PathFinderMixin`) | **Не перенесено**: поиск пути MTR 4 находится в `Transport-Simulation-Core` (в zip только jar без исходников) |
| `RailwayData*Mixin`, `UpdateNearbyMovingObjectsMixin`, `SidingMixin`, `TrainServerMixin`, `LiftServerMixin`, `RailwayDataCooldownModuleMixin` | Не применимо: серверные классы MTR 3 в MTR 4 отсутствуют |
| `RailMixin`, `RailAngleMixin`, `RailTypeMixin`, `BezierCurve`, `RailCalculator` | Не применимо: в MTR 4 математика рельсов в `railMath` ядра |
| Скриптинг (GraalJS/Truffle), EyeCandy, кастомные модели рельсов, GUI | Это функции, а не оптимизации; не переносились |

## Идеи для следующих итераций (требуют исходников ядра или тестов в игре)

1. Ограничение дальности отрисовки поездов/лифтов отдельным параметром (нужен стабильный хук в лямбде `RenderVehicles.render`).
2. Устранение аллокаций `Vector3d` на каждый сегмент рельса в `renderWithinRenderDistance`.
3. Оптимизация пути в ядре (потребуются исходники `Transport-Simulation-Core`).
