# BetterThanBrewery

Конфигурируемый плагин пивоварения, дистилляции, выдержки и чаеварения для **Paper 26.2 / Java 25**. Все станционные интерфейсы создаются через **CustomGuiReworked 2.x**, а CraftEngine и ItemsAdder подключаются мягко и используются по namespace-ID из конфигурации.

## Основная идея

BetterThanBrewery разделяет три вещи:

1. **GUI и персистентность** — ответственность CustomGuiReworked.
2. **Состояние технологического процесса** — ответственность BetterThanBrewery.
3. **Определение предметов и блоков** — vanilla, ItemsAdder или CraftEngine через конфигурацию.

Станция продолжает работать после закрытия меню и рестарта сервера. Состояние не хранится в памяти игрока: оно привязано к локации функционального блока и сохраняется через BLOCK storage CustomGuiReworked.

Поддерживаемые станции:

- бойлер;
- дистиллятор;
- бочонок;
- чайник.

## Что изменено в модели результатов

Станции больше **не используют RESULT-слоты**.

Удалены из конфигурации и модели станций:

- `result-slot`;
- `byproduct-slots`;
- `StationDefinition.resultSlot`;
- `StationDefinition.byproductSlots`;
- назначение `SlotType.RESULT` для станционных меню.

Основной результат любого процесса теперь является жидкостью. Он хранится в графе жидкости и показывается в fluid-колонках. Выдача происходит только через настроенную тару.

Твёрдые побочные продукты не занимают слоты результата. Если у рецепта сработал `byproducts`, предмет выпадает рядом со станцией.

## Fluid-колонки

Fluid-колонки — это список слотов, в которых визуально показывается уровень жидкости. По умолчанию используются:

```yaml
gui:
  fluid-slots: [7, 8, 16, 17, 25, 26, 34, 35, 43, 44]
```

Для каждой станции список можно переопределить в скелете:

```yaml
stations:
  boiler:
    skeleton:
      fluid: [7, 8, 16, 17, 25, 26, 34, 35, 43, 44]
```

### Как зарегистрированы fluid-слоты

Fluid-слоты регистрируются через CustomGuiReworked как обычные персистентные `CONTAINER`-слоты. Это позволяет использовать BLOCK storage фреймворка и сохранить единый механизм хранения.

При этом они не являются обычными контейнерами для игрока. BetterThanBrewery блокирует их вручную:

- обычный клик пустой рукой;
- попытку положить любой предмет;
- попытку забрать визуальный предмет;
- drag по fluid-колонке;
- shift-click из инвентаря игрока в станцию.

Фактический клик обрабатывается `FunctionalBlock` и `StationManager`.

В текущем API CustomGuiReworked ещё нет внешней регистрации новых `SlotType`. Поэтому `fluid` — это конфигурационное имя специальной механики BetterThanBrewery, которая мапится на `SlotType.CONTAINER` и блокируется плагином. Когда во фреймворке появится API кастомных типов слотов, fluid можно будет перенести на отдельный нативный тип без изменения рецептов.

### Состояние жидкости

Для блока используются persistent keys CustomGuiReworked:

| Ключ | Значение |
|---|---|
| `fluid` | ID текущей жидкости |
| `fluid-level` | количество жидкостных единиц |
| `age-ticks` | возраст жидкости для выдержки |
| `quality` | качество результата |
| `progress` | прогресс текущего процесса |
| `recipe` | активный рецепт |
| `fluid-render` | технический ключ последнего визуального состояния |

`fluid-render` нужен для оптимизации: визуальные предметы не перезаписываются в storage каждый тик, если уровень, возраст и качество не изменились.

### Визуальный предмет жидкости

В fluid-слотах хранится только безопасная визуализация:

```yaml
gui:
  fluid-icon: minecraft:potion
```

Визуализация получает имя, цвет и lore жидкости, но не получает PDC-теги напитка BetterThanBrewery. Поэтому даже если CustomGuiReworked отдаст визуальный предмет при разрушении блока, это не будет настоящим напитком и не позволит обойти правило тары.

Настоящий напиток создаётся только при переливании в подходящую тару через `DrinkService.createFilled(...)`.

## Получение жидкости только тарой

Тара настраивается в `config.yml`:

