# 지금은 따로 지킬 규칙 없음 (kotlinx.serialization 은 라이브러리에 규칙이 들어 있다)
# WorkManager 의 Room DB(WorkDatabase_Impl)는 빈 생성자를 이름으로 찾아 만든다. R8 이 지우면 출시판이 켜지자마자 죽는다 (2026-10-28 갤럭시)
-keep class * extends androidx.room.RoomDatabase { <init>(); }
