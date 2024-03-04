package org.example;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class CrptApi {
    private final String URL = "https://ismp.crpt.ru/api/v3/lk/documents/create";
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final Semaphore semaphore;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        if (requestLimit >= 0) {
            semaphore = new Semaphore(requestLimit);
            scheduler.scheduleAtFixedRate(() -> {
                semaphore.release(requestLimit - semaphore.availablePermits());
            }, 0, 15, timeUnit);
        } else {
            throw new IllegalArgumentException("Значение должно быть положительным");
        }
    }

    public void sendRequest(Document document, String signature) {
        String json = getDocJson(document, signature);
        try {
            httpRequest(json);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String getDocJson(Document document, String signature) { // Не понял где использовать подпись в json
        Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd")
                .setPrettyPrinting()
                .setFieldNamingStrategy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                .create();

        return gson.toJson(document);
    }

    private void httpRequest(String json) throws IOException {
        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        CloseableHttpClient httpClient = HttpClients.createDefault();

        HttpPost httpPost = new HttpPost(URL);
        httpPost.setHeader("Content-Type", "application/json");
        httpPost.setHeader("Accept", "application/json");

        StringEntity entity = new StringEntity(json);
        httpPost.setEntity(entity);

        httpClient.execute(httpPost);
        httpClient.close();
    }

    @Data
    public abstract static class InnData {
        private String participantInn;
    }

    @EqualsAndHashCode(callSuper = true)
    @Data
    public static class Description extends InnData {

    }

    @EqualsAndHashCode(callSuper = true)
    @Data
    public abstract static class OwnerData extends InnData {
        private String ownerInn;
        private Date productionDate;
        private String producerInn;
    }

    @EqualsAndHashCode(callSuper = true)
    @Data
    public static class Document extends OwnerData {
        private Description description;
        private String docId;
        private String docStatus;
        private DocType docType;
        private boolean importRequest;
        private String productionType;
        private List<Product> products;
        private Date regDate;
        private String regNumber;

        public enum DocType {
            LP_INTRODUCE_GOODS
        }

        @EqualsAndHashCode(callSuper = true)
        @Data
        public static class Product extends OwnerData {
            private String certificateDocument;
            private Date certificateDocumentDate;
            private String certificateDocumentNumber;
            private String tnvedCode;
            private String uitCode;
            private String uituCode;
        }
    }
}
