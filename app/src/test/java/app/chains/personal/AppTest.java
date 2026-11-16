package app.chains.personal;

import android.app.*;
import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.android.controller.ActivityController;
import java.io.*;
import java.time.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35, qualifiers="w393dp-h852dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class AppTest {
    Context context;
    @Before public void prepare(){context=RuntimeEnvironment.getApplication();context.deleteDatabase("chains.db");context.getSharedPreferences("settings",0).edit().clear().commit();}
    Habit sample(Store s){Habit h=new Habit();h.name="Читать";h.created=LocalDate.now().minusDays(30);s.save(h);s.mark(h,LocalDate.now().minusDays(1));return h;}
    @Test public void reopeningAndEditingKeepHistory(){
        String id;
        try(Store s=new Store(context)){Habit h=sample(s);id=h.id;h.name="Читать 20 минут";h.goal=90;h.breakAfter=2;s.save(h);}
        try(Store s=new Store(context)){Habit h=s.all().get(0);assertEquals(id,h.id);assertEquals(90,h.goal);assertEquals(1,h.days.size());assertEquals(1,h.streak(LocalDate.now()));}
    }
    @Test public void backupRestoresAndMergesWithoutLosingCurrentMarks() throws Exception {
        try(Store s=new Store(context)){Habit h=sample(s);String backup=s.exportJson();s.mark(h,LocalDate.now());s.importJson(backup);assertEquals(2,s.all().get(0).days.size());assertEquals(1,s.all().size());s.delete(h);assertTrue(s.all().isEmpty());s.importJson(backup);assertEquals(1,s.all().get(0).days.size());}
    }
    @Test public void invalidImportIsAtomic() throws Exception {
        try(Store s=new Store(context)){sample(s);String before=s.exportJson();String invalid=before.replace("\"goal\": 30","\"goal\": 0");assertNotEquals(before,invalid);try{s.importJson(invalid);fail();}catch(Exception expected){}assertEquals(before,s.exportJson());}
    }
    @Test public void reminderSchedulesAndCancels(){
        Habit h=new Habit();h.reminder=true;ReminderReceiver.schedule(context,h);org.robolectric.shadows.ShadowAlarmManager alarms=Shadows.shadowOf((AlarmManager)context.getSystemService(Context.ALARM_SERVICE));assertEquals(1,alarms.getScheduledAlarms().size());h.reminder=false;ReminderReceiver.schedule(context,h);assertEquals(0,alarms.getScheduledAlarms().size());
    }
    @Test public void activityAndDialogsRender() throws Exception {
        try(Store s=new Store(context)){
            Habit a=sample(s);s.mark(a,LocalDate.now());for(int i=2;i<16;i++)if(i!=8)s.mark(a,LocalDate.now().minusDays(i));
            Habit b=new Habit();b.name="Тренировка";b.goal=60;b.breakAfter=2;b.created=LocalDate.now().minusDays(24);s.save(b);for(int i:new int[]{1,2,3,5,6,7,9,10,11,13,14})s.mark(b,LocalDate.now().minusDays(i));
            Habit c=new Habit();c.name="Без сахара";c.goal=21;c.breakAfter=0;c.created=LocalDate.now().minusDays(15);s.save(c);for(int i:new int[]{0,1,2,3,4,6,7,8,10,11})s.mark(c,LocalDate.now().minusDays(i));
        }
        try(ActivityController<MainActivity> ctrl=Robolectric.buildActivity(MainActivity.class).setup()){
            MainActivity a=ctrl.get();assertEquals(3,a.habits.size());screenshot(a.getWindow().getDecorView(),"today");
            a.edit(a.habits.get(0));Dialog edit=org.robolectric.shadows.ShadowDialog.getLatestDialog();assertTrue(edit.isShowing());screenshot(edit.getWindow().getDecorView(),"edit");edit.dismiss();
            a.settings();assertTrue(org.robolectric.shadows.ShadowDialog.getLatestDialog().isShowing());org.robolectric.shadows.ShadowDialog.getLatestDialog().dismiss();
        }
    }
    @Test public void firstCompletionFillsFirstLinkAndNoCalendar(){
        try(Store s=new Store(context)){Habit h=new Habit();h.name="Ходить 10 тысяч";s.save(h);s.mark(h,LocalDate.now());}
        try(ActivityController<MainActivity> ctrl=Robolectric.buildActivity(MainActivity.class).setup()){
            MainActivity a=ctrl.get();MainActivity.ChainProgress chain=a.new ChainProgress(a.habits.get(0));assertTrue(chain.filled(0));assertFalse(chain.filled(1));assertFalse(chain.filled(29));assertEquals(30,chain.goal);assertEquals(3,chain.rows);
            assertNull(findText(a.root,"Цепочки"));assertNull(findText(a.root,"28 дней"));
            a.edit(a.habits.get(0));Dialog edit=org.robolectric.shadows.ShadowDialog.getLatestDialog();assertNotNull(findText(edit.getWindow().getDecorView(),"Удалить привычку"));edit.dismiss();
        }
    }
    @Test public void freshInstallIsEmpty(){try(ActivityController<MainActivity> ctrl=Robolectric.buildActivity(MainActivity.class).setup()){assertTrue(ctrl.get().habits.isEmpty());}}
    @Test public void creatingAndCheckingFromUiPersists(){
        try(ActivityController<MainActivity> ctrl=Robolectric.buildActivity(MainActivity.class).setup()){
            MainActivity a=ctrl.get();a.edit(null);Dialog editor=org.robolectric.shadows.ShadowDialog.getLatestDialog();
            java.util.List<EditText> inputs=new java.util.ArrayList<>();collectInputs(editor.getWindow().getDecorView(),inputs);assertEquals(2,inputs.size());inputs.get(0).setText("Медитация");inputs.get(1).setText("45");findText(editor.getWindow().getDecorView(),"Сохранить").performClick();assertFalse(editor.isShowing());assertEquals(45,a.store.all().get(0).goal);
            CheckBox check=findCheck(a.root);assertNotNull(check);check.performClick();assertTrue(a.store.all().get(0).days.contains(LocalDate.now()));
            findCheck(a.root).performClick();assertTrue(a.store.all().get(0).days.isEmpty());
            a.edit(a.store.all().get(0));Dialog edit=org.robolectric.shadows.ShadowDialog.getLatestDialog();findText(edit.getWindow().getDecorView(),"Никогда").performClick();findText(edit.getWindow().getDecorView(),"Сохранить").performClick();assertEquals(0,a.store.all().get(0).breakAfter);
        }
    }
    @Test public void pastStartFromUiPersistsAndSurvivesBackupAndEdits() throws Exception {
        LocalDate start=LocalDate.now().minusDays(12);
        try(ActivityController<MainActivity> ctrl=Robolectric.buildActivity(MainActivity.class).setup()){
            MainActivity a=ctrl.get();a.edit(null);Dialog editor=org.robolectric.shadows.ShadowDialog.getLatestDialog();
            java.util.List<EditText> inputs=new java.util.ArrayList<>();collectInputs(editor.getWindow().getDecorView(),inputs);inputs.get(0).setText("Читать");
            findText(editor.getWindow().getDecorView(),"Сегодня").performClick();DatePickerDialog picker=(DatePickerDialog)org.robolectric.shadows.ShadowDialog.getLatestDialog();
            Shadows.shadowOf(picker).getOnDateSetListenerCallback().onDateSet(picker.getDatePicker(),start.getYear(),start.getMonthValue()-1,start.getDayOfMonth());picker.dismiss();
            findText(editor.getWindow().getDecorView(),"Сохранить").performClick();Habit h=a.store.all().get(0);
            assertEquals(start,h.created);assertEquals(12,h.streak(LocalDate.now()));assertTrue(h.days.contains(start));assertFalse(h.days.contains(LocalDate.now()));assertTrue(a.new ChainProgress(h).filled(11));assertFalse(a.new ChainProgress(h).filled(12));
            a.edit(h);Dialog edit=org.robolectric.shadows.ShadowDialog.getLatestDialog();findText(edit.getWindow().getDecorView(),"Сохранить").performClick();assertEquals(12,a.store.all().get(0).days.size());
            findCheck(a.root).performClick();assertEquals(13,a.store.all().get(0).streak(LocalDate.now()));
            String backup=a.store.exportJson();a.store.delete(h);a.store.importJson(backup);assertEquals(start,a.store.all().get(0).created);assertEquals(13,a.store.all().get(0).days.size());
        }
    }
    @Test public void startTodayIsEmptyAndFutureStartIsRejected(){
        try(Store s=new Store(context)){Habit h=new Habit();h.name="Сегодня";s.createFromDate(h,LocalDate.now());assertTrue(s.all().get(0).days.isEmpty());
            try{s.createFromDate(new Habit(),LocalDate.now().plusDays(1));fail();}catch(IllegalArgumentException expected){}assertEquals(1,s.all().size());
            try{s.createFromDate(h,LocalDate.now().minusDays(5));fail();}catch(IllegalArgumentException expected){}assertTrue(s.all().get(0).days.isEmpty());
        }
    }
    @Test public void migrationFromVersionOnePreservesAllHistory() throws Exception {
        String id=java.util.UUID.randomUUID().toString();LocalDate start=LocalDate.now().minusDays(10);
        try(android.database.sqlite.SQLiteDatabase db=context.openOrCreateDatabase("chains.db",0,null)){
            db.execSQL("CREATE TABLE habits (id TEXT PRIMARY KEY, name TEXT NOT NULL, goal INTEGER NOT NULL, break_after INTEGER NOT NULL, minute INTEGER NOT NULL, reminder INTEGER NOT NULL, created TEXT NOT NULL)");
            db.execSQL("CREATE TABLE marks (habit TEXT NOT NULL REFERENCES habits(id) ON DELETE CASCADE, day TEXT NOT NULL, PRIMARY KEY(habit,day))");
            db.execSQL("INSERT INTO habits VALUES (?,?,?,?,?,?,?)",new Object[]{id,"Читать",90,2,1230,1,start.toString()});
            db.execSQL("INSERT INTO marks VALUES (?,?)",new Object[]{id,LocalDate.now().minusDays(1).toString()});db.setVersion(1);
        }
        try(Store store=new Store(context)){
            Habit h=store.all().get(0);assertEquals(id,h.id);assertEquals(start,h.created);assertEquals(90,h.goal);assertEquals(2,h.breakAfter);assertEquals(1230,h.minute);assertTrue(h.reminder);assertEquals(1,h.days.size());assertFalse(h.isNote);assertTrue(h.daily);assertEquals("",h.note);assertNull(h.completedOn);assertEquals(3,store.getReadableDatabase().getVersion());
            org.json.JSONObject old=new org.json.JSONObject(store.exportJson());old.put("schema",1);org.json.JSONObject item=old.getJSONArray("habits").getJSONObject(0);item.remove("isNote");item.remove("note");item.remove("daily");item.remove("completed");store.delete(h);store.importJson(old.toString());assertEquals(id,store.all().get(0).id);assertEquals(1,store.all().get(0).days.size());
        }
    }
    @Test public void noteCanBeReadEveryDayAndArchivedWithDates() throws Exception {
        try(ActivityController<MainActivity> ctrl=Robolectric.buildActivity(MainActivity.class).setup()){
            MainActivity a=ctrl.get();a.edit(null,true);Dialog editor=org.robolectric.shadows.ShadowDialog.getLatestDialog();
            java.util.List<EditText> inputs=new java.util.ArrayList<>();collectInputs(editor.getWindow().getDecorView(),inputs);assertEquals(3,inputs.size());inputs.get(1).setText("Моя заметка\nВторая строка");inputs.get(2).setText("1");
            findText(editor.getWindow().getDecorView(),"Сохранить").performClick();Habit h=a.store.all().get(0);assertTrue(h.isNote);assertEquals("Заметка",h.name);assertEquals("Моя заметка\nВторая строка",h.note);assertTrue(h.tracksDaily());assertNull(findText(a.root,h.note));assertNotNull(findText(a.root,"Прочитать"));screenshot(a.getWindow().getDecorView(),"note-card");
            findText(a.root,"Прочитать").performClick();Dialog reader=org.robolectric.shadows.ShadowDialog.getLatestDialog();assertNotNull(findText(reader.getWindow().getDecorView(),h.note));screenshot(reader.getWindow().getDecorView(),"note-reader");findText(reader.getWindow().getDecorView(),"Прочитано").performClick();h=a.store.all().get(0);assertEquals(1,h.streak(LocalDate.now()));
            findText(a.root,"В архив").performClick();assertTrue(a.habits.isEmpty());h=a.store.all().get(0);assertEquals(LocalDate.now(),h.completedOn);assertEquals(1,h.days.size());
            a.archive();Dialog archive=org.robolectric.shadows.ShadowDialog.getLatestDialog();String date=LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"));assertNotNull(findText(archive.getWindow().getDecorView(),date+" → "+date));assertNull(findText(archive.getWindow().getDecorView(),h.note));assertNotNull(findText(archive.getWindow().getDecorView(),"Прочитать"));screenshot(archive.getWindow().getDecorView(),"archive");findText(archive.getWindow().getDecorView(),"Вернуть").performClick();assertEquals(1,a.habits.size());assertNull(a.store.all().get(0).completedOn);assertEquals(1,a.store.all().get(0).days.size());
        }
    }
    @Test public void plainNoteHasNoCheckboxAndKeepsTextAfterReopening(){
        try(ActivityController<MainActivity> ctrl=Robolectric.buildActivity(MainActivity.class).setup()){
            MainActivity a=ctrl.get();a.edit(null,true);Dialog editor=org.robolectric.shadows.ShadowDialog.getLatestDialog();java.util.List<EditText> inputs=new java.util.ArrayList<>();collectInputs(editor.getWindow().getDecorView(),inputs);inputs.get(0).setText("Запись");inputs.get(1).setText("Текст записи");findText(editor.getWindow().getDecorView(),"Читать ежедневно").performClick();findText(editor.getWindow().getDecorView(),"Сохранить").performClick();Habit h=a.store.all().get(0);assertFalse(h.tracksDaily());assertEquals(View.GONE,findCheck(a.root).getVisibility());a.store.mark(h,LocalDate.now());assertTrue(h.days.isEmpty());a.store.archive(h);assertEquals(LocalDate.now(),h.completedOn);
        }
        try(Store store=new Store(context)){Habit h=store.all().get(0);assertEquals("Текст записи",h.note);assertFalse(h.daily);assertNotNull(h.completedOn);}
    }
    @Test public void archiveBackupMergeAndRemindersPreserveData() throws Exception {
        try(Store store=new Store(context)){
            Habit h=new Habit();h.name="Заметка";h.isNote=true;h.note="Полный текст";h.goal=2;h.reminder=true;LocalDate start=LocalDate.now().minusDays(5);store.createFromDate(h,start);ReminderReceiver.schedule(context,h);
            store.archive(h);assertEquals(start.plusDays(1),h.completedOn);ReminderReceiver.schedule(context,h);assertTrue(Shadows.shadowOf((AlarmManager)context.getSystemService(Context.ALARM_SERVICE)).getScheduledAlarms().isEmpty());
            store.mark(h,LocalDate.now());assertEquals(5,store.all().get(0).days.size());String backup=store.exportJson();store.delete(h);store.importJson(backup);Habit restored=store.all().get(0);assertEquals(h.completedOn,restored.completedOn);assertEquals(start,restored.created);assertEquals(5,restored.days.size());assertEquals(h.note,restored.note);assertTrue(restored.reminder);
            org.json.JSONObject old=new org.json.JSONObject(backup);old.put("schema",1);org.json.JSONObject item=old.getJSONArray("habits").getJSONObject(0);item.remove("isNote");item.remove("note");item.remove("daily");item.remove("completed");store.importJson(old.toString());Habit merged=store.all().get(0);assertEquals(h.note,merged.note);assertEquals(h.completedOn,merged.completedOn);assertEquals(5,merged.days.size());
            Habit unfinished=new Habit();unfinished.name="Ещё не готово";store.save(unfinished);try{store.archive(unfinished);fail();}catch(IllegalStateException expected){}assertNull(unfinished.completedOn);
        }
    }
    @Test public void completionDateIncludesPastAchievementEvenAfterBreak(){
        Habit h=new Habit();h.goal=2;LocalDate today=LocalDate.now();h.created=today.minusDays(10);h.days.add(today.minusDays(10));h.days.add(today.minusDays(9));h.days.add(today);assertEquals(1,h.streak(today));assertEquals(today.minusDays(9),h.completionDate(today));
    }
    @Test public void completedFilterPersistsAndDailySummaryUpdates() throws Exception {
        try(Store store=new Store(context)){
            Habit h=new Habit();h.name="Привычка";h.created=LocalDate.now().minusDays(1);store.save(h);
            Habit note=new Habit();note.name="Чтение";note.isNote=true;note.note="Ежедневный текст";store.save(note);
            Habit plain=new Habit();plain.name="Обычная заметка";plain.isNote=true;plain.daily=false;plain.note="Без отметок";store.save(plain);
        }
        try(ActivityController<MainActivity> ctrl=Robolectric.buildActivity(MainActivity.class).setup()){
            MainActivity a=ctrl.get();assertNull(findText(a.root,"Все выполнено"));findText(a.root,"Скрыть выполненные").performClick();findCheck(a.root).performClick();assertNull(findText(a.root,"Привычка"));assertNotNull(findText(a.root,"Чтение"));assertNull(findText(a.root,"Все выполнено"));
            findCheck(a.root).performClick();assertNull(findText(a.root,"Чтение"));assertNotNull(findText(a.root,"Обычная заметка"));assertNotNull(findText(a.root,"Все выполнено"));assertEquals(1,a.store.all().get(0).days.size());screenshot(a.getWindow().getDecorView(),"all-done");
        }
        try(ActivityController<MainActivity> ctrl=Robolectric.buildActivity(MainActivity.class).setup()){
            MainActivity a=ctrl.get();assertNotNull(findText(a.root,"Показать выполненные"));assertNull(findText(a.root,"Привычка"));assertNotNull(findText(a.root,"Все выполнено"));findText(a.root,"Показать выполненные").performClick();assertNotNull(findText(a.root,"Привычка"));assertTrue(findCheck(a.root).isChecked());
            findCheck(a.root).performClick();assertNull(findText(a.root,"Все выполнено"));findText(a.root,"Скрыть выполненные").performClick();assertNotNull(findText(a.root,"Привычка"));assertNull(findText(a.root,"Чтение"));
            Habit note=a.store.all().get(1);a.store.mark(note,LocalDate.now());a.render();assertNotNull(findText(a.root,"Чтение"));assertNull(findText(a.root,"Все выполнено"));
        }
    }
    @Test public void previousDayMarksAndPlainNotesDoNotCompleteToday(){
        try(Store store=new Store(context)){Habit h=new Habit();h.name="Вчера";h.created=LocalDate.now().minusDays(1);store.save(h);store.mark(h,LocalDate.now().minusDays(1));}
        context.getSharedPreferences("settings",0).edit().putBoolean("show_completed",false).commit();
        try(ActivityController<MainActivity> ctrl=Robolectric.buildActivity(MainActivity.class).setup()){MainActivity a=ctrl.get();assertNotNull(findText(a.root,"Вчера"));assertNull(findText(a.root,"Все выполнено"));assertFalse(findCheck(a.root).isChecked());}
        context.deleteDatabase("chains.db");
        try(Store store=new Store(context)){Habit h=new Habit();h.name="Запись";h.isNote=true;h.note="Текст";h.daily=false;store.save(h);}
        try(ActivityController<MainActivity> ctrl=Robolectric.buildActivity(MainActivity.class).setup()){assertNull(findText(ctrl.get().root,"Все выполнено"));assertNotNull(findText(ctrl.get().root,"Запись"));}
    }
    @Test public void editingStartRecountsAndKeepsHistoryAndToday() throws Exception {
        LocalDate today=LocalDate.now();
        try(Store store=new Store(context)){
            Habit h=new Habit();h.name="Цепочка";h.goal=2;store.createFromDate(h,today.minusDays(10));store.mark(h,today);assertEquals(11,h.streak(today));
            store.saveWithStart(h,today.minusDays(3));assertEquals(4,h.streak(today));assertEquals(today.minusDays(2),h.completionDate(today));assertEquals(11,h.days.size());assertTrue(h.days.contains(today));assertEquals(today.minusDays(10),h.created);
            String backup=store.exportJson();store.delete(h);store.importJson(backup);h=store.all().get(0);assertEquals(today.minusDays(3),h.startDate());assertEquals(11,h.days.size());assertEquals(4,h.streak(today));
            store.saveWithStart(h,today.minusDays(15));assertEquals(16,h.streak(today));assertEquals(16,h.days.size());assertEquals(today.minusDays(15),h.created);
            store.mark(h,today.minusDays(1));store.saveWithStart(h,h.startDate());assertFalse(store.all().get(0).days.contains(today.minusDays(1)));
            String before=store.exportJson();try{store.saveWithStart(h,today.plusDays(1));fail();}catch(IllegalArgumentException expected){}assertEquals(before,store.exportJson());
            store.saveWithStart(h,today);assertEquals(1,h.streak(today));assertNull(h.completionDate(today));assertTrue(h.days.contains(today));assertEquals(15,h.days.size());
        }
    }
    @Test public void editStartThroughUiAndCancelSelection() throws Exception {
        LocalDate today=LocalDate.now(),start=today.minusDays(4);
        try(Store store=new Store(context)){Habit h=new Habit();h.name="Читать";h.isNote=true;h.note="Текст";store.save(h);}
        try(ActivityController<MainActivity> ctrl=Robolectric.buildActivity(MainActivity.class).setup()){
            MainActivity a=ctrl.get();a.edit(a.store.all().get(0));Dialog editor=org.robolectric.shadows.ShadowDialog.getLatestDialog();findText(editor.getWindow().getDecorView(),"Сегодня").performClick();DatePickerDialog picker=(DatePickerDialog)org.robolectric.shadows.ShadowDialog.getLatestDialog();Shadows.shadowOf(picker).getOnDateSetListenerCallback().onDateSet(picker.getDatePicker(),start.getYear(),start.getMonthValue()-1,start.getDayOfMonth());picker.dismiss();editor.dismiss();assertEquals(today,a.store.all().get(0).startDate());assertTrue(a.store.all().get(0).days.isEmpty());
            a.edit(a.store.all().get(0));editor=org.robolectric.shadows.ShadowDialog.getLatestDialog();findText(editor.getWindow().getDecorView(),"Сегодня").performClick();picker=(DatePickerDialog)org.robolectric.shadows.ShadowDialog.getLatestDialog();Shadows.shadowOf(picker).getOnDateSetListenerCallback().onDateSet(picker.getDatePicker(),start.getYear(),start.getMonthValue()-1,start.getDayOfMonth());picker.dismiss();findText(editor.getWindow().getDecorView(),"Сохранить").performClick();
            Habit h=a.store.all().get(0);assertEquals(start,h.startDate());assertEquals(4,h.streak(today));assertFalse(h.days.contains(today));assertNotNull(findText(a.root,"Начало · "+start.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"))));MainActivity.ChainProgress chain=a.new ChainProgress(h);assertTrue(chain.filled(3));assertFalse(chain.filled(4));
            a.store.saveWithStart(h,today);assertEquals(0,h.streak(today));assertEquals(4,h.days.size());assertNull(h.completionDate(today));
        }
    }
    @Test public void migrationFromVersionTwoKeepsNotesArchiveAndMarks() throws Exception {
        String id=java.util.UUID.randomUUID().toString();LocalDate date=LocalDate.now().minusDays(5);
        try(android.database.sqlite.SQLiteDatabase db=context.openOrCreateDatabase("chains.db",0,null)){
            db.execSQL("CREATE TABLE habits (id TEXT PRIMARY KEY, name TEXT NOT NULL, goal INTEGER NOT NULL, break_after INTEGER NOT NULL, minute INTEGER NOT NULL, reminder INTEGER NOT NULL, created TEXT NOT NULL, is_note INTEGER NOT NULL DEFAULT 0, note TEXT NOT NULL DEFAULT '', daily INTEGER NOT NULL DEFAULT 1, completed TEXT)");
            db.execSQL("CREATE TABLE marks (habit TEXT NOT NULL REFERENCES habits(id) ON DELETE CASCADE, day TEXT NOT NULL, PRIMARY KEY(habit,day))");db.execSQL("INSERT INTO habits VALUES (?,?,?,?,?,?,?,?,?,?,?)",new Object[]{id,"Заметка",1,0,1230,1,date.toString(),1,"Текст",1,date.toString()});db.execSQL("INSERT INTO marks VALUES (?,?)",new Object[]{id,date.toString()});db.setVersion(2);
        }
        try(Store store=new Store(context)){Habit h=store.all().get(0);assertEquals(date,h.startDate());assertEquals(date,h.completedOn);assertEquals("Текст",h.note);assertTrue(h.reminder);assertEquals(1230,h.minute);assertEquals(1,h.days.size());assertEquals(3,store.getReadableDatabase().getVersion());
            org.json.JSONObject legacy=new org.json.JSONObject(store.exportJson());legacy.put("schema",2);legacy.getJSONArray("habits").getJSONObject(0).remove("start");store.delete(h);store.importJson(legacy.toString());assertEquals(date,store.all().get(0).startDate());assertEquals(date,store.all().get(0).completedOn);
        }
    }
    @Test public void calendarChainKeepsMissedDaysInPlaceAndTodayOpen() throws Exception {
        LocalDate today=LocalDate.now();
        try(Store store=new Store(context)){Habit h=new Habit();h.name="Каждый день";h.goal=3;h.created=today.minusDays(5);store.save(h);store.mark(h,today.minusDays(5));store.mark(h,today.minusDays(3));store.mark(h,today.minusDays(1));}
        try(ActivityController<MainActivity> ctrl=Robolectric.buildActivity(MainActivity.class).setup()){
            MainActivity a=ctrl.get();Habit h=a.store.all().get(0);MainActivity.ChainProgress chain=a.new ChainProgress(h);assertEquals(6,chain.goal);assertTrue(chain.filled(0));assertFalse(chain.filled(1));assertTrue(chain.missed(1));assertTrue(chain.filled(2));assertTrue(chain.missed(3));assertTrue(chain.filled(4));assertFalse(chain.filled(5));assertFalse(chain.missed(5));assertFalse(chain.missed(6));assertNotNull(findText(a.root,"День 6 · 1 / 3"));
            screenshot(a.getWindow().getDecorView(),"calendar-chain");a.store.mark(h,LocalDate.now());a.render();chain=a.new ChainProgress(a.store.all().get(0));assertTrue(chain.filled(5));assertTrue(chain.missed(1));assertEquals(4,a.store.all().get(0).days.size());
        }
    }
    static void collectInputs(View v,java.util.List<EditText> out){if(v instanceof EditText)out.add((EditText)v);if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)collectInputs(((ViewGroup)v).getChildAt(i),out);}
    static TextView findText(View v,String text){if(v instanceof TextView&&text.contentEquals(((TextView)v).getText()))return (TextView)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){TextView found=findText(((ViewGroup)v).getChildAt(i),text);if(found!=null)return found;}return null;}
    static CheckBox findCheck(View v){if(v instanceof CheckBox)return (CheckBox)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){CheckBox c=findCheck(((ViewGroup)v).getChildAt(i));if(c!=null)return c;}return null;}
    private void screenshot(View view,String name) throws IOException {
        view.measure(View.MeasureSpec.makeMeasureSpec(786,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1704,View.MeasureSpec.EXACTLY));view.layout(0,0,786,1704);
        Bitmap image=Bitmap.createBitmap(786,1704,Bitmap.Config.ARGB_8888);view.draw(new Canvas(image));File folder=new File("build/previews");folder.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(folder,name+".png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();
    }
}
