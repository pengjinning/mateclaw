package vip.mate.tool.builtin;

/**
 * 跟踪 Agent 委派调用深度，防止无限递归。
 * <p>
 * 使用 ThreadLocal 存储当前线程的委派层级。
 * 每次 {@link DelegateAgentTool} 发起委派时 +1，返回后 -1。
 *
 * @author MateClaw Team
 */
public final class DelegationContext {

    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    private DelegationContext() {}

    /** 获取当前委派深度（0 = 顶层调用） */
    public static int currentDepth() {
        return DEPTH.get();
    }

    /** 进入下一层委派 */
    public static void enter() {
        DEPTH.set(DEPTH.get() + 1);
    }

    /** 退出当前委派层 */
    public static void exit() {
        int current = DEPTH.get();
        if (current <= 1) {
            DEPTH.remove();
        } else {
            DEPTH.set(current - 1);
        }
    }
}
