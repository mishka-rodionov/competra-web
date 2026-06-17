# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**competra-web** — веб-приложение для управления соревнованиями по ориентированию. Kotlin Multiplatform + Compose Multiplatform, скомпилированный в WebAssembly (wasmJs). Деплоится на GitHub Pages.

## Build Commands

```bash
# Сборка production-дистрибутива (выход: web/build/dist/wasmJs/productionExecutable)
./gradlew :web:wasmJsBrowserDistribution

# Запуск dev-сервера с hot reload
./gradlew :web:wasmJsBrowserDevelopmentRun

# Сборка только shared-модуля
./gradlew :shared:build

# Полная сборка
./gradlew build
```

Тестов в проекте нет. CI запускает `./gradlew :web:wasmJsBrowserDistribution --no-daemon` и деплоит содержимое `web/build/dist/wasmJs/productionExecutable` на GitHub Pages при каждом пуше в `master`.

## Architecture

Проект состоит из двух Gradle-модулей:

### `:shared` (commonMain + wasmJsMain)
Платформонезависимая бизнес-логика:
- `data/api/` — `ApiClient.kt` (два Ktor HttpClient: `public` без токена и `auth` с Bearer), `ApiResult<T>` (sealed class Success/Error), `CommonModel<T>` (обёртка ответа `{status, result, errors}`)
- `data/auth/` — `TokenStorage` (интерфейс), `LocalStorageTokenStorage` (реализация через `@JsFun` / `localStorage`); `AuthRepository` — email + code flow
- `data/repository/` — `CompetitionRepository` (публичные и авторизованные списки соревнований), `DistanceRepository` (список дистанций, импорт XML)
- `domain/models/` — `Competition` (плоская модель от `/public`), `OrienteeringCompetition` (обёртка с `competitionId` + вложенным `Competition`)

### `:web` (wasmJsMain only)
Compose Multiplatform UI, рендерится через `ComposeViewport(document.body!!)`:
- `Main.kt` — точка входа: инициализация Koin, запуск `App()`
- `App.kt` — навигация через sealed class `Page` (Competitions / CompetitionDetail / Profile), `MainScaffold` с bottom navigation
- `di/AppModule.kt` — Koin DI: два именованных HttpClient (`"public"`, `"auth"`), репозитории
- `pages/` — `CompetitionsPage` (табы «Публичные» / «Мои»), `CompetitionDetailPage` (табы «Дистанции» / «Результаты»), `LoginPage`, `ProfilePage`
- `theme/` — `CompetiraTheme`

## Key Patterns

**API responses**: Все ответы бэкенда (`https://competra.ru/api`) оборачиваются в `CommonModel<T>`. Успех определяется по `status == 1`, не по HTTP-коду. Используй `safeApiCall { ... }` или `safeApiCallUnit { ... }` для обработки.

**Два типа соревнований**: `/public` возвращает `List<Competition>` (плоская структура, поле `remoteId: Long?`). Авторизованные эндпоинты возвращают `List<OrienteeringCompetition>` (поле `competitionId: String` + вложенный `competition: Competition`). Не путай эти две модели.

**DI**: Koin, все зависимости объявлены в `AppModule`. В composable-функциях использовать `koinInject()`.

**Токен**: хранится в `localStorage` под ключом `competra_access_token`. Нативный JS вызывается через `@JsFun`.

## Межпроектные связи

Этот проект — часть экосистемы из четырёх репозиториев:

| Проект | Путь | Роль |
|---|---|---|
| **eSport** (backend) | `/Users/rodionov/backend_projects/eSport` | Ktor-сервер, источник всего API |
| **competra-android** | `/Users/rodionov/android_projects/competra-android` | Android-приложение с той же доменной областью |
| **mapper** | `/Users/rodionov/android_projects/mapper` | Qt/C++ редактор карт, создаёт дистанции в формате IOF XML |

### Правила для Claude

**При изменении функционала** (новый экран, новый API-вызов, новая бизнес-логика) — **спроси пользователя**: нужно ли то же самое сделать в `competra-android`? Обе платформы покрывают одну доменную область, и фичи часто должны быть на обеих.

**При изменении модели данных или API-вызова** — **спроси**: не сломает ли это бэкенд (eSport)? Контракт: `CommonModel<T>` с `status == 1`, `BASE_URL = https://competra.ru/api`.

**NFC-фичи** (`DistanceRepository.importFromXml`, чтение чипов) — специфичны для Android, в Web аналога нет.

### Цепочка IOF XML
Mapper экспортирует дистанции → пользователь загружает файл через `POST /event/orienteering/import/courses` (реализовано в `shared/data/repository/DistanceRepository.importFromXml`) → eSport парсит через `IOFXmlParser.kt`. Если меняется логика загрузки, уточни, не нужно ли обновить парсер в eSport.
