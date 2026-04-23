package vip.mate.tool.image.provider;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vip.mate.llm.service.ModelProviderService;
import vip.mate.system.model.SystemSettingsDTO;
import vip.mate.tool.image.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * OpenAI 图片生成 Provider — 支持 DALL-E 3 / DALL-E 2 / gpt-image-1
 * <p>
 * 同步模式：直接返回图片 URL。
 * 复用已有的 OpenAI LLM provider 的 API Key。
 *
 * @author MateClaw Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiImageProvider implements ImageGenerationProvider {

    private final ModelProviderService modelProviderService;
    private final ObjectMapper objectMapper;

    private static final String DEFAULT_MODEL = "dall-e-3";

    @Override
    public String id() {
        return "openai";
    }

    @Override
    public String label() {
        return "OpenAI (DALL-E)";
    }

    @Override
    public boolean requiresCredential() {
        return true;
    }

    @Override
    public int autoDetectOrder() {
        return 200;
    }

    @Override
    public Set<ImageCapability> capabilities() {
        return Set.of(ImageCapability.TEXT_TO_IMAGE);
    }

    @Override
    public ImageProviderCapabilities detailedCapabilities() {
        return ImageProviderCapabilities.builder()
                .modes(capabilities())
                .supportedSizes(List.of("1024x1024", "1024x1792", "1792x1024"))
                .aspectRatios(List.of("1:1", "9:16", "16:9"))
                .maxCount(1) // DALL-E 3 只支持 n=1
                .defaultModel(DEFAULT_MODEL)
                .models(List.of("dall-e-3", "dall-e-2", "gpt-image-1"))
                .build();
    }

    @Override
    public boolean isAvailable(SystemSettingsDTO config) {
        try {
            return modelProviderService.isProviderConfigured("openai");
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public ImageSubmitResult submit(ImageGenerationRequest request, SystemSettingsDTO config) {
        String apiKey = getOpenAiApiKey();
        String baseUrl = getOpenAiBaseUrl();
        if (apiKey == null) {
            return ImageSubmitResult.failure(id(), "OpenAI API Key 未配置");
        }

        try {
            String model = request.getModel() != null && !request.getModel().isBlank()
                    ? request.getModel() : DEFAULT_MODEL;

            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", model);
            body.put("prompt", request.getPrompt());
            body.put("size", normalizeSize(request.getSize(), request.getAspectRatio()));
            body.put("n", 1);
            body.put("response_format", "url");

            String url = (baseUrl != null ? baseUrl : "https://api.openai.com") + "/v1/images/generations";

            HttpResponse response = HttpRequest.post(url)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .body(body.toString())
                    .timeout(60_000)
                    .execute();

            JsonNode result = objectMapper.readTree(response.body());

            if (response.getStatus() == 200 && result.has("data")) {
                List<String> imageUrls = new ArrayList<>();
                for (JsonNode item : result.get("data")) {
                    String imageUrl = item.has("url") ? item.get("url").asText() : null;
                    if (imageUrl != null) {
                        imageUrls.add(imageUrl);
                    }
                }
                if (imageUrls.isEmpty()) {
                    return ImageSubmitResult.failure(id(), "API 返回成功但未包含图片 URL");
                }
                log.info("[OpenAI Image] Generated {} image(s) (model={})", imageUrls.size(), model);
                return ImageSubmitResult.syncSuccess(id(), imageUrls);
            } else {
                String errMsg = result.has("error")
                        ? result.path("error").path("message").asText("Unknown error")
                        : "HTTP " + response.getStatus();
                log.warn("[OpenAI Image] Submit failed: {}", errMsg);
                return ImageSubmitResult.failure(id(), errMsg);
            }
        } catch (Exception e) {
            log.error("[OpenAI Image] Submit error: {}", e.getMessage(), e);
            return ImageSubmitResult.failure(id(), e.getMessage());
        }
    }

    private String getOpenAiApiKey() {
        try {
            var providerEntity = modelProviderService.getProviderConfig("openai");
            return providerEntity.getApiKey();
        } catch (Exception e) {
            return null;
        }
    }

    private String getOpenAiBaseUrl() {
        try {
            var providerEntity = modelProviderService.getProviderConfig("openai");
            return providerEntity.getBaseUrl();
        } catch (Exception e) {
            return null;
        }
    }

    private String normalizeSize(String size, String aspectRatio) {
        // 优先使用 size
        if (size != null && !size.isBlank()) {
            List<String> supported = List.of("1024x1024", "1024x1792", "1792x1024");
            if (supported.contains(size)) return size;
        }
        // 根据 aspectRatio 推断
        if (aspectRatio != null) {
            return switch (aspectRatio) {
                case "9:16" -> "1024x1792";
                case "16:9" -> "1792x1024";
                default -> "1024x1024";
            };
        }
        return "1024x1024";
    }
}
