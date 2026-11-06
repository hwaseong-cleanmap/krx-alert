package krx.alert;
import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.text.InputType;
import android.widget.*;
import org.json.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
public final class MainActivity extends Activity {
 private long signalVersion; private final Map<String,TextView> prices=new HashMap<>();
 private Store store;private LinearLayout root,rows;private TextView state;private int tab;private final Handler timer=new Handler(Looper.getMainLooper());
 private final Runnable update=new Runnable(){public void run(){automatic();if(signalVersion!=store.prefs.getLong("signalVersion",0)){draw();}for(Map.Entry<String,TextView> e:prices.entrySet())e.getValue().setText(priceText(e.getKey()));if(scanState!=null)scanState.setText(scanText());if(state!=null){long age=System.currentTimeMillis()-store.prefs.getLong("statusAt",0);state.setText(store.prefs.getString("status","감시 중지")+(age>120000?" · 상태 갱신 없음":""));}timer.postDelayed(this,3000);}};
 @Override public void onCreate(Bundle b){super.onCreate(b);store=new Store(this);draw();timer.postDelayed(()->automatic(),500);}
 @Override public void onResume(){super.onResume();timer.post(update);}
 @Override public void onPause(){timer.removeCallbacks(update);super.onPause();}
 private void text(LinearLayout l,String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setPadding(0,12,0,12);l.addView(t);}
 private void button(LinearLayout l,String title,Runnable action){Button b=new Button(this);b.setText(title);l.addView(b);b.setOnClickListener(v->action.run());}
 private LinearLayout line(LinearLayout l){LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);l.addView(r);return r;}
 private String priceText(String code){long seen=store.prefs.getLong("seen_"+code,0);return (seen==0?"시세 미수신":String.format(Locale.KOREA,"%,.0f원",(double)store.prefs.getLong("price_"+code,0)))+(System.currentTimeMillis()-seen>20000?" · 최신 여부 확인 필요":" · 최근 수신");}
 private TextView scanState; private boolean autoAttempted;private String autoCutoff="";
 private JSONObject find(String code){JSONArray a=store.items();for(int i=0;i<a.length();i++)if(code.equals(a.optJSONObject(i).optString("code")))return a.optJSONObject(i);return null;}
 private JSONArray candidates(){try{return new JSONArray(store.prefs.getString("candidates","[]"));}catch(Exception e){return new JSONArray();}}
 private boolean currentScan(){return MarketData.cutoff().equals(store.prefs.getString("scanCutoff",""));}
 private void collect(){
  if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){permission();toast("알림 허용 후 자료 갱신을 눌러주세요");return;}
  try{store.credentials();startForegroundService(new Intent(this,ScanService.class));toast("전체 종목 자동 수집·분석 시작");}catch(Exception e){toast("실전 API 키를 설정한 뒤 다시 실행하세요");}
 }
 private void automatic(){if(autoAttempted&&autoCutoff.equals(MarketData.cutoff()))return;autoAttempted=true;autoCutoff=MarketData.cutoff();if(!currentScan()){try{store.credentials();collect();}catch(Exception e){}}}
 private void register(JSONObject candidate){
  try{
   if(!currentScan()){toast("먼저 자료 갱신을 완료하세요. 이전 날짜의 후보입니다.");return;}
   String code=candidate.getString("code");JSONObject existing=find(code);
   if(existing!=null){toast(existing.optBoolean("owned")?"이미 보유·감시 중인 종목입니다":"이미 감시 목록에 있습니다");return;}
   JSONObject x=new JSONObject().put("code",code).put("name",candidate.getString("name")).put("strategy",candidate.getString("strategy")).put("signalDate",candidate.getString("asof")).put("owned",false).put("buy",0).put("stop",0).put("take",0).put("revision",UUID.randomUUID().toString());
   upsert(x,true);clear(code);start();toast("감시 등록 완료 · 실제 매수 후 ‘샀어요’를 눌러주세요");draw();
  }catch(Exception e){toast("등록 실패: "+e.getMessage());}
 }
 private void candidateDetail(JSONObject c){
  register(c);
 }
 private void draw(){
  prices.clear();signalVersion=store.prefs.getLong("signalVersion",0);
  root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(20,24,20,10);setContentView(root);
  text(root,"국내주식 자동 포착",24);state=new TextView(this);state.setText(store.prefs.getString("status","감시 중지"));root.addView(state);
  scanState=new TextView(this);scanState.setText(scanText());root.addView(scanState);
  Switch sw=new Switch(this);sw.setText("장중 신호 알림");sw.setChecked(store.prefs.getBoolean("alerts",true));root.addView(sw);sw.setOnCheckedChangeListener((v,on)->{store.prefs.edit().putBoolean("alerts",on).apply();if(on)permission();});
  LinearLayout controls=line(root);compactButton(controls,"자료 갱신",()->collect());compactButton(controls,"감시 시작",()->start());compactButton(controls,"설정",()->settings());
  LinearLayout controls2=line(root);compactButton(controls2,"수집 중지",()->{stopService(new Intent(this,ScanService.class));store.prefs.edit().putString("scanStatus","수집 중지 · 다음 실행 시 저장 자료 활용").apply();});compactButton(controls2,"감시 중지",()->{stopService(new Intent(this,WatchService.class));store.status("감시 중지");draw();});
  LinearLayout tabs=line(root);String[] titles={"살 것","감시","팔 것","보유"};for(int n=0;n<4;n++){final int which=n;compactButton(tabs,titles[n],()->{tab=which;draw();});}
  ScrollView scroll=new ScrollView(this);rows=new LinearLayout(this);rows.setOrientation(LinearLayout.VERTICAL);scroll.addView(rows);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));int count=0;
  if(tab==0){
   text(rows,"B4 단기급락형 · 연구 후보\n검증된 수익 전략 없음 · 예상 확률 미표시",13);
   JSONArray cs=candidates();for(int n=0;n<cs.length();n++){JSONObject c=cs.optJSONObject(n);if(c==null)continue;count++;
    LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(12,12,12,22);rows.addView(card);
    text(card,c.optString("name")+" ("+c.optString("code")+")",20);
    text(card,"확정일 "+c.optString("asof")+" · 종가 "+c.optLong("close")+"원",15);
    text(card,String.format(Locale.KOREA,"5일 %.1f%% · 20일 %.1f%%",c.optDouble("r5"),c.optDouble("r20")),15);
    String code=c.optString("code");JSONObject item=find(code);String label=item==null?"눌러서 감시 등록":item.optBoolean("owned")?"보유·감시 중":"감시 등록됨";
    button(card,label,()->candidateDetail(c));card.setOnClickListener(v->candidateDetail(c));
    if(item==null||!item.optBoolean("owned"))button(card,"샀어요",()->{if(!currentScan()){toast("먼저 자료를 갱신하세요");return;}JSONObject before=find(code);if(before==null){register(c);before=find(code);}if(before!=null)bought(before);});
   }
   if(count==0)text(rows,"자동 분석 결과가 없거나 조건에 맞는 후보가 없습니다.\n위의 자료 기준일·수집 상태를 확인하세요.",16);
  }else{
   JSONArray a=store.items();for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x==null)continue;boolean owned=x.optBoolean("owned");String code=x.optString("code"),signal=store.prefs.getString("signal_"+code,"");
    if(tab>=2&&!owned||tab==2&&signal.isEmpty())continue;count++;
    LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(8,12,8,20);rows.addView(card);text(card,x.optString("name")+" ("+code+")",20);
    if(tab==3){text(card,"매수 "+x.optString("boughtAt")+"\n"+x.optLong("boughtPrice")+"원",17);button(card,"팔았어요",()->confirm("보유 기록을 종료할까요?",()->remove(code)));button(card,"매도 조건",()->edit(x));}
    else{
     TextView live=new TextView(this);live.setTextSize(17);live.setText(priceText(code));card.addView(live);prices.put(code,live);
     if(!signal.isEmpty())text(card,"포착 기록: "+signal+"\n현재 조건은 새 체결 수신으로 확인하세요.",14);
     if(owned)text(card,"손절 "+x.optLong("stop")+" / 목표 "+x.optLong("take"),15);
     else{text(card,x.optString("strategy").isEmpty()?"직접 지정 가격 감시":"B4 확정 후보 · 다음 거래일 첫 시세 알림",14);button(card,"샀어요",()->bought(x));}
     button(card,"조건 수정",()->edit(x));if(!owned)button(card,"감시 삭제",()->confirm("감시 종목을 삭제할까요?",()->remove(code)));
    }
   }
   if(count==0)text(rows,tab==1?"‘살 것’에서 후보를 누르면 자동 등록됩니다.":tab==2?"보유 종목의 매도 조건 포착 기록이 없습니다.":"실제 매수 후 ‘샀어요’로 기록하세요.",16);
   if(tab==1)button(rows,"직접 추가 (보조)",()->edit(null));
  }
  text(root,"자동 주문 없음 · B4는 과거 표본 우선 후보\n실전 우수성 미확인 · 매수 기록은 직접 확인",11);
 }
 private void compactButton(LinearLayout parent,String label,Runnable r){Button b=new Button(this);b.setText(label);b.setTextSize(13);b.setPadding(1,0,1,0);parent.addView(b,new LinearLayout.LayoutParams(0,-2,1));b.setOnClickListener(v->r.run());}
 private String scanText(){String date=store.prefs.getString("scanAsOf","");return "자료 기준: "+(date.isEmpty()?"미수집":date)+(currentScan()?"":" · 갱신 필요")+"\n"+store.prefs.getString("scanStatus","앱을 열면 자동 수집 · 최초에는 시간이 걸립니다");}
 private void permission(){if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},10);}
 private void start(){
  if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){permission();toast("알림 허용 후 감시 시작을 다시 눌러주세요");return;}
  try{store.credentials();}catch(Exception e){toast("한국투자 API 키를 먼저 설정하세요");settings();return;}
  if(store.items().length()==0){toast("감시 종목을 추가하세요");return;}
  try{startForegroundService(new Intent(this,WatchService.class));toast("감시 시작 요청");}catch(Exception e){toast("감시를 시작하지 못했습니다. 앱을 다시 열어 확인하세요.");}
 }
 private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
 private void confirm(String s,Runnable r){new AlertDialog.Builder(this).setMessage(s).setPositiveButton("확인",(d,w)->r.run()).setNegativeButton("취소",null).show();}
 private EditText input(LinearLayout form,String hint,String value,boolean number){EditText e=new EditText(this);e.setHint(hint);e.setText(value);e.setInputType(number?InputType.TYPE_CLASS_NUMBER:InputType.TYPE_CLASS_TEXT);form.addView(e);return e;}
 private interface Save {void run()throws Exception;}
 private void form(String title,LinearLayout fields,Save save){AlertDialog d=new AlertDialog.Builder(this).setTitle(title).setView(fields).setPositiveButton("저장",null).setNegativeButton("취소",null).create();d.setOnShowListener(v->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b->{try{save.run();d.dismiss();draw();}catch(Exception e){toast("입력 확인: "+e.getMessage());}}));d.show();}
 private LinearLayout formBox(){LinearLayout f=new LinearLayout(this);f.setOrientation(LinearLayout.VERTICAL);f.setPadding(24,4,24,4);return f;}
 private long amount(EditText e,boolean zero)throws Exception{String v=e.getText().toString().trim();long a=v.isEmpty()?0:Long.parseLong(v);if(a<0||(!zero&&a==0))throw new Exception("가격은 양수로 입력하세요");return a;}
 private void edit(JSONObject original){
  LinearLayout f=formBox();boolean owned=original!=null&&original.optBoolean("owned");
  EditText code=input(f,"종목코드 6자리",original==null?"":original.optString("code"),true);code.setEnabled(original==null);
  EditText name=input(f,"종목명",original==null?"":original.optString("name"),false);
  EditText buy=input(f,"매수 감시가: 이상이면 포착",original==null?"":original.optString("buy"),true);buy.setVisibility(owned||(original!=null&&!original.optString("strategy").isEmpty())?android.view.View.GONE:android.view.View.VISIBLE);
  EditText stop=input(f,"손절가 (선택, 0=끄기)",original==null?"":original.optString("stop"),true);
  EditText take=input(f,"목표가 (선택, 0=끄기)",original==null?"":original.optString("take"),true);
  text(f,"조건은 직접 지정합니다. 수익성이 검증된 전략이 아닙니다.",12);
  form("감시 조건",f,()->{
   String c=code.getText().toString().trim();if(!c.matches("[0-9]{6}"))throw new Exception("6자리 종목코드");
   String n=name.getText().toString().trim();if(n.isEmpty())throw new Exception("종목명");
   long b=amount(buy,owned||(original!=null&&!original.optString("strategy").isEmpty())),s=amount(stop,true),t=amount(take,true);if(s>0&&t>0&&s>=t)throw new Exception("손절가는 목표가보다 낮아야 합니다");
   JSONObject x=original==null?new JSONObject():new JSONObject(original.toString());x.put("code",c).put("name",n).put("buy",b).put("stop",s).put("take",t).put("revision",UUID.randomUUID().toString());upsert(x,original==null);clear(c);
  });
 }
 private void bought(JSONObject original){LinearLayout f=formBox();EditText price=input(f,"실제 매수 가격","",true);EditText when=input(f,"매수 시각 yyyy-MM-dd HH:mm",LocalDateTime.now(Rules.SEOUL).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),false);
  form("샀어요 · 직접 기록",f,()->{long p=amount(price,false);String at=when.getText().toString().trim();LocalDateTime.parse(at,DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm").withResolverStyle(ResolverStyle.STRICT));JSONObject current=find(original.getString("code"));if(current==null)throw new Exception("감시 등록이 해제된 종목입니다");JSONObject x=new JSONObject(current.toString());if(!x.optString("strategy").isEmpty())x.put("stop",ScannerModel.target(p,false)).put("take",ScannerModel.target(p,true));x.put("owned",true).put("boughtPrice",p).put("boughtAt",at).put("revision",UUID.randomUUID().toString());upsert(x,false);clear(x.getString("code"));start();});
 }
 private void upsert(JSONObject x,boolean fresh)throws Exception{JSONArray a=store.items(),out=new JSONArray();boolean found=false;for(int n=0;n<a.length();n++){JSONObject y=a.getJSONObject(n);if(y.getString("code").equals(x.getString("code"))){if(fresh)throw new Exception("이미 등록된 종목");out.put(x);found=true;}else out.put(y);}if(!found){if(a.length()>=40)throw new Exception("최대 40종목");out.put(x);}store.save(out);}
 private void clear(String code){store.prefs.edit().remove("signal_"+code).remove("event_"+code).apply();}
 private void remove(String code){JSONArray a=store.items(),out=new JSONArray();for(int i=0;i<a.length();i++)if(!code.equals(a.optJSONObject(i).optString("code")))out.put(a.optJSONObject(i));store.save(out);clear(code);draw();}
 private void settings(){
  LinearLayout f=formBox();text(f,"한국투자 실전 OpenAPI 키\n키는 휴대폰 암호화 저장. 채팅에 보내지 마세요.\n키 변경 시 감시는 중지됩니다.",14);
  button(f,"선정 조건·연구 결과",()->new AlertDialog.Builder(this).setTitle("B4 연구 후보").setMessage("5일·20일 수익률 각각 -10% 이하, 종가 일중위치 0.5 이하. 20일 평균 거래대금 5천만원 이상, 사전 급등 등 제외.\n2024~2026 일별 비교: B4 평균 가상 순수익 +0.615%, B6 +0.290%, B5 -0.174%. B4의 2026년은 -0.142%.\n다음 시가 매수·익절10%·손절7%·최대5거래일·비용0.40%p 가정. 실전 우수성 검증 통과 없음. 개별 종목 예상 수익·확률이 아닙니다.").setPositiveButton("확인",null).show());
  EditText app=input(f,"AppKey","",false),secret=input(f,"AppSecret","",false);app.setInputType(129);secret.setInputType(129);
  text(f,"서버 없이 폰에서 시세 연결. 공식 실시간 주소는 WS(평문)이며 접속 승인키가 이 연결에 사용됩니다. AppSecret은 HTTPS 인증에만 전송합니다.\nAndroid 15 이상: 백그라운드 dataSync 감시 시간 한도 적용. 하루 장 전체의 무중단 알림은 보장하지 않습니다.\n알림 OFF여도 감시 상태 알림은 남습니다.",12);
  form("연결 설정",f,()->{String k=app.getText().toString().trim(),s=secret.getText().toString().trim();if(k.isEmpty()||s.isEmpty())throw new Exception("두 키를 입력하세요");stopService(new Intent(this,WatchService.class));stopService(new Intent(this,ScanService.class));store.credentials(k,s);autoAttempted=false;timer.postDelayed(()->automatic(),500);});
 }
}
