package com.competra.web.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// Оператор персональных данных, ИНН/ОГРНИП и дата вступления в силу — заполнить перед публикацией.
private const val OPERATOR_NAME = "[ИП Фамилия Имя Отчество]"
private const val OPERATOR_INN = "[ИНН]"
private const val OPERATOR_OGRNIP = "[ОГРНИП]"
private const val CONTACT_EMAIL = "rodionov.mikhail.a@yandex.ru"
private const val EFFECTIVE_DATE = "[ДД.ММ.ГГГГ]"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyPage(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Политика конфиденциальности") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text(
                "Действует с $EFFECTIVE_DATE. Применяется к мобильному приложению Competra (Google Play, RuStore) и веб-версии competra.ru.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            LegalSection("1. Оператор персональных данных") {
                Paragraph(
                    "Оператором персональных данных пользователей сервиса Competra является " +
                        "$OPERATOR_NAME, ИНН $OPERATOR_INN, ОГРНИП $OPERATOR_OGRNIP " +
                        "(далее — «Оператор», «мы»)."
                )
                Paragraph("По всем вопросам обработки персональных данных обращайтесь: $CONTACT_EMAIL.")
            }

            LegalSection("2. Какие данные мы собираем") {
                Paragraph("При регистрации и использовании сервиса мы обрабатываем:")
                BulletList(
                    "Email — используется как логин и для отправки кода подтверждения входа",
                    "Фамилия, имя, отчество, дата рождения — для участия в соревнованиях и отображения в результатах",
                    "Номер телефона (опционально) — для связи по вопросам участия в соревнованиях",
                    "Фотография профиля (опционально)",
                    "Геолокация и GPS-трек тренировки — только когда вы сами включаете запись тренировки в дневнике; хранится локально на устройстве и, если вы сохраняете тренировку на сервере, передаётся нам",
                    "Токен push-уведомлений устройства (FCM) — для отправки уведомлений о соревнованиях",
                    "Технический идентификатор пользователя — передаётся в сервис аналитики AppMetrica для сбора обезличенной статистики использования приложения",
                )
            }

            LegalSection("3. Цели обработки") {
                BulletList(
                    "регистрация и аутентификация в сервисе",
                    "организация участия в соревнованиях по спортивному ориентированию и публикация результатов",
                    "ведение личного дневника тренировок",
                    "отправка push-уведомлений о соревнованиях, в которых вы участвуете",
                    "улучшение работы сервиса и анализ использования приложения",
                )
            }

            LegalSection("4. Правовое основание обработки") {
                Paragraph(
                    "Обработка осуществляется на основании вашего согласия (ст. 6 ч. 1 п. 1 " +
                        "Федерального закона № 152-ФЗ «О персональных данных»), а также в объёме, " +
                        "необходимом для оказания вам услуг сервиса. Для пользователей из Европейского " +
                        "союза правовым основанием также является ст. 6(1)(a) и 6(1)(b) Регламента " +
                        "(ЕС) 2016/679 (GDPR) — согласие и необходимость исполнения договора."
                )
            }

            LegalSection("5. Передача данных третьим лицам") {
                Paragraph("Для работы сервиса мы привлекаем следующих обработчиков:")
                BulletList(
                    "AppMetrica (ООО «Яндекс») — аналитика использования приложения",
                    "Firebase (Google LLC) — push-уведомления (Cloud Messaging), диагностика сбоев (Crashlytics); данные могут обрабатываться на серверах за пределами РФ",
                    "Почтовый сервис Яндекс — отправка писем с кодом подтверждения на ваш email",
                    "Облачное хранилище Yandex Cloud (S3-совместимое) — хранение загруженных фотографий",
                )
                Paragraph(
                    "Мы не продаём и не передаём персональные данные третьим лицам в маркетинговых " +
                        "целях."
                )
            }

            LegalSection("6. Трансграничная передача данных") {
                Paragraph(
                    "В связи с использованием сервисов Google/Firebase часть данных может " +
                        "обрабатываться на серверах, расположенных за пределами Российской Федерации. " +
                        "Осуществляя регистрацию, вы даёте согласие на такую трансграничную передачу " +
                        "в соответствии со ст. 12 152-ФЗ."
                )
            }

            LegalSection("7. Сроки хранения") {
                Paragraph(
                    "Данные хранятся до тех пор, пока у вас есть учётная запись в сервисе, либо до " +
                        "получения от вас запроса на удаление. После удаления аккаунта данные " +
                        "удаляются из основной базы данных; обезличенные данные аналитики могут " +
                        "сохраняться для статистических целей."
                )
            }

            LegalSection("8. Ваши права") {
                Paragraph("В соответствии со ст. 14 152-ФЗ вы вправе:")
                BulletList(
                    "получить информацию о том, какие ваши данные обрабатываются",
                    "потребовать уточнения, блокирования или уничтожения данных, если они неполны, устарели или неточны",
                    "отозвать согласие на обработку персональных данных",
                    "удалить свою учётную запись и связанные с ней данные",
                )
                Paragraph(
                    "Для пользователей из Европейского союза дополнительно применяются права по " +
                        "ст. 15–21 GDPR: доступ, исправление, удаление («право на забвение»), " +
                        "ограничение обработки, портируемость данных и возражение против обработки."
                )
                Paragraph("Чтобы воспользоваться любым из этих прав, напишите на $CONTACT_EMAIL.")
            }

            LegalSection("9. Удаление аккаунта") {
                Paragraph(
                    "Удалить учётную запись можно из настроек профиля в приложении. Если такая " +
                        "возможность временно недоступна — направьте запрос на $CONTACT_EMAIL с адреса, " +
                        "указанного при регистрации. Мы удалим аккаунт и связанные с ним персональные " +
                        "данные в течение 30 дней."
                )
            }

            LegalSection("10. Меры защиты данных") {
                Paragraph(
                    "Данные передаются по защищённому соединению (HTTPS/TLS). Токены авторизации на " +
                        "устройстве хранятся в зашифрованном виде. Доступ к базе данных на сервере " +
                        "ограничен и защищён паролем."
                )
            }

            LegalSection("11. Обработка данных несовершеннолетних") {
                Paragraph(
                    "Сервис не предназначен для самостоятельной регистрации лицами младше 18 лет. " +
                        "Если несовершеннолетний участвует в соревновании, его данные (ФИО, год " +
                        "рождения) вносятся организатором соревнования или законным представителем."
                )
            }

            LegalSection("12. Cookies и локальное хранилище") {
                Paragraph(
                    "Веб-версия сервиса использует localStorage браузера для хранения токена " +
                        "авторизации на вашем устройстве. Сторонние рекламные cookies не используются."
                )
            }

            LegalSection("13. Изменения политики") {
                Paragraph(
                    "Мы можем обновлять эту политику. Актуальная версия всегда доступна по этой " +
                        "ссылке; о существенных изменениях мы уведомим через приложение или по email."
                )
            }

            HorizontalDivider()

            LegalSection("Пользовательское соглашение") {
                Paragraph(
                    "Используя приложение и сайт Competra, вы соглашаетесь со следующими условиями."
                )
            }

            LegalSection("1. Предмет соглашения") {
                Paragraph(
                    "Competra — сервис для организации соревнований по спортивному ориентированию, " +
                        "регистрации участников, публикации результатов и ведения дневника " +
                        "тренировок. Сервис предоставляется «как есть»."
                )
            }

            LegalSection("2. Учётная запись") {
                Paragraph(
                    "Вы обязуетесь предоставлять достоверные данные при регистрации и не передавать " +
                        "доступ к своей учётной записи третьим лицам. Вы несёте ответственность за все " +
                        "действия, совершённые под вашей учётной записью."
                )
            }

            LegalSection("3. Правила использования") {
                Paragraph(
                    "Запрещается использовать сервис для внесения заведомо ложных результатов " +
                        "соревнований, нарушения работы сервиса или доступа к данным других " +
                        "пользователей."
                )
            }

            LegalSection("4. Ограничение ответственности") {
                Paragraph(
                    "Точность GPS-трека тренировки зависит от условий приёма сигнала на устройстве " +
                        "и не гарантируется. Сервис не является средством навигации или обеспечения " +
                        "безопасности при нахождении в лесу — при участии в соревнованиях " +
                        "руководствуйтесь правилами организатора и мерами предосторожности."
                )
                Paragraph(
                    "Оператор не несёт ответственности за точность данных о соревнованиях и " +
                        "результатах, внесённых организаторами."
                )
            }

            LegalSection("5. Интеллектуальная собственность") {
                Paragraph(
                    "Дизайн, программный код и товарный знак Competra принадлежат Оператору. " +
                        "Загружаемые вами материалы (фотографии, треки) остаются вашей собственностью; " +
                        "вы предоставляете Оператору право их хранения и отображения в рамках работы " +
                        "сервиса."
                )
            }

            LegalSection("6. Прекращение действия соглашения") {
                Paragraph(
                    "Вы можете прекратить использование сервиса в любой момент, удалив учётную " +
                        "запись. Оператор вправе заблокировать учётную запись при нарушении условий " +
                        "настоящего соглашения."
                )
            }

            LegalSection("7. Применимое право") {
                Paragraph(
                    "К отношениям сторон применяется законодательство Российской Федерации. Споры " +
                        "разрешаются путём переговоров, а при недостижении согласия — в судебном " +
                        "порядке по месту нахождения Оператора."
                )
            }

            LegalSection("Контакты") {
                Paragraph("По всем вопросам: $CONTACT_EMAIL")
            }
        }
    }
}

@Composable
private fun LegalSection(title: String, content: ColumnScopeContent) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            content()
        }
    }
}

private typealias ColumnScopeContent = @Composable () -> Unit

@Composable
private fun Paragraph(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun BulletList(vararg items: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEach { item ->
            Text("•  $item", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
