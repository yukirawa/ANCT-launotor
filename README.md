# 洗濯乾燥機時間監視 (Launotor)

**v0.1.0** — ランドリールームの配置そのままに、全14台の洗濯機・乾燥機の残り時間を同時にリアルタイム監視できる Android アプリです。

---

## 主な機能 / Features

- **物理配置をそのまま再現したランドリールーム画面（全14台）**
  - **左側ウォール**: 洗濯機 1〜4
  - **中央アイランド**: 乾燥機 1〜3（左列）・乾燥機 4〜6（右列）
  - **右側ウォール**: 洗濯機 5〜8
  - 空き台数サマリー（洗濯機 `X/8`・乾燥機 `Y/6`・稼働監視中台数）をリアルタイム表示。
- **ワンタップで素早い残り時間入力**
  - 任意のマシンをタップすると Material 3 Expressive ボトムシートが即座に開き、プリセット（洗濯機 `5/10/15/20/30/40/45分`、乾燥機 `5/10/15/20/30/45/60分`）、`-5 / -1 / +1 / +5` ボタン群、スライダー・数値入力（1〜180分）で即開始・更新・空き戻しが可能。
- **正確なエポック時刻ベース監視＆永続化**
  - Jetpack DataStore に `endTimeEpochMillis` と `initialDurationMillis` を保存。画面オフ、アプリ終了、端末再起動（`BOOT_COMPLETED`）後も正確に残り時間を復元しアラームを再スケジュールします。
- **まもなく空き通知 & 完了アラート**
  - まもなく空き通知（例: `洗濯機1が空きそうです（残り5分）`、設定で `OFF / 3 / 5 / 10分前` を変更可能。開始時間がしきい値以下の場合はスキップ）。
  - 完了通知（例: `乾燥機4が空きました！`、高重要度アラート＋通知からの「空きにする」クイックアクション）。
- **Android 16 Live Updates API (API 36) 完全準拠**
  - `NotificationCompat.ProgressStyle`・`setRequestPromotedOngoing(true)`・`setShortCriticalText(...)` によるステータスバーチップおよび進行状況の常駐表示に対応（API 26〜35 では標準の進行状況常駐通知へ自動フォールバック）。
- **Material 3 Expressive UI**
  - `androidx.compose.material3:material3:1.5.0`、`MaterialExpressiveTheme`、`MotionScheme.expressive()`、`LinearWavyProgressIndicator` / `CircularWavyProgressIndicator`、`ButtonGroup` / `FilledTonalToggleButton`、等幅数字（`tnum`）、Dynamic Color（Light/Dark）、エッジツーエッジ表示に対応。

---

## English Overview

**Launotor (`洗濯乾燥機時間監視`)** is a Material 3 Expressive Android app (`minSdk 26`, `targetSdk 36`) for simultaneously monitoring 14 laundry machines arranged in the exact physical room layout:

- **Left wall**: Washers `1–4`
- **Center island**: Dryers `1–3` (left) & `4–6` (right)
- **Right wall**: Washers `5–8`

It persists epoch end-times in Jetpack DataStore, reschedules `AlarmManager` alerts across process death and device reboots, and integrates Android 16's **Live Updates API** (`ProgressStyle`, promoted ongoing chip, `canPostPromotedNotifications()`) with graceful fallback on Android 8.0–15.

---

## ビルド・テスト / Build & Test

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

- Application ID: `jp.yukirawa.launotor`
- Version: `0.1.0` (`versionCode = 1`)
- Min / Target / Compile SDK: `26 / 36 / 36`
