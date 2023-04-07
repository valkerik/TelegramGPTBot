# telegram-gpt-bot

Проект telegram-gpt-bot представляет собой простую интеграцию между Telegram и OpenAI Apis, 
которая позволяет вам создать персонализированного помощника GPT в виде бота Telegram. 
С помощью этого инструмента вы можете получить доступ к своему помощнику GPT с любого устройства, 
используя обычное приложение Telegram, что делает его невероятно удобным в использовании. 
Используется модель gpt-3.5-turbo, та же модель, что и в обычном ChatGpt.

Одним из преимуществ telegram-gpt-bot является возможность настроить личность вашего помощника GPT. 
Вы можете выбрать тон, язык, специализацию и даже имя своего помощника, чтобы сделать его более персонализированным и привлекательным.

Цель этого проекта — иметь личного бота, которым вы можете поделиться со своими друзьями, а не быть платформой для ботов.

## Как использовать

### НАЧАЛО

- У вас должен быть ключ OpenAI API. [get it here](https://platform.openai.com)
- Вы должны получить токен бота Telegram. [talk to Botfather about it](https://t.me/botfather), ([more about Botfather here](https://t.me/botfather)) кроме токена, обратите внимание на URL-адрес бота.
- Вам необходимо установить Java JRE (не менее 11) [download from here](https://adoptium.net/es/installation/#x64_win-jre)
- Вам нужен apache maven (только если вы хотите собрать из исходного кода). [download from here](https://maven.apache.org/download.cgi)

### Установка и запуск

1. Перейти по ссылке [releases](https://github.com/achousa/telegram-gpt-bot/releases) скачать 
2. Разархивируйте файл на локальном диске
3. Отредактируйте application.properties, чтобы включить ключ API и токен бота, остальное можете оставить как есть (прочитайте следующий раздел, чтобы узнать о параметрах).
4. Выполнить run.sh / run.cmd (оставить это окно открытым)
5. Откройте Telegram и перейдите по ссылке, которую дал вам Botfather, чтобы начать общение с вашим ботом!

### Конфигурация

Большинство свойств из файла свойств приложения говорят сами за себя, давайте рассмотрим наиболее важные.

| Property | Description | Обязательный параметр |
| ----------- | ----------- |-----------------------|
| bot.name | Your bot's name | Yes                   |
| bot.token | The token Botfather gave you when you registered the bot | Yes                   |
| bot.presentation | A natural language command, to tell the bot how to present himself | Yes                   |
| bot.whitelist | A comma separated list of users or groups which are granted to talk to the bot. You can leave this empty if you want the bot to be accessible to everyone | Yes                   |
| openai.url | Url of the Open Ai endpoint | Yes                   |
| openai.apikey | Your open AI Api Key | Yes                   |
| openai.model | Name of the gpt-3 model (defaults to gpt-3.5-turbo) | Yes                   |
| openai.temperature | Measure of the model creativity from 0 to 1 | Yes                   |
| openai.maxtokens | Maximum number of tokens a request can consume | Yes                   |
| openai.max.message.pool.size | Number of previous messages that are kept in the context of the conversation | Yes                   |
| openai.systemprompt | This is where you tell the bot, in natural language, what to do, and how to behave | Yes                   |
| openai.example.1 | This is the first example (in role: content format) | No                    |
| openai.example.2 | This is the second example (in role: content format) | No                    |

#### Настройка поведения бота

По сути, вы настраиваете общее поведение с помощью systempromt. 
Затем вы можете дополнительно предоставить серию примеров сообщений, 
показывающих модели, как ожидается взаимодействие с пользователем и помощником.

Примеры необязательны, но если они предоставлены в формате «роль: 
контент», они также должны иметь суффикс точки и последовательный непрерывный номер. 
Роль должна быть либо «пользователь», либо «помощник».

Параметр openai.max.message.pool.size указывает количество предыдущих сообщений,
которые хранятся в памяти и отправляются с каждым запросом. Чем больше сообщений, 
тем больше у модели контекста разговора, но больше потребление токенов.


```
openai.systemprompt=You are "NameBot" a helpful translator and language assistant.
openai.example.1=user: How do you say in Spanish: yesterday
openai.example.2=assistant: In Spanish, we say: ayer
openai.example.3=user: What language is this: Es ist Zeit zu essen
openai.example.4=assistant: It is German
```

Подробнее об этом вы можете прочитать в [openAI api documentation](https://platform.openai.com/docs/guides/chat/introduction)

Свойство презентации настраивает то, как бот представляет себя новому пользователю. 
Это не текст, который нужно произнести, а инструкции для бота о том, какой должна быть презентация. 
Таким образом, текст презентации каждый раз разный.
```
bot.presentation=Say your name, and succinctly state your purpose. At the end offer your help in the areas you excel at.
```

#### Бот в группах

Если вы хотите иметь возможность добавлять бота в группы, есть дополнительный шаг настройки. 
Снова поговорите с botfather и включите «Разрешить группы» 
(перейдите в /mybots -> настройки бота -> Разрешить группы. 
В том же меню настроек выберите «Режим конфиденциальности» и отключите его.

Находясь в группе, бот не сохраняет контекст беседы. 
Каждый запрос от пользователя к боту фактически считается первым взаимодействием пользователя с ботом. 
В этом режиме бот всегда отвечает пользователю, который задал вопрос. 
Бот прослушивает только те сообщения, которые содержат «@botname» в своем теле.


#### Команды

На данный момент доступны следующие команды:

| Command | Action |
| ----------- | ----------- |
| /reset | Восстанавливает контекст разговора, забывает все предыдущие сообщения, которые вы отправили боту. Его можно использовать только в приватном чате. |
| /usage | Выводит сумму токенов, использованных во всех разговорах. Это значение не сохраняется и сбрасывается при каждом перезапуске приложения. |

## Настройка разработки

clone the project

`git clone https://github.com/achousa/telegram-gpt-bot`

install dependencies and build

`mvn install`

