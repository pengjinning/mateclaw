package vip.mate.tool.builtin;

import java.util.Set;

/**
 * 跟踪 Agent 委派调用的上下文信息，防止无限递归并传递父会话信息。
 * <p>
 * 使用 ThreadLocal 存储当前线程的委派层级、父会话 ID 和子 Agent 禁用工具集。
 * 每次 {@link DelegateAgentTool} 发起委派时调用 enter()，返回后调用 exit()。
 *
 * @author MateClaw Team
 */
public final class DelegationContext {

    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<String> PARENT_CONVERSATION_ID = new ThreadLocal<>();
    private static final ThreadLocal<Set<String>> CHILD_DENIED_TOOLS = new ThreadLocal<>();

    private DelegationContext() {}

    /** 获取当前委派深度（0 = 顶层调用） */
    public static int currentDepth() {
        return DEPTH.get();
    }

    /** 获取父会话 ID（用于事件 relay） */
    public static String parentConversationId() {
        return PARENT_CONVERSATION_ID.get();
    }

    /** 获取子 Agent 禁用的工具集 */
    public static Set<String> childDeniedTools() {
        Set<String> denied = CHILD_DENIED_TOOLS.get();
        return denied != null ? denied : Set.of();
    }

    /** 进入下一层委派（带父会话 ID 和子 Agent 工具限制） */
    public static void enter(String parentConversationId, Set<String> deniedTools) {
        DEPTH.set(DEPTH.get() + 1);
        PARENT_CONVERSATION_ID.set(parentConversationId);
        if (deniedTools != null) {
            CHILD_DENIED_TOOLS.set(deniedTools);
        }
    }

    /** 进入下一层委派（兼容旧调用） */
    public static void enter() {
        enter(null, null);
    }

    /** 退出当前委派层 */
    public static void exit() {
        int current = DEPTH.get();
        if (current <= 1) {
            DEPTH.remove();
            PARENT_CONVERSATION_ID.remove();
            CHILD_DENIED_TOOLS.remove();
        } else {
            DEPTH.set(current - 1);
        }
    }
}
