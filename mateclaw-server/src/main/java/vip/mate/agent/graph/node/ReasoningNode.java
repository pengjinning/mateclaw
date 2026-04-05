package vip.mate.agent.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.util.StringUtils;
import vip.mate.agent.AgentToolSet;
import vip.mate.agent.GraphEventPublisher;
import vip.mate.agent.graph.NodeStreamingChatHelper;
import vip.mate.agent.context.ConversationWindowManager;
import vip.mate.agent.context.RuntimeContextInjector;
import vip.mate.agent.graph.state.FinishReason;
import vip.mate.agent.graph.state.MateClawStateAccessor;
import vip.mate.agent.graph.state.MateClawStateKeys;

import vip.mate.channel.web.ChatStreamTracker;

import java.util.*;
import java.util.concurrent.CancellationException;

import static vip.mate.agent.graph.state.MateClawStateKeys.*;

/**
 * 推理节点（ReAct Thought 阶段）
 * <p>
 * 调用 LLM 进行单次推理，判断是否需要工具调用。
 * 关键：通过 internalToolExecutionEnabled=false 禁用 ChatModel 内部工具循环，
 * 使 StateGraph 完全控制 ReAct 循环。
 * <p>
 * 支持 forced_tool_call 机制：当审批通过后的重放请求到达时，
 * 跳过 LLM 调用，直接发出预批准的工具调用。
 * <p>
 * 使用 {@link NodeStreamingChatHelper} 进行流式调用，实时推送 content/thinking 增量。
 *
 * @author MateClaw Team
 */
@Slf4j
public class ReasoningNode implements NodeAction {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ChatModel chatModel;
    private final List<ToolCallback> toolCallbacks;
    private final String reasoningEffort;
    private final NodeStreamingChatHelper streamingHelper;
    private final ConversationWindowManager conversationWindowManager;
    private final ChatStreamTracker streamTracker;

    public ReasoningNode(ChatModel chatModel, AgentToolSet toolSet, String reasoningEffort,
                         NodeStreamingChatHelper streamingHelper,
                         ConversationWindowManager conversationWindowManager,
                         ChatStreamTracker streamTracker) {
        this.chatModel = chatModel;
        this.toolCallbacks = toolSet.callbacks();
        this.reasoningEffort = reasoningEffort;
        this.streamingHelper = streamingHelper;
        this.conversationWindowManager = conversationWindowManager;
        this.streamTracker = streamTracker;
    }

    public ReasoningNode(ChatModel chatModel, AgentToolSet toolSet, String reasoningEffort,
                         NodeStreamingChatHelper streamingHelper,
                         ConversationWindowManager conversationWindowManager) {
        this(chatModel, toolSet, reasoningEffort, streamingHelper, conversationWindowManager, null);
    }

    /**
     * @deprecated Use {@link #ReasoningNode(ChatModel, AgentToolSet, String, NodeStreamingChatHelper)} instead
     */
    @Deprecated
    public ReasoningNode(ChatModel chatModel, AgentToolSet toolSet, String reasoningEffort) {
        this(chatModel, toolSet, reasoningEffort, null, null);
    }

    /**
     * @deprecated Use {@link #ReasoningNode(ChatModel, AgentToolSet, String, NodeStreamingChatHelper)} instead
     */
    @Deprecated
    public ReasoningNode(ChatModel chatModel, AgentToolSet toolSet) {
        this(chatModel, toolSet, null, null, null);
    }