```yaml
containers:
  bottle:
    empty: minecraft:glass_bottle
    filled: minecraft:potion
    units: 1

  cup:
    empty: minecraft:bowl
    filled: minecraft:potion
    units: 1
```

Для кастомной тары используются те же provider и namespace-ID:

```yaml
containers:
  brewery_bottle:
    empty: itemsadder:brewery:empty_bottle
    filled: itemsadder:brewery:filled_bottle
    units: 2
```

Алгоритм выдачи жидкости:

1. Игрок кликает по fluid-колонке.
2. Плагин проверяет предмет на курсоре через `containers.*.empty`.
3. Если предмет не является пустой тарой, действие отменяется.
4. Если жидкостных единиц меньше, чем требует тара, действие отменяется.
5. Из состояния станции вычитается `units`.
6. Создаётся заполненная тара из `containers.*.filled`.
7. В заполненный предмет записываются PDC-теги:
   - `drink`;
   - `age-weeks`;
   - `alcohol`;
   - `quality`;
   - `units`.

Таким образом, жидкость невозможно получить кликом по визуальной колонке, обычным предметом или пустой рукой.

Вода работает по тому же принципу. Источник воды задаётся так:

```yaml
water:
  source-items:
    - minecraft:water_bucket
    - minecraft:potion_water
  bucket-units: 4
  bottle-name: "&bВода"
```

## Архитектура GUI

Станционное меню строится в `StationManager.registerGui(...)` через:

```java
CustomGuiAPI.builder(station.gui())
    .title(title)
    .size(54)
    .storage(StorageType.BLOCK);
```

Дальше применяется скелет:

1. Все 54 слота получают тип `DESIGN` и предмет-заполнитель.
2. Fluid-колонки переводятся в `CONTAINER`.
3. Слоты `craft` переводятся в `CRAFT`.
4. Слоты `fuel` переводятся в `FUEL`.
5. Слоты `container` переводятся в `CONTAINER`.
6. Все остальные слоты остаются декоративными `DESIGN`.

Функциональный блок регистрируется через:

```java
FunctionalBlock.builder(blockId)
    .gui(station.gui())
    .onOpen(...)
    .onTick(...)
    .onClick(...)
    .onItemChanged(...)
    .onBlockTick(...)
    .register();
```

Используются именно события и storage CustomGuiReworked, а не самописный Bukkit inventory GUI.

### Скелет станции

```yaml
stations:
  boiler:
    enabled: true
    gui: boiler
    capacity: 10
    water-capacity: 10
    water-input-slot: 40
    ingredient-slots: [10, 11, 12, 13, 14, 15]
    skeleton:
      fluid: [7, 8, 16, 17, 25, 26, 34, 35, 43, 44]
      craft: [10, 11, 12, 13, 14, 15]
      fuel: []
      container: []
```

Доступные ключи скелета:

| Ключ | Тип CustomGuiReworked | Назначение |
|---|---|---|
| `fluid` | `CONTAINER` + ручная блокировка | жидкостные колонки |
| `craft` | `CRAFT` | ингредиенты рецепта |
| `fuel` | `FUEL` | топливо |
| `container` | `CONTAINER` | обычные персистентные слоты |
| остальные | `DESIGN` | декоративный filler |

Если секцию `skeleton` удалить, используются старые поля `ingredient-slots`, а fluid-слоты берутся из `gui.fluid-slots`.

### Входные слоты

Входные слоты жидкости не являются результатами:

```yaml
stations:
  boiler:
    water-input-slot: 40

  distiller:
    fluid-input-slot: 40

  barrel:
    fluid-input-slot: 40
```

- бойлер и чайник принимают воду;
- дистиллятор принимает напиток с PDC-тегом BetterThanBrewery;
- бочонок принимает напиток с PDC-тегом BetterThanBrewery;
- fluid-колонки показывают состояние жидкости и используются для забора через тару.

## Заполнители интерфейса

Глобальный filler:

```yaml
gui:
  filler: minecraft:black_stained_glass_pane
```

Filler отдельной станции:

```yaml
stations:
  boiler:
    filler: itemsadder:brewery:boiler_filler
```

Поддерживаются vanilla и кастомные предметы:

```yaml
minecraft:black_stained_glass_pane
itemsadder:brewery:boiler_filler
craftengine:brewery:boiler_filler
ia:brewery:boiler_filler
ce:brewery:boiler_filler
```

