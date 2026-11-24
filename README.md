# 洗濯乾燥機時間監視 (Launotor)

**v0.4.0** — ランドリールームの配置そのままに、全14台の洗濯機・乾燥機の残り時間を同時にリアルタイム監視できる Android アプリです。**v0.4.0 ではハイブリッド P2P 自動共有（試験版）を搭載しました。**

---

## 主な機能 / Features

- **ハイブリッド P2P 自動共有（試験版 / Experimental Preview）**
  - **BLE すれ違いゴシップ ＋ 同一 Wi-Fi LAN 自動同期**: サーバーやアカウント登録なしで、同じ Wi-Fi 上の端末（NSD / TCP・UDP）および近くの端末（Bluetooth LE 広告＆スキャンによるすれ違いゴシップ）と全14台の稼働状況を自動同期します。
  - **共有ボタン不要のゼロUI共有**: タイマーの開始・更新・空き戻し操作だけで、近くの端末へ自動的に状態が共有されます（端末間の時刻ズレに左右されない相対残り時間ベースの CRDT / Last-Write-Wins マージを採用）。
  - **ウォッチ通知分離**: 自分が開始したマシンは自動で通知オン（カードに小さなベルマークを表示）。他の端末から共有されただけのタイマーではアラームや Live Updates 通知を鳴らさず、気になる台だけボトムシートの「通知を受け取る」チップから通知オンにできます。
  - **設定の「みんなで自動共有」**: 設定画面の「みんなで自動共有」カードから「近くの端末と自動共有」「バックグラウンドでも自動共有（オプトイン）」を切り替えでき、すれ違い共有を使う場合のみ「Bluetooth を許可」から権限を付与できます。
  - ※ 本バージョンは **P2P 通信機能の試験版** です。実環境・複数端末でのフィールドチューニングを継続しており、Bluetooth / Wi-Fi オフ時や権限未許可時はローカル単体アプリとして安全に動作します。
- **物理配置をそのまま再現したランドリールーム画面（全14台・枠線なし均等レイアウト）**
  - **左側ウォール**: 洗濯機 1〜4
  - **中央アイランド**: 乾燥機 1〜3（左列）・乾燥機 4〜6（右列）
  - **右側ウォール**: 洗濯機 5〜8
  - 4列均等幅・1:1 正方形比率の枠線なしレイアウトと、空き台数ピル（洗濯機・乾燥機）＋設定ボタンのみのすっきりした日本語ヘッダー。
- **カラフルな Material 3 Expressive 動的シェイプ (`MaterialShapes`)**
  - **洗濯機（スカイ／コバルト系）** と **乾燥機（ピーチ／コーラル系）** を明確な色分けと形状で表現。
  - 状態に応じてシェイプが滑らかにモーフ（`Morph`）し、稼働中は内接の円形進捗アークを表示します（空き: `Square` / `Circle`、稼働中: `Cookie9Sided` / `Cookie6Sided`、もうすぐ空き: `Cookie12Sided` + ゴールド、完了: `Sunny` + エメラルド）。
- **巨大スライダーのみのシンプルタイマー入力**
  - 任意のマシンをタップすると Material 3 Expressive ボトムシートが開き、1〜90分の太いピル型スライダー（ハプティクス対応）と大きな残り時間表示から、ワンアクションで開始・更新・空き戻しが可能。
- **正確なエポック時刻ベース監視＆永続化**
  - Jetpack DataStore に `endTimeEpochMillis` と `initialDurationMillis` を保存。画面オフ、アプリ終了、端末再起動（`BOOT_COMPLETED`）後も正確に残り時間を復元しアラームを再スケジュールします。
- **まもなく空き通知 & 完了アラート**
  - まもなく空き通知（例: `洗濯機1が空きそうです（残り5分）`、設定で `オフ / 3 / 5 / 10分前` を変更可能。開始時間がしきい値以下の場合はスキップ）。
  - 完了通知（例: `乾燥機4が空きました！`、高重要度アラート＋通知からの「空きにする」クイックアクション）。
- **Android 16+ Live Updates API (API 36 / 37) 完全準拠**
  - `NotificationCompat.ProgressStyle`・`setRequestPromotedOngoing(true)`・`setShortCriticalText(...)` によるステータスバーチップおよびロック画面・通知の進行状況表示に対応（API 26〜35 では標準の進行状況常駐通知へ自動フォールバック）。
- **Material 3 Expressive UI**
  - `androidx.compose.material3:material3:1.5.0`（安定版 1.5.0 Expressive リリース）、`MaterialExpressiveTheme`、`MotionScheme.expressive()`、`MaterialShapes` + `Morph`、`Slider`、`ButtonGroup`、等幅数字（`tnum`）、Dynamic Color（Light/Dark）、エッジツーエッジ表示、わかりやすい日本語UIに対応。

---

## English Overview

**Launotor (`洗濯乾燥機時間監視`)** is a Material 3 Expressive Android app (`minSdk 26`, `compileSdk 37`, `targetSdk 37`) for simultaneously monitoring 14 laundry machines arranged in the exact physical room layout:

- **Left wall**: Washers `1–4`
- **Center island**: Dryers `1–3` (left) & `4–6` (right)
- **Right wall**: Washers `5–8`

**v0.4.0** introduces **Hybrid P2P Auto-Sync (Experimental Preview)** — serverless, zero-button state sharing across nearby devices via **BLE gossip** and **same-Wi-Fi LAN sync** (NSD + TCP/UDP) using skew-immune relative-time CRDT merging. Remote updates populate the floorplan without firing alarms or Live Updates unless the user opts in per machine via the `通知を受け取る` watch toggle, while Settings provides the `みんなで自動共有` card for auto-share, opt-in background sync, and Bluetooth permission. Real-world multi-device field tuning is ongoing ahead of v1.0.0. It builds on the v0.3.0 borderless 4-column `1:1` `MaterialShapes` floorplan, giant `1–90` min slider sheet, Jetpack DataStore persistence, `AlarmManager` reboot recovery, and Android 16+ **Live Updates API** (`ProgressStyle`, promoted ongoing chip, `canPostPromotedNotifications()`).

---

## ビルド・テスト / Build & Test

```bash
./gradlew clean testDebugUnitTest lintDebug assembleRelease
```

- Application ID: `jp.yukirawa.launotor`
- Version: `0.4.0` (`versionCode = 4`)
- Min / Target / Compile SDK: `26 / 37 / 37`
- Material 3: `androidx.compose.material3:material3:1.5.0`
