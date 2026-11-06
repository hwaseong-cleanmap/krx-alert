package krx.alert;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
/** Pure domain rules. Price triggers are not validated investment predictions. */
public final class Rules {
 public static final ZoneId SEOUL=ZoneId.of("Asia/Seoul");
 public static boolean fresh(String date,String time,String market,Instant now) {
  try {
   LocalDateTime tick=LocalDateTime.of(LocalDate.parse(date,DateTimeFormatter.BASIC_ISO_DATE),LocalTime.parse(time,DateTimeFormatter.ofPattern("HHmmss")));
   ZonedDateTime local=now.atZone(SEOUL);
   long age=Duration.between(tick.atZone(SEOUL).toInstant(),now).getSeconds();
   return tick.toLocalDate().equals(local.toLocalDate()) && local.getDayOfWeek().getValue()<=5 && age>=-3 && age<=20
    && !tick.toLocalTime().isBefore(LocalTime.of(9,0)) && !tick.toLocalTime().isAfter(LocalTime.of(15,30))
    && ("2".equals(market)||"5".equals(market));
  } catch(Exception e){return false;}
 }
 public static String trigger(boolean owned,double price,double buy,double stop,double take) {
  if(!Double.isFinite(price)||price<=0)return "";
  if(owned){if(stop>0&&price<=stop)return "손절 조건";if(take>0&&price>=take)return "목표가 조건";}
  else if(buy>0&&price>=buy)return "매수 가격 조건";
  return "";
 }
 /** Official H0STCNT0 frames can include multiple records; no fixed final field count. */
 public static List<String[]> records(String frame){
  List<String[]> out=new ArrayList<>();
  String[] head=frame.split("\\|",4);
  if(head.length!=4||!"0".equals(head[0])||!"H0STCNT0".equals(head[1]))return out;
  try {int n=Integer.parseInt(head[2]);String[] fields=head[3].split("\\^",-1);
   if(n<1||fields.length%n!=0||fields.length/n<47)return out;
   int width=fields.length/n;for(int i=0;i<n;i++)out.add(Arrays.copyOfRange(fields,i*width,(i+1)*width));
  }catch(NumberFormatException e){}return out;
 }
}
