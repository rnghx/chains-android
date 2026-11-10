package app.chains.personal;
import java.time.LocalDate;
public final class HabitTest {
    static int checks;
    static void check(int actual,int expected,String label){checks++;if(actual!=expected)throw new AssertionError(label+": "+actual+" != "+expected);}
    public static void main(String[] args){
        LocalDate t=LocalDate.of(2026,11,10);Habit h=new Habit();
        check(h.streak(t),0,"empty");
        h.days.add(t.minusDays(2));h.days.add(t.minusDays(1));check(h.streak(t),2,"today still open");
        h.days.add(t);check(h.streak(t),3,"consecutive");
        h.days.remove(t.minusDays(1));check(h.streak(t),1,"one missed breaks");
        h.breakAfter=2;check(h.streak(t),2,"one missed allowed");
        h.days.add(t.minusDays(5));check(h.streak(t),2,"two missed break");
        h.breakAfter=0;check(h.streak(t),3,"never breaks");
        h.breakAfter=1;h.days.clear();h.days.add(t.minusDays(2));check(h.streak(t),0,"yesterday missed");
        h.breakAfter=2;check(h.streak(t),1,"yesterday allowed");
        h.days.clear();h.days.add(t.minusDays(3));check(h.streak(t),0,"two days missed by yesterday");
        h.days.add(t);check(h.streak(t),1,"fresh series");
        h.breakAfter=0;h.days.add(t.plusDays(1));check(h.streak(t),2,"future ignored");
        h.goal=1;check(h.streak(t),2,"goal change keeps history");
        Habit year=new Habit();year.days.add(LocalDate.of(2025,12,31));year.days.add(LocalDate.of(2026,1,1));check(year.streak(LocalDate.of(2026,1,2)),2,"year boundary");
        Habit leap=new Habit();leap.days.add(LocalDate.of(2024,2,28));leap.days.add(LocalDate.of(2024,2,29));check(leap.streak(LocalDate.of(2024,3,1)),2,"leap day");
        System.out.println(checks+" habit checks passed");
    }
}
