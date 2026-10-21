# Applied to the app when it shrinks a release build.
# WorkManager (used for weather refresh, clock redraws and Glance sessions) creates its Room
# database and its workers by reflection; without these R8 removes the constructors and the
# app crashes at startup with NoSuchMethodException: WorkDatabase_Impl.<init>.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