Plain namespaced ID тоже поддерживается:

```yaml
brewery:boiler_filler
```

В таком случае ItemService попробует найти предмет сначала в ItemsAdder, затем в CraftEngine.

## ItemsAdder, CraftEngine и namespace

Полный provider-ID состоит из трёх частей:

```text
provider:namespace:item_id
```

Примеры:

```yaml
itemsadder:brewery:recipe_book
craftengine:brewery:recipe_book
itemsadder:decor:boiler_filler
craftengine:decor:filled_bottle
```

Внутрь API провайдера передаётся именно namespaced ID без provider-префикса:

```text
brewery:recipe_book
brewery:boiler_filler
```

Это правило работает одинаково для:

- ингредиентов;
- топлива;
- тары;
- результата напитка;
- filler предметов;
- иконок GUI;
- предмета книги рецептов;
- блоков станций.

## Книга рецептов

Книга открывается правым кликом по настроенному предмету через CustomGuiReworked. Она не расходуется.

```yaml
recipe-book:
  enabled: true
  items:
    - "itemsadder:brewery:recipe_book"
    - "craftengine:brewery:recipe_book"

  # Пустой список показывает все загруженные рецепты.
  recipes: []

  gui:
    title: "&6Книга рецептов"
    detail-title: "&6Рецепт: &f{recipe}"
    size: 54
    filler: "minecraft:black_stained_glass_pane"
    recipe-icon: "minecraft:potion"
    recipe-slots: [10, 11, 12, 13, 14, 15, 16]
```

Можно указать один предмет:

```yaml
recipe-book:
  item: "itemsadder:brewery:recipe_book"
```

Если указать plain namespaced ID, будут проверены оба optional-провайдера:

```yaml
recipe-book:
  item: "brewery:recipe_book"
```

Книга поддерживает:

- страницы;
- список разрешённых ID рецептов;
- иконки напитков;
- подробную страницу рецепта;
- список ингредиентов;
- информацию о станции;
- время;
- воду;
- крепость;
- выдержку;
- кастомные filler и кнопки.

После `/betterbrewery reload` страницы книги перестраиваются.

## Рецепты

Рецепты можно хранить по одному в файле:

```yaml
id: wheat_beer
station: boiler
time: 700
ideal-time: 700
overcook-window: 350
water: 3
ingredients:
  - item: minecraft:wheat
    amount: 3
output:
  id: wheat_beer
  name: "&6Пшеничное пиво"
  color: "#D49A45"
  alcohol: 6
```

Или несколько рецептов в одном файле:

```yaml
recipes:
  wheat_beer:
    station: boiler
    time-seconds: 35
    water: 3
    ingredients:
      - item: minecraft:wheat
        amount: 3
    output:
      name: "&6Пшеничное пиво"
      color: "#D49A45"
      alcohol: 6

  berry_wine:
    id: berry_wine
    station: boiler
    ingredients:
      - item: minecraft:sweet_berries
        amount: 6
    output:
      name: "&dЯгодное вино"
      color: "#A8326B"
      alcohol: 8
```

Также поддерживается список:

```yaml
recipes:
  - id: mint_tea
    station: kettle
    ingredients:
      - item: minecraft:short_grass
        amount: 2
    output:
      name: "&aМятный чай"
      color: "#73C56B"
  - id: coffee
    station: kettle
    ingredients:
      - item: minecraft:cocoa_beans
        amount: 2
    output:
      name: "&8Кофе"
      color: "#6B3E26"
```

Загрузчик рекурсивно читает все `.yml` и `.yaml` внутри:

```text
plugins/BetterThanBrewery/recipes/**/*.yml
plugins/BetterThanBrewery/recipes/**/*.yaml
```

При совпадении ID последний загруженный рецепт заменяет предыдущий, а в консоль записывается предупреждение.

## Поля рецепта

Обязательные поля напитка:

```yaml
output:
  name: "Название"
  color: "#D49A45"
```

Остальные поля опциональны:

```yaml
output:
  id: wheat_beer
  name: "&6Пшеничное пиво"
  color: "#D49A45"
  item: "itemsadder:brewery:wheat_beer"
  food: 3
  alcohol: 6
  lore:
    - "&7Мягкая солодовая основа."
  effects:
    - type: NIGHT_VISION
      duration: 80
      amplifier: 0
      chance: 1.0
```

