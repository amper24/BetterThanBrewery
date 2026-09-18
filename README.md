# BetterThanBrewery

Конфигурируемый плагин пивоварения, дистилляции, выдержки и чаеварения для **Paper 26.2 / Java 25**. GUI построены через **CustomGuiReworked 2.x** — состояние станций хранится в его персистентном BLOCK-хранилище и продолжает работать без открытого меню.

## Что уже реализовано

- Бойлер, дистиллятор, бочонок и малый чайник: свои графические меню на 54 слота.
- CraftEngine и ItemsAdder: ID блоков берутся из `config.yml`; GUI-фреймворк открывает их по правому клику. Предметы `craftengine:...` и `itemsadder:...` разрешаются без жёсткой зависимости и не ломают сервер, если интеграция не установлена.
- Рецепты рекурсивно читаются из `plugins/BetterThanBrewery/recipes/` во всех файлах `*.yml` и `*.yaml`. Поддерживаются и отдельные файлы с одним рецептом, и один файл с несколькими рецептами через секцию `recipes:`. Можно добавлять сколько угодно файлов и папок.
- Жидкость — это состояние станции с уровнем и возрастом. Забирать её можно только пустой тарой. Количество единиц на одну тару задаётся в секции `containers`.
- Ингредиенты, вода, топливо, время, идеальное время, окно переваривания, побочные продукты с шансом, частицы и звуки.
- Напитки используют Potion или предмет CraftEngine/ItemsAdder и получают PDC-теги `drink`, `age-weeks`, `alcohol`, `quality`, `units`. Поэтому кастомный предмет сохраняет механику плагина.
- HEX и именованные цвета, hunger, эффекты, lore, команды Bukkit/CommandAPI-совместимые команды и Denizen script hooks.
- Выдержка в неделях и безопасные формулы: `+`, `-`, `*`, `/`, `%`, `^`, скобки, `min`, `max`, `clamp`, `abs`, `floor`, `ceil`, `round`, `sqrt`, `if`. Формулы не исполняют Java/команды.
- Опьянение 0–100: спад, стадии, эффекты, плавный случайный микросдвиг, чат-замены, actionbar, optional resource-pack overlay и plugin-message hook для pitch-аддона Simple Voice Chat.
- Локальный title через CustomGuiReworked, `gui.title-offset` для ресурс-пак шрифтов, анимация уровня жидкости через local design, цветные индикаторы качества, подсказки прямо в слотах воды/переливания, частицы и звуки.
- Профессиональный reload: открытые станционные GUI безопасно закрываются, рецепты валидируются, ошибки отдельных файлов не ломают остальные рецепты.

## Проверки и CI

В `src/test` находятся unit-тесты формул и цветов. GitHub Actions запускает `clean test build` на Java 25 для каждого push и pull request (`.github/workflows/build.yml`). Локальный запуск: `./gradlew test`; для полной проверки — `./gradlew clean test build`.

## Установка

1. Установить Paper 26.2, CustomGuiReworked 2.x и (по желанию) CraftEngine, ItemsAdder, Denizen и voice-chat.
2. Положить BetterThanBrewery в `plugins/` и один раз запустить сервер.
3. В `plugins/BetterThanBrewery/config.yml` заменить примерные `stations.*.blocks` на реальные ID блоков.
4. Редактировать рецепты в `plugins/BetterThanBrewery/recipes/`; `/betterbrewery reload` перечитывает их.

В каждом рецепте `output.name` и `output.color` обязательны; остальные поля напитка можно не указывать. `output.item` позволяет заменить Potion на CraftEngine/ItemsAdder предмет.

## Интерфейсы, скелет и выпадение при разрушении

Станционные GUI создаются именно через CustomGuiReworked: используется `CustomGuiAPI.builder(...)`, `StorageType.BLOCK` и функциональный блок с `onBlockTick`, `onItemChanged` и обработчиком кликов. В плагине нет отдельного самописного `InventoryClickEvent`-GUI и нет собственного обработчика `BlockBreakEvent` для станций. Персистентное содержимое блока и его выпадение при разрушении отдаётся CustomGuiReworked; BetterThanBrewery отвечает за состояние жидкости, прогресс, рецепты и побочные продукты. Для ItemsAdder-станций плагин дополнительно адресует BLOCK-хранилище через официальный `StorageKey.forBlock`, чтобы содержимое имело тот же ключ мира и координат.

По умолчанию каждый слот сначала становится `DESIGN` и получает заполнитель. Затем из секции `stations.<id>.skeleton` нужные слоты переводятся в типы CustomGuiReworked:

- `craft` — ингредиенты;
- `fuel` — топливо;
- `container` — обычный персистентный контейнер;
- `fluid` — колонка жидкости на персистентных `CONTAINER`-слотах; клики, drag и shift-click блокируются, а фактическое поведение даёт FunctionalBlock;
- неуказанные слоты остаются `design`.

Заполнитель может быть ванильным или кастомным предметом с provider и namespace:

