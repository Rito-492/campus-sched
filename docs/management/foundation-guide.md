# PM公共模块接入说明

适用：开发任务包v3.0；交付分支`work/55230223-foundation`；提交及评审状态见交接单最新记录。接收人：AN/DS/CM/UI/QA，业务接收均尚未确认。

## 1 工程与检查

Java21、Spring Boot3.5.16、Maven3.9.11，Wrapper自带发行包SHA-256核对。单元测试为`*Test`，真实库检查为`*IT`，默认`verify`包含两类且零测试失败。应用入口为`com.campus.events.CampusEventsApplication`。依赖均已实际解析。

```powershell
# 根目录，需先配置Java21及数据库环境变量
./server/mvnw.cmd -f server/pom.xml test
./server/mvnw.cmd -f server/pom.xml verify
```

2026-10-31已解除CM种子及DS类型编译阻塞，默认test可运行全部源码和当前单元集；全量verify仍因QA fixture装载顺序失败。`-Dtest=...`只筛选执行，不会解决其他源码或测试源码的编译依赖。不要通过skipTests、空实现或删除验收规避。

其他模块未就绪时，PM专用检查可明确隔离源码和测试范围：

```powershell
# 仅本机campus_test；用户名、密码和DEMO_PASSWORD从本地环境提供
# TEST_DATABASE_URL须为jdbc:postgresql://127.0.0.1:<端口>/campus_test
./server/mvnw.cmd -f server/pom.xml -Ppm-isolated clean verify
node qa/scripts/check-contract.ts
```

已接收AN/DS后，当前推荐在标准POM上执行明确模块集合：

```powershell
./server/mvnw.cmd -f server/pom.xml "-Dit.test=FoundationIT,PublishedModulesIT,StandardMavenSchemaIT,DemoDataSeederIT" clean verify
```

本轮该命令通过76项全体单元和30项指定真库IT。它是有范围的模块验证，默认全量verify另有失败日志。PublishedModulesIT显式选真实PM/AN/DS且不装配CM，验证缺依赖的错误与回滚，不使用端口替身；StandardMavenSchemaIT验证标准Spring/Flyway与QA扩展使用同一随机schema。这两项跨模块测试不编译进历史pm-isolated配置，默认构建仍包括它们。

Failsafe从`.mvn/integration-test.properties`读取test配置和运行期占位符：TEST_DATABASE_URL由环境提供，test.schema.name由QA扩展发布；不能把尚未生成的schema名提前写死或回落public。各模块显式DynamicPropertySource仍优先。本轮没有修改QA装载器，数据外键/事务问题仍需原作者修复。

2026-10-20该入口真实执行22项单元测试和21项真实PostgreSQL集成测试；不能将隔离生成的jar用于全项目发布，也不据此标记M0/M2/M3完成。PM测试为随机`t_<32hex>` schema，仅删除本次schema；不使用QA演示导入器，不能据此宣称后者已通过。正式全量检查在修复依赖后使用默认入口。

Clock默认系统UTC，只有e2e profile读取APP_TEST_NOW；普通dev不受该值影响。测试可用@Primary Clock。Jackson保留契约要求的显式null，拒绝未知字段、缺失必需字段、原始类型null、重复JSON键和尾随内容。

## 2 HTTP与账号

真实实现为API-01～06、37～39；其余API仅有统一契约。应用未添加生产替身或空成功接口。详细请求/响应结构见`contracts/openapi.yaml`，归属见[接口登记](api-register.md)。

登录示例：`POST /api/v1/auth/login`，请求为`{"username":"applicant_a","password":"本地DEMO_PASSWORD的值"}`。成功`data`含token、expiresAt和user，后续带`Authorization: Bearer <token>`。密码不返回，数据库只保存BCrypt哈希和令牌SHA-256。会话8小时，停用时删除该用户全部会话，重新启用不恢复旧令牌，退出只删当前会话。

`GET /users?purpose=staff`仅申请人可查同组织启用账号，返回StaffView；`GET /users`与`GET /organizations`需ADMIN及USER_MANAGE。列表按createdAt降序、id升序取201项，仅返回前200项并给出truncated。UI须保留该标志。

`PATCH /users/{id}/authorization`必须提供identity、roles、permissions、approvalScopes、managedResourceIds全部五项。删除ADMIN时清空permissions和managedResourceIds，删除APPROVER时清空approvalScopes。角色不推导身份，身份不产生权限；审批类别必须来自现存资源，组织与资源必须存在，通配符和重复值返回400。

