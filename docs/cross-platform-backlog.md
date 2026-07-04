# Кросс-платформенный бэклог

Фичи, которые есть в `competra-android`, но ещё не перенесены в `competra-web` (и наоборот). См. раздел «Межпроектные связи» в [CLAUDE.md](../CLAUDE.md) — обе платформы покрывают одну доменную область, парность фич поддерживается вручную через этот файл.

## Таблица сплитов группы (перенос из Android)

**Статус**: не реализовано. Реализовано в `competra-android` (`GroupSplitsTableScreen` в `:feature:center` + `EventGroupSplitsTableScreen` в `:core:eventdetails`).

**Что нужно**: отдельный экран/блок — сплиты **всех** участников группы одной таблицей (участники — строки, КП — колонки, дельта + кумулятив на каждый перегон), с подсветкой лучшего сплита на перегоне. Сейчас `ResultsTab.kt` показывает только место/время/статус на группу — сплиты (`OrienteeringResult.splits`) нигде не отображаются вообще, даже по одному участнику.

### Что уже есть в модели (готово, без изменений)

`OrienteeringResult.splits: List<SplitTime>` и `SplitTime(controlPoint: Int, timestamp: Long)` уже объявлены в `shared/src/commonMain/kotlin/com/competra/domain/models/Participant.kt:43,49` — 1:1 с Android. Бэкенд менять не нужно.

### Расчёт (перенести в `:shared`, а не дублировать в `:web`)

В Android общий расчёт вынесен в чистую функцию `buildSplitsTable()` (`domain/src/main/java/com/competra/domain/models/orienteering/SplitsTableBuilder.kt` в `competra-android`) — стоит завести аналог в `shared/src/commonMain/kotlin/com/competra/domain/` (например `splits/SplitsTableBuilder.kt`), а не писать логику прямо в Compose-коде `:web`, т.к. `:shared` — общий для всех таргетов модуль.

Ключевые моменты алгоритма, которые стоит скопировать один в один (уже отлажены на Android):
- **Позиционное сопоставление** сплитов с КП (`splits[i] ↔ cpOrder[i]`), а не по номеру КП — корректно обрабатывает дистанции с повторяющимися номерами контролов. `cpOrder` берётся у участника с максимальным числом сплитов в группе.
- **Ранги на каждом КП** (кумулятивный и по перегону) считаются позиционно по всем участникам группы; `isBestLeg` = участник с минимальным временем перегона (rank == 1).
- **Анкер отсчёта времени**: `result.startTime ?: participant.startTime`. На Android был баг (`494816:00:18` в UI — время считалось от эпохи Unix), когда `result.startTime` терялся (после HTML-импорта результатов) и код молча падал на `0L` вместо планового времени участника. В `:shared` НЕ повторяйте `?: 0L` — сразу используйте фолбэк на `participant.startTime`.

### UI

- Компонент таблицы со sticky первой колонкой (участник) и sticky-заголовком, горизонтальный скролл при большом числе КП — на Android сделано двумя `Row` с общим `Modifier.horizontalScroll(rememberScrollState())`, сама sticky-колонка/заголовок — просто элементы вне скроллящегося `Row`. Тот же подход должен работать и в Compose Multiplatform Web.
- Точка входа: кнопка «Сплиты» рядом с заголовком каждой группы в `ResultsTab.kt` (`web/src/wasmJsMain/kotlin/com/competra/web/pages/ResultsTab.kt:87-94`, где сейчас просто `Text(groupNamesById[groupId] ...)`).
- Навигация: `web` использует плоский `sealed class Page` в `App.kt` (`web/src/wasmJsMain/kotlin/com/competra/web/App.kt:30`), без вложенных nav-графов, как в Android. Проще всего добавить `data class GroupSplitsTable(val competitionId: String, val groupId: Long) : Page()` — либо, если не хочется городить новую страницу, показать таблицу как модалку/диалог поверх `CompetitionDetailPage`, по аналогии с уже существующим `ImportResultsPreviewDialog.kt`.

### На что обратить внимание (отличие от Android)

В Android есть отдельный поток (`EventResultsScreen` в `:core:eventdetails`, публичный просмотр события), где `ParticipantGroup.groupId` для групп, пришедших с сервера, **всегда равен 0** — реальный id лежит в `remoteId` (Room-специфичный артефакт офлайн-синхронизации), из-за чего кнопка «Сплиты» сначала вела на первую группу независимо от выбранной вкладки. **В web этой проблемы нет** — `ParticipantGroupDetail.groupId` (`shared/.../models/Competition.kt:78`) приходит из единственного API-вызова `GroupRepository.getGroups()` и всегда содержит настоящий id с сервера. Но стоит явно перепроверить при реализации, что `OrienteeringResult.groupId`/`OrienteeringParticipant.groupId` из `ResultRepository` действительно совпадают с `ParticipantGroupDetail.groupId` (а не с каким-то локальным суррогатом) — сейчас `ResultsTab.kt:76-79` уже полагается на это совпадение для группировки, так что скорее всего всё ок, но лишняя проверка не помешает.
