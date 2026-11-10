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
    @Before public void prepare(){context=RuntimeEnvironment.getApplication();context.deleteDatabase("chains.db");}
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
    static void collectInputs(View v,java.util.List<EditText> out){if(v instanceof EditText)out.add((EditText)v);if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)collectInputs(((ViewGroup)v).getChildAt(i),out);}
    static TextView findText(View v,String text){if(v instanceof TextView&&text.contentEquals(((TextView)v).getText()))return (TextView)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){TextView found=findText(((ViewGroup)v).getChildAt(i),text);if(found!=null)return found;}return null;}
    static CheckBox findCheck(View v){if(v instanceof CheckBox)return (CheckBox)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){CheckBox c=findCheck(((ViewGroup)v).getChildAt(i));if(c!=null)return c;}return null;}
    private void screenshot(View view,String name) throws IOException {
        view.measure(View.MeasureSpec.makeMeasureSpec(786,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1704,View.MeasureSpec.EXACTLY));view.layout(0,0,786,1704);
        Bitmap image=Bitmap.createBitmap(786,1704,Bitmap.Config.ARGB_8888);view.draw(new Canvas(image));File folder=new File("build/previews");folder.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(folder,name+".png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();
    }
}