Поддерживаются:

- `station`;
- `ingredients`;
- `water` или `water-units`;
- `time`;
- `time-seconds`;
- `ideal-time`;
- `max-time`;
- `overcook-window`;
- `fuel.item`;
- `fuel.amount`;
- `input-fluid`;
- `output.fluid`;
- `weeks` или `age-weeks`;
- `byproducts`;
- `formulas`;
- `food`;
- `alcohol`;
- `effects`;
- `commands`;
- `denizen-script`;
- `output.item`.

## Цепочки производства

Рецепты могут соединяться через ID жидкости:

```text
бойлер/чайник → жидкость → дистиллятор → жидкость → бочонок → заполненная тара
```

Пример:

```yaml
# boiler recipe
id: wheat_beer
station: boiler
output:
  id: wheat_beer
  fluid: wheat_beer
  name: "&6Пшеничное пиво"
  color: "#D49A45"
```

```yaml
# distiller recipe
id: grain_vodka
station: distiller
input-fluid: wheat_beer
output:
  id: grain_vodka
  fluid: grain_vodka
  name: "&fЗерновая водка"
  color: "#E8F4FF"
```

```yaml
# barrel recipe
id: oak_beer
station: barrel
input-fluid: wheat_beer
weeks: 4
output:
  id: oak_beer
  fluid: oak_beer
  name: "&6Дубовое пиво"
  color: "#B87830"
```

## Выдержка и формулы

```yaml
station: barrel
input-fluid: apple_cider
weeks: 4
formulas:
  alcohol: "alcohol + age * 0.75"
  food: "food + floor(age / 2)"
```

Доступные переменные:

- `age`;
- `weeks`;
- `alcohol`;
- `base_alcohol`;
- `food`;
- `base_food`;
- `quality`;
- `potency`.

Поддерживаются безопасные операции:

```text
+ - * / % ^
min max clamp abs floor ceil round sqrt if
```

Формулы не исполняют Java-код, команды или произвольные выражения.

## Разрушение станции и persistence

BetterThanBrewery не реализует собственный `BlockBreakEvent` для станций.

Фреймворк CustomGuiReworked отвечает за:

- BLOCK storage;
- связь GUI с миром и координатами блока;
- сохранение содержимого;
- закрытие открытых GUI;
- обработку разрушения функционального блока;
- выпадение содержимого persistent-слотов.

BetterThanBrewery отвечает за:

- liquid graph;
- `fluid`, `fluid-level`, `age-ticks`, `quality`;
- прогресс;
- проверку рецепта;
- выдачу только через тару;
- безопасные fluid-визуализации;
- выпадение твёрдых побочных продуктов после успешного процесса.

Для ItemsAdder storage дополнительно адресуется через `StorageKey.forBlock(...)`, чтобы ключ использовал тот же мир и координаты блока.

## Команды

```text
/betterbrewery reload
```

Перечитывает:

- `config.yml`;
- сообщения;
- контейнеры;
- рецепты;
- станции;
- книгу рецептов;
- GUI-скелеты.

```text
/betterbrewery info
```

Показывает версию, количество рецептов, напитков и станций.

## Проверки и CI

Тесты находятся в `src/test`.

Проверяются:

- цветовые форматы;
- формулы;
- наличие bundled-рецептов;
- загрузка нескольких рецептов из одного YAML;
- provider/namespace-разбор custom item ID.

GitHub Actions запускает:

```text
./gradlew clean test build --no-daemon --stacktrace
```

CI выполняется на Temurin Java 25 для push и pull request.

Локальная проверка:

```bash
./gradlew test
./gradlew clean test build
```

## Установка

1. Установить Paper 26.2.
2. Установить CustomGuiReworked 2.x.
3. По необходимости установить CraftEngine, ItemsAdder, Denizen и voice-chat.
4. Положить BetterThanBrewery в `plugins/`.
5. Запустить сервер.
6. Настроить `stations.*.blocks` под реальные ID CraftEngine или ItemsAdder.
7. Настроить `recipe-book.items`, если нужна кастомная книга.
8. Перезапустить сервер или выполнить `/betterbrewery reload`.

Без установленного ItemsAdder или CraftEngine соответствующие ID не ломают загрузку плагина: предмет считается недоступным и пропускается.
