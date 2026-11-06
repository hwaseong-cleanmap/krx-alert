package krx.alert;
import java.util.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
public class ScannerSelfTest {
 static int n;static void check(boolean x){n++;if(!x)throw new AssertionError("scanner check "+n);}
 static List<ScannerModel.Bar> bars(){List<ScannerModel.Bar> b=new ArrayList<>();LocalDate d=LocalDate.of(2026,4,1);for(int i=0;i<100;i++){while(d.getDayOfWeek().getValue()>5)d=d.plusDays(1);b.add(new ScannerModel.Bar(d.format(DateTimeFormatter.BASIC_ISO_DATE),100,101,99,100,1000000,100000000,0));d=d.plusDays(1);}return b;}
 static List<String> dates(List<ScannerModel.Bar>b){List<String> c=new ArrayList<>();for(ScannerModel.Bar x:b)c.add(x.date);return c;}
 static void dip(List<ScannerModel.Bar>b,int i,double close,double change){String d=b.get(i).date;b.set(i,new ScannerModel.Bar(d,100,102,close-1,close,1000000,100000000,change));}
 static String master(String code,String name,String market,int changed,String flag){int[] widths=market.equals("KOSPI")?MasterParser.KOSPI:MasterParser.KOSDAQ;StringBuilder tail=new StringBuilder();for(int i=0;i<widths.length;i++){String v=i==0?"ST":i==changed?flag:"0";tail.append(v);for(int k=v.length();k<widths[i];k++)tail.append(' ');}return String.format("%-9s",code)+"KR7005930003"+name+tail;}
 public static void main(String[] args){
  List<ScannerModel.Bar>b=bars();check(!ScannerModel.b4(b,99));dip(b,99,85,-15);check(ScannerModel.b4(b,99));check(ScannerModel.firstSignal(b,dates(b)));
  b=bars();dip(b,98,85,-15);dip(b,99,85,0);check(!ScannerModel.firstSignal(b,dates(b)));
  b=bars();dip(b,99,90,-10);check(ScannerModel.b4(b,99));
  b=bars();dip(b,99,90.01,-9.99);check(!ScannerModel.b4(b,99));
  b=bars();dip(b,99,85,0);check(!ScannerModel.b4(b,99)); // unexplained corporate price gap
  b=bars();dip(b,99,85,-15);ScannerModel.Bar x=b.get(90);b.set(90,new ScannerModel.Bar(x.date,100,121,99,120,1e6,1e8,20));check(!ScannerModel.b4(b,99));
  b=bars();dip(b,99,85,-15);for(int i=80;i<100;i++){x=b.get(i);b.set(i,new ScannerModel.Bar(x.date,x.open,x.high,x.low,x.close,x.volume,1000,x.change));}check(!ScannerModel.b4(b,99));
  b=bars();dip(b,99,85,-15);x=b.get(99);b.set(99,new ScannerModel.Bar(x.date,100,102,84,85,0,1e8,-15));check(!ScannerModel.b4(b,99));
  check(!ScannerModel.b4(b,49));check(!ScannerModel.firstSignal(b.subList(0,50),dates(b)));
  check(ScannerModel.ordinaryName("삼성전자"));check(!ScannerModel.ordinaryName("삼성전자우"));check(!ScannerModel.ordinaryName("테스트스팩"));check(!ScannerModel.ordinaryName("테스트리츠"));
  check(ScannerModel.target(10000,true)==11000);check(ScannerModel.target(10000,false)==9300);
  for(String market:new String[]{"KOSPI","KOSDAQ"}){
   MasterParser.Stock stock=MasterParser.parse(master("005930","삼성전자",market,-1,""),market);check(stock!=null&&stock.code.equals("005930")&&stock.name.equals("삼성전자"));
   check(MasterParser.parse(master("005935","삼성전자우",market,-1,""),market)==null);
   int halt=market.equals("KOSPI")?34:29;check(MasterParser.parse(master("005930","삼성전자",market,halt,"Y"),market)==null);
   int pref=market.equals("KOSPI")?54:49;check(MasterParser.parse(master("005930","삼성전자",market,pref,"1"),market)==null);
  }
  boolean rejected=false;try{MasterParser.parse("broken","KOSPI");}catch(IllegalArgumentException e){rejected=true;}check(rejected);
  List<String> calendar=Arrays.asList("20261102","20261103","20261104","20261105","20261106");
  check(!Rules.holdingDue("2026-11-02 09:00",calendar,"20261106","100000"));
  check(Rules.holdingDue("2026-11-02 09:00",calendar,"20261106","153000"));
  check(Rules.holdingDue("2026-11-02 09:00",calendar,"20261109","090001"));
  check(!Rules.holdingDue("2026-11-03 09:00",calendar,"20261106","153000"));
  check(!Rules.holdingDue("2026-10-01 09:00",calendar,"20261106","153000"));
  check(!Rules.holdingDue("bad",calendar,"20261106","153000"));
  System.out.println("PASS "+n+" scanner checks");
 }
}