    /**
     * @deprecated Use {@link #ReasoningNode(ChatModel, AgentToolSet, String, NodeStreamingChatHelper)} instead
     */
    @Deprecated
    public ReasoningNode(ChatModel chatModel, List<ToolCallback> toolCallbacks) {
        this.chatModel = chatModel;
        this.toolCallbacks = toolCallbacks;
        this.reasoningEffort = null;
        this.streamingHelper = null;
        this.conversationWindowManager = null;
        this.streamTracker = null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> apply(OverAllState state) throws Exception {
        MateClawStateAccessor accessor = new MateClawStateAccessor(state);

        // ======= 取消检查 =======
        String conversationId = accessor.conversationId();
        if (streamTracker != null && streamTracker.isStopRequested(conversationId)) {
            log.info("[ReasoningNode] Stop requested, aborting LLM call");
            throw new CancellationException("Stream stopped by user");
        }

        // ======= forced_tool_call 检测：审批通过后的重放 =======
        String forcedToolCallJson = accessor.forcedToolCall();
        if (!forcedToolCallJson.isEmpty()) {
            try {
                log.info("[ReasoningNode] Detected forced_tool_call, skipping LLM, emitting tool call directly");

                AssistantMessage.ToolCall toolCall = deserializeToolCall(forcedToolCallJson);

                // 构造合成的 AssistantMessage
                AssistantMessage syntheticMsg = AssistantMessage.builder()
                        .content("")
                        .toolCalls(List.of(toolCall))
                        .build();

                return MateClawStateAccessor.output()
                        .needsToolCall(true)
                        .toolCalls(List.of(toolCall))
                        .messages(List.of((Message) syntheticMsg))
                        .iterationCount(accessor.iterationCount() + 1)
                        .forcedToolCall("")  // 清空，防止下一轮再触发
                        .currentPhase("forced_replay")
                        .contentStreamed(true)   // 无 content 需要流式推送
                        .thinkingStreamed(true)  // 无 thinking 需要流式推送
                        .events(List.of(GraphEventPublisher.phase("forced_replay", Map.of(
                                "toolName", toolCall.name(),
                                "iteration", accessor.iterationCount() + 1))))
                        .build();
            } catch (Exception e) {
                log.error("[ReasoningNode] Failed to deserialize forced_tool_call, falling through to normal LLM: {}",
                        e.getMessage());
                // 不 return，清空 forcedToolCall 后走正常 LLM 流程
            }
        }
        // ======= forced_tool_call 检测结束 =======

        String systemPrompt = accessor.systemPrompt();
        List<Message> messages = accessor.messages();

        // 构建 Prompt，附带工具定义但禁用内部工具执行
        List<Message> promptMessages = new ArrayList<>();
        promptMessages.add(new SystemMessage(systemPrompt));
        // 注入运行时上下文（当前时间），让 LLM 在推理阶段即可感知真实日期
        promptMessages.add(new UserMessage(RuntimeContextInjector.buildContextMessage()));
        promptMessages.addAll(messages);

        ChatOptions options;
        if (StringUtils.hasText(reasoningEffort)) {
            OpenAiChatOptions oaiOpts = OpenAiChatOptions.builder()
                    .toolCallbacks(toolCallbacks)
                    .reasoningEffort(reasoningEffort)
                    .build();
            oaiOpts.setInternalToolExecutionEnabled(false);
            options = oaiOpts;
        } else {
            options = ToolCallingChatOptions.builder()
                    .toolCallbacks(toolCallbacks)
                    .internalToolExecutionEnabled(false)
                    .build();
        }

        Prompt prompt = new Prompt(promptMessages, options);

        log.debug("[ReasoningNode] Calling LLM with {} messages, {} tool definitions, iteration {}/{}",
                promptMessages.size(), toolCallbacks.size(),
                accessor.iterationCount(), accessor.maxIterations());

        // 构建 phase 事件
        GraphEventPublisher.GraphEvent phaseEvent = GraphEventPublisher.phase("reasoning",
                Map.of("iteration", accessor.iterationCount()));

        // 流式 LLM 调用：content/thinking 增量实时推送给前端
        NodeStreamingChatHelper.StreamResult result = streamingHelper.streamCall(
                chatModel, prompt, conversationId, "reasoning");

        // PTL 处理：压缩后重试（由 Node 层负责，因为 helper 不知道哪些消息可压缩）
        if (result.isPromptTooLong() && conversationWindowManager != null) {
            log.warn("[ReasoningNode] Prompt too long, attempting compaction and retry");
            List<Message> compactedMessages = conversationWindowManager.compactForRetry(messages);
            if (compactedMessages != null && compactedMessages.size() < messages.size()) {
                List<Message> retryPromptMessages = new ArrayList<>();
                retryPromptMessages.add(new SystemMessage(systemPrompt));
                retryPromptMessages.addAll(compactedMessages);
                Prompt retryPrompt = new Prompt(retryPromptMessages, options);
                log.info("[ReasoningNode] Retrying with compacted messages: {} -> {} messages",
                        messages.size(), compactedMessages.size());
                result = streamingHelper.streamCall(chatModel, retryPrompt, conversationId, "reasoning_compact_retry");
            } else {
                log.warn("[ReasoningNode] Compaction did not reduce messages, cannot retry");
            }
        }

        // 用户主动停止且有部分内容：设为 finalAnswer + finalThinking 让 accumulator 持久化
        if (result.stopped() && result.hasAnyContent()) {
            String partialText = result.text() != null ? result.text() : "";
            String partialThinking = result.thinking() != null ? result.thinking() : "";
            log.info("[ReasoningNode] Stop with partial content ({} chars, thinking {} chars), " +
                            "flushing as final answer",
                    partialText.length(), partialThinking.length());
            var builder = MateClawStateAccessor.output()
                    .finalAnswer(partialText)
                    .contentStreamed(true)
                    .mergeUsage(state, result)
                    .finishReason(FinishReason.STOPPED);
            if (!partialThinking.isEmpty()) {
                builder.finalThinking(partialThinking);
                builder.thinkingStreamed(true);
            }
            return builder.build();
        }

        // 错误处理：无任何可用内容时直接终止图执行。
        // NodeStreamingChatHelper 已广播结构化 error 事件，这里不能再把错误文本当成正常 final answer。
        if (result.hasFatalError()) {
            log.error("[ReasoningNode] Fatal LLM error: {}", result.errorMessage());
            throw new IllegalStateException(result.errorMessage());
        }
        if (result.partial()) {
            // 有部分内容 — 当作最终回答处理（LLM 已经回答了大部分）
            log.warn("[ReasoningNode] Partial LLM result ({} chars), treating as final answer", result.text().length());
        }

        if (result.hasToolCalls()) {
            // LLM 请求工具调用
            log.info("[ReasoningNode] LLM requested {} tool call(s): {}",
                    result.toolCalls().size(),
                    result.toolCalls().stream().map(AssistantMessage.ToolCall::name).toList());

            return MateClawStateAccessor.output()
                    .needsToolCall(true)
                    .toolCalls(result.toolCalls())
                    .messages(List.of((Message) result.assistantMessage()))
                    .currentPhase("reasoning")
                    .currentThinking(result.thinking())
                    // 暂存已流式推送的 content/thinking，供 AWAITING_APPROVAL 路径持久化
                    .streamedContent(result.text() != null ? result.text() : "")
                    .streamedThinking(result.thinking())
                    .contentStreamed(true)
                    .thinkingStreamed(!result.thinking().isEmpty())
                    .mergeUsage(state, result)
                    .events(List.of(phaseEvent))
                    .build();
        } else {
            // LLM 给出最终回答
            String content = result.text();
            log.info("[ReasoningNode] LLM produced final answer ({} chars)", content != null ? content.length() : 0);

            return MateClawStateAccessor.output()
                    .needsToolCall(false)
                    .finalAnswer(content != null ? content : "")
                    .finalThinking(result.thinking())
                    .messages(List.of((Message) result.assistantMessage()))
                    .currentPhase("reasoning")
                    .contentStreamed(true)
                    .thinkingStreamed(!result.thinking().isEmpty())
                    .mergeUsage(state, result)
                    .events(List.of(phaseEvent))
                    .build();
        }
    }

    /**
     * 反序列化 JSON 为 ToolCall
     */
    private AssistantMessage.ToolCall deserializeToolCall(String json) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, String> map = OBJECT_MAPPER.readValue(json, Map.class);
            return new AssistantMessage.ToolCall(
                    map.getOrDefault("id", UUID.randomUUID().toString()),
                    map.getOrDefault("type", "function"),
                    map.getOrDefault("name", ""),
                    map.getOrDefault("arguments", "")
            );
        } catch (Exception e) {
            log.error("[ReasoningNode] Failed to deserialize forced_tool_call: {}", e.getMessage());
            throw new RuntimeException("无法反序列化 forced_tool_call: " + e.getMessage(), e);
        }
    }
}
