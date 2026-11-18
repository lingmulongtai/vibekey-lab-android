# VibeKey Lab for Android

Ulanzi Vibe Key AU05 を Android で使うための非公式コンパニオンです。最優先は、縦に長い画面をノブで気持ちよくスクロールできることです。Android 14（API 34）以降に対応し、Galaxy S25 Ultra と AU05 の USB-C ドングルを主な検証対象にしています。

[APK をダウンロード](https://github.com/lingmulongtai/vibekey-lab-android/releases/download/v0.1.4-dev/VibeKeyLab-0.1.4-dev.apk) · [プレリリースの詳細](https://github.com/lingmulongtai/vibekey-lab-android/releases/tag/v0.1.4-dev)

## 使い方

1. APK を Android で開き、OS の確認に従ってインストールします。
2. AU05 のドングルを接続し、VibeKey Lab で「AU05 の回転入力元を選択」を開きます。「スクロール比較」で通常の動きと補間した動きを比べられます。
3. 「本体の割当とレイヤー」でノブや 3 キーを選び、回転距離・方向やボタンの動作を試せます。ボタンは実機から Android に届いた入力を登録します。
4. アプリを起動・再表示したときに、GitHub の公開プレリリースを含めて更新を確認します。「アプリの更新」から APK をダウンロードし、そのまま Android のインストール確認に進めます。手動での再確認もできます。初回は OS が VibeKey Lab に対するインストール元の許可を求める場合があります。Galaxy では Auto Blocker など端末のポリシーがインストールに適用されます。

[診断手順](docs/diagnostic-guide.ja.md)

## 設計方針

- **スクロールを先に検証する。** 標準の縦ホイール入力を使い、自アプリ内で直接スクロールとフレーム補間を同じ条件で比較します。他アプリ向け機能は明示的に開始する実験です。Android のモーション読み取り API は入力種別全体に作用するため、本アプリの実験では全マウス入力を捕捉・抑止する経路を使いません。対象アプリの対応や Android バージョンにより挙動が異なります。 [Android AccessibilityService](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService)
- **端末が受け取った入力を基準にする。** AU05 の既定割り当てには Windows 用ショートカットがあり、Android のシステム操作に解釈される場合があります。アプリ内レイヤーは Android に届いた入力に適用します。電源ボタンの再割り当て、待機時間の変更、公式 Windows アプリからの設定移植は未実装です。Galaxy で約 1 分の無操作後に本体ランプとマイクが止まり、キーで復帰する報告があります。設定値や待機の原因は未確定です。 [AU05 公式ガイド](https://docs.ulanzistudio.com/vibekey/en/au05/)
- **診断は自アプリで完結させる。** ユーザー補助を有効にせずスクロールと接続状態を確認できます。書き出す回転診断には相対時刻と回転量だけを含めます。更新通信先は公開 GitHub Releases とその配信先です。診断データやプロファイルを送信する処理はありません。
- **更新は同じ署名で引き継ぐ。** APK のサイズと SHA-256、パッケージ名、リリースに対応するバージョン名、増加した `versionCode`、インストール済みアプリと同じ署名を確認します。Android のインストール確認後に更新します。 [GitHub Release Assets](https://docs.github.com/en/rest/releases/assets) · [Android アプリ署名](https://developer.android.com/studio/publish/app-signing)

## 開発

JDK 17 と Android SDK Platform 36 を使います。Gradle Wrapper 同梱。Windows では `./gradlew.bat` で実行できます。

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
./gradlew connectedDebugAndroidTest
```

端末テストはローカルの Android 端末またはエミュレーター 1 台で実行します。エミュレーターの疑似入力と実機の AU05 入力を区別しています。

### プレリリース

1. `app/build.gradle.kts` の `versionName` と `versionCode` を更新し、テスト後に `assembleRelease` でビルドします。
2. 既存の公開 APK と同じ署名鍵で署名し、`apksigner verify --print-certs` で確認します。鍵とパスワードはリポジトリ外で管理します。公開版はデバッグ無効です。
3. ソースを `v<versionName>` でタグ付けし、プレリリースに `VibeKeyLab-<versionName>.apk` を添付します。更新画面は `0.1.4-dev`、`0.1.4-dev.2`、`0.1.4` の形式を認識します。添付後、公開 API の SHA-256 とダウンロードした APK を照合します。
