# Release Workflow 配置说明

`/.github/workflows/release.yml` 在以下情况触发：

- 推送 `v*` 形式的 tag（例如 `git push personal v1.0.0`）
- 在 Actions 页手动 workflow_dispatch（可选输入 tag、是否 prerelease）

构建会出 box / bfr 双风味 release APK + SHA256 校验，并自动创建 GitHub Release。

## 必需 Secrets

去仓库 **Settings → Secrets and variables → Actions** 配置：

| Secret | 说明 | 取值方式 |
| --- | --- | --- |
| `KEYSTORE_BASE64` | Release 签名 keystore 的 base64 编码 | `base64 -w 0 your.keystore`（Linux/macOS）或 `[Convert]::ToBase64String([IO.File]::ReadAllBytes('your.keystore'))`（PowerShell） |
| `KEYSTORE_PASSWORD` | keystore 文件密码 | 创建 keystore 时设置的 storePassword |
| `KEY_ALIAS` | 签名密钥别名 | 创建 keystore 时设置的 alias |
| `KEY_PASSWORD` | 密钥密码 | 创建 keystore 时设置的 keyPassword |
| `HYPERCEILER_TOKEN` | 双重用途：(a) checkout 私有 submodule `libs/hyperx-compose`；(b) 拉 fan.miuix maven。需要 `repo`(read) + `read:packages`。建议用 fine-grained PAT（按上述 scope 限定到 `MiChongs/hyperx-compose` + Packages 即可） |

## 可选 Secrets

| Secret | 说明 |
| --- | --- |
| `JITPACK_AUTH_TOKEN` | 私有 jitpack token（提高拉依赖额度） |

## 生成新 keystore

```bash
keytool -genkeypair -v \
  -keystore release.keystore \
  -alias box-key \
  -keyalg RSA -keysize 4096 \
  -validity 36500 \
  -storepass <strong-password> \
  -keypass <strong-password> \
  -dname "CN=Box ReApp, O=MiChongs, C=CN"

base64 -w 0 release.keystore | pbcopy   # macOS
base64 -w 0 release.keystore | xclip    # Linux
[Convert]::ToBase64String([IO.File]::ReadAllBytes('release.keystore')) | Set-Clipboard   # PowerShell
```

## 触发 release

```bash
# 1. 打 tag（与 versionName 不必一致；versionCode 取自 commit 数）
git tag -a v1.0.0 -m "v1.0.0"
git push personal v1.0.0

# 2. 或者去 Actions → Release → Run workflow 手动触发
```

## 注意事项

- keystore 与密码**全部**通过 secrets 注入，仓库内不应再保留 `app/keystore.properties` / `app/release.keystore`；workflow 会在运行时生成临时文件，结束后清理。
- workflow 使用 `fetch-depth: 0` + `submodules: recursive`，因为 versionCode 由 `git rev-list --count HEAD` 推导，且 `libs/hyperx-compose` 是 submodule。
- Pre-release 判定：tag 含 `-rc` / `-beta` / `-alpha`，或 workflow_dispatch 时勾选 prerelease=true。
- 如需调整 changelog 模板，编辑 release.yml 的 `Generate changelog` 步骤。
