package krx.alert;
import java.util.*;
/** Frozen B4 research definition. Historical sample leader, NOT validated advice. */
public final class ScannerModel {
 public static final String VERSION="B4-daily-v1";
 public static final double TAKE=1.10, STOP=.93;
 public static final class Bar {
  public final String date;public final double open,high,low,close,volume,amount,change;
  public Bar(String d,double o,double h,double l,double c,double v,double a,double change){date=d;open=o;high=h;low=l;close=c;volume=v;amount=a;this.change=change;}
  public boolean valid(){return Double.isFinite(open)&&Double.isFinite(high)&&Double.isFinite(low)&&Double.isFinite(close)&&Double.isFinite(volume)&&open>0&&low>0&&close>0&&high>=Math.max(open,close)&&low<=Math.min(open,close)&&Double.isFinite(amount)&&amount>=0&&volume>=0;}
 }
 public static double ret(List<Bar> b,int i,int n){return (b.get(i).close/b.get(i-n).close-1)*100;}
 public static boolean eligible(List<Bar> b,int i){
  if(i<50||!b.get(i).valid()||b.get(i).volume<=0||b.get(i).amount<=0)return false;
  double amount=0;
  for(int j=i-19;j<=i;j++){
   Bar x=b.get(j);if(!x.valid())return false;amount+=x.amount;
   double actual=(x.close/b.get(j-1).close-1)*100;
   if(!Double.isFinite(x.change)||Math.abs(actual-x.change)>9&&Math.abs(actual)>9)return false;
  }
  if(amount/20<50000000)return false;
  for(int j=i-20;j<=i;j++)if(!Double.isFinite(b.get(j).change)||b.get(j).change>=15)return false;
  return true;
 }
 public static boolean b4(List<Bar> b,int i){
  if(!eligible(b,i))return false;Bar x=b.get(i);
  double clv=x.high==x.low?.5:(x.close-x.low)/(x.high-x.low);
  return ret(b,i,5)<=-10+1e-9&&ret(b,i,20)<=-10+1e-9&&clv<=.5;
 }
 /** Align dedup to an observed clean ten-session gap; otherwise history is insufficient. */
 public static boolean firstSignal(List<Bar> b,List<String> calendar){
  int quiet=0,anchor=-1;for(int i=50;i<b.size();i++){quiet=b4(b,i)?0:quiet+1;if(quiet>=10)anchor=i;}
  if(anchor<0||anchor>=b.size()-1)return false;
  int last=-100000;boolean current=false;
  for(int i=anchor+1;i<b.size();i++)if(b4(b,i)){
   int seq=Collections.binarySearch(calendar,b.get(i).date);if(seq<0)return false;
   if(seq-last>=10){last=seq;current=i==b.size()-1;}
  }
  return current;
 }
 public static boolean ordinaryName(String name){return !name.matches("(?i).*(우$|우B$|우C$|우[0-9]+$|우\\(전환\\)|스팩|SPAC|리츠|ETF|ETN).*");}
 public static long target(double price,boolean take){return take?(long)Math.ceil(price*TAKE-1e-8):(long)Math.floor(price*STOP+1e-8);}
}