```yaml
gui:
  filler: "minecraft:black_stained_glass_pane"

stations:
  boiler:
    # необязательно: переопределяет gui.filler только для бойлера
    filler: "itemsadder:brewery:boiler_filler"
    skeleton:
      fluid: [7, 8, 16, 17, 25, 26, 34, 35, 43, 44]
      craft: [10, 11, 12, 13, 14, 15]
      fuel: []
      container: []
```

Для CraftEngine используется такой же формат: `craftengine:brewery:boiler_filler`. Если секцию `skeleton` удалить, сохраняется обратная совместимость со старыми `ingredient-slots`; основной результат всё равно отображается и забирается только через fluid-колонки.

## Книга рецептов

Книга открывается правым кликом по настроенному предмету и использует GUI-фреймворк CustomGuiReworked. По умолчанию это обычная `minecraft:book`, но предмет можно заменить на ItemsAdder или CraftEngine в `config.yml`:

```yaml
recipe-book:
  enabled: true
  items:
    - "itemsadder:brewery:recipe_book"
    - "craftengine:brewery:recipe_book"
  recipes: [] # пусто — показывать все рецепты; иначе список ID
  gui:
    title: "&6Книга рецептов"
    recipe-icon: "minecraft:potion"
    recipe-slots: [10, 11, 12, 13, 14, 15, 16]
```

Поддерживаются префиксы владельца `itemsadder:`/`ia:` и `craftengine:`/`ce:`. Полный ID записывается как `itemsadder:<namespace>:<item>` или `craftengine:<namespace>:<item>`, например `itemsadder:brewery:recipe_book`. Namespace и ID после префикса передаются в провайдер как единый ID `brewery:recipe_book`. Если указать просто `brewery:recipe_book`, загрузчик попробует оба провайдера. Можно указать один предмет через `recipe-book.item` вместо списка `recipe-book.items`. Предмет не расходуется. Клик по напитку открывает подробную страницу с ингредиентами, станцией, временем, водой, крепостью и выдержкой. Размер GUI, кнопки страниц, слоты, иконки, внешний вид и тексты полностью настраиваются в секции `recipe-book.gui`. После `/betterbrewery reload` книга и список рецептов перестраиваются без перезапуска сервера.

## Базовый набор рецептов

В поставку добавлен небольшой стартовый набор в `recipes/brewery/`:

- бойлер: пшеничное пиво, ягодное вино, медовуха;
- дистиллятор: зерновая водка, ягодный спирт, виски, ром и абсент;
- бочонок: дубовое пиво и выдержанное ягодное вино;
- чайник: чёрный, мятный и ягодный чай, кофе.

Это не жёсткая игровая система: каждый YAML можно менять, копировать или удалять. Например, чтобы сделать ром, достаточно заменить ингредиенты, цвет, формулу и `input-fluid`.

## Один файл с несколькими рецептами

Обычный формат с одним рецептом в файле продолжает работать. Если рецептов много, их можно собрать в один `recipes.yml` или в любой другой YAML-файл внутри папки `recipes/`:

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
    time: 900
    ingredients:
      - item: minecraft:sweet_berries
        amount: 6
    output:
      name: "&dЯгодное вино"
      color: "#A8326B"
      alcohol: 8
```

Ключ `wheat_beer` используется как `id`, если `id` внутри рецепта не указан. Также допускается YAML-список под `recipes:` — в таком варианте каждый элемент должен иметь свой `id`:

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

Оба варианта можно использовать одновременно: загрузчик рекурсивно собирает рецепты из всех `.yml` и `.yaml`. При совпадении ID последний прочитанный рецепт заменяет предыдущий и записывается предупреждение в консоль.

## Формат формулы выдержки

```yaml
station: barrel
input-fluid: apple_cider
weeks: 4
formulas:
  alcohol: "alcohol + age * 0.75"
  food: "food + floor(age / 2)"
```

`age`, `weeks`, `alcohol`, `base_alcohol`, `food`, `base_food`, `quality` и `potency` доступны в формулах. Качество автоматически влияет на базовую крепость через `potency`, а формулы могут изменить это поведение. Для эффектов доступны `duration-formula` и `amplifier-formula`.

## Важное про CustomGuiReworked

Фреймворк — не просто InventoryClickListener: у него есть скелет `DESIGN/CRAFT/FUEL/CONTAINER/RESULT`, BLOCK storage, local title/design для конкретного зрителя, функциональные блоки с `onBlockTick` и события фактического изменения слотов. BetterThanBrewery использует именно эти API: закрытие GUI не останавливает варку, предметы не лежат в памяти игрока, а экранная жидкость не может быть украдена как декоративный предмет. Станции больше не используют framework-слоты `RESULT`: жидкость отображается и забирается только через fluid-колонки `CONTAINER`, а все их клики блокируются и обрабатываются FunctionalBlock вручную.

Pitch Simple Voice Chat требует небольшого клиентского/voice addon, который слушает канал `betterthanbrewery:voice_pitch` (float pitch, int duration, UTF-8 player name). Это сделано намеренно: официальный Bukkit API Simple Voice Chat не меняет pitch входящего микрофона сам по себе. Без addon остальные стадии опьянения продолжают работать.
