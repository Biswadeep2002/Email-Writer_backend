package com.email.writer.app;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Service
public class EmailGeneratorService {

    private final WebClient webClient;

    @Value("${groq.api.url}")
    private String groqApiURL;

    @Value("${groq.api.key}")
    private String groqApiKey;

    @Value("${groq.api.model}")
    private String groqModel;

    public EmailGeneratorService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    public String generateEmailReply(EmailRequest emailRequest) {

        String prompt = buildPrompt(emailRequest);

        Map<String, Object> requestBody = Map.of(
                "model", groqModel,
                "messages", new Object[]{
                        Map.of(
                                "role", "user",
                                "content", prompt
                        )
                }
        );

        String response = webClient.post()
                .uri(groqApiURL)
                .header(
                        "Authorization",
                        "Bearer " + groqApiKey
                )
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .onStatus(
                        status -> status.isError(),
                        clientResponse ->
                                clientResponse
                                        .bodyToMono(String.class)
                                        .map(errorBody ->
                                                new RuntimeException(
                                                        "Groq API Error: "
                                                                + errorBody
                                                )
                                        )
                )
                .bodyToMono(String.class)
                .block();
        return extractResponseContent(response);
    }

    private String buildPrompt(EmailRequest emailRequest) {

        StringBuilder prompt = new StringBuilder();

        prompt.append(
                "Generate a professional email reply for the following email content. "
        );

        prompt.append(
                "Please don't generate the Subject Line. "
        );

        if (emailRequest.getTone() != null &&
                !emailRequest.getTone().isEmpty()) {

            prompt.append("Use a ")
                    .append(emailRequest.getTone())
                    .append(" tone. ");
        }

        prompt.append("\nOriginal Email:\n")
                .append(emailRequest.getEmailContent());

        return prompt.toString();
    }

    private String extractResponseContent(String response) {

        try {

            ObjectMapper mapper = new ObjectMapper();

            JsonNode rootNode = mapper.readTree(response);

            JsonNode choices = rootNode.path("choices");

            if (!choices.isArray() || choices.isEmpty()) {

                System.out.println(
                        "No choices found in Groq response"
                );

                System.out.println(
                        "Full response: " + response
                );

                return "Groq did not return a response.";
            }

            JsonNode content = choices
                    .get(0)
                    .path("message")
                    .path("content");

            if (content.isMissingNode() ||
                    content.asText().isBlank()) {

                return "Groq returned an empty response.";
            }

            return content.asText();

        } catch (Exception e) {

            System.out.println(
                    "Error processing Groq response: "
                            + e.getMessage()
            );

            System.out.println(
                    "Full Groq response: " + response
            );

            return "Error processing Groq response.";
        }
    }
}