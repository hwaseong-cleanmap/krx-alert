package krx.alert;
import java.time.*;
import java.util.*;
public class RulesSelfTest {
 static int checks;
 static void check(boolean b){checks++;if(!b)throw new AssertionError("check "+checks);}
 static String record(String code){String[] f=new String[47];Arrays.fill(f,"0");f[0]=code;f[1]="100000";f[2]="70000";f[33]="20261105";f[34]="20";f[46]="2";return String.join("^",f);}
 public static void main(String[] args){
  Instant now=Instant.parse("2026-11-05T01:00:05Z");
  check(Rules.fresh("20261105","100000","2",now));
  check(!Rules.fresh("20261105","100000","20",now));
  check(!Rules.fresh("20261104","100000","2",now));
  check(!Rules.fresh("20261105","095900","2",now));
  check(!Rules.fresh("20261105","100009","2",now));
  check(!Rules.fresh("20261105","085959","2",Instant.parse("2026-11-04T23:59:59Z")));
  check(!Rules.fresh("20261105","153001","2",Instant.parse("2026-11-05T06:30:01Z")));
  check(Rules.fresh("20261105","153000","5",Instant.parse("2026-11-05T06:30:01Z")));
  check(!Rules.fresh("20261105","100000","3",now));
  check(!Rules.fresh("20261107","100000","2",Instant.parse("2026-11-07T01:00:01Z")));
  check(!Rules.fresh("bad","bad","2",now));
  check(Rules.trigger(false,100,100,90,110).equals("매수 가격 조건"));
  check(Rules.trigger(false,99,100,90,110).isEmpty());
  check(Rules.trigger(false,90,100,90,110).isEmpty());
  check(Rules.trigger(true,90,100,90,110).equals("손절 조건"));
  check(Rules.trigger(true,110,100,90,110).equals("목표가 조건"));
  check(Rules.trigger(true,100,100,90,110).isEmpty());
  check(Rules.trigger(false,0,100,90,110).isEmpty());
  check(Rules.trigger(false,Double.NaN,100,90,110).isEmpty());
  check(Rules.trigger(true,100,0,0,0).isEmpty());
  check(Rules.records("0|H0STCNT0|001|"+record("005930")).size()==1);
  check(Rules.records("0|H0STCNT0|002|"+record("005930")+"^"+record("000660")).get(1)[0].equals("000660"));
  check(Rules.records("0|H0STCNT0|002|broken").isEmpty());
  check(Rules.records("1|H0STCNT0|001|"+record("005930")).isEmpty());
  check(Rules.records("0|H0STANC0|001|"+record("005930")).isEmpty());
  check(Rules.records("{bad}").isEmpty());
  System.out.println("PASS "+checks+" domain checks");
 }
}
