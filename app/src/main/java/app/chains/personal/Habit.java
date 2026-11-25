package app.chains.personal;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

public final class Habit {
    public String id = UUID.randomUUID().toString(), name = "";
    public int goal = 30, breakAfter = 1, minute = 21 * 60;
    public boolean reminder = false, isNote = false, daily = true;
    public String note = "";
    public LocalDate completedOn, chainStart, archivedOn;
    public LocalDate created = LocalDate.now();
    public final TreeSet<LocalDate> days = new TreeSet<>();

    public boolean isArchived() { return archivedOn!=null||completedOn!=null; }
    public LocalDate archiveDate() { return archivedOn!=null?archivedOn:completedOn; }
    public LocalDate startDate() { return chainStart==null?created:chainStart; }
    public boolean tracksDaily() { return !isNote || daily; }
    public LocalDate completionDate(LocalDate today) {
        int count=0;LocalDate previous=null;
        for(LocalDate day:days){
            if(day.isBefore(startDate())) continue;
            if(day.isAfter(today)) break;
            if(previous!=null&&breakAfter>0&&ChronoUnit.DAYS.between(previous,day)-1>=breakAfter) count=0;
            count++;if(count>=goal) return day;previous=day;
        }
        return null;
    }

    public int completedDays(LocalDate until) {
        if(until.isBefore(startDate())) return 0;
        return days.subSet(startDate(),true,until,true).size();
    }
    public List<LocalDate> breakDates(LocalDate until) {
        List<LocalDate> breaks=new ArrayList<>();if(breakAfter<=0)return breaks;
        LocalDate previous=null,closed=until.minusDays(1);
        for(LocalDate day:days){
            if(day.isBefore(startDate()))continue;if(day.isAfter(until))break;
            if(previous!=null){LocalDate broken=previous.plusDays(breakAfter);if(broken.isBefore(day))breaks.add(broken);}
            previous=day;
        }
        if(previous!=null){LocalDate broken=previous.plusDays(breakAfter);if(!broken.isAfter(closed))breaks.add(broken);}
        return breaks;
    }

    public int streak(LocalDate today) {
        int count = 0;
        LocalDate newer = today.plusDays(1);
        for (LocalDate day : days.descendingSet()) {
            if (day.isAfter(today)) continue;
            if (day.isBefore(startDate())) break;
            long missed = ChronoUnit.DAYS.between(day, newer) - 1;
            // Today remains available until midnight.
            if (newer.equals(today.plusDays(1)) && !day.equals(today)) missed--;
            if (breakAfter > 0 && missed >= breakAfter) break;
            count++;
            newer = day;
        }
        return count;
    }
}
