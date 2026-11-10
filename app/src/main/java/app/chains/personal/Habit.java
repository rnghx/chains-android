package app.chains.personal;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

public final class Habit {
    public String id = UUID.randomUUID().toString(), name = "";
    public int goal = 30, breakAfter = 1, minute = 21 * 60;
    public boolean reminder = false;
    public LocalDate created = LocalDate.now();
    public final TreeSet<LocalDate> days = new TreeSet<>();

    public int streak(LocalDate today) {
        int count = 0;
        LocalDate newer = today.plusDays(1);
        for (LocalDate day : days.descendingSet()) {
            if (day.isAfter(today)) continue;
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
