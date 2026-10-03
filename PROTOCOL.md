# 设备初始化接入与所有权转移协议

本文件描述设备初始化接入、所有权转移、会话管理的协议流程、安全边界与错误码。
所有密钥、凭证与所有权状态均在本地生成与校验，不依赖外部服务。

## 1. 角色与密钥

| 角色 | 密钥 | 说明 |
|------|------|------|
| 签发方 (Issuer) | Ed25519 密钥对 | 平台自持，用于签发设备所有权凭证。密钥在首次启动时生成并持久化。 |
| 所有者 (Owner) | Ed25519 密钥对 | 所有者本地生成，私钥是发起转移与创建会话的授权凭证。 |
| 设备 (Device) | Ed25519 密钥对 | 设备本地生成，私钥是确认/拒绝转移的授权凭证。 |

所有签名使用 Ed25519，密钥以 Base64 编码传输。签名载荷为以 `|` 分隔的规范化字符串，
并带版本前缀（如 `DOT-REGISTER-v1`）。

## 2. 协议流程

### 2.1 设备初始化登记

```
Iss                 Platform                     Device
 |                      |                          |
 |  签发所有权凭证       |                          |
 |  (绑定 device+owner) |                          |
 |--------------------->|                          |
 |                      |                          |
 |                      |  提交身份材料+公钥+凭证   |
 |                      |<-------------------------|
 |                      |  校验签发方签名           |
 |                      |  校验设备身份与凭证一致   |
 |                      |  校验凭证未过期           |
 |                      |  校验设备未登记           |
 |                      |  校验请求 nonce 未重放    |
 |                      |  绑定首任所有者           |
 |                      |------------------------->|
```

登记请求包含：`deviceId`、`devicePublicKey`、`credential`、`nonce`、`timestamp`、`deviceSignature`。

- `credential` 是签发方签发的所有权凭证，格式为 `base64url(payload).base64url(signature)`，
  其中 payload 为 `DOT-OWNERSHIP-CREDENTIAL-v1|deviceId|devicePublicKey|ownerId|ownerPublicKey|nonce|expiresAt`。
- `deviceSignature` 是设备对 `DOT-REGISTER-v1|deviceId|devicePublicKey|nonce|timestamp` 的签名，
  证明设备持有与 `devicePublicKey` 对应的私钥。

平台校验项：
1. 凭证签名必须由签发方私钥签发（伪造凭证 → `CREDENTIAL_INVALID`）。
2. 凭证中的 `deviceId`、`devicePublicKey` 必须与提交的一致（不匹配 → `CREDENTIAL_DEVICE_MISMATCH`）。
3. 凭证未过期（`expiresAt` 晚于当前时间）。
4. 设备未登记过（重复登记 → `DEVICE_ALREADY_REGISTERED`）。
5. 请求 `nonce` 未被使用过（重放 → `NONCE_REUSED`）。
6. `timestamp` 在允许的时间窗口内（过期 → `STALE_TIMESTAMP`）。
7. 设备签名必须有效（无效 → `INVALID_SIGNATURE`）。

### 2.2 所有权转移

```
Owner                 Platform                     Device
 |                      |                          |
 |  发起转移             |                          |
 |  (指定新所有者)       |                          |
 |--------------------->|                          |
 |                      |  校验现任所有者签名       |
 |                      |  校验无待处理转移         |
 |                      |  创建 PENDING 转移        |
 |                      |                          |
 |                      |  设备拉取待处理转移       |
 |                      |<-------------------------|
 |                      |  校验设备签名             |
 |                      |------------------------->|
 |                      |                          |
 |                      |  设备确认转移             |
 |                      |<-------------------------|
 |                      |  校验设备签名             |
 |                      |  原子切换所有者           |
 |                      |  递增 epoch               |
 |                      |  撤销旧所有者会话         |
 |                      |------------------------->|
```

**发起转移**：现任所有者对 `DOT-TRANSFER-INITIATE-v1|deviceId|newOwnerPublicKey|nonce|timestamp` 签名。
平台校验签名来自现任所有者，且设备当前无待处理转移。

**待处理**：转移创建后状态为 `PENDING`，等待设备确认。设备可在重新连接后拉取待处理转移。

**确认**：设备对 `DOT-TRANSFER-CONFIRM-v1|transferId|deviceId|nonce|timestamp` 签名。
平台校验后原子地：将转移置为 `CONFIRMED`、切换设备所有者、递增 epoch、撤销旧所有者的全部会话。

**拒绝**：设备对 `DOT-TRANSFER-REJECT-v1|transferId|deviceId|nonce|timestamp` 签名，
转移置为 `REJECTED`，所有者不变。

**撤销**：现任所有者在确认前可对 `DOT-TRANSFER-CANCEL-v1|transferId|deviceId|nonce|timestamp` 签名，
转移置为 `CANCELLED`。

### 2.3 离线转移

转移可在设备不在线时发起并保持 `PENDING`。设备重新连接后：
1. 调用待处理转移查询接口，获取 `PENDING` 转移。
2. 设备确认或拒绝。

等待期间：
- 旧所有者仍保留控制权（可创建会话）。
- 新所有者未获得任何控制权。
- 任一时刻至多存在一个待处理转移。

### 2.4 会话管理

所有者可对 `DOT-SESSION-CREATE-v1|deviceId|ownerPublicKey|nonce|timestamp` 签名创建会话。
平台返回会话令牌，格式为 `base64url(payload).base64url(signature)`，
payload 为 `DOT-SESSION-TOKEN-v1|sessionId|deviceId|ownerId|epoch|issuedAt|expiresAt`，
由平台签发方密钥签名。

