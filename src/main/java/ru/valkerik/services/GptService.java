/**
 * Этот класс предоставляет службу для связи с API OpenAI.
 * и генерировать текст на основе пользовательского ввода. Он использует HashMap для хранения
 * контекст каждого разговора пользователя и белый список для ограничения
 * доступ к боту. Он также предоставляет метод для сброса контекста
 * пользователя.
 */

package ru.valkerik.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Update;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import ru.valkerik.clients.OpenAIApiClient;
import ru.valkerik.model.request.ChatRequest;
import ru.valkerik.model.request.Message;
import ru.valkerik.model.response.ChatResponse;
import ru.valkerik.utils.MessageLog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

@Slf4j
@Service
public class GptService {


    @Autowired
    private OpenAIApiClient client;
    @Value("${openai.maxtokens}")
    private Integer maxtokens;
    @Value("${openai.model}")
    private String model;
    @Value("${openai.temperature}")
    private Double temperature;
    @Value("${openai.systemprompt}")
    private String systemprompt;
    @Value("${openai.max.message.pool.size}")
    Integer maxMessagePoolSize;
    @Value("${bot.presentation}")
    private String presentation;
    @Value("#{'${bot.whitelist:}'.empty ? null : '${bot.whitelist}'.split(',')}")
    private ArrayList<String> whiteList;
    private HashSet<String> whiteSet;

    // UserContext Map, идентификатор пользователя и списком предыдущих сообщений.
    private HashMap<Long, MessageLog<Message>> userContext = new HashMap<>();

    private ArrayList<String> examples;

    @Autowired
    private Environment env;

    private Integer ntokens = 0;

    public Integer getNumTokens() {
        return this.ntokens;
    }

    /**
     * Этот метод вызывается после создания экземпляра bean-компонента и
     *  используется для инициализации списка примеров и белого списка.
     */
    @PostConstruct
    private void init(){
        // Read examples from configuration
        this.examples = getPropertyList("openai.example");
        // turn whitelist into a hashset for quicker access
        if(this.whiteList!=null && !this.whiteList.isEmpty()) {
            this.whiteSet = new HashSet<>();
            for(String name : this.whiteList) {
                this.whiteSet.add(name.toLowerCase());
            }
        } else {
            this.whiteSet = null;
        }

    }

    /**
     * Этот метод получает список свойств из среды Spring.
     * и возвращает ArrayList строк. Используется для загрузки примеров из конфигурации.
     *  @param name имя свойства для извлечения
     *  @return ArrayList со значениями свойства
     */
    private ArrayList<String> getPropertyList(String name) {
        ArrayList<String> list = new ArrayList<>();
        int i = 1;
        while (env.containsProperty(name+ "." + i)) {
            list.add(env.getProperty(name +"." + i));
            i++;
        }
        return list;
    }

    /**
     * Этот метод отправляет сообщение в API OpenAI для создания текстового
     * ответ на основе пользовательского ввода. Проверяет, авторизован ли пользователь
     * поговорить с ботом и является ли разговор приватным или нет.
     * @param update объект обновления, содержащий пользовательский ввод
     *  @return текстовый ответ, сгенерированный API OpenAI
     */
    public synchronized String SendMessage(Update update) {

        if(!checkPermission(update)){
            return "Извините, но я не могу с вами разговаривать, вас нет в списке доступа.";
        }

        ChatResponse response = null;
        try {

            // Compose new request
            ObjectMapper mapper = new ObjectMapper();
            ChatRequest chatRequest = new ChatRequest();
            chatRequest.setModel(model);
            chatRequest.setTemperature(temperature);
            chatRequest.setMaxTokens(maxtokens);

            // Set up array of messages
            ArrayList<Message> messages = new ArrayList<>();
            Message systemMessage = new Message();

            // System prompt
            systemMessage.setRole("system");
            systemMessage.setContent(systemprompt);
            messages.add(systemMessage);

            // List of user messages
            if(isPrivate(update)) {
                // Если это приватный чат, то мы отправляем ряд предыдущих сообщений для контекста
                if(!userContext.containsKey(update.message().from().id())) {
                    // Если пользователя нет на карте, нажмите для него новую запись
                    userContext.put(update.message().from().id(),new MessageLog<>(maxMessagePoolSize));
                    // Если есть примеры, добавьте их в контекст (это делается только в первый раз)
                    if(!this.examples.isEmpty()) {
                        userContext.get(update.message().from().id()).addAll(getExamples(this.examples));
                    }
                }
                // Добавьте новое сообщение в журнал сообщений, предыдущие сообщения будут удалены, когда контекст достигнет максимальной длины.
                Message newUserMessage = new Message();
                newUserMessage.setRole("user");
                newUserMessage.setContent(update.message().text());
                userContext.get(update.message().from().id()).add(newUserMessage);
                // add to userMessages
                messages.addAll(userContext.get(update.message().from().id()));
            } else {
                // Если это группа, сообщение обрабатывается без какого-либо контекста.
                // Если есть примеры, добавляем их в список сообщений перед сообщением пользователя
                if(!this.examples.isEmpty()) {
                    messages.addAll(getExamples(this.examples));
                }
                Message userMessage = new Message();
                userMessage.setRole("user");
                userMessage.setContent(update.message().text());
                messages.add(userMessage);
            }

            // set messages to the request
            chatRequest.setMessages(messages);

            // Отправить синхронный запрос в API OpenAI
            response = client.getCompletion(chatRequest);
            // Incremet token counter
            ntokens = ntokens + response.getUsage().getTotalTokens();

            // Если это приватный разговор, добавьте ответ помощника в контекст пользователя.
            if(isPrivate(update)) {
                Message assistantMessage = new Message();
                assistantMessage.setRole("assistant");
                assistantMessage.setContent(response.getChoices().get(0).getMessage().getContent());
                userContext.get(update.message().from().id()).add(assistantMessage);
            }

            // Return text to be sent to the user
            return response.getChoices().get(0).getMessage().getContent();

        } catch (Exception e) {
            e.printStackTrace();
           log.error("Извините, что-то пошло не так. " + "Ошибка: " + e);
            return "Извините, что-то пошло не так. " +
                    "(Идет процесс отладки этой ошибки)";
        }
    }

