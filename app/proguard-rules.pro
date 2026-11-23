# Launotor ProGuard / R8 rules

# Keep manifest-declared entry points
-keep public class jp.yukirawa.launotor.LaunotorApplication
-keep public class jp.yukirawa.launotor.MainActivity
-keep public class jp.yukirawa.launotor.notifications.MonitoringForegroundService
-keep public class jp.yukirawa.launotor.notifications.TimerAlarmReceiver
-keep public class jp.yukirawa.launotor.notifications.BootCompletedReceiver

# Keep enums parsed via valueOf() or persisted by name
-keepclassmembers enum jp.yukirawa.launotor.domain.model.UpdateSource {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-keepclassmembers enum jp.yukirawa.launotor.notifications.AlarmKind {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep BuildConfig version metadata displayed in SettingsSheet
-keepclassmembers class jp.yukirawa.launotor.BuildConfig {
    public static final java.lang.String VERSION_NAME;
    public static final int VERSION_CODE;
}
