package app.chains.personal;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class MainActivity extends Activity {
    static final int INK=0xfff2f2ed, MUTED=0xff858580, PANEL=0xff111211, LINE=0xff252724, ACCENT=0xffd9f99d;
    static final Locale RU=new Locale("ru");
    Store store; Updater updater; LinearLayout root,content; LocalDate shownDay; List<Habit> habits;
    final Handler handler=new Handler(Looper.getMainLooper());
    final Runnable dayWatcher=new Runnable(){public void run(){if(!LocalDate.now().equals(shownDay)) render(); handler.postDelayed(this,30000);}};
    int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override public void onCreate(Bundle state){super.onCreate(state);store=new Store(this);updater=new Updater(this);getWindow().setStatusBarColor(Color.BLACK);getWindow().setNavigationBarColor(Color.BLACK);render();ReminderReceiver.sync(this);}
    @Override public void onResume(){super.onResume();if(store!=null){render();ReminderReceiver.sync(this);}handler.post(dayWatcher);}
    @Override public void onPause(){handler.removeCallbacks(dayWatcher);super.onPause();}
    @Override public void onDestroy(){store.close();super.onDestroy();}

    GradientDrawable bg(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    TextView text(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setFontFeatureSettings("kern");t.setGravity(Gravity.CENTER_VERTICAL);return t;}
    TextView strong(String s,int size,int color){TextView t=text(s,size,color);t.setTypeface(android.graphics.Typeface.create("sans-serif-medium",Typeface.NORMAL));return t;}
    LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    void space(LinearLayout l,int h){l.addView(new View(this),new LinearLayout.LayoutParams(1,dp(h)));}
    void weight(LinearLayout l,View v){l.addView(v,new LinearLayout.LayoutParams(0,-2,1));}
    void touch(View v,Runnable run){v.setOnClickListener(w->run.run());android.util.TypedValue value=new android.util.TypedValue();getTheme().resolveAttribute(android.R.attr.selectableItemBackground,value,true);v.setForeground(getDrawable(value.resourceId));}
    TextView button(String title,Runnable run){TextView t=strong(title,15,INK);t.setGravity(Gravity.CENTER);t.setMinHeight(dp(52));t.setPadding(dp(16),0,dp(16),0);t.setBackground(bg(PANEL,18));touch(t,run);return t;}
    TextView readButton(Habit h){TextView t=button("Прочитать",()->readNote(h));t.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);t.setPadding(0,0,0,0);return t;}
    TextView icon(String kind,String label,Runnable run){TextView t=button("",run);t.setCompoundDrawablesWithIntrinsicBounds(new Symbol(kind),null,null,null);t.setPadding(dp(13),dp(13),dp(13),dp(13));t.setContentDescription(label);t.setMinHeight(0);t.setLayoutParams(new LinearLayout.LayoutParams(dp(48),dp(48)));if(kind.equals("settings"))t.setBackground(null);return t;}
    void title(LinearLayout parent,String s){TextView t=text(s,12,MUTED);t.setLetterSpacing(.13f);parent.addView(t);space(parent,12);}

    void render(){
        shownDay=LocalDate.now(); habits=new ArrayList<>();for(Habit h:store.all())if(h.completedOn==null)habits.add(h);
        root=column();root.setBackgroundColor(Color.BLACK);root.setPadding(dp(24),0,dp(24),0);
        root.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(dp(24),insets.getSystemWindowInsetTop(),dp(24),insets.getSystemWindowInsetBottom());return insets.consumeSystemWindowInsets();});
        setContentView(root);root.requestApplyInsets();space(root,18);
        LinearLayout header=row();ImageView logo=new ImageView(this);logo.setImageResource(R.drawable.ic_brand);logo.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);LinearLayout.LayoutParams logoSize=new LinearLayout.LayoutParams(dp(32),dp(32));logoSize.setMarginEnd(dp(10));header.addView(logo,logoSize);TextView brand=strong("chains",32,INK);brand.setLetterSpacing(-.06f);weight(header,brand);header.addView(icon("settings","Настройки",this::settings));root.addView(header);space(root,30);
        boolean showCompleted=getSharedPreferences("settings",MODE_PRIVATE).getBoolean("show_completed",true);
        LinearLayout dayRow=row();TextView date=text(shownDay.format(DateTimeFormatter.ofPattern("d MMMM",RU)),13,MUTED);weight(dayRow,date);
        if(!habits.isEmpty()){TextView filter=button(showCompleted?"Скрыть выполненные":"Показать выполненные",()->{getSharedPreferences("settings",MODE_PRIVATE).edit().putBoolean("show_completed",!showCompleted).apply();render();});filter.setTextSize(12);filter.setTextColor(MUTED);filter.setPadding(dp(8),0,0,0);filter.setBackgroundColor(Color.TRANSPARENT);filter.setMinHeight(dp(48));dayRow.addView(filter);}root.addView(dayRow);space(root,24);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);scroll.setVerticalScrollBarEnabled(false);content=column();content.setPadding(0,0,0,dp(12));scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        if(habits.isEmpty()){
            LinearLayout empty=column();empty.setGravity(Gravity.CENTER);empty.addView(new EmptyChain(),new LinearLayout.LayoutParams(dp(160),dp(150)));space(empty,24);empty.addView(strong("Первая цепочка",23,INK));space(empty,30);TextView create=button("+  Создать привычку",()->edit(null));create.setTextColor(Color.BLACK);create.setBackground(bg(ACCENT,18));empty.addView(create,new LinearLayout.LayoutParams(-1,dp(56)));space(empty,8);empty.addView(button("+  Заметка",()->edit(null,true)),new LinearLayout.LayoutParams(-1,dp(52)));content.addView(empty,new LinearLayout.LayoutParams(-1,-1));
        }else{
            int dailyCount=0,doneCount=0;for(Habit h:habits)if(h.tracksDaily()){dailyCount++;if(h.days.contains(shownDay))doneCount++;}
            if(dailyCount>0&&dailyCount==doneCount){TextView done=strong("Все выполнено",22,ACCENT);done.setGravity(Gravity.CENTER);done.setPadding(0,dp(24),0,dp(32));content.addView(done);}
            for(Habit h:habits){if(!showCompleted&&h.tracksDaily()&&h.days.contains(shownDay))continue;content.addView(card(h));space(content,12);}
            TextView add=button("+  Новая привычка",()->edit(null));add.setTextColor(ACCENT);add.setBackground(bg(PANEL,20));content.addView(add);space(content,8);content.addView(button("+  Заметка",()->edit(null,true)));space(content,12);
        }
    }
    View card(Habit h){
        LinearLayout card=column();card.setBackground(bg(PANEL,24));card.setPadding(dp(20),dp(20),dp(20),dp(20));
        LinearLayout top=row();LinearLayout labels=column();TextView name=strong(h.name,19,INK);name.setMaxLines(2);labels.addView(name);space(labels,5);
        int n=h.streak(shownDay);if(h.tracksDaily()){labels.addView(text("Начало · "+h.created.format(DateTimeFormatter.ofPattern("dd.MM.yyyy",RU)),12,MUTED));space(labels,5);labels.addView(text(n+" / "+h.goal,13,n>=h.goal?ACCENT:MUTED));}weight(top,labels);
        CheckBox check=new CheckBox(this);check.setButtonTintList(android.content.res.ColorStateList.valueOf(h.days.contains(shownDay)?ACCENT:0xff61665a));check.setChecked(h.days.contains(shownDay));check.setContentDescription(h.name+(h.isNote?": прочитано сегодня":": выполнено сегодня"));check.setVisibility(h.tracksDaily()?View.VISIBLE:View.GONE);
        top.addView(check,new LinearLayout.LayoutParams(dp(48),dp(48)));check.setOnCheckedChangeListener((v,checked)->{store.mark(h,LocalDate.now());if(checked){((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).cancel(h.id.hashCode());v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);}render();});
        card.addView(top);
        if(h.isNote){space(card,12);card.addView(readButton(h));}
        if(h.tracksDaily()){space(card,20);ChainProgress chain=new ChainProgress(h);card.addView(chain,new LinearLayout.LayoutParams(-1,dp(chain.rows*28)));}
        if(h.tracksDaily()&&h.completionDate(shownDay)!=null){space(card,12);card.addView(button("В архив",()->archiveHabit(h)));}
        touch(card,()->{if(h.isNote)readNote(h);else edit(h);});return card;
    }
    Dialog sheet(LinearLayout view){Dialog d=new Dialog(this);ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.addView(view);view.setPadding(dp(24),dp(26),dp(24),dp(28));d.setContentView(scroll);Window w=d.getWindow();w.setBackgroundDrawable(bg(PANEL,28));w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);WindowManager.LayoutParams a=w.getAttributes();a.width=-1;a.height=-2;a.gravity=Gravity.BOTTOM;a.dimAmount=.65f;w.setAttributes(a);w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);return d;}
    EditText input(String value,String hint,int type){EditText e=new EditText(this);e.setText(value);e.setHint(hint);e.setTextSize(18);e.setSingleLine(true);e.setTextColor(INK);e.setHintTextColor(MUTED);e.setInputType(type);e.setPadding(dp(14),dp(12),dp(14),dp(12));e.setBackground(bg(0xff1d1f1b,14));return e;}
    void edit(Habit original){edit(original,false);}
    void edit(Habit original,boolean note){
        Habit h=original==null?new Habit():original;if(original==null)h.isNote=note;LinearLayout body=column();Dialog dialog=sheet(body);
        LinearLayout head=row();weight(head,strong(h.isNote?(original==null?"Новая заметка":"Заметка"):(original==null?"Новая привычка":"Привычка"),24,INK));head.addView(icon("close","Закрыть",dialog::dismiss));body.addView(head);space(body,26);
        title(body,"НАЗВАНИЕ");EditText name=input(h.name,h.isNote?"Название":"Например, читать",android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);name.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(80)});body.addView(name);space(body,24);
        EditText noteText=input(h.note,"",android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);noteText.setSingleLine(false);noteText.setMinLines(5);noteText.setGravity(Gravity.TOP|Gravity.START);noteText.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(20000)});
        Switch daily=new Switch(this);daily.setText("Читать ежедневно");daily.setTextColor(INK);daily.setTextSize(16);daily.setMinHeight(dp(48));daily.setChecked(h.daily);
        if(h.isNote){title(body,"ТЕКСТ");body.addView(noteText);space(body,16);body.addView(daily);space(body,24);}
        LinearLayout tracking=column();body.addView(tracking);tracking.setVisibility(h.tracksDaily()?View.VISIBLE:View.GONE);daily.setOnCheckedChangeListener((v,on)->tracking.setVisibility(on?View.VISIBLE:View.GONE));
        final LocalDate[] start={LocalDate.now()};
        if(original==null){
            title(tracking,"ДАТА НАЧАЛА");TextView startDate=button("",()->{});Runnable showStart=()->startDate.setText(start[0].equals(LocalDate.now())?"Сегодня":start[0].format(DateTimeFormatter.ofPattern("d MMMM yyyy",RU)));showStart.run();
            touch(startDate,()->{DatePickerDialog picker=new DatePickerDialog(this,(v,year,month,day)->{LocalDate selected=LocalDate.of(year,month+1,day);if(!selected.isAfter(LocalDate.now())){start[0]=selected;showStart.run();}},start[0].getYear(),start[0].getMonthValue()-1,start[0].getDayOfMonth());picker.getDatePicker().setMaxDate(System.currentTimeMillis());picker.show();});
            tracking.addView(startDate);space(tracking,24);
        }
        title(tracking,"ЦЕЛЬ · ДНЕЙ");EditText goal=input(""+h.goal,"30",android.text.InputType.TYPE_CLASS_NUMBER);goal.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(4)});tracking.addView(goal);space(tracking,24);
        title(tracking,"ОБРЫВ ПОСЛЕ ПРОПУСКОВ");LinearLayout choices=row();final int[] rule={h.breakAfter};final ArrayList<TextView> opts=new ArrayList<>();
        int[] values={1,2,0,-1};String[] names={"1 дня","2 дней","Никогда",h.breakAfter>2?h.breakAfter+" дней":"Другое"};
        Runnable refresh=()->{for(int j=0;j<opts.size();j++){boolean active=values[j]==rule[0]||(j==3&&rule[0]>2);opts.get(j).setBackground(bg(active?ACCENT:0xff20221e,12));opts.get(j).setTextColor(active?Color.BLACK:MUTED);}};
        for(int i=0;i<4;i++){final int at=i;TextView o=button(names[i],()->{if(at==3){EditText number=input(rule[0]>2?""+rule[0]:"3","3–365",android.text.InputType.TYPE_CLASS_NUMBER);AlertDialog other=new AlertDialog.Builder(this).setTitle("Дней подряд").setView(number).setNegativeButton("Отмена",null).setPositiveButton("Готово",null).create();other.setOnShowListener(x->other.getButton(-1).setOnClickListener(v->{try{int n=Integer.parseInt(number.getText().toString());if(n<3||n>365)throw new Exception();rule[0]=n;opts.get(3).setText(n+" дней");refresh.run();other.dismiss();}catch(Exception e){number.setError("От 3 до 365");}}));other.show();}else{rule[0]=values[at];refresh.run();}});o.setTextSize(12);o.setPadding(dp(4),0,dp(4),0);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(48),1);if(i>0)p.leftMargin=dp(5);choices.addView(o,p);opts.add(o);}refresh.run();tracking.addView(choices);space(tracking,24);
        LinearLayout reminderRow=row();weight(reminderRow,strong("Напоминание",16,INK));Switch reminder=new Switch(this);reminder.setContentDescription("Ежедневное напоминание");reminder.setChecked(h.reminder);reminderRow.addView(reminder);tracking.addView(reminderRow);space(tracking,10);
        final int[] minute={h.minute};TextView time=button(String.format(RU,"%02d:%02d",minute[0]/60,minute[0]%60),()->{});time.setBackground(bg(0xff20221e,14));touch(time,()->new TimePickerDialog(this,(v,hr,min)->{minute[0]=hr*60+min;time.setText(String.format(RU,"%02d:%02d",hr,min));},minute[0]/60,minute[0]%60,true).show());time.setVisibility(reminder.isChecked()?View.VISIBLE:View.GONE);reminder.setOnCheckedChangeListener((v,on)->time.setVisibility(on?View.VISIBLE:View.GONE));tracking.addView(time);space(tracking,26);
        TextView save=button("Сохранить",()->{String n=name.getText().toString().trim();int g;try{g=Integer.parseInt(goal.getText().toString());if(g<1||g>9999)throw new Exception();}catch(Exception e){goal.setError("От 1 до 9999");return;}if(h.isNote&&noteText.getText().toString().trim().isEmpty()){noteText.setError("Текст");return;}if(h.isNote&&n.isEmpty())n="Заметка";if(n.isEmpty()){name.setError("Название");return;}h.note=h.isNote?noteText.getText().toString():"";h.daily=!h.isNote||daily.isChecked();h.name=n;h.goal=g;h.breakAfter=rule[0];h.minute=minute[0];h.reminder=reminder.isChecked();if(original==null)store.createFromDate(h,start[0]);else store.save(h);ReminderReceiver.schedule(this,h);dialog.dismiss();render();if(h.reminder)notificationPermission();});save.setBackground(bg(ACCENT,16));save.setTextColor(Color.BLACK);body.addView(save,new LinearLayout.LayoutParams(-1,dp(56)));
        if(original!=null&&(h.completionDate(LocalDate.now())!=null||!h.tracksDaily())){space(body,10);body.addView(button("В архив",()->{dialog.dismiss();archiveHabit(h);}));}
        if(original!=null){space(body,10);TextView remove=button(h.isNote?"Удалить заметку":"Удалить привычку",()->new AlertDialog.Builder(this).setTitle("Удалить «"+h.name+"»?").setMessage("История этой привычки будет удалена.").setNegativeButton("Отмена",null).setPositiveButton("Удалить",(d,w)->{h.reminder=false;ReminderReceiver.schedule(this,h);((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).cancel(h.id.hashCode());store.delete(h);dialog.dismiss();render();}).show());remove.setTextColor(0xffd8a8a0);body.addView(remove);}
        dialog.show();
    }
    void archiveHabit(Habit h){
        store.archive(h);ReminderReceiver.schedule(this,h);((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).cancel(h.id.hashCode());render();
    }
    void readNote(Habit h){
        LinearLayout body=column();Dialog d=sheet(body);LinearLayout top=row();weight(top,strong(h.name,24,INK));
        if(h.completedOn==null)top.addView(icon("edit","Редактировать",()->{d.dismiss();edit(h);}));top.addView(icon("close","Закрыть",d::dismiss));body.addView(top);space(body,20);
        TextView note=text(h.note,18,INK);note.setTextIsSelectable(true);note.setLineSpacing(dp(4),1);body.addView(note);
        if(h.completedOn==null&&h.tracksDaily()){space(body,24);TextView read=button(h.days.contains(LocalDate.now())?"Прочитано сегодня":"Прочитано",()->{if(!h.days.contains(LocalDate.now())){store.mark(h,LocalDate.now());((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).cancel(h.id.hashCode());}d.dismiss();render();});body.addView(read);}
        d.show();
    }
    void archive(){
        LinearLayout body=column();Dialog d=sheet(body);LinearLayout top=row();weight(top,strong("Архив",24,INK));top.addView(icon("close","Закрыть",d::dismiss));body.addView(top);space(body,24);
        boolean empty=true;
        for(Habit h:store.all())if(h.completedOn!=null){
            empty=false;LinearLayout item=column();item.setPadding(dp(16),dp(16),dp(16),dp(16));item.setBackground(bg(0xff1d1f1b,18));item.addView(strong(h.name,19,INK));space(item,8);
            DateTimeFormatter format=DateTimeFormatter.ofPattern("dd.MM.yyyy",RU);item.addView(text(h.created.format(format)+" → "+h.completedOn.format(format),13,MUTED));
            if(h.isNote){space(item,12);item.addView(readButton(h));}
            space(item,12);item.addView(button("Вернуть",()->{store.restore(h);ReminderReceiver.schedule(this,h);d.dismiss();render();}));body.addView(item);space(body,12);
        }
        if(empty)body.addView(text("Пусто",15,MUTED));d.show();
    }
    void notificationPermission(){if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},41);}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==41&&(results.length==0||results[0]!=PackageManager.PERMISSION_GRANTED))new AlertDialog.Builder(this).setTitle("Уведомления выключены").setMessage("Разрешить их можно в настройках Android.").setPositiveButton("Настройки",(d,w)->startActivity(new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,getPackageName()))).setNegativeButton("Закрыть",null).show();}

    void settings(){
        LinearLayout body=column();Dialog d=sheet(body);LinearLayout top=row();weight(top,strong("Настройки",24,INK));top.addView(icon("close","Закрыть",d::dismiss));body.addView(top);space(body,28);title(body,"ДАННЫЕ");
        body.addView(button("Архив",()->{d.dismiss();archive();}));space(body,8);
        body.addView(button("Сохранить копию  ↗",()->{Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"chains-"+LocalDate.now()+".json");startActivityForResult(i,101);}));space(body,8);
        body.addView(button("Восстановить из копии  ↙",()->startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),102)));space(body,26);title(body,"ПРИЛОЖЕНИЕ");
        body.addView(button("Обновить приложение  ↗",()->{d.dismiss();updater.check();}));space(body,8);
        body.addView(button("Уведомления  ↗",()->startActivity(new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,getPackageName()))));
        if(Build.VERSION.SDK_INT>=31){space(body,8);body.addView(button("Точные напоминания  ↗",()->startActivity(new Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,android.net.Uri.parse("package:"+getPackageName())))));}
        space(body,24);String number="";try{number=getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(Exception ignored){}TextView version=text("chains  /  "+number,12,MUTED);version.setGravity(Gravity.CENTER);body.addView(version);d.show();
    }
    @Override public void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);if(req==103){if(Build.VERSION.SDK_INT>=26&&getPackageManager().canRequestPackageInstalls())updater.installPending();return;}if(result!=RESULT_OK||data==null||data.getData()==null)return;
        if(req==101){try(OutputStream out=getContentResolver().openOutputStream(data.getData(),"wt")){if(out==null)throw new IOException();out.write(store.exportJson().getBytes(StandardCharsets.UTF_8));Toast.makeText(this,"Копия сохранена",Toast.LENGTH_SHORT).show();}catch(Exception e){error("Не удалось сохранить копию");}}
        if(req==102){try(InputStream in=getContentResolver().openInputStream(data.getData())){if(in==null)throw new IOException();ByteArrayOutputStream buf=new ByteArrayOutputStream();byte[] part=new byte[8192];int n;while((n=in.read(part))!=-1){if(buf.size()+n>16*1024*1024)throw new IOException();buf.write(part,0,n);}String json=buf.toString(StandardCharsets.UTF_8.name());new AlertDialog.Builder(this).setTitle("Восстановить копию?").setMessage("Привычки и отметки добавятся к текущим. Существующие настройки сохранятся.").setNegativeButton("Отмена",null).setPositiveButton("Восстановить",(d,w)->{try{try(FileOutputStream backup=openFileOutput("before-import.json",MODE_PRIVATE)){backup.write(store.exportJson().getBytes(StandardCharsets.UTF_8));}store.importJson(json);ReminderReceiver.sync(this);render();Toast.makeText(this,"Копия восстановлена",Toast.LENGTH_SHORT).show();}catch(Exception e){error("Не удалось прочитать копию. Данные сохранены.");}}).show();}catch(Exception e){error("Не удалось открыть файл");}}
    }
    void error(String s){new AlertDialog.Builder(this).setMessage(s).setPositiveButton("Закрыть",null).show();}

    class Symbol extends Drawable {
        final String kind;Paint p=new Paint(3);Symbol(String k){kind=k;setBounds(0,0,dp(22),dp(22));}
        public int getIntrinsicWidth(){return dp(22);}public int getIntrinsicHeight(){return dp(22);}
        public void draw(Canvas c){c.save();c.translate(getBounds().left,getBounds().top);c.scale(getBounds().width()/24f,getBounds().height()/24f);p.setColor(INK);p.setStrokeWidth(1.6f);p.setStrokeCap(Paint.Cap.ROUND);p.setStyle(Paint.Style.STROKE);
            if(kind.equals("settings")){Path gear=new Path();gear.setFillType(Path.FillType.EVEN_ODD);for(int i=0;i<32;i++){double angle=i*Math.PI/16;float radius=(i%4==1||i%4==2)?10:8;float x=12+radius*(float)Math.cos(angle),y=12+radius*(float)Math.sin(angle);if(i==0)gear.moveTo(x,y);else gear.lineTo(x,y);}gear.close();gear.addCircle(12,12,3.2f,Path.Direction.CW);p.setStyle(Paint.Style.FILL);c.drawPath(gear,p);}
            else if(kind.equals("close")){c.drawLine(6,6,18,18,p);c.drawLine(18,6,6,18,p);}
            else if(kind.equals("edit")){c.drawLine(6,17,17,6,p);c.drawLine(17,6,20,9,p);c.drawLine(20,9,9,20,p);c.drawLine(9,20,5,21,p);c.drawLine(5,21,6,17,p);}
            else{boolean left=kind.equals("left");c.drawLine(left?15:9,6,left?9:15,12,p);c.drawLine(left?9:15,12,left?15:9,18,p);}c.restore();}
        public void setAlpha(int a){p.setAlpha(a);}public void setColorFilter(ColorFilter f){p.setColorFilter(f);}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
    class ChainProgress extends View {
        final int goal,completed,rows;final Paint paint=new Paint(3);
        ChainProgress(Habit h){super(MainActivity.this);goal=h.goal;completed=Math.min(h.streak(LocalDate.now()),goal);rows=(goal+9)/10;setContentDescription(h.name+": "+completed+" из "+goal);}
        boolean filled(int index){return index>=0&&index<completed;}
        protected void onDraw(Canvas canvas){
            float width=getWidth(),pad=dp(2),size=Math.min(dp(18),(width-2*pad-dp(7)*9)/10f),step=(width-2*pad-size)/9f;
            Rect clip=canvas.getClipBounds();int first=Math.max(0,clip.top/dp(28))*10,last=Math.min(goal,((clip.bottom/dp(28))+1)*10);
            for(int index=first;index<last;index++){
                int column=index%10;float x=pad+column*step,y=dp(3)+(index/10)*dp(28);
                paint.setStyle(Paint.Style.FILL);
                if(column<9&&index+1<goal){paint.setColor(filled(index+1)?0xff7b9253:LINE);canvas.drawRect(x+size,y+size/2-dp(1),x+step,y+size/2+dp(1),paint);}
                paint.setColor(filled(index)?ACCENT:LINE);canvas.drawRoundRect(x,y,x+size,y+size,dp(6),dp(6),paint);
            }
        }
    }
    class EmptyChain extends View {Paint p=new Paint(3);EmptyChain(){super(MainActivity.this);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        protected void onDraw(Canvas canvas){canvas.save();canvas.translate(getWidth()/2f,getHeight()/2f);canvas.rotate(-38);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(2));p.setColor(0xff3d4433);canvas.drawRoundRect(-dp(57),-dp(21),dp(6),dp(21),dp(21),dp(21),p);p.setColor(ACCENT);canvas.drawRoundRect(-dp(6),-dp(21),dp(57),dp(21),dp(21),dp(21),p);canvas.restore();}}
}
