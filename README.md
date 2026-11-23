# 洗濯乾燥機時間監視 (Launotor)

**v0.3.0** — ランドリールームの配置そのままに、全14台の洗濯機・乾燥機の残り時間を同時にリアルタイム監視できる Android アプリです。

---

## 主な機能 / Features

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

In **v0.3.0**, the UI features a borderless 4-column `1:1` spatial floorplan with colorful `MaterialShapes` morphing by machine type and state (`Square`/`Circle`, `Cookie9Sided`/`Cookie6Sided`, `Cookie12Sided`, `Sunny`), a giant slider-only `1–90` min timer bottom sheet, and plain Japanese copy. It persists epoch end-times in Jetpack DataStore, reschedules `AlarmManager` alerts across process death and device reboots, and integrates Android 16+ **Live Updates API** (`ProgressStyle`, promoted ongoing chip, `canPostPromotedNotifications()`) with graceful fallback on Android 8.0–15.

---

## ビルド・テスト / Build & Test

```bash
./gradlew clean testDebugUnitTest lintDebug assembleRelease
```

- Application ID: `jp.yukirawa.launotor`
- Version: `0.3.0` (`versionCode = 3`)
- Min / Target / Compile SDK: `26 / 37 / 37`
- Material 3: `androidx.compose.material3:material3:1.5.0`