会话校验项：
1. 会话存在。
2. 未被撤销。
3. 未过期。
4. `epoch` 与设备当前 epoch 一致。

转移确认后，旧所有者的会话被撤销（`revoked=true`），且设备 epoch 递增，
旧所有者的令牌同时因 epoch 不匹配而失效。

### 2.5 崩溃恢复

所有状态（所有者、设备、转移、会话、nonce、审计）在每次变更后原子写入本地数据目录。
写入采用「写临时文件 + fsync + 原子重命名」策略，确保不会出现部分写入。
服务重启后自动加载全部状态，`PENDING` 转移保持不变，可继续确认或拒绝。

## 3. 安全边界

1. **凭证仅在未绑定时有效**：所有权凭证只在设备尚未绑定所有者时可用于登记。
   设备绑定后，凭证无法用于接管（重复登记被拒绝）。
2. **变更必须由现任所有者发起**：绑定后的任何归属变更都必须由现任所有者签名发起。
   伪造签名或非现任所有者发起的请求被拒绝。
3. **变更必须经设备确认**：转移必须经设备签名确认后才生效。
4. **重放保护**：每个签名请求包含一次性 `nonce`，平台记录已使用的 nonce。
   重放同一请求返回 `NONCE_REUSED`。请求同时包含 `timestamp`，超出时间窗口返回 `STALE_TIMESTAMP`。
5. **并发串行化**：同一设备的多个转移请求被串行化，任一时刻至多一个待处理转移。
   后到请求返回 `PENDING_TRANSFER_EXISTS`，不静默覆盖先到请求。
6. **撤销与确认竞争确定**：撤销与确认并发时，先获得设备锁的一方生效，
   另一方返回 `TRANSFER_NOT_PENDING`。最终归属唯一且可解释。
7. **旧所有者会话立即失效**：转移确认后，旧所有者的会话被撤销，令牌因 epoch 递增失效。
8. **状态持久化原子性**：崩溃恢复后状态完整，不会出现设备同时归属两个所有者、
   待处理转移丢失或状态部分写入。

## 4. 错误码

| 错误码 | HTTP 状态 | 枚举 | 说明 |
|--------|-----------|------|------|
| 40000 | 400 | `INVALID_REQUEST` | 请求格式错误 |
| 40001 | 401 | `INVALID_SIGNATURE` | 签名无效或签名者非授权方 |
| 40002 | 401 | `CREDENTIAL_INVALID` | 所有权凭证签名无效（伪造） |
| 40003 | 401 | `CREDENTIAL_EXPIRED` | 所有权凭证已过期 |
| 40004 | 400 | `CREDENTIAL_DEVICE_MISMATCH` | 凭证与设备身份不匹配 |
| 40005 | 409 | `DEVICE_ALREADY_REGISTERED` | 设备已登记 |
| 40006 | 404 | `DEVICE_NOT_REGISTERED` | 设备未登记 |
| 40007 | 404 | `OWNER_NOT_FOUND` | 所有者不存在 |
| 40008 | 404 | `DEVICE_NOT_FOUND` | 设备不存在 |
| 40009 | 409 | `NONCE_REUSED` | nonce 已使用（重放） |
| 40010 | 401 | `STALE_TIMESTAMP` | 时间戳超出允许窗口 |
| 40011 | 403 | `NOT_OWNER` | 非现任所有者 |
| 40012 | 409 | `PENDING_TRANSFER_EXISTS` | 已存在待处理转移 |
| 40013 | 404 | `TRANSFER_NOT_FOUND` | 转移不存在 |
| 40014 | 409 | `TRANSFER_NOT_PENDING` | 转移非待处理状态 |
| 40015 | 409 | `TRANSFER_ALREADY_FINALIZED` | 转移已终结 |
| 40016 | 404 | `SESSION_NOT_FOUND` | 会话不存在 |
| 40017 | 401 | `SESSION_REVOKED` | 会话已被撤销 |
| 40018 | 401 | `SESSION_EXPIRED` | 会话已过期 |
| 40019 | 401 | `SESSION_EPOCH_MISMATCH` | 会话 epoch 与设备不匹配 |
| 40020 | 400 | `INVALID_TRANSFER_TARGET` | 新所有者公钥无对应所有者 |

## 5. HTTP 接口概览

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/owners` | 创建所有者 |
| GET | `/api/owners/{ownerId}` | 查询所有者 |
| POST | `/api/issuer/devices` | 签发方签发设备凭证（模拟生产写入） |
| POST | `/api/devices/register` | 设备登记并绑定首任所有者 |
| GET | `/api/devices/{deviceId}` | 查询设备当前所有者 |
| GET | `/api/devices/{deviceId}/audit` | 查询设备审计记录 |
| POST | `/api/devices/{deviceId}/transfers` | 现任所有者发起转移 |
| GET | `/api/devices/{deviceId}/transfers` | 查询转移历史 |
| POST | `/api/devices/{deviceId}/transfer/pending` | 设备拉取待处理转移 |
| POST | `/api/devices/{deviceId}/transfer/confirm` | 设备确认转移 |
| POST | `/api/devices/{deviceId}/transfer/reject` | 设备拒绝转移 |
| POST | `/api/devices/{deviceId}/transfer/cancel` | 现任所有者撤销转移 |
| POST | `/api/devices/{deviceId}/sessions` | 所有者创建会话 |
| POST | `/api/sessions/{sessionId}/verify` | 校验会话有效性 |
