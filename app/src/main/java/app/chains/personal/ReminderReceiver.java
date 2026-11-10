package app.chains.personal;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;
import java.time.*;

public class ReminderReceiver extends BroadcastReceiver {
    public static void schedule(Context c, Habit h) {
        AlarmManager manager=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        Intent intent=new Intent(c,ReminderReceiver.class).setAction("habit:"+h.id).putExtra("id",h.id);
        PendingIntent pi=PendingIntent.getBroadcast(c,0,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        manager.cancel(pi);
        if(!h.reminder) return;
        ZonedDateTime now=ZonedDateTime.now(), at=now.toLocalDate().atTime(h.minute/60,h.minute%60).atZone(now.getZone());
        if(!at.isAfter(now)) at=at.plusDays(1);
        long trigger=at.toInstant().toEpochMilli();
        if(Build.VERSION.SDK_INT<31 || manager.canScheduleExactAlarms()) manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,trigger,pi);
        else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,trigger,pi);
    }
    public static void sync(Context c) { try(Store s=new Store(c)) { for(Habit h:s.all()) schedule(c,h); } }
    @Override public void onReceive(Context c,Intent intent) {
        String action=intent.getAction();
        if(action==null) return;
        String id=intent.getStringExtra("id");
        if(id==null) { if(Intent.ACTION_BOOT_COMPLETED.equals(action)||Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)||Intent.ACTION_TIME_CHANGED.equals(action)||Intent.ACTION_TIMEZONE_CHANGED.equals(action)) sync(c); return; }
        if(!action.equals("habit:"+id)) return;
        try(Store store=new Store(c)) {
            for(Habit h:store.all()) if(h.id.equals(id)) {
                schedule(c,h);
                if(!h.reminder||h.days.contains(LocalDate.now())) return;
                if(Build.VERSION.SDK_INT>=33 && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) return;
                NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
                nm.createNotificationChannel(new NotificationChannel("habits","Привычки",NotificationManager.IMPORTANCE_DEFAULT));
                PendingIntent open=PendingIntent.getActivity(c,0,new Intent(c,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
                nm.notify(h.id.hashCode(),new Notification.Builder(c,"habits").setSmallIcon(app.chains.personal.R.drawable.ic_notification).setContentTitle(h.name).setContentText("Продолжи цепочку").setContentIntent(open).setAutoCancel(true).build());
            }
        }
    }
}
