# BetterThanBrewery

Конфигурируемый плагин пивоварения, дистилляции, выдержки и чаеварения для **Paper 26.2 / Java 25**. GUI построены через **CustomGuiReworked 2.x** — состояние станций хранится в его персистентном BLOCK-хранилище и продолжает работать без открытого меню.

## Что уже реализовано

- Бойлер, дистиллятор, бочонок и малый чайник: свои графические меню на 54 слота.
- CraftEngine и ItemsAdder: ID блоков берутся из `config.yml`; GUI-фреймворк открывает их по правому клику. Предметы `craftengine:...` и `itemsadder:...` разрешаются без жёсткой зависимости и не ломают сервер, если интеграция не установлена.
- Рецепты рекурсивно читаются из `plugins/BetterThanBrewery/recipes/**/*.yml`. В каждом файле поле `station` явно указывает станцию. Можно добавлять сколько угодно файлов и папок.
- Жидкость — это состояние станции с уровнем и возрастом. Забирать её можно только пустой тарой. Количество единиц на одну тару задаётся в секции `containers`.
- Ингредиенты, вода, топливо, время, идеальное время, окно переваривания, побочные продукты с шансом, частицы и звуки.
- Напитки используют Potion или предмет CraftEngine/ItemsAdder и получают PDC-теги `drink`, `age-weeks`, `alcohol`, `quality`, `units`. Поэтому кастомный предмет сохраняет механику плагина.
- HEX и именованные цвета, hunger, эффекты, lore, команды Bukkit/CommandAPI-совместимые команды и Denizen script hooks.
- Выдержка в неделях и безопасные формулы: `+`, `-`, `*`, `/`, `%`, `^`, скобки, `min`, `max`, `clamp`, `abs`, `floor`, `ceil`, `round`, `sqrt`, `if`. Формулы не исполняют Java/команды.
- Опьянение 0–100: спад, стадии, эффекты, плавный случайный микросдвиг, чат-замены, actionbar, optional resource-pack overlay и plugin-message hook для pitch-аддона Simple Voice Chat.
- Локальный title через CustomGuiReworked, `gui.title-offset` для ресурс-пак шрифтов, анимация уровня жидкости через local design, цветные индикаторы качества, подсказки прямо в слотах воды/переливания, частицы и звуки.
- Профессиональный reload: открытые станционные GUI безопасно закрываются, рецепты валидируются, ошибки отдельных файлов не ломают остальные рецепты.

## Установка

1. Установить Paper 26.2, CustomGuiReworked 2.x и (по желанию) CraftEngine, ItemsAdder, Denizen и voice-chat.
2. Положить BetterThanBrewery в `plugins/` и один раз запустить сервер.
3. В `plugins/BetterThanBrewery/config.yml` заменить примерные `stations.*.blocks` на реальные ID блоков.
4. Редактировать рецепты в `plugins/BetterThanBrewery/recipes/`; `/betterbrewery reload` перечитывает их.

В каждом рецепте `output.name` и `output.color` обязательны; остальные поля напитка можно не указывать. `output.item` позволяет заменить Potion на CraftEngine/ItemsAdder предмет.

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

Фреймворк — не просто InventoryClickListener: у него есть скелет `DESIGN/CRAFT/FUEL/CONTAINER/RESULT`, BLOCK storage, local title/design для конкретного зрителя, функциональные блоки с `onBlockTick` и события фактического изменения слотов. BetterThanBrewery использует именно эти API: закрытие GUI не останавливает варку, предметы не лежат в памяти игрока, а экранная жидкость не может быть украдена как декоративный предмет.

Pitch Simple Voice Chat требует небольшого клиентского/voice addon, который слушает канал `betterthanbrewery:voice_pitch` (float pitch, int duration, UTF-8 player name). Это сделано намеренно: официальный Bukkit API Simple Voice Chat не меняет pitch входящего микрофона сам по себе. Без addon остальные стадии опьянения продолжают работать.
