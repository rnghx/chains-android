package app.chains.personal;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import org.json.*;
import java.time.LocalDate;
import java.util.*;

public final class Store extends SQLiteOpenHelper {
    public Store(Context c) { super(c, "chains.db", null, 2); }
    public void onConfigure(SQLiteDatabase db) { db.setForeignKeyConstraintsEnabled(true); }
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE habits (id TEXT PRIMARY KEY, name TEXT NOT NULL, goal INTEGER NOT NULL, break_after INTEGER NOT NULL, minute INTEGER NOT NULL, reminder INTEGER NOT NULL, created TEXT NOT NULL, is_note INTEGER NOT NULL DEFAULT 0, note TEXT NOT NULL DEFAULT '', daily INTEGER NOT NULL DEFAULT 1, completed TEXT)");
        db.execSQL("CREATE TABLE marks (habit TEXT NOT NULL REFERENCES habits(id) ON DELETE CASCADE, day TEXT NOT NULL, PRIMARY KEY(habit,day))");
    }
    public void onUpgrade(SQLiteDatabase db, int old, int next) {
        if(old<2) {
            db.execSQL("ALTER TABLE habits ADD COLUMN is_note INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE habits ADD COLUMN note TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE habits ADD COLUMN daily INTEGER NOT NULL DEFAULT 1");
            db.execSQL("ALTER TABLE habits ADD COLUMN completed TEXT");
        }
    }
    public List<Habit> all() {
        List<Habit> list = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT * FROM habits ORDER BY rowid", null)) {
            while (c.moveToNext()) {
                Habit h = new Habit(); h.id=c.getString(0); h.name=c.getString(1); h.goal=c.getInt(2); h.breakAfter=c.getInt(3); h.minute=c.getInt(4); h.reminder=c.getInt(5)==1; h.created=LocalDate.parse(c.getString(6));h.isNote=c.getInt(7)==1;h.note=c.getString(8);h.daily=c.getInt(9)==1;h.completedOn=c.isNull(10)?null:LocalDate.parse(c.getString(10));
                try (Cursor m=getReadableDatabase().rawQuery("SELECT day FROM marks WHERE habit=?",new String[]{h.id})) { while(m.moveToNext()) h.days.add(LocalDate.parse(m.getString(0))); }
                list.add(h);
            }
        }
        return list;
    }
    public void save(Habit h) {
        ContentValues v=new ContentValues(); v.put("id",h.id); v.put("name",h.name); v.put("goal",h.goal); v.put("break_after",h.breakAfter); v.put("minute",h.minute); v.put("reminder",h.reminder?1:0); v.put("created",h.created.toString());v.put("is_note",h.isNote?1:0);v.put("note",h.note);v.put("daily",h.daily?1:0);v.put("completed",h.completedOn==null?null:h.completedOn.toString());
        SQLiteDatabase db=getWritableDatabase();
        if(db.update("habits",v,"id=?",new String[]{h.id})==0) db.insertOrThrow("habits",null,v);
    }
    public void createFromDate(Habit h, LocalDate start) {
        LocalDate today=LocalDate.now();
        if(start.isAfter(today)) throw new IllegalArgumentException("Future start date");
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            try(Cursor c=db.rawQuery("SELECT id FROM habits WHERE id=?",new String[]{h.id})) { if(c.moveToFirst()) throw new IllegalArgumentException("Habit already exists"); }
            h.created=start;save(h);
            for(LocalDate day=start;h.tracksDaily()&&day.isBefore(today);day=day.plusDays(1)) {
                ContentValues v=new ContentValues();v.put("habit",h.id);v.put("day",day.toString());db.insertOrThrow("marks",null,v);
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        h.days.clear();for(LocalDate day=start;h.tracksDaily()&&day.isBefore(today);day=day.plusDays(1)) h.days.add(day);
    }
    public void mark(Habit h, LocalDate day) {
        if(h.completedOn!=null || !h.tracksDaily() || day.isAfter(LocalDate.now()) || day.isBefore(h.created)) return;
        if(h.days.contains(day)) { getWritableDatabase().delete("marks","habit=? AND day=?",new String[]{h.id,day.toString()}); h.days.remove(day); }
        else { ContentValues v=new ContentValues(); v.put("habit",h.id); v.put("day",day.toString()); getWritableDatabase().insertOrThrow("marks",null,v); h.days.add(day); }
    }
    public void archive(Habit h) {
        if(h.completedOn!=null) return;
        LocalDate completed=h.tracksDaily()?h.completionDate(LocalDate.now()):LocalDate.now();
        if(completed==null) throw new IllegalStateException("Goal not reached");
        h.completedOn=completed;save(h);
    }
    public void restore(Habit h) { h.completedOn=null;save(h); }
    public void delete(Habit h) { getWritableDatabase().delete("habits","id=?",new String[]{h.id}); }
    public String exportJson() throws JSONException {
        JSONArray items=new JSONArray();
        for(Habit h:all()) { JSONArray days=new JSONArray(); for(LocalDate d:h.days) days.put(d.toString()); items.put(new JSONObject().put("id",h.id).put("name",h.name).put("goal",h.goal).put("breakAfter",h.breakAfter).put("minute",h.minute).put("reminder",h.reminder).put("created",h.created.toString()).put("days",days).put("isNote",h.isNote).put("note",h.note).put("daily",h.daily).put("completed",h.completedOn==null?JSONObject.NULL:h.completedOn.toString())); }
        return new JSONObject().put("app","chains").put("schema",2).put("habits",items).toString(2);
    }
    public int importJson(String json) throws Exception {
        JSONObject root=new JSONObject(json);
        if(!"chains".equals(root.getString("app")) || (root.getInt("schema")!=1&&root.getInt("schema")!=2)) throw new Exception("Неподдерживаемая копия");
        JSONArray items=root.getJSONArray("habits"); if(items.length()>10000) throw new Exception("Слишком много привычек");
        List<Habit> parsed=new ArrayList<>(); Set<String> ids=new HashSet<>();
        for(int i=0;i<items.length();i++) {
            JSONObject o=items.getJSONObject(i); Habit h=new Habit(); h.id=UUID.fromString(o.getString("id")).toString(); h.name=o.getString("name").trim(); h.goal=o.getInt("goal"); h.breakAfter=o.getInt("breakAfter"); h.minute=o.getInt("minute"); h.reminder=o.getBoolean("reminder"); h.created=LocalDate.parse(o.getString("created"));
            if(root.getInt("schema")==2) {
                h.isNote=o.getBoolean("isNote");h.note=o.getString("note");h.daily=o.getBoolean("daily");
                if(!o.isNull("completed")) h.completedOn=LocalDate.parse(o.getString("completed"));
                if(h.note.length()>20000 || (h.isNote&&h.note.trim().isEmpty()) || (h.completedOn!=null&&(h.completedOn.isBefore(h.created)||h.completedOn.isAfter(LocalDate.now())))) throw new Exception("Некорректная заметка");
            }
            if(!ids.add(h.id)||h.name.isEmpty()||h.name.length()>80||h.goal<1||h.goal>9999||h.breakAfter<0||h.breakAfter>365||h.minute<0||h.minute>1439||h.created.isAfter(LocalDate.now())) throw new Exception("Некорректная копия");
            JSONArray marks=o.getJSONArray("days"); for(int j=0;j<marks.length();j++) { LocalDate d=LocalDate.parse(marks.getString(j)); if(d.isBefore(h.created)||d.isAfter(LocalDate.now())) throw new Exception("Некорректная дата"); h.days.add(d); } parsed.add(h);
        }
        Map<String,Habit> existing=new HashMap<>(); for(Habit h:all()) existing.put(h.id,h);
        SQLiteDatabase db=getWritableDatabase(); db.beginTransaction();
        try {
            for(Habit h:parsed) {
                Habit old=existing.get(h.id);
                if(old==null) save(h);
                else if(h.created.isBefore(old.created)) { old.created=h.created; save(old); }
                for(LocalDate d:h.days) { ContentValues v=new ContentValues(); v.put("habit",h.id); v.put("day",d.toString()); db.insertWithOnConflict("marks",null,v,SQLiteDatabase.CONFLICT_IGNORE); }
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        return parsed.size();
    }
}
