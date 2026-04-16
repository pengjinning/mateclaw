package vip.mate.wiki.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Service;
import vip.mate.llm.embedding.EmbeddingModelFactory;
import vip.mate.llm.model.ModelConfigEntity;
import vip.mate.llm.service.ModelConfigService;
import vip.mate.system.model.SystemSettingEntity;
import vip.mate.system.repository.SystemSettingMapper;
import vip.mate.wiki.WikiProperties;
import vip.mate.wiki.model.WikiChunkEntity;
import vip.mate.wiki.model.WikiKnowledgeBaseEntity;
import vip.mate.wiki.repository.WikiChunkMapper;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

/**
 * RFC-011 + Embedding-UI-Config: Wiki 嵌入服务
 * <p>
 * 按知识库（KB）动态解析应使用的 Embedding 模型，解析优先级：
 * <ol>
 *   <li>KB 级绑定：{@link WikiKnowledgeBaseEntity#getEmbeddingModelId()}</li>
 *   <li>系统默认：{@code mate_system_setting.setting_key = 'embedding.default.model.id'}</li>
 *   <li>任意 enabled 的 embedding 模型（取第一个）</li>
 *   <li>全无 → 返回不可用，上层降级（语义搜索返回空，关键词搜索仍可用）</li>
 * </ol>
 *
 * @author MateClaw Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WikiEmbeddingService {

    private final WikiChunkMapper chunkMapper;
    private final WikiProperties properties;
    private final EmbeddingModelFactory factory;
    private final ModelConfigService modelConfigService;
    private final WikiKnowledgeBaseService kbService;
    private final SystemSettingMapper systemSettingMapper;

    /** 系统默认 embedding 模型的 mate_system_setting key */
    public static final String SYSTEM_SETTING_DEFAULT_EMBEDDING_ID = "embedding.default.model.id";

    /**
     * 判断全局是否有可用的 embedding 能力（任何 enabled 的 embedding 模型配置）
     */
    public boolean isAvailable() {
        try {
            ModelConfigEntity fallback = modelConfigService.findFirstEnabledEmbedding();
            return fallback != null;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 解析指定 KB 应使用的 embedding 模型与模型实例
     */
    public Resolved resolveForKb(Long kbId) {
        // 优先级 1：KB 级绑定
        try {
            WikiKnowledgeBaseEntity kb = kbService.getById(kbId);
            if (kb != null && kb.getEmbeddingModelId() != null) {
                ModelConfigEntity model = safeGetModel(kb.getEmbeddingModelId());
                if (isUsable(model)) {
                    return new Resolved(factory.build(model), model.getModelName());
                }
                log.warn("[WikiEmbedding] KB {} bound embedding model {} is unusable, falling back",
                        kbId, kb.getEmbeddingModelId());
            }
        } catch (Exception e) {
            log.debug("[WikiEmbedding] KB binding resolve failed for kbId={}: {}", kbId, e.getMessage());
        }

        // 优先级 2：系统默认
        Long defaultId = readSystemDefaultEmbeddingId();
        if (defaultId != null) {
            ModelConfigEntity model = safeGetModel(defaultId);
            if (isUsable(model)) {
                return new Resolved(factory.build(model), model.getModelName());
            }
            log.warn("[WikiEmbedding] System default embedding model {} is unusable, falling back", defaultId);
        }

        // 优先级 3：任意 enabled
        ModelConfigEntity anyEnabled = modelConfigService.findFirstEnabledEmbedding();
        if (isUsable(anyEnabled)) {
            return new Resolved(factory.build(anyEnabled), anyEnabled.getModelName());
        }

        log.warn("[WikiEmbedding] No usable embedding model configured. "
                + "Configure one under Settings → Models → Embedding tab.");
        return null;
    }

    /**
     * 批量嵌入指定 KB 中缺失 embedding 的 chunk。
     * <p>
     * 只嵌入 embedding 为 NULL 或 embeddingModel 与当前解析出的模型不一致的 chunk。
     * 模型切换时自动触发全量重嵌（通过 embedding_model 字段比对）。
     */
    public int embedMissingChunks(Long kbId) {
        Resolved r = resolveForKb(kbId);
        if (r == null) {
            log.debug("[WikiEmbedding] Skipping kbId={} — no embedding model available", kbId);
            return 0;
        }

        String modelName = r.modelName();
        List<WikiChunkEntity> pending = chunkMapper.selectList(
                new LambdaQueryWrapper<WikiChunkEntity>()
                        .eq(WikiChunkEntity::getKbId, kbId)
                        .and(w -> w.isNull(WikiChunkEntity::getEmbedding)
                                   .or().ne(WikiChunkEntity::getEmbeddingModel, modelName)));

        if (pending.isEmpty()) {
            log.debug("[WikiEmbedding] No chunks need embedding for kbId={}", kbId);
            return 0;
        }

        int batchSize = Math.max(1, properties.getEmbeddingBatchSize());
        int total = 0;

        for (int offset = 0; offset < pending.size(); offset += batchSize) {
            List<WikiChunkEntity> batch = pending.subList(offset, Math.min(offset + batchSize, pending.size()));
            try {
                List<String> inputs = batch.stream()
                        .map(WikiChunkEntity::getContent)
                        .toList();

                EmbeddingResponse resp = r.model().call(new EmbeddingRequest(inputs, null));

                for (int i = 0; i < batch.size(); i++) {
                    float[] vec = resp.getResults().get(i).getOutput();
                    WikiChunkEntity chunk = batch.get(i);
                    chunk.setEmbedding(floatsToBytes(vec));
                    chunk.setEmbeddingModel(modelName);
                    chunkMapper.updateById(chunk);
                }
                total += batch.size();
            } catch (Exception e) {
                log.error("[WikiEmbedding] Batch embedding failed (kbId={}, batchSize={}, model={}): {}",
                        kbId, batch.size(), modelName, e.getMessage());
                // 继续下一批，不中断
            }
        }

        if (total == 0 && !pending.isEmpty()) {
            log.warn("[WikiEmbedding] ALL {} chunks failed for kbId={} model={} — check API key / model availability",
                    pending.size(), kbId, modelName);
        } else {
            log.info("[WikiEmbedding] Embedded {}/{} chunks for kbId={}, model={}",
                    total, pending.size(), kbId, modelName);
        }
        return total;
    }

    /**
     * 查询向量化（混合搜索时调用，需指定 KB 以便解析对应模型）
     */
    public float[] embedQuery(Long kbId, String query) {
        Resolved r = resolveForKb(kbId);
        if (r == null) return null;
        try {
            EmbeddingResponse resp = r.model().call(new EmbeddingRequest(List.of(query), null));
            return resp.getResults().get(0).getOutput();
        } catch (Exception e) {
            log.error("[WikiEmbedding] Query embedding failed for kbId={}: {}", kbId, e.getMessage());
            return null;
        }
    }

    /**
     * 清空指定 KB 的所有 embedding（模型切换时调用）
     */
    public void clearEmbeddings(Long kbId) {
        chunkMapper.update(null, new LambdaUpdateWrapper<WikiChunkEntity>()
                .eq(WikiChunkEntity::getKbId, kbId)
                .set(WikiChunkEntity::getEmbedding, null)
                .set(WikiChunkEntity::getEmbeddingModel, null));
        log.info("[WikiEmbedding] Cleared all embeddings for kbId={}", kbId);
    }

    // ==================== 私有 helper ====================

    private ModelConfigEntity safeGetModel(Long id) {
        try {
            return modelConfigService.getModel(id);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isUsable(ModelConfigEntity model) {
        return model != null
                && Boolean.TRUE.equals(model.getEnabled())
                && "embedding".equals(model.getModelType());
    }

    private Long readSystemDefaultEmbeddingId() {
        try {
            SystemSettingEntity entity = systemSettingMapper.selectOne(
                    new LambdaQueryWrapper<SystemSettingEntity>()
                            .eq(SystemSettingEntity::getSettingKey, SYSTEM_SETTING_DEFAULT_EMBEDDING_ID)
                            .last("LIMIT 1"));
            if (entity == null || entity.getSettingValue() == null || entity.getSettingValue().isBlank()) {
                return null;
            }
            return Long.parseLong(entity.getSettingValue().trim());
        } catch (Exception e) {
            log.debug("[WikiEmbedding] Failed to read system default embedding id: {}", e.getMessage());
            return null;
        }
    }

    // ==================== 向量序列化 ====================

    public static byte[] floatsToBytes(float[] vec) {
        ByteBuffer buf = ByteBuffer.allocate(vec.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (float v : vec) buf.putFloat(v);
        return buf.array();
    }

    public static float[] bytesToFloats(byte[] bytes) {
        ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        float[] vec = new float[bytes.length / 4];
        for (int i = 0; i < vec.length; i++) vec[i] = buf.getFloat();
        return vec;
    }

    /** 余弦相似度 */
    public static float cosine(float[] a, float[] b) {
        if (a.length != b.length) return 0f;
        float dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        float denom = (float) (Math.sqrt(normA) * Math.sqrt(normB));
        return denom == 0 ? 0f : dot / denom;
    }

    /** 解析结果 DTO */
    public record Resolved(EmbeddingModel model, String modelName) {}

    /**
     * 暴露 factory 给外部（如测试连通性 API）
     */
    public EmbeddingModelFactory getFactory() {
        return factory;
    }
}