    /**
     * Этот метод возвращает ArrayList сообщений на основе списка строк ArrayList .
     * Каждое сообщение создается с ролью и содержимым.
     * @param examples ArrayList строк для преобразования в сообщения
     *  @return ArrayList сообщений
     */
    private ArrayList<Message> getExamples(ArrayList<String> examples) {
        ArrayList<Message> results = new ArrayList<>();
        for(String example: examples) {
            try {
                String role = example.split(":",2)[0];
                String content = example.split(":",2)[1];
                if(StringUtils.isNotEmpty(role) && StringUtils.isNotEmpty(content)) {
                    Message exampleMessage = new Message();
                    exampleMessage.setRole(role.toLowerCase());
                    exampleMessage.setContent(content);
                    results.add(exampleMessage);
                } else {
                    log.error("Что-то пошло не так с этим примером: " + example);
                }
            } catch(Exception e) {
                log.error("Что-то пошло не так с этим примером: " + example + " " + e.getMessage());
            }
        }
        return results;
    }

    /**
     * Этот метод проверяет, авторизован ли пользователь для общения с ботом.
     * на основе белого списка. Если белый список пуст, каждый может
     * поговорить с ботом. В противном случае метод проверяет, является ли пользователь или
     * группа находится в белом списке.
     *  @param update объект обновления, содержащий информацию о пользователе
     *   @return true, если пользователь авторизован, иначе false
     */
    private boolean checkPermission(Update update) {
        // если белый список пуст, с ботом могут разговаривать все, в противном случае его необходимо проверить
        String userName = "none";
        String groupName = "none";
        if (this.whiteSet!=null && !this.whiteSet.isEmpty()) {
            if(update.message().from().firstName()!=null) {
                userName = update.message().from().firstName().toLowerCase();
            }
            if(update.message().chat().title()!=null) {
                groupName = update.message().chat().title().toLowerCase();
            }
            // either name on the list, grants access
            if(this.whiteSet.contains(userName)) {
                // access is granted for the user
                return true;
            }
            if(this.whiteSet.contains(groupName)) {
                // access is granted for the group
                return true;
            }

            log.error("Неавторизованный пользователь пытался поговорить со мной: " + userName);
            return false;
        }
        // no whitelist
        return true;

    }

    /**
     * Этот метод отправляет сообщение в API OpenAI для создания текстового
     *  ответ. Сообщение основано не на пользовательском вводе, а на пользовательском тексте презентации.
     *  @param update объект обновления, содержащий информацию о пользователе
     *  @param text пользовательский текст презентации
     *  @return the text, сгенерированный API OpenAI
     */
    public String sendCustomMessage(Update update, String text) {
        ChatResponse response = null;
        try {

            // Compose new request
            ObjectMapper mapper = new ObjectMapper();
            ChatRequest chatRequest = new ChatRequest();
            chatRequest.setModel(model);
            chatRequest.setTemperature(temperature);
            chatRequest.setMaxTokens(maxtokens);
            // Set up array of messages
            ArrayList<Message> messages = new ArrayList<>();
            Message systemMessage = new Message();
            // System prompt
            systemMessage.setRole("system");
            systemMessage.setContent(systemprompt);
            messages.add(systemMessage);
            // Custom message
            Message userMessage = new Message();
            userMessage.setRole("user");
            userMessage.setContent(this.presentation);
            messages.add(userMessage);
            // set messages to the request
            chatRequest.setMessages(messages);

            // Send synchronous request to the OpenAI Api
            response = client.getCompletion(chatRequest);
            // Incremet token counter
            ntokens = ntokens + response.getUsage().getTotalTokens();
            // Return text to be sent to the user
            return response.getChoices().get(0).getMessage().getContent();

        } catch (Exception e) {
            e.printStackTrace();
            return "Извините, что-то пошло не так. ";
        }
    }

    /**
     * Определяет, было ли сообщение отправлено боту лично или в групповой чат.
     *       * @param update Сообщение, полученное от пользователя.
     *       * @return true, если сообщение является личным, иначе false.
     */
    private boolean isPrivate(Update update) {
        if (update.message().chat().type().equals(Chat.Type.Private)) {
            return true;
        } else {
            return false;
        }
    }

    public String resetUserContext(Update update) {
        // If this is a private chat, reset context for current user
        if(isPrivate(update)){
           if (this.userContext.containsKey(update.message().from().id())) {
               this.userContext.remove(update.message().from().id());
               return "Пользовательский контекст был сброшен " + update.message().from().firstName();
           } else {
               return "Я не нашел контекст для пользователя " + update.message().from().firstName();
           }
        } else {
            return "Сбрасывать нечего, так как это группа.";
        }
    }
}
