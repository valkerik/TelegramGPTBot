/**
 * Этот класс представляет клиент, который взаимодействует с API OpenAI для получения ответа в чате.
 */
package ru.valkerik.clients;
import com.fasterxml.jackson.databind.ObjectMapper;
import ru.valkerik.model.request.ChatRequest;
import ru.valkerik.model.response.ChatResponse;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.concurrent.TimeUnit;

@Service
public class OpenAIApiClient {

    public final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    @Value("${openai.apikey}")
    private String apiKey;
    @Value("${openai.url}")
    private String url;
    private OkHttpClient client;

    /**
     *  Инициализирует OkHttpClient с таймаутом 50 секунд для каждой операции.
     */
    @PostConstruct
    private void init() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(50, TimeUnit.SECONDS)
                .writeTimeout(50, TimeUnit.SECONDS)
                .readTimeout(50, TimeUnit.SECONDS)
                .build();
    }

    /**
     * Отправляет запрос чата в API OpenAI и возвращает ответ чата.
     * @param chatRequest запрос на отправку чата.
     * @return ответ чата от OpenAI API.
     * @throws Exception, если ответ не удался или возникла проблема с запросом.
     */
    public ChatResponse getCompletion(ChatRequest chatRequest) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        RequestBody body = RequestBody.create(mapper.writeValueAsString(chatRequest), JSON);
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer " + apiKey)
                .post(body)
                .build();
        Response response = client.newCall(request).execute();
        if (!response.isSuccessful()) throw new Exception("Unexpected code " + response);
        ChatResponse chatResponse = mapper.readValue(response.body().string(), ChatResponse.class);
        return chatResponse;
    }
}
