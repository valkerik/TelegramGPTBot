/**
 * Этот класс представляет службу, которая обрабатывает связь с ботом Telegram. Он получает сообщения от бота
 * и обрабатывает их соответствующим образом, либо выполняя команды, либо генерируя ответы с использованием технологии GPT.
 */

package ru.valkerik.services;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.model.request.ReplyKeyboardRemove;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.SendResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.List;

@Slf4j
@Service
public class TelegramBotService {


    private static final String EMOJI_UFF = "\uD83D\uDE13" ;
    @Autowired
    GptService gptService;

    @Value("${bot.token}")
    private String BOT_TOKEN;
    @Value("${bot.name}")
    private String botName;
    @Value("${bot.presentation}")
    private String presentationText;
    private TelegramBot bot;

    /**
     * Инициализирует бота Telegram и настраивает прослушиватель обновлений для получения сообщений и их обработки.
     */
    @PostConstruct
    private void init() {

        this.bot = new TelegramBot(BOT_TOKEN);
        bot.setUpdatesListener(new UpdatesListener() {
            @Override
            public int process(List<Update> updates) {
                try {
                    Thread.sleep(6000);
                } catch (InterruptedException e) {
                   log.error("TelegramBotService. init() Error : " + e.getMessage());
                }
                // process updates
                for (Update update : updates) {
                    if (update.message() != null && update.message().text() != null) {
                        if (update.message().text().startsWith("/")) {
                            processCommand(update);
                        } else {
                            // если в группе, обрабатываются только сообщения, напрямую адресованные боту
                            if(isPrivate(update)){
                                processText(update);
                            } else if(update.message().text().toLowerCase().contains("@"+botName.toLowerCase())) {
                                processText(update);
                            }
                        }
                    }
                }
                return UpdatesListener.CONFIRMED_UPDATES_ALL;
            }
        });

    }

    /**
     * Закрывает Telegram Bot, когда приложение закрывается.
     */
    @PreDestroy
    private void dispose(){
        log.info("shutting down bot");
        bot.shutdown();
    }

    /**
     * Обрабатывает сообщение, полученное от пользователя Telegram, генерируя ответ с использованием технологии GPT и
     * отправка обратно.
     * @param update Сообщение, полученное от пользователя.
     */
    private void processText(Update update) {

        log.info(update.message().from().firstName()+" said ... " + update.message().text());
        String response = this.gptService.SendMessage(update);
        log.info(this.botName + " said ... " + response);
        sendReply(update, response);
    }

    /**
     * Отправляет ответное сообщение обратно пользователю.
     *  @param update Сообщение, полученное от пользователя.
     *  @param message Сообщение для отправки обратно пользователю.
     */
    private void sendReply(Update update, String message) {
        SendMessage request = new SendMessage(update.message().chat().id(), message)
                .parseMode(ParseMode.Markdown)
                .disableWebPagePreview(true)
                .disableNotification(true)
                .replyMarkup(new ReplyKeyboardRemove());
        if(!isPrivate(update)){
            // Если мы в группе, сделай  replyTo
            request.replyToMessageId(update.message().messageId());
        }
        // request.replyMarkup(new ForceReply());
        SendResponse sendResponse = bot.execute(request);
        if(!sendResponse.isOk()){
            log.error(sendResponse.message().toString());
        }
    }

    /**
     * Определяет, было ли сообщение отправлено боту лично или в групповой чат.
     *  @param update Сообщение, полученное от пользователя.
     *  @return true, если сообщение является личным, иначе false.
     */
    private boolean isPrivate(Update update) {
        if (update.message().chat().type().equals(Chat.Type.Private)) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Обрабатывает команду, полученную от пользователя Telegram, выполняя соответствующее действие.
     *  @param update Сообщение, полученное от пользователя.
     */
    private void processCommand(Update update) {
        if (update.message().text().equalsIgnoreCase("/start")) {
            presentation(update);
        } else if (update.message().text().equalsIgnoreCase("/usage")) {
            printUsage(update);
        } else if (update.message().text().equalsIgnoreCase("/reset")) {
            resetUserContext(update);
        } else {
            log.warn("Неизвестная команда:" +update.message().text());
        }
    }

    /**
     * Отправляет пользовательское сообщение презентации пользователю.
     *  @param update Сообщение, полученное от пользователя.
     */
    private void presentation(Update update) {
        String response = this.gptService.sendCustomMessage(update, presentationText);
        sendReply(update, response);
    }

    /**
     * Отправляет сообщение пользователю с информацией о количестве токенов, которые в настоящее время используются при генерации GPT.
     * @param update Сообщение, полученное от пользователя.
     */
    private void printUsage(Update update) {
        String message = String.format("Счетчик токенов: %d",gptService.getNumTokens());
        sendReply(update, message);
    }

    /**
     * Сбрасывает контекст пользователя, очищая историю сообщений. Примеры будут загружены обратно, когда контекст создается заново.
     *  @param update Сообщение, полученное от пользователя.
     */
    private void resetUserContext(Update update) {
        String message = gptService.resetUserContext(update);
        sendReply(update, message);
    }


}
