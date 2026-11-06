package krx.alert;
import android.app.*;
import android.content.*;
import android.os.*;
import org.json.*;
import okhttp3.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
public final class WatchService extends Service {
 private Store store; private NotificationManager nm;
 private final ScheduledExecutorService work=Executors.newSingleThreadScheduledExecutor();
 private final OkHttpClient http=new OkHttpClient.Builder().connectTimeout(10,TimeUnit.SECONDS).readTimeout(15,TimeUnit.SECONDS).build();
 private volatile WebSocket socket; private volatile boolean alive,connecting; private volatile long retryAt,lastMessage; private volatile int backoff=5; private String approval=""; private long approvalAt;
 private String listKey=""; private volatile long lastStatusWrite;
 private final Map<String,Long> lastQuoteWrite=new ConcurrentHashMap<>();
 private final Set<String> watching=ConcurrentHashMap.newKeySet();
 @Override public void onCreate(){super.onCreate();store=new Store(this);nm=getSystemService(NotificationManager.class);
  nm.createNotificationChannel(new NotificationChannel("monitor","감시 상태",NotificationManager.IMPORTANCE_LOW));
  nm.createNotificationChannel(new NotificationChannel("signals","장중 가격 조건",NotificationManager.IMPORTANCE_HIGH));
 }
 private PendingIntent open(){return PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);}
 private Notification statusNotification(String s){return new Notification.Builder(this,"monitor").setSmallIcon(android.R.drawable.ic_menu_view).setContentTitle("국내주식 감시").setContentText(s).setContentIntent(open()).setOngoing(true).build();}
 private void status(String s){store.status(s);nm.notify(1,statusNotification(s));}
 @Override public int onStartCommand(Intent i,int flags,int id){
  if(alive)return START_NOT_STICKY;
  startForeground(1,statusNotification("연결 준비"));alive=true;
  work.scheduleWithFixedDelay(()->{try{pulse();}catch(Exception e){status("연결 준비 실패 · 설정 확인");}},0,2,TimeUnit.SECONDS);
  return START_NOT_STICKY;
 }
 private boolean market(){ZonedDateTime t=ZonedDateTime.now(Rules.SEOUL);return t.getDayOfWeek().getValue()<=5&&!t.toLocalTime().isBefore(LocalTime.of(9,0))&&!t.toLocalTime().isAfter(LocalTime.of(15,30));}
 private void pulse()throws Exception {
  if(!alive)return;
  if(!market()){disconnect();status("장외 · 수신 대기 (KRX 09:00~15:30)");return;}
  JSONArray a=store.items();List<String> codes=new ArrayList<>();
  for(int pass=0;pass<2;pass++)for(int j=0;j<a.length();j++){JSONObject x=a.getJSONObject(j);String code=x.getString("code");if(x.optBoolean("owned")== (pass==0)&&!codes.contains(code))codes.add(code);}
  if(codes.isEmpty()){disconnect();status("감시할 종목을 추가하세요");return;}
  if(codes.size()>40){disconnect();status("40종목 초과 · 목록을 줄이세요");return;}
  String next=codes.toString();if(!next.equals(listKey)){disconnect();listKey=next;retryAt=0;}
  long now=System.currentTimeMillis();
  if(socket!=null&&now-lastMessage>90000){disconnect();status("시세 응답 지연 · 재연결 대기");retryAt=now+5000;}
  if(socket==null&&!connecting&&now>=retryAt)connect(codes);
 }
 private void disconnect(){WebSocket old=socket;socket=null;watching.clear();if(old!=null)old.cancel();}
 private void connect(List<String> codes)throws Exception {
  connecting=true;
  try{
   long now=System.currentTimeMillis();
   if(approval.isEmpty()||now-approvalAt>23*3600000L){
    JSONObject cred=store.credentials();JSONObject data=new JSONObject().put("grant_type","client_credentials").put("appkey",cred.getString("app")).put("secretkey",cred.getString("secret"));
    Request request=new Request.Builder().url("https://openapi.koreainvestment.com:9443/oauth2/Approval").post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"),data.toString())).build();
    try(Response response=http.newCall(request).execute()){
     if(!response.isSuccessful()||response.body()==null)throw new Exception("auth");
     approval=new JSONObject(response.body().string()).optString("approval_key");if(approval.isEmpty())throw new Exception("auth");approvalAt=now;
    }
   }
   if(!alive)return;
   final String approvalForConnection=approval;
   lastMessage=now;status("실시간 시세 연결 중");
   socket=http.newWebSocket(new Request.Builder().url("ws://ops.koreainvestment.com:21000").build(),new WebSocketListener(){
    @Override public void onOpen(WebSocket ws,Response response){
     work.execute(()->{try{
      if(!alive||socket!=ws){ws.cancel();return;}
      for(String code:codes){
       JSONObject msg=new JSONObject().put("header",new JSONObject().put("approval_key",approvalForConnection).put("custtype","P").put("tr_type","1").put("content-type","utf-8")).put("body",new JSONObject().put("input",new JSONObject().put("tr_id","H0STCNT0").put("tr_key",code)));
       if(!ws.send(msg.toString()))throw new Exception("send");watching.add(code);Thread.sleep(150);
      }
      backoff=5;status("구독 요청 완료 · 체결 시세 대기 · "+codes.size()+"종목");
     }catch(Exception e){failed(ws);}});
    }
    @Override public void onMessage(WebSocket ws,String text){
     if(!alive||socket!=ws)return;lastMessage=System.currentTimeMillis();
     try {
      if(text.startsWith("{")){
       JSONObject j=new JSONObject(text);if("PINGPONG".equals(j.optJSONObject("header").optString("tr_id"))){ws.send(text);return;}
       JSONObject b=j.optJSONObject("body");if(b!=null&&!"0".equals(b.optString("rt_cd","0"))){status("구독 오류 · API 이용 권한/중복 접속 확인");}
       return;
      }
      List<String[]> records=Rules.records(text);if(records.isEmpty()&&text.startsWith("0|H0STCNT0|"))status("시세 필드 형식 변경 · 알림 중지 · 업데이트 필요");for(String[] f:records)tick(f);
     }catch(Exception e){status("시세 형식 확인 필요 · 해당 메시지 제외");}
    }
    @Override public void onFailure(WebSocket ws,Throwable t,Response r){failed(ws);}
    @Override public void onClosed(WebSocket ws,int code,String reason){failed(ws);}
   });
  }catch(Exception e){approval="";retryAt=System.currentTimeMillis()+60000;status("인증/연결 실패 · 60초 후 재시도 · 키/통신 확인");}
  finally{connecting=false;}
 }
 private void failed(WebSocket ws){if(socket!=ws||!alive)return;socket=null;watching.clear();retryAt=System.currentTimeMillis()+backoff*1000L;backoff=Math.min(60,backoff*2);status("연결 끊김 · 자동 재연결 대기");}
 private void tick(String[] f)throws Exception {
  if(!watching.contains(f[0])||!Rules.fresh(f[33],f[1],f[46],Instant.now()))return;
  double price=Double.parseDouble(f[2]);if(!Double.isFinite(price)||price<=0)return;
  long received=System.currentTimeMillis();
  if(received-lastQuoteWrite.getOrDefault(f[0],0L)>=1000){store.prefs.edit().putLong("price_"+f[0],(long)price).putLong("seen_"+f[0],received).apply();lastQuoteWrite.put(f[0],received);}
  if(received-lastStatusWrite>=3000){store.status("수신 중 · "+f[1]+" · 요청 "+watching.size()+"종목");lastStatusWrite=received;}
  JSONArray a=store.items();for(int n=0;n<a.length();n++){
   JSONObject item=a.getJSONObject(n);if(!f[0].equals(item.optString("code")))continue;
   boolean owned=item.optBoolean("owned");String signal=Rules.trigger(owned,price,item.optDouble("buy",0),item.optDouble("stop",0),item.optDouble("take",0));
   if(!owned&&ScannerModel.VERSION.equals(item.optString("strategy"))&&MarketData.cutoff().equals(store.prefs.getString("scanCutoff",""))&&item.optString("signalDate").equals(store.prefs.getString("scanAsOf",""))&&f[33].compareTo(item.optString("signalDate"))>0)signal="B4 매수 검토 후보 · 첫 시세 수신";
   if(owned&&signal.isEmpty()&&ScannerModel.VERSION.equals(item.optString("strategy"))){
    org.json.JSONArray days=new org.json.JSONArray(store.prefs.getString("calendar","[]"));java.util.List<String> dates=new java.util.ArrayList<>();for(int d=0;d<days.length();d++)dates.add(days.getString(d));
    if(Rules.holdingDue(item.optString("boughtAt"),dates,f[33],f[1]))signal="5거래일 보유 경과 · 청산 검토";
   }
   if(signal.isEmpty())continue;
   String side=owned?"sell":"buy";
   String event=f[33]+":"+side+":"+signal+":"+item.optString("revision");String key="event_"+f[0];
   if(event.equals(store.prefs.getString(key,"")))return;
   if(!signal.equals(store.prefs.getString("signal_"+f[0],"")))store.prefs.edit().putString("signal_"+f[0],signal).putLong("signalVersion",System.currentTimeMillis()).apply();
   if(!store.prefs.getBoolean("alerts",true))return;
   if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)return;
   if(!nm.areNotificationsEnabled())return;
   NotificationChannel channel=nm.getNotificationChannel("signals");if(channel==null||channel.getImportance()==NotificationManager.IMPORTANCE_NONE)return;
   nm.notify(100+Integer.parseInt(f[0]),new Notification.Builder(this,"signals").setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle((owned?"팔 것 확인 · ":"살 것 확인 · ")+item.optString("name",f[0])).setContentText(signal+" · "+(long)price+"원 · "+f[1]+" (주문 아님)").setContentIntent(open()).setAutoCancel(true).build());
   store.prefs.edit().putString(key,event).apply();
  }
 }
 @Override public void onTimeout(int startId,int fgsType){store.status("안드로이드 감시 시간 한도 · 앱에서 재시작 필요");stopSelf();}
 @Override public void onDestroy(){alive=false;disconnect();work.shutdownNow();http.dispatcher().executorService().shutdown();http.connectionPool().evictAll();String s=store.prefs.getString("status","");if(!s.contains("시간 한도"))store.status("감시 중지");stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();}
 @Override public IBinder onBind(Intent i){return null;}
}
