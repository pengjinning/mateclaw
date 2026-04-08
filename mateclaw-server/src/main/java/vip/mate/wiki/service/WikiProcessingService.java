package vip.mate.wiki.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import vip.mate.agent.AgentGraphBuilder;
import vip.mate.agent.prompt.PromptLoader;
import vip.mate.llm.model.ModelConfigEntity;
import vip.mate.llm.service.ModelConfigService;
import vip.mate.wiki.WikiProperties;
import vip.mate.wiki.model.WikiKnowledgeBaseEntity;
import vip.mate.wiki.model.WikiPageEntity;
import vip.mate.wiki.model.WikiRawMaterialEntity;

import java.util.List;

/**
 * Wiki 处理服务
 * <p>
 * 核心管线：将原始材料通过 LLM 消化为结构化 Wiki 页面。
 *
 * @author MateClaw Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WikiProcessingService {

    private final WikiKnowledgeBaseService kbService;
    private final WikiRawMaterialService rawService;
    private final WikiPageService pageService;
    private final WikiProperties properties;
    private final ModelConfigService modelConfigService;
    private final AgentGraphBuilder agentGraphBuilder;
    private final ObjectMapper objectMapper;

    /**
     * 处理单个原始材料
     */
    public void processRawMaterial(Long rawId) {
        // CAS 式抢占：防止并发重复处理
        if (!rawService.claimForProcessing(rawId)) {
            log.debug("[Wiki] Raw material {} already claimed or not pending, skipping", rawId);
            return;
        }

        WikiRawMaterialEntity raw = rawService.getById(rawId);
        if (raw == null) {
            log.warn("[Wiki] Raw material not found: {}", rawId);
            return;
        }

        WikiKnowledgeBaseEntity kb = kbService.getById(raw.getKbId());
        if (kb == null) {
            log.warn("[Wiki] Knowledge base not found for raw material: kbId={}", raw.getKbId());
            return;
        }

        kbService.updateStatus(kb.getId(), "processing");

        try {
            // Phase 1: 获取文本内容
            String textContent = rawService.getTextContent(raw);
            if (textContent == null || textContent.isBlank()) {
                rawService.updateProcessingStatus(rawId, "failed", "No text content available");
                kbService.updateStatus(kb.getId(), "active");
                return;
            }

            // Phase 2: LLM 消化
            // result[0] = totalPages, result[1] = failedChunks, result[2] = totalChunks
            int[] result;
            if (textContent.length() > properties.getMaxChunkSize()) {
                result = processInChunks(kb, raw, textContent);
            } else {
                int pages = processChunk(kb, raw, textContent);
                result = new int[]{pages, pages == 0 ? 1 : 0, 1};
            }

            int totalPages = result[0];
            int failedChunks = result[1];
            int totalChunks = result[2];

            // Phase 3: 更新状态和计数
            if (totalPages == 0) {
                rawService.updateProcessingStatus(rawId, "failed", "No pages generated from LLM response");
            } else if (failedChunks > 0) {
                // 部分成功：有些 chunk 失败但有些产出了页面
                rawService.updateProcessingStatus(rawId, "partial",
                        failedChunks + " of " + totalChunks + " chunks failed, " + totalPages + " pages generated");
            } else {
                rawService.updateProcessingStatus(rawId, "completed", null);
            }
            int pageCount = pageService.countByKbId(kb.getId());
            kbService.setPageCount(kb.getId(), pageCount);
            kbService.updateStatus(kb.getId(), "active");

            log.info("[Wiki] Processing completed for raw={}, kbId={}, generatedPages={}, totalPages={}",
                    rawId, kb.getId(), totalPages, pageCount);

        } catch (Exception e) {
            log.error("[Wiki] Processing failed for raw={}: {}", rawId, e.getMessage(), e);
            rawService.updateProcessingStatus(rawId, "failed", e.getMessage());
            kbService.updateStatus(kb.getId(), "active");
        }
    }

    /**
     * 处理知识库中所有待处理的原始材料
     */
    public void processAllPending(Long kbId) {
        List<WikiRawMaterialEntity> pendingList = rawService.listPending(kbId);
        if (pendingList.isEmpty()) {
            log.info("[Wiki] No pending raw materials for kbId={}", kbId);
            return;
        }
        log.info("[Wiki] Processing {} pending raw materials for kbId={}", pendingList.size(), kbId);
        for (WikiRawMaterialEntity raw : pendingList) {
            processRawMaterial(raw.getId());
        }
    }

    /**
     * 分块处理大文档
     *
     * @return int[3]: [totalPages, failedChunks, totalChunks]
     */
    private int[] processInChunks(WikiKnowledgeBaseEntity kb, WikiRawMaterialEntity raw, String text) {
        int chunkSize = properties.getMaxChunkSize();
        int overlap = 500; // 块间重叠
        int start = 0;
        int totalPages = 0;
        int failedChunks = 0;

        int chunkIndex = 0;
        while (start < text.length()) {
            int previousStart = start;
            int end = Math.min(start + chunkSize, text.length());

            // 在句子边界切分
            if (end < text.length()) {
                int lastPeriod = text.lastIndexOf("。", end);
                int lastNewline = text.lastIndexOf("\n", end);
                int breakAt = Math.max(lastPeriod, lastNewline);
                if (breakAt > start + chunkSize / 2) {
                    end = breakAt + 1;
                }
            }

            String chunk = text.substring(start, end);
            log.info("[Wiki] Processing chunk {}: chars {}-{} of {}", chunkIndex, start, end, text.length());
            try {
                totalPages += processChunk(kb, raw, chunk);
            } catch (Exception e) {
                failedChunks++;
                // content_filter 错误：标注后继续处理其他 chunk
                if (e.getMessage() != null && e.getMessage().contains("content_filter")) {
                    log.warn("[Wiki] Chunk {} blocked by content filter, skipping", chunkIndex);
                } else {
                    log.warn("[Wiki] Chunk {} failed: {}", chunkIndex, e.getMessage());
                }
            }

            start = end - overlap;
            if (start < 0) start = 0;
            // 保证前进，防止死循环
            if (start <= previousStart) start = end;
            chunkIndex++;
        }
        return new int[]{totalPages, failedChunks, chunkIndex};
    }

    /**
     * 处理单个文本块
     *
     * @return 创建+更新的页面数
     */
    private int processChunk(WikiKnowledgeBaseEntity kb, WikiRawMaterialEntity raw, String textContent) {
        // 构建已有页面索引
        String existingPagesIndex = buildExistingPagesIndex(kb.getId());

        // 加载 prompt 模板
        String systemPrompt = PromptLoader.loadPrompt("wiki/digest-system");
        String userTemplate = PromptLoader.loadPrompt("wiki/digest-user");

        String userPrompt = userTemplate
                .replace("{config}", kb.getConfigContent() != null ? kb.getConfigContent() : "")
                .replace("{existing_pages}", existingPagesIndex)
                .replace("{raw_title}", raw.getTitle())
                .replace("{raw_content}", textContent);

        // 调用 LLM
        String llmResponse;
        try {
            ChatModel chatModel = buildChatModel();
            Prompt prompt = new Prompt(List.of(
                    new SystemMessage(systemPrompt),
                    new UserMessage(userPrompt)
            ));
            ChatResponse response = chatModel.call(prompt);
            llmResponse = response.getResult().getOutput().getText();
        } catch (Exception e) {
            throw new RuntimeException("LLM call failed: " + e.getMessage(), e);
        }

        // 解析并持久化页面
        return applyLlmResponse(kb.getId(), raw.getId(), llmResponse);
    }

    /**
     * 解析 LLM 响应并创建/更新 Wiki 页面
     *
     * @return 创建+更新的页面总数
     */
    private int applyLlmResponse(Long kbId, Long rawId, String llmResponse) {
        JsonNode root = parseJsonResponse(llmResponse);
        if (root == null) {
            log.warn("[Wiki] Failed to parse LLM response for kbId={}, rawId={}", kbId, rawId);
            return 0;
        }

        String sourceRawIds = "[" + rawId + "]";
        int created = 0;
        int updated = 0;

        // 新页面
        JsonNode pagesNode = root.path("pages");
        if (pagesNode.isArray()) {
            for (JsonNode pageNode : pagesNode) {
                String slug = pageNode.path("slug").asText("");
                String title = pageNode.path("title").asText("");
                String content = pageNode.path("content").asText("");
                String summary = pageNode.path("summary").asText("");

                if (slug.isBlank() || title.isBlank()) continue;

                // 检查是否已存在（LLM 可能将已有页面误判为新页面）
                WikiPageEntity existing = pageService.getBySlug(kbId, slug);
                if (existing != null) {
                    pageService.updatePageByAi(kbId, slug, content, summary, rawId);
                    updated++;
                } else {
                    pageService.createPage(kbId, slug, title, content, summary, sourceRawIds);
                    created++;
                }
            }
        }

        // 更新的页面
        JsonNode updatedPagesNode = root.path("updated_pages");
        if (updatedPagesNode.isArray()) {
            for (JsonNode pageNode : updatedPagesNode) {
                String slug = pageNode.path("slug").asText("");
                String content = pageNode.path("content").asText("");
                String summary = pageNode.path("summary").asText("");

                if (slug.isBlank()) continue;

                WikiPageEntity existing = pageService.getBySlug(kbId, slug);
                if (existing != null) {
                    // 保护手动编辑的页面：仍然更新，但 LLM 已在 prompt 中被告知要保留手动内容
                    pageService.updatePageByAi(kbId, slug, content, summary, rawId);
                    updated++;
                }
            }
        }

        log.info("[Wiki] Applied LLM response: kbId={}, rawId={}, created={}, updated={}",
                kbId, rawId, created, updated);
        return created + updated;
    }

    /**
     * 构建已有 Wiki 页面索引（供 LLM 参考）
     */
    private String buildExistingPagesIndex(Long kbId) {
        List<WikiPageEntity> summaries = pageService.listSummaries(kbId);
        if (summaries.isEmpty()) {
            return "（暂无已有页面）";
        }

        StringBuilder sb = new StringBuilder();
        for (WikiPageEntity page : summaries) {
            sb.append("- **[[").append(page.getTitle()).append("]]** (slug: `").append(page.getSlug()).append("`");
            if ("manual".equals(page.getLastUpdatedBy())) {
                sb.append(", 手动编辑");
            }
            sb.append("): ");
            sb.append(page.getSummary() != null ? page.getSummary() : "无摘要");
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    private ChatModel buildChatModel() {
        ModelConfigEntity defaultModel = modelConfigService.getDefaultModel();
        return agentGraphBuilder.buildRuntimeChatModel(defaultModel);
    }

    private JsonNode parseJsonResponse(String response) {
        if (response == null || response.isBlank()) return null;

        String cleaned = response.trim();

        // 1. 剥离 markdown 代码块标记
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        cleaned = cleaned.trim();

        // 2. 清洗控制字符（保留 \n \r \t），防止 LLM 输出含不可见字符导致 JSON 解析失败
        cleaned = cleaned.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "");

        // 3. 第一次尝试直接解析
        try {
            return objectMapper.readTree(cleaned);
        } catch (Exception e) {
            // 4. 如果整体不是 JSON，尝试提取第一个 JSON 对象块（LLM 可能在 JSON 前后加了说明文字）
            int jsonStart = cleaned.indexOf("{");
            int jsonEnd = cleaned.lastIndexOf("}");
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                String extracted = cleaned.substring(jsonStart, jsonEnd + 1);
                try {
                    return objectMapper.readTree(extracted);
                } catch (Exception e2) {
                    log.warn("[Wiki] Failed to parse extracted JSON block: {}", e2.getMessage());
                }
            }
            log.warn("[Wiki] Failed to parse JSON response: {}", e.getMessage());
            return null;
        }
    }
}