API-05新建密码按NFR-08至少8个字符且UTF-8最多72字节；HTTP DTO和账号Service均检查，错误400且不创建用户或回执。该创建规则不新增既有账号登录的长度门禁。UI表单、QA数据与接口说明需保持一致，口令不写入交接日志。

真实失败示例（requestId由每次请求产生）：

```json
{"error":{"code":"UNAUTHENTICATED","message":"请重新登录","details":{}},"requestId":"请求追踪ID"}
```

未授权账号修改权限返回403 FORBIDDEN；同幂等键不同请求返回409 IDEMPOTENCY_KEY_REUSED；缺少UUID幂等键或不完整授权返回400 VALIDATION_ERROR；账号不存在返回404 NOT_FOUND。未知数据库错误为500 INTERNAL_ERROR，不返回SQL、密码或堆栈。

## 3 写操作接入

所有持久化POST/PATCH/PUT（登录/退出除外）携带UUID格式Idempotency-Key；schedule-preview、alternatives、change-preview为只读例外。UI超时重试须保留原键和请求体，收到409后重新GET实体并保留表单。

```java
CommandResult result = commandExecutor.execute(actor, key,
    "POST /api/v1/activities/" + activityId + "/submit",
    canonicalJson.write(input), () -> {
        // 此处已有事务；先锁activity/change，再按UUID字符串顺序锁用户，再锁资源。
        // 使用锁定后的最新操作者/所有者重新检查权限与身份。
        return Responses.success(200, actualActivityView);
    });
return Responses.http(result);
```

示例中的actualActivityView应来自各模块真实业务，不提供默认成功值。CommandResult.body是完整信封，不再套一层data。普通GET也通过Responses.success/list构造；Responses.error用于有意提交的业务冲突结果。

执行顺序为插入receipt占位（ON CONFLICT DO NOTHING）→锁receipt→检查route和规范化JSON的SHA-256→已有结果则重放→执行业务→保存完整status/body→提交。同用户相同键只能执行一次，字段顺序/空白及等价数字不影响hash，数组顺序保留。重放保留首次requestId。业务409若需要保存CONFLICTED等终态，应返回Responses.error；异常抛出后回执与业务整体回滚，不要捕获数据库异常后继续使用已中止事务。

## 4 端口与锁

AccountPort由PM实现，仅依赖账号Repository。get读取最新账号；lock/grantResource为MANDATORY，要求已有事务。grantResource由DS创建资源编排在锁定创建者后调用，不允许DS直接写授权表。不存在账号返回404；是否启用及是否有权操作由统一策略和调用服务检查。

AuthorizationPolicy提供role、permission、approval、resources、confirmation、registration。首次审批与改期复审共用approval：必须覆盖活动组织和主场地/所有设备类别，拒绝所有者本人。首次/改期资源复核共用confirmation：ADMIN及RESOURCE_CONFIRM，必须覆盖所有资源且非所有者。普通教师、USER_MANAGE管理员均无隐含审批权。AN/CM应在各自入口真实调用策略，PM单元测试不能替代这四个入口的N-AT-05验收。

统一锁序为receipt→activity→change→按UUID字符串升序的user→按UUID字符串升序的resource。重要操作至少锁操作者与活动所有者，取其最新身份/范围；AccountPort不能自动猜测哪些用户需锁。Java UUID自然比较对高位有符号，跨模块统一使用UUID.toString的字典序，与PostgreSQL uuid排序一致。

用户行锁统一使用`FOR NO KEY UPDATE`，登录路径同样如此。它仍互斥于其他用户写操作，但兼容command_receipt等外键的隐式KEY SHARE，避免同账号不同键的回执插入后再升级FOR UPDATE形成死锁。本版本不提供修改用户id/username等唯一键或删除账号的命令；未来新增这类操作必须重新检查锁策略，不能直接复用当前非键更新锁假设。详见[评审修复记录](review-fixes-2026-10-20.md)。

2026-10-31已接收AN ActivityPortAdapter和DS SchedulingPortAdapter；ChangePort/EventPort仍仅有定义。禁止反向依赖Controller，禁止在写端口中另开REQUIRES_NEW，禁止生产Mock。CM应先交仅依赖自身Repository的ChangePortAdapter和EventPort，再由AN/DS接入，避免循环。接收状态及尚未统一的评审建议见[契约协调记录](contract-coordination-2026-10-31.md)。
