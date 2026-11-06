package krx.alert;
import java.util.*;
public final class MasterParser {
 public static final int[] KOSPI={2,1,4,4,4,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,9,5,5,1,1,1,2,1,1,1,2,2,2,3,1,3,12,12,8,15,21,2,7,1,1,1,1,1,9,9,9,5,9,8,9,3,1,1,1};
 public static final int[] KOSDAQ={2,1,4,4,4,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,9,5,5,1,1,1,2,1,1,1,2,2,2,3,1,3,12,12,8,15,21,2,7,1,1,1,1,9,9,9,5,9,8,9,3,1,1,1};
 public static final class Stock {public final String code,name,market;Stock(String c,String n,String m){code=c;name=n;market=m;}}
 public static Stock parse(String line,String market){
  int[] widths=market.equals("KOSPI")?KOSPI:KOSDAQ;int total=0;for(int v:widths)total+=v;
  if(line.length()<total+22)throw new IllegalArgumentException("master schema");
  String prefix=line.substring(0,line.length()-total),tail=line.substring(line.length()-total);
  String code=prefix.substring(0,9).trim(),name=prefix.substring(21).trim();
  String[] f=new String[widths.length];int off=0;for(int j=0;j<widths.length;j++){f[j]=tail.substring(off,off+widths[j]).trim();off+=widths[j];}
  if(!code.matches("[0-9]{6}")||!"ST".equals(f[0])||name.isEmpty()||!ScannerModel.ordinaryName(name))return null;
  int halt=market.equals("KOSPI")?34:29, liquidate=halt+1, management=halt+2;
  int preferred=market.equals("KOSPI")?54:49,spac=market.equals("KOSPI")?19:14;
  if("Y".equals(f[halt])||"Y".equals(f[liquidate])||"Y".equals(f[management])||"Y".equals(f[spac]))return null;
  if(!f[preferred].isEmpty()&&!"0".equals(f[preferred])&&!"N".equals(f[preferred]))return null;
  return new Stock(code,name,market);
 }
}
