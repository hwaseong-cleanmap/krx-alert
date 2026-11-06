package krx.alert;
import android.app.*;
import android.content.*;
import android.os.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;
/** Full universe is enumerated. Failures are counted and excluded, never silently replaced with a shortlist. */
public final class ScanService extends Service {
 private Store store;private NotificationManager nm;private volatile boolean running;private final ExecutorService worker=Executors.newSingleThreadExecutor();
 private void progress(String s){store.prefs.edit().putString("scanStatus",s).putLong("scanHeartbeat",System.currentTimeMillis()).apply();nm.notify(2,notice(s));}
 private Notification notice(String s){PendingIntent open=PendingIntent.getActivity(this,2,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);return new Notification.Builder(this,"scan").setSmallIcon(android.R.drawable.ic_menu_search).setContentTitle("국내 종목 자동 분석").setContentText(s).setContentIntent(open).setOngoing(true).build();}
 @Override public void onCreate(){super.onCreate();store=new Store(this);nm=getSystemService(NotificationManager.class);nm.createNotificationChannel(new NotificationChannel("scan","자료 수집·분석",NotificationManager.IMPORTANCE_LOW));}
 @Override public int onStartCommand(Intent i,int flags,int id){if(running)return START_NOT_STICKY;startForeground(2,notice("종목 목록 수집 준비"));running=true;store.prefs.edit().putBoolean("scanning",true).apply();worker.execute(()->run());return START_NOT_STICKY;}
 private void run(){
  int done=0,errors=0,shortage=0,stale=0;List<JSONObject> candidates=new ArrayList<>();String cutoff=MarketData.cutoff();
  try(MarketData api=new MarketData(this)){
   progress("코스피·코스닥 종목 목록 다운로드");List<MasterParser.Stock> stocks=api.universe();
   progress("기준 거래일 확인");List<ScannerModel.Bar> reference=api.history("005930",cutoff);String asof=reference.get(reference.size()-1).date;
   List<String> calendar=new ArrayList<>();JSONArray cal=new JSONArray();for(ScannerModel.Bar b:reference){calendar.add(b.date);cal.put(b.date);}Collections.sort(calendar);
   store.prefs.edit().putInt("scanTotal",stocks.size()).putInt("scanDone",0).putString("scanTarget",asof).apply();
   for(MasterParser.Stock stock:stocks){
    if(!running||Thread.currentThread().isInterrupted())throw new InterruptedException();
    try{
     List<ScannerModel.Bar> bars=api.cached(stock.code,asof);
     if(bars==null){bars=api.history(stock.code,asof);if(bars.get(bars.size()-1).date.equals(asof))api.save(stock.code,asof,bars);}
     if(bars.size()<61){shortage++;}
     else if(!bars.get(bars.size()-1).date.equals(asof)){stale++;}
     else if(ScannerModel.firstSignal(bars,calendar)){
      ScannerModel.Bar last=bars.get(bars.size()-1);double amount=0;for(int k=bars.size()-20;k<bars.size();k++)amount+=bars.get(k).amount;
      JSONObject x=new JSONObject().put("code",stock.code).put("name",stock.name).put("market",stock.market).put("asof",asof).put("strategy",ScannerModel.VERSION).put("close",last.close).put("r5",ScannerModel.ret(bars,bars.size()-1,5)).put("r20",ScannerModel.ret(bars,bars.size()-1,20)).put("amount20",amount/20).put("reason","5일·20일 각각 -10% 이하, 당일 저가권 마감 · 최초 신호");candidates.add(x);
     }
    }catch(MarketData.AuthException auth){throw auth;}catch(InterruptedException stop){throw stop;}catch(Exception e){errors++;}
    done++;if(done%5==0||done==stocks.size()){store.prefs.edit().putInt("scanDone",done).apply();progress(done+" / "+stocks.size()+" 분석 · 포착 "+candidates.size()+" · 조회오류 "+errors);}
   }
   // No fitted per-stock probability or invented profitability score. Equal B4 evidence, stable code ordering.
   candidates.sort((a,b)->a.optString("code").compareTo(b.optString("code")));JSONArray result=new JSONArray();for(JSONObject x:candidates)result.put(x);
   String report=(errors>0?"부분 완료":"전체 조회 완료")+" · "+done+"종목 · 후보 "+result.length()+" · 오류 "+errors+" · 이력부족 "+shortage+" · 날짜불일치 "+stale;
   if(!running)throw new InterruptedException();
   store.prefs.edit().putString("candidates",result.toString()).putString("scanAsOf",asof).putString("scanCutoff",cutoff).putString("calendar",cal.toString()).putInt("scanErrors",errors).putInt("scanShort",shortage).putInt("scanStale",stale).putLong("scanCompleted",System.currentTimeMillis()).putLong("signalVersion",System.currentTimeMillis()).putBoolean("scanning",false).putString("scanStatus",report).commit();
  }catch(InterruptedException e){store.prefs.edit().putString("scanStatus","분석 중지 · 완료된 일봉은 저장됨 · 다음 실행 시 이어받기").apply();Thread.currentThread().interrupt();}
  catch(Exception e){store.prefs.edit().putString("scanStatus",e instanceof MarketData.AuthException?e.getMessage():"수집 실패 · 통신/API/종목파일 형식 확인 · 이전 결과는 갱신되지 않음").apply();}
  finally{store.prefs.edit().putBoolean("scanning",false).apply();stopSelf();}
 }
 @Override public void onTimeout(int id,int type){store.prefs.edit().putString("scanStatus","Android 실행 한도 · 앱에서 분석 다시 시작 필요").apply();stopSelf();}
 @Override public void onDestroy(){running=false;worker.shutdownNow();store.prefs.edit().putBoolean("scanning",false).apply();stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();}
 @Override public IBinder onBind(Intent i){return null;}
}
