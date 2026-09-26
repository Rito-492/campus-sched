package com.campus.events.support;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 并发测试执行器（QA-01，03 §2）。
 *
 * <p>并发测试必须使用独立连接/事务与请求屏障（CountDownLatch 起跑栅栏），
 * 不是单连接循环。每个任务获得自己的 {@link Connection}（独立事务），
 * 所有任务在同一起跑点释放后同时执行。
 *
 * <p>典型用法（两个确认请求竞争同一资源，D-AT-07）：
 * <pre>{@code
 * List<CommandResult> results = ConcurrentRunner.run(db.dataSource(), 2,
 *         conn -> postResourceConfirm(clientFor(conn), activityId));
 * }</pre>
 */
public final class ConcurrentRunner {

    /** 单个并发任务：拿到自己的独立连接，返回断言所需的结果对象。 */
    @FunctionalInterface
    public interface DbTask<T> {
        T execute(Connection connection) throws Exception;
    }

    private ConcurrentRunner() {
    }

    /**
     * 以 n 个独立连接/事务并发执行同一任务，起跑栅栏同步释放。
     *
     * @param dataSource 绑定到当前测试 schema 的数据源
     * @param parallelism 并发度（≥2 才构成并发场景）
     * @param task 每个并发分支执行的逻辑；失败分支的异常按分支序号收集后抛出聚合异常
     * @return 按分支序号排列的结果
     */
    public static <T> List<T> run(DataSource dataSource, int parallelism, DbTask<T> task) {
        if (parallelism < 1) {
            throw new IllegalArgumentException("parallelism 必须 ≥1");
        }
        List<DbTask<T>> tasks = new ArrayList<>(parallelism);
        for (int i = 0; i < parallelism; i++) {
            tasks.add(task);
        }
        return run(dataSource, tasks);
    }

    /**
     * 以独立连接/事务并发执行一组不同任务，起跑栅栏同步释放。
     * 返回值与入参列表一一对应；任何分支抛出异常时其余分支仍跑完，
     * 最终以 {@link BranchException} 聚合抛出（含各分支结果与异常，便于断言竞争结局）。
     */
    public static <T> List<T> run(DataSource dataSource, List<DbTask<T>> tasks) {
        int n = tasks.size();
        CountDownLatch ready = new CountDownLatch(n);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(n);
        try {
            List<Future<T>> futures = new ArrayList<>(n);
            for (DbTask<T> task : tasks) {
                futures.add(pool.submit(() -> {
                    try (Connection conn = dataSource.getConnection()) {
                        conn.setAutoCommit(false); // 独立事务
                        ready.countDown();
                        if (!start.await(30, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("起跑栅栏等待超时");
                        }
                        T result = task.execute(conn);
                        conn.commit();
                        return result;
                    }
                }));
            }
            if (!ready.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("并发分支未能全部就绪");
            }
            start.countDown(); // 起跑栅栏：所有分支同时开始

            List<T> results = new ArrayList<>(n);
            List<Exception> errors = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                try {
                    results.add(futures.get(i).get(60, TimeUnit.SECONDS));
                } catch (Exception e) {
                    errors.add(e);
                    results.add(null);
                }
            }
            if (!errors.isEmpty()) {
                throw new BranchException(results, errors);
            }
            return results;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("并发执行被中断", e);
        } finally {
            start.countDown(); // 兜底释放，避免就绪分支悬挂
            pool.shutdownNow();
        }
    }

    /** 并发分支部分失败时抛出；携带全部分支结果与异常，供断言「恰 1 成功 1 冲突」等结局。 */
    public static final class BranchException extends RuntimeException {
        private final transient List<?> results;
        private final transient List<Exception> errors;

        BranchException(List<?> results, List<Exception> errors) {
            super("并发分支存在失败：共 " + errors.size() + " 个分支抛出异常");
            this.results = results;
            this.errors = errors;
        }

        /** 各分支结果（失败分支为 null），按分支序号排列。 */
        public List<?> getResults() {
            return results;
        }

        /** 各分支抛出的异常。 */
        public List<Exception> getErrors() {
            return errors;
        }
    }
}
