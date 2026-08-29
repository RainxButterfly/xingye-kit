<div align="center">

# 星叶工具箱 xingye-kit

**一套零依赖、锁定 JDK 8 语法的通用 Java 工具集，可选 Spring Boot 自动装配**

[![JDK](https://img.shields.io/badge/JDK-8%2B-orange.svg)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.7.x%20%7C%203.x-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Dependencies](https://img.shields.io/badge/dependencies-zero-success.svg)](#)
[![Maven](https://img.shields.io/badge/Maven-3.6%2B-c71a36.svg)](https://maven.apache.org/)
[![License: AGPL v3.0](https://img.shields.io/badge/License-AGPL%20v3.0-blue.svg)](https://www.gnu.org/licenses/agpl-3.0.html)

</div>

---

## 目录

- [项目简介](#项目简介)
- [模块总览](#模块总览)
- [环境要求](#环境要求)
- [快速开始](#快速开始)
- [核心用法示例](#核心用法示例)
- [Spring Boot 自动装配](#spring-boot-自动装配)
- [兼容性矩阵](#兼容性矩阵)
- [构建](#构建)
- [项目结构](#项目结构)
- [设计约定](#设计约定)
- [参与贡献](#参与贡献)
- [开源协议](#开源协议)

## 项目简介

`xingye-kit`（星叶工具箱）是一套面向日常开发的通用 Java 工具集。它的核心理念是：**用 JDK 8 的语法，写出现代化的代码风格和 API 设计，兼顾兼容性与可读性**。

- **零依赖**：7 个核心模块不依赖 Spring、不依赖任何第三方库，可在任何 Java 项目（包括 Android 服务端、纯后端服务、工具脚本）中直接使用
- **全版本兼容**：源码锁定 JDK 8 语法、编译为 JDK 8 字节码，天然运行在 JDK 8 / 11 / 17 / 21 / 25 上
- **Spring 可选**：唯一的 `xingye-kit-boot` 模块提供自动装配，同时兼容 Spring Boot 2.7.x 与 3.x，不需要 Spring 时完全不用引入
- **接口与实现分离**：厂商类能力（短信、邮件、Redis 等）只定义契约 + 提供 JDK 默认实现，真实厂商接入由使用方按接口适配，不强绑定任何 SDK
- **安全默认**：AES-GCM 随机 IV、PBKDF2 按 OWASP 推荐迭代数、JWT 常量时间比较、ZIP 解压防路径穿越（Zip-Slip）、日志脱敏注解

## 模块总览

| 模块 | 说明 | 主要工具 |
|---|---|---|
| `xingye-kit-core` | 基础核心，被其余模块依赖 | `Result`、`BizException`、`ErrorCode`、`Assert`、`StringUtils`、`DateUtils`、`StopWatch`、`RetryTemplate`、`IdGenerator`、`ClassUtils` |
| `xingye-kit-id` | 标识生成 | `Snowflake`（时钟回拨处理）、`UuidUtils`、`ShortCode`（Base62 / 防歧义字符）、`RequestNo` |
| `xingye-kit-notify` | 通知通信 | `Notifier`、`SmsClient`、`MailClient`、`WebhookClient`（钉钉/飞书/企业微信）、`VerificationCode`、`NotificationTemplate` |
| `xingye-kit-net` | HTTP 与远程调用 | `HttpTool`（超时/重试/代理/上传）、`HttpRequest`、`HttpResponse`、`RateLimiter`（令牌桶）、`CircuitBreaker` |
| `xingye-kit-io` | 文件与 IO | `FileUtils`、`ZipUtils`、`CsvWriter` / `CsvReader`、`QrCodeUtils`（ZXing 可选）、`ImageUtils` |
| `xingye-kit-cache` | 缓存 | `LocalCache`（TTL + 近似 LRU）、`RedisHelper`（前缀 key / 分布式锁 / 计数器）、`Idempotent` |
| `xingye-kit-security` | 安全 | `AESUtils`（GCM）、`RSAUtils`、`HashUtils`（SHA/HMAC/PBKDF2）、`JwtWrapper`（HS256/384/512、RS256）、`SensitiveMask` |
| `xingye-kit-boot` | Spring Boot 自动装配（唯一含 Spring 依赖的模块） | `XingyeKitAutoConfiguration` 及 http / id / cache / notification 四组装配 |

## 环境要求

| 项 | 要求 |
|---|---|
| JDK | 8 及以上（编译目标 1.8） |
| Maven | 3.6+ |
| Spring Boot（可选） | 2.7.x 或 3.x，仅在引入 `xingye-kit-boot` 时需要 |

## 快速开始

### 1. 引入依赖

**方式 A：安装到本地仓库 / 私服**

```bash
git clone https://github.com/RainxButterfly/xingye-kit.git
cd xingye-kit
mvn clean install
```

**方式 B：直接在项目中按需引入**

```xml
<dependency>
    <groupId>com.xingheyiye</groupId>
    <artifactId>xingye-kit-core</artifactId>
    <version>1.0.0</version>
</dependency>
```

> 只用 HTTP 工具就引 `xingye-kit-net`，只用加密就引 `xingye-kit-security`——各核心模块可独立使用；`xingye-kit-id` 依赖 `core`，其余模块零内部依赖。

### 2. 非 Spring 项目三行上手

```java
// 统一返回结果
Result<User> result = Result.ok(user);

// 雪花 ID
Snowflake snowflake = new Snowflake(1, 1);
long id = snowflake.nextLongId();

// HTTP 调用
HttpRequest request = HttpRequest.get("https://api.example.com/users/1").build();
HttpResponse response = HttpTool.send(request);
```

### 3. Spring Boot 项目

```xml
<dependency>
    <groupId>com.xingheyiye</groupId>
    <artifactId>xingye-kit-boot</artifactId>
    <version>1.0.0</version>
</dependency>
```

```yaml
xingye-kit:
  http:
    connect-timeout: 5000
    read-timeout: 10000
    max-retry: 1
  id:
    worker-id: 1
    datacenter-id: 1
  cache:
    maximum-size: 10000
    expire-after-write-millis: 600000
    cleanup-interval-seconds: 60
  notify:
    default-channel: log
```

引入即自动注册 `HttpTool`、`Snowflake` / `IdGenerator`、`LocalCache`、`Notifier` 四组 Bean，详见 [Spring Boot 自动装配](#spring-boot-自动装配)。

## 核心用法示例

### core —— 统一返回 / 断言 / 重试

```java
// 业务异常携带错误码
public enum OrderErrorCode implements ErrorCode {
    NOT_FOUND(40401, "订单不存在");

    private final int code;
    private final String message;
    // ...构造器与 getter
}

Assert.notNull(order, OrderErrorCode.NOT_FOUND.getCode(), OrderErrorCode.NOT_FOUND.getMessage());
Result<Order> result = Result.fail(OrderErrorCode.NOT_FOUND);

// 指数退避重试：最多 3 次，初始 200ms，倍率 2，封顶 2s，仅 IO 异常触发重试
String data = RetryTemplate.create()
        .maxAttempts(3)
        .retryOn(IOException.class)
        .exponentialBackoff(200, 2.0, 2000)
        .execute(() -> loadFromRemote());
```

### id —— 雪花 ID / 短码 / 流水号

```java
// workerId, datacenterId（位宽可配，时钟回拨自愈）
Snowflake snowflake = new Snowflake(1, 1);
long id = snowflake.nextLongId();

// 反解生成时间
long bornAt = Snowflake.extractTimestamp(id);

// 趋势有序 UUID，适合做 MySQL 主键
UuidUtils.ordered();

// 防歧义字符短码（剔除 0/O/1/l/I）
ShortCode.randomUnambiguous(8);

// 时间串 + 节点 + 序列，如 260830101530123 + 07 + 0001
RequestNo requestNo = RequestNo.builder().node("07").build();
String no = requestNo.next();
```

### net —— HTTP / 限流 / 熔断

```java
// POST JSON + 超时
HttpResponse resp = HttpTool.send(HttpRequest.post("https://api.example.com/orders")
        .bearerToken(token)
        .json("{\"skuId\": 1001}")
        .connectTimeoutMillis(3000)
        .readTimeoutMillis(5000)
        .build());
if (resp.isSuccess()) {
    System.out.println(resp.getBodyText() + " / " + resp.getElapsedMillis() + "ms");
}

// 令牌桶：每秒 100 个令牌，突发容量 200
RateLimiter limiter = new RateLimiter(100, 200);
if (limiter.tryAcquire()) { /* 放行 */ }

// 熔断器：窗口 20 次，失败率 >= 50% 熔断 30s，半开放行 1 个试探请求
CircuitBreaker breaker = new CircuitBreaker("order-query", 20, 0.5, 30000, 1);
if (breaker.allowRequest()) {
    try {
        callRemote();
        breaker.recordSuccess();
    } catch (Exception e) {
        breaker.recordFailure();
    }
}
```

### notify —— 验证码 / Webhook

```java
// 验证码：60s 有效、60s 防重发、错 5 次作废
VerificationCode code = new VerificationCode(new InMemoryCodeStore());
String target = "13800138000";
code.generate(target);   // 内部生成并存储，60s 内重复生成抛 IllegalStateException
boolean ok = code.verify(target, userInput);   // 一次性校验

// 钉钉机器人
WebhookClient dingTalk = new DingTalkWebhookClient("https://oapi.dingtalk.com/robot/send?access_token=xxx");
SendResult sendResult = dingTalk.send("告警", "CPU 使用率超过 90%");
```

### cache —— 本地缓存 / 分布式锁 / 幂等

```java
// 本地缓存：容量 2048、写入 60s 过期、每 30s 后台清理
LocalCache<String, User> cache = LocalCache.<String, User>newBuilder()
        .maximumSize(2048)
        .expireAfterWriteMillis(60000)
        .cleanupIntervalSeconds(30)
        .build();
User user = cache.get("user:1001", key -> loadUser(key));

// 分布式锁 / 计数（RedisClient 由 Jedis/Lettuce 适配实现）
RedisHelper redis = new RedisHelper(myRedisClient, "app1");
if (redis.tryLock("order:1001", "worker-07", 30000)) {
    try { /* 临界区 */ } finally { redis.unlock("order:1001", "worker-07"); }
}
// 60s 固定窗口计数
long count = redis.nextCount("api:/pay", 60000);

// 幂等：请求号去重
Idempotent idempotent = new Idempotent(new MemoryIdempotentStore());
if (!idempotent.tryBegin(requestNo, 30000)) { return Result.fail(40901, "重复请求"); }
// ... 业务成功后 idempotent.complete(requestNo)
```

### security —— 加密 / JWT / 脱敏

```java
// 口令派生 AES-GCM（salt|iv|密文 一段 Base64）
String cipher = AESUtils.encryptWithPassword(password, "机密数据");
String plain = AESUtils.decryptWithPassword(password, cipher);

// 零依赖 JWT
String token = JwtWrapper.create()
        .issuer("app1")
        .expiresInSeconds(1800)
        .claim("uid", 10001L)
        .signHmacSha256("at-least-16-bytes-secret!!");
JwtClaims claims = JwtWrapper.verify(token, "at-least-16-bytes-secret!!");

// 密码哈希（PBKDF2，OWASP 推荐迭代数；BCrypt/Argon2 可实现 PasswordHasher 接入）
PasswordHasher hasher = new Pbkdf2PasswordHasher();
String stored = hasher.hash("raw-password");
boolean valid = hasher.verify("raw-password", stored);

// 日志脱敏：实体字段标注 @Sensitive
public class UserVO {
    @Sensitive(SensitiveType.PHONE) private String phone;   // 138****8000
    @Sensitive(SensitiveType.ID_CARD) private String idCard;   // 330***********1234
    @Sensitive(SensitiveType.BANK_CARD) private String bankCard;   // 6222************5678
}
log.info("user={}", SensitiveMask.maskObject(userVO));
```

### io —— ZIP / CSV / 二维码

```java
// 内置 Zip-Slip 路径穿越防护
ZipUtils.unzip(uploadFile, targetDir);

try (CsvWriter writer = new CsvWriter(new File("users.csv"), StandardCharsets.UTF_8)) {
    writer.writeHeader("id", "name", "memo");
    // 含逗号/引号/换行的字段自动 RFC 4180 转义
    writer.writeRow("1", "星叶", "含,逗号 \"引号\"\n换行");
}

// 二维码：ZXing 为运行时可选依赖（反射调用），不引入则 available() == false
if (QrCodeUtils.available()) {
    byte[] png = QrCodeUtils.generatePng("https://github.com/RainxButterfly/xingye-kit", 300, 300);
}
```

## Spring Boot 自动装配

引入 `xingye-kit-boot` 后自动注册以下 Bean（均带 `@ConditionalOnMissingBean`，自定义同类型 Bean 即可覆盖默认装配；对应模块类不存在时整体跳过）：

| Bean | 类型 | 配置前缀 |
|---|---|---|
| `httpTool` | `com.xingheyiye.xingye.kit.net.HttpTool` | `xingye-kit.http.*` |
| `snowflake` | `com.xingheyiye.xingye.kit.id.Snowflake` | `xingye-kit.id.*` |
| `idGenerator` | `com.xingheyiye.xingye.kit.core.IdGenerator`（委托 snowflake） | `xingye-kit.id.*` |
| `localCache` | `com.xingheyiye.xingye.kit.cache.LocalCache<Object,Object>`（关闭时自动 `shutdown()`） | `xingye-kit.cache.*` |
| `notifier` | `com.xingheyiye.xingye.kit.notify.Notifier`（默认 `LoggingNotifier`） | `xingye-kit.notify.*` |

**全部配置项：**

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `xingye-kit.http.connect-timeout` | `5000` | 连接超时（毫秒） |
| `xingye-kit.http.read-timeout` | `10000` | 读取超时（毫秒） |
| `xingye-kit.http.max-retry` | `1` | 网络/连接类错误自动重试次数 |
| `xingye-kit.id.worker-id` | `1` | 雪花机器位（默认 5 位：0..31，多实例必须唯一） |
| `xingye-kit.id.datacenter-id` | `1` | 雪花数据中心位（默认 5 位：0..31） |
| `xingye-kit.cache.maximum-size` | `10000` | 本地缓存最大条目数 |
| `xingye-kit.cache.expire-after-write-millis` | `600000` | 写入后过期时长（毫秒） |
| `xingye-kit.cache.cleanup-interval-seconds` | `60` | 后台清理周期（秒） |
| `xingye-kit.notify.default-channel` | `log` | 默认通知渠道标识 |
| `xingye-kit.notify.sms.provider` | - | 短信厂商标识（仅承载配置，供使用方实现读取） |
| `xingye-kit.notify.sms.access-key` | - | AccessKey（推荐 `${SMS_ACCESS_KEY}` 环境变量注入） |
| `xingye-kit.notify.sms.access-secret` | - | AccessKey Secret（推荐环境变量注入） |

## 兼容性矩阵

| JDK | 8 | 11 | 17 | 21 | 25 |
|---|---|---|---|---|---|
| 核心七模块 | ✅ | ✅ | ✅ | ✅ | ✅ |
| xingye-kit-boot | ✅（Boot 2.7.x） | ✅ | ✅（Boot 2.7.x / 3.x） | ✅（Boot 3.x） | ✅（Boot 3.x） |

| Spring Boot | 2.7.x | 3.x |
|---|---|---|
| 支持方式 | `spring.factories` + `AutoConfiguration.imports` 双注册 | `AutoConfiguration.imports` |

## 构建

```bash
# 完整构建（产物为各模块 jar）
mvn clean package

# 安装到本地仓库
mvn clean install
```

构建产物：`xingye-kit-*/target/*.jar`。本工程未配置 `maven-source-plugin` 等附加插件，按需自行添加。

## 项目结构

```
xingye-kit
├── pom.xml
│   (父 POM：packaging=pom，统一版本与编译配置)
├── xingye-kit-core
│   └── src/main/java/com/xingheyiye/xingye/kit/core
├── xingye-kit-id
│   └── src/main/java/com/xingheyiye/xingye/kit/id
│       └── impl (IdGenerator 适配实现)
├── xingye-kit-notify
│   └── src/main/java/com/xingheyiye/xingye/kit/notify
│       └── impl (JDK 默认实现 + 钉钉/飞书/企微 Webhook)
├── xingye-kit-net
│   └── src/main/java/com/xingheyiye/xingye/kit/net
├── xingye-kit-io
│   └── src/main/java/com/xingheyiye/xingye/kit/io
├── xingye-kit-cache
│   └── src/main/java/com/xingheyiye/xingye/kit/cache
│       └── impl (内存幂等存储)
├── xingye-kit-security
│   └── src/main/java/com/xingheyiye/xingye/kit/security
│       └── impl (PBKDF2 密码哈希)
└── xingye-kit-boot
    ├── src/main/java/com/xingheyiye/xingye/kit/boot
    └── src/main/resources/META-INF
        ├── spring.factories (Boot 2.7 注册)
        └── spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports (Boot 2.7+/3.x 注册)
```

每个模块的包内均含 `package-info.java` 说明模块用途；每个类的 Javadoc 含职责、适用场景、线程安全性声明与 `@code` 使用示例，可直接当文档读。

## 设计约定

1. **JDK 8 语法锁定**：不使用 `var`、`record`、`switch` 箭头、`sealed`、文本块、`instanceof` 模式匹配、`Stream.toList()` 等高版本特性
2. **核心模块零依赖**：仅 boot 模块依赖 Spring（`spring-boot-autoconfigure` 为 `provided`，由宿主应用提供）
3. **接口与实现分离**：实现类统一放同包 `impl` 子包
4. **配置不硬编码**：所有参数经构造器/Builder 传入；密钥、token 一律外部注入，代码中不落任何敏感值
5. **错误不静默**：HTTP 网络错误以 `HttpErrorType` 分类返回而非抛异常；解压、解析类错误显式抛出并说明原因

## 参与贡献

1. Fork 本仓库并创建特性分支
2. 提交前请确保 `mvn clean package` 通过，且新增代码遵循上述设计约定与注释规范
3. 通过 [Issues](https://github.com/RainxButterfly/xingye-kit/issues) 提交问题或提案
4. 任何贡献默认同意以 AGPLv3.0 协议发布

## 开源协议

本项目基于 [GNU Affero General Public License v3.0](https://www.gnu.org/licenses/agpl-3.0.html) 开源。

- 你可以自由使用、修改、分发本工具库
- 如果你修改了本工具库并通过网络提供服务，你必须开源你的修改
- 完整协议文本见仓库根目录 `LICENSE` 文件

---

<div align="center">

**星叶工具箱** · 由 [星河一叶 (RainxButterfly)](https://github.com/RainxButterfly) 维护

如果这个项目对你有帮助，欢迎 Star ⭐ 支持

</div>
