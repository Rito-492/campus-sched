# DS 纯逻辑检查证据 v1.0

2026-10-19，黄方勇 55230315，main 基准 68158cf。

Java 21.0.6（D:/Java_jdk）；JUnit Jupiter 5.10.2 / Platform 1.10.2，使用本机已有 Maven 缓存。标准 Maven 工程缺失，以下仅编译 scheduling 生产代码与三个单测，直接交由真实 JUnit 引擎发现/执行。未编译全工程、未接数据库。13 用例、0 失败、0 跳过；编译及执行退出码均为 0。

## 复现命令（仓库根目录 PowerShell）

先将文末启动器保存到被 Git 忽略的 `server/target/ds-check/RunTests.java`，然后执行：

```powershell
$jars = @(Get-ChildItem C:/Users/35049/.m2/repository/org/junit,C:/Users/35049/.m2/repository/org/opentest4j,C:/Users/35049/.m2/repository/org/apiguardian -Recurse -Filter '*.jar' | ForEach-Object FullName) -join ';'
$sources = @(Get-ChildItem server/src/main/java/com/campus/events/scheduling,server/src/test/java/com/campus/events/scheduling -Filter '*.java' | ForEach-Object FullName)
& D:/Java_jdk/bin/javac.exe --release 21 -encoding UTF-8 -cp $jars -d server/target/ds-check/classes @sources server/target/ds-check/RunTests.java
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& D:/Java_jdk/bin/java.exe -cp "server/target/ds-check/classes;$jars" RunTests com.campus.events.scheduling.TimeIntervalTest com.campus.events.scheduling.CapacitySweepTest com.campus.events.scheduling.CandidateRankingTest
exit $LASTEXITCODE
```

编译器提示临时启动器使用已弃用 API（ConfigurationParameters.size），不影响执行。正式工程到位后使用 Maven Wrapper，不把临时启动器加入产品源码。

## 实际 JUnit 输出

```text
SUCCESSFUL validatesOpeningBoundaries()
SUCCESSFUL rejectsInvalidDurationPrecisionAndBuffers()
SUCCESSFUL rejectsCrossDayIncludingBuffersAndUsesShanghaiDay()
SUCCESSFUL adjacentIntervalsDoNotOverlapButSetupDoes()
SUCCESSFUL TimeIntervalTest
SUCCESSFUL reportsExactShortageAndClipsToRequestedInterval()
SUCCESSFUL ignoresOutsideLoadsAndDoesNotOverflowIntegerTotals()
SUCCESSFUL agreesWithIndependentMinuteOracleOnRandomOverlaps()
SUCCESSFUL nonSimultaneousReservationsAreNotSummed()
SUCCESSFUL overlappingFaultsClampAvailabilityToZero()
SUCCESSFUL validatesQuantities()
SUCCESSFUL CapacitySweepTest
SUCCESSFUL canonicalEquipmentIgnoresInsertionOrderAndKeepsQuantities()
SUCCESSFUL rejectsInvalidSearchCostsAndEquipmentQuantities()
SUCCESSFUL ordersAllSixCostFieldsAndIsStableAcrossInputOrder()
SUCCESSFUL CandidateRankingTest
SUCCESSFUL JUnit Jupiter
tests=13 failures=0 skipped=0
```

## 统一入口检查

- `./scripts/check.ps1 -Scope scheduling`，退出码 1：后端工程未就绪（缺 server/pom.xml 或 Maven Wrapper）。
- `./server/mvnw.cmd -f server/pom.xml test`，退出码 1：PowerShell 命令不存在。
- `git diff --check`，退出码 0。
- Maven verify / PostgreSQL / SchemaIT / 产品验收 / StrategyEvaluator 评测：未执行。

## 本轮使用的临时 JUnit 引擎启动器

```java
import java.util.*;
import org.junit.jupiter.engine.JupiterTestEngine;
import org.junit.platform.engine.*;
import org.junit.platform.engine.discovery.DiscoverySelectors;

public class RunTests {
    public static void main(String[] args) {
        var config = new ConfigurationParameters() {
            public Optional<String> get(String key) { return Optional.empty(); }
            public Optional<Boolean> getBoolean(String key) { return Optional.empty(); }
            public int size() { return 0; }
            public Set<String> keySet() { return Set.of(); }
        };
        var request = new EngineDiscoveryRequest() {
            public <T extends DiscoverySelector> List<T> getSelectorsByType(Class<T> type) {
                return Arrays.stream(args).map(DiscoverySelectors::selectClass).filter(type::isInstance).map(type::cast).toList();
            }
            public <T extends DiscoveryFilter<?>> List<T> getFiltersByType(Class<T> type) { return List.of(); }
            public ConfigurationParameters getConfigurationParameters() { return config; }
        };
        int[] counts = new int[3];
        var listener = new EngineExecutionListener() {
            public void executionSkipped(TestDescriptor test, String reason) { counts[2]++; }
            public void executionFinished(TestDescriptor test, TestExecutionResult result) {
                if (test.isTest()) counts[0]++;
                if (result.getStatus() != TestExecutionResult.Status.SUCCESSFUL) counts[1]++;
                System.out.println(result.getStatus() + " " + test.getDisplayName());
                result.getThrowable().ifPresent(Throwable::printStackTrace);
            }
        };
        var engine = new JupiterTestEngine();
        var root = engine.discover(request, UniqueId.forEngine(engine.getId()));
        engine.execute(new ExecutionRequest(root, listener, config));
        System.out.printf("tests=%d failures=%d skipped=%d%n", counts[0], counts[1], counts[2]);
        if (counts[0] == 0 || counts[1] != 0 || counts[2] != 0) System.exit(1);
    }
}

```
