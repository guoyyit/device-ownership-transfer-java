# Device Ownership Transfer

设备初始化接入与所有权转移服务。设备登记后绑定首任所有者，后续移交必须经现任所有者发起、
设备确认后完成归属切换。密钥、凭证与所有权状态全部在本地生成和校验，不依赖外部服务。

## 构建与运行

```bash
mvn compile        # 编译
mvn test           # 运行全部测试
mvn spring-boot:run
```

服务默认监听 `8080`，状态持久化到 `app.store.path`（默认 `./data/state.json`）。
所有状态变更先写临时文件再原子 rename，崩溃不会产生部分写入；重启后已登记设备、
所有权关系、待处理转移、已用 nonce、签发方密钥完整恢复。

## 角色与密钥

- **签发方（Issuer）**：服务启动时在本地生成 Ed25519 密钥对并持久化，负责签发所有权凭证。
- **设备**：持有自己的 Ed25519 密钥对，公钥在登记时提交，私钥用于签名确认/拒绝/轮询。
- **所有者（Owner）**：通过 `POST /api/owners` 创建，获得 `ownerId` 与 `secret`；
  用 `secret` 换取会话令牌（`X-Owner-Token`），令牌有效期 1 小时。

## 协议流程

### 1. 初始化登记

1. `POST /api/credentials` 由签发方签发所有权凭证 `{credentialId, deviceId, devicePublicKey, ownerId, issuedAt, expiresAt, signature}`，
   签名字段为对前六个字段规范拼接（`a|b|c|...`）的 Ed25519 签名，有效期 24 小时。
2. 设备提交 `POST /api/devices/register` `{deviceId, publicKey, credential, nonce, timestamp}`。
3. 平台依次校验：nonce 未使用且时间戳在 ±5 分钟窗口内 → 凭证签名有效 →
   凭证与设备身份（deviceId + publicKey）匹配 → 凭证未过期、未被消费 → 设备未登记。
   全部通过后绑定首任所有者，并将凭证标记为已消费（单次有效）。

凭证只在设备未绑定所有者时有效；设备一旦绑定，任何归属变更只能走转移流程。
凭证被窃取后无法用于接管已绑定设备（重复登记一律拒绝）。

### 2. 所有权转移

1. 现任所有者 `POST /api/devices/{deviceId}/transfers`（头 `X-Owner-Token`）
   `{toOwnerId, nonce, timestamp}`，创建 `PENDING` 转移，返回 `transferId` 与 `confirmChallenge`。
   同一设备任一时刻至多一个 `PENDING` 转移，并发请求被串行化，后到请求返回 `TRANSFER_CONFLICT`，不会静默覆盖。
2. 设备重连后轮询 `POST /api/devices/{deviceId}/pending-transfer`，
   请求体 `{deviceId, signature, nonce, timestamp}`，签名为设备私钥对
   `POLL|{deviceId}|{nonce}|{timestamp}` 的签名。离线期间转移保持待处理，新旧所有者不会同时获得控制权。
3. 设备确认 `POST /api/transfers/{transferId}/confirm`，签名为
   `CONFIRM|{transferId}|{confirmChallenge}|{nonce}|{timestamp}`。
   确认后原子完成归属切换，旧所有者的全部会话立即吊销。
4. 设备也可拒绝 `POST /api/transfers/{transferId}/reject`（签名 `REJECT|...`），归属不变。
5. 发起方可在确认前撤销 `POST /api/transfers/{transferId}/cancel`（头 `X-Owner-Token`）。
   撤销与确认并发时在同一锁内串行，结果确定：只有一个成功，另一个得到 `TRANSFER_NOT_PENDING`。

### 3. 查询

- `GET /api/devices/{deviceId}/owner`：当前所有者。
- `GET /api/devices/{deviceId}/transfers`：转移历史。
- `GET /api/devices/{deviceId}/audit`：审计记录（登记、发起、确认、拒绝、撤销全程留痕）。

## 安全边界

- **凭证防伪**：所有权凭证由本地签发方 Ed25519 密钥签名，伪造或篡改字段即验签失败。
- **防重放**：所有变更类请求必须携带唯一 `nonce` 与 ±5 分钟内的 `timestamp`；
  nonce 持久化保存，重复使用或回放历史请求一律拒绝，重启后仍有效。
- **设备鉴权**：确认/拒绝/轮询必须携带设备私钥签名，签名绑定 `transferId`、
  一次性 `confirmChallenge`、`nonce` 与时间戳，无法跨转移或跨请求复用。
- **所有者鉴权**：发起/撤销转移需要有效会话令牌；转移完成瞬间旧所有者会话全部吊销。
- **串行化**：每台设备的转移在单锁内完成状态检查与切换，保证归属唯一且可解释。
- **持久化**：状态写透（write-through）+ 原子 rename，崩溃恢复后不出现双重归属或丢失的待处理转移。
- 本服务假设设备私钥与所有者 `secret` 由各自安全保管；签发接口仅用于本地发放首任凭证。

## 错误码

错误响应统一为 `{"code": "...", "message": "..."}`。

| code | HTTP | 含义 |
| --- | --- | --- |
| `VALIDATION_FAILED` | 400 | 请求参数缺失或非法 |
| `CREDENTIAL_INVALID` | 400 | 凭证缺失或签名校验失败（伪造/篡改） |
| `CREDENTIAL_EXPIRED` | 400 | 凭证已过期 |
| `CREDENTIAL_REUSED` | 409 | 凭证已被消费（单次有效） |
| `CREDENTIAL_DEVICE_MISMATCH` | 400 | 凭证与设备身份不匹配 |
| `DEVICE_ALREADY_REGISTERED` | 409 | 设备已登记（含凭证被盗后的接管尝试） |
| `DEVICE_NOT_FOUND` | 404 | 设备不存在 |
| `OWNER_NOT_FOUND` | 404 | 所有者不存在 |
| `SESSION_INVALID` | 401 | 会话令牌缺失、未知或过期 |
| `SESSION_REVOKED` | 401 | 会话已被吊销（转移完成后旧令牌立即失效） |
| `CREDENTIALS_INVALID` | 401 | 所有者 secret 错误 |
| `SIGNATURE_INVALID` | 401 | 设备签名校验失败 |
| `REPLAY_DETECTED` | 409 | nonce 已使用（重放请求） |
| `STALE_TIMESTAMP` | 400 | 时间戳超出允许窗口 |
| `NOT_DEVICE_OWNER` | 403 | 非现任所有者发起转移 |
| `TRANSFER_CONFLICT` | 409 | 已存在待处理转移（并发/重复发起） |
| `TRANSFER_NOT_FOUND` | 404 | 转移不存在 |
| `TRANSFER_NOT_PENDING` | 409 | 转移已终态（重复确认/撤销竞争失败方） |
| `TRANSFER_FORBIDDEN` | 403 | 无权操作该转移（如非发起方撤销） |
| `INTERNAL_ERROR` | 500 | 服务内部错误 |

## 测试

`mvn test` 覆盖：凭证伪造与篡改、凭证与设备不匹配、重复登记、凭证盗用接管、
请求重放（nonce 复用/过期时间戳/重启后重放）、离线转移、旧会话立即失效、
并发转移串行化、撤销与确认竞争（20 轮对抗）、崩溃恢复（设备/归属/待处理转移/签发密钥），
以及基于随机端口的 HTTP 端到端全流程。
