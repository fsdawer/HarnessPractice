package beauty.beauty.global.db;

public class DataSourceContextHolder {

    private static final ThreadLocal<DataSourceType> CONTEXT     = new ThreadLocal<>();
    private static final ThreadLocal<Boolean>        FORCE_MASTER = new ThreadLocal<>();

    public static void forceMaster() {
        FORCE_MASTER.set(true);
        CONTEXT.set(DataSourceType.MASTER);
    }

    public static void set(DataSourceType type) {
        if (Boolean.TRUE.equals(FORCE_MASTER.get())) return;
        CONTEXT.set(type);
    }

    public static DataSourceType get() {
        return CONTEXT.get() == null ? DataSourceType.MASTER : CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
        FORCE_MASTER.remove();
    }
}
