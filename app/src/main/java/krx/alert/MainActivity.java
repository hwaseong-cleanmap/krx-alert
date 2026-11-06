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
 private final Runnable update=new Runnable(){public void run(){if(signalVersion!=store.prefs.getLong("signalVersion",0)){draw();}for(Map.Entry<String,TextView> e:prices.entrySet())e.getValue().setText(priceText(e.getKey()));if(state!=null){long age=System.currentTimeMillis()-store.prefs.getLong("statusAt",0);state.setText(store.prefs.getString("status","감시 중지")+(age>120000?" · 상태 갱신 없음":""));}timer.postDelayed(this,3000);}};
 @Override public void onCreate(Bundle b){super.onCreate(b);store=new Store(this);draw();}
 @Override public void onResume(){super.onResume();timer.post(update);}
 @Override public void onPause(){timer.removeCallbacks(update);super.onPause();}
 private void text(LinearLayout l,String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setPadding(0,12,0,12);l.addView(t);}
 private void button(LinearLayout l,String title,Runnable action){Button b=new Button(this);b.setText(title);l.addView(b);b.setOnClickListener(v->action.run());}
 private LinearLayout line(LinearLayout l){LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);l.addView(r);return r;}
 private String priceText(String code){long seen=store.prefs.getLong("seen_"+code,0);return (seen==0?"시세 미수신":String.format(Locale.KOREA,"%,.0f원",(double)store.prefs.getLong("price_"+code,0)))+(System.currentTimeMillis()-seen>20000?" · 최신 여부 확인 필요":" · 최근 수신");}
 private void draw(){
  prices.clear();signalVersion=store.prefs.getLong("signalVersion",0);
  root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(24,24,24,12);setContentView(root);
  text(root,"국내주식 포착",25);state=new TextView(this);state.setText(store.prefs.getString("status","감시 중지"));root.addView(state);
  Switch sw=new Switch(this);sw.setText("장중 신호 알림");sw.setChecked(store.prefs.getBoolean("alerts",true));root.addView(sw);sw.setOnCheckedChangeListener((v,on)->{store.prefs.edit().putBoolean("alerts",on).apply();if(on)permission();});
  LinearLayout controls=line(root);button(controls,"감시 시작",()->start());button(controls,"중지",()->{stopService(new Intent(this,WatchService.class));store.status("감시 중지");draw();});button(controls,"설정",()->settings());
  LinearLayout tabs=line(root);String[] titles={"살 것","팔 것","보유"};for(int n=0;n<3;n++){final int which=n;button(tabs,titles[n],()->{tab=which;draw();});}
  if(tab==0)button(root,"+ 감시 종목",()->edit(null));
  ScrollView scroll=new ScrollView(this);rows=new LinearLayout(this);rows.setOrientation(LinearLayout.VERTICAL);scroll.addView(rows);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
  int count=0;JSONArray a=store.items();for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x==null)continue;boolean owned=x.optBoolean("owned");String code=x.optString("code"),signal=store.prefs.getString("signal_"+code,"");
   if((tab==0&&owned)||(tab>0&&!owned)||(tab==1&&signal.isEmpty()))continue;count++;
   LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(8,12,8,20);rows.addView(card);
   text(card,x.optString("name")+" ("+code+")",20);
   if(tab==2){text(card,"매수 "+x.optString("boughtAt")+"\n"+x.optLong("boughtPrice")+"원",17);button(card,"팔았어요",()->confirm("보유 기록을 종료할까요?",()->remove(code)));button(card,"매도 조건",()->edit(x));}
   else{
    long seen=store.prefs.getLong("seen_"+code,0);String price=seen==0?"시세 미수신":String.format(Locale.KOREA,"%,.0f원",(double)store.prefs.getLong("price_"+code,0));
    TextView live=new TextView(this);live.setTextSize(17);live.setText(priceText(code));card.addView(live);prices.put(code,live);
    if(!signal.isEmpty())text(card,"포착 기록: "+signal+"\n현재 조건은 감시 중 새 체결로 확인하세요.",15);
    text(card,owned?"손절 "+x.optLong("stop")+" / 목표 "+x.optLong("take"):"매수 감시가 "+x.optLong("buy")+"원 이상",15);
    if(!owned)button(card,"샀어요",()->bought(x));
    button(card,"조건 수정",()->edit(x));if(!owned)button(card,"삭제",()->confirm("감시 종목을 삭제할까요?",()->remove(code)));
   }
  }
  if(count==0)text(rows,tab==0?"감시할 종목과 가격을 추가하세요.":tab==1?"보유 종목의 매도 조건 포착 기록이 없습니다.":"매수 기록이 없습니다.",17);
  text(root,"가격 조건 시험판 · 예측 확률 미제공\n자동 주문 없음 · 감시 상태를 확인하세요",12);
 }
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
  EditText buy=input(f,"매수 감시가: 이상이면 포착",original==null?"":original.optString("buy"),true);buy.setVisibility(owned?android.view.View.GONE:android.view.View.VISIBLE);
  EditText stop=input(f,"손절가 (선택, 0=끄기)",original==null?"":original.optString("stop"),true);
  EditText take=input(f,"목표가 (선택, 0=끄기)",original==null?"":original.optString("take"),true);
  text(f,"조건은 직접 지정합니다. 수익성이 검증된 전략이 아닙니다.",12);
  form("감시 조건",f,()->{
   String c=code.getText().toString().trim();if(!c.matches("[0-9]{6}"))throw new Exception("6자리 종목코드");
   String n=name.getText().toString().trim();if(n.isEmpty())throw new Exception("종목명");
   long b=amount(buy,owned),s=amount(stop,true),t=amount(take,true);if(s>0&&t>0&&s>=t)throw new Exception("손절가는 목표가보다 낮아야 합니다");
   JSONObject x=original==null?new JSONObject():new JSONObject(original.toString());x.put("code",c).put("name",n).put("buy",b).put("stop",s).put("take",t).put("revision",UUID.randomUUID().toString());upsert(x,original==null);clear(c);
  });
 }
 private void bought(JSONObject original){LinearLayout f=formBox();EditText price=input(f,"실제 매수 가격","",true);EditText when=input(f,"매수 시각 yyyy-MM-dd HH:mm",LocalDateTime.now(Rules.SEOUL).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),false);
  form("샀어요 · 직접 기록",f,()->{long p=amount(price,false);String at=when.getText().toString().trim();LocalDateTime.parse(at,DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm").withResolverStyle(ResolverStyle.STRICT));JSONObject x=new JSONObject(original.toString());x.put("owned",true).put("boughtPrice",p).put("boughtAt",at).put("revision",UUID.randomUUID().toString());upsert(x,false);clear(x.getString("code"));});
 }
 private void upsert(JSONObject x,boolean fresh)throws Exception{JSONArray a=store.items(),out=new JSONArray();boolean found=false;for(int n=0;n<a.length();n++){JSONObject y=a.getJSONObject(n);if(y.getString("code").equals(x.getString("code"))){if(fresh)throw new Exception("이미 등록된 종목");out.put(x);found=true;}else out.put(y);}if(!found){if(a.length()>=40)throw new Exception("최대 40종목");out.put(x);}store.save(out);}
 private void clear(String code){store.prefs.edit().remove("signal_"+code).remove("event_"+code).apply();}
 private void remove(String code){JSONArray a=store.items(),out=new JSONArray();for(int i=0;i<a.length();i++)if(!code.equals(a.optJSONObject(i).optString("code")))out.put(a.optJSONObject(i));store.save(out);clear(code);draw();}
 private void settings(){
  LinearLayout f=formBox();text(f,"한국투자 실전 OpenAPI 키\n키는 휴대폰 암호화 저장. 채팅에 보내지 마세요.\n키 변경 시 감시는 중지됩니다.",14);
  EditText app=input(f,"AppKey","",false),secret=input(f,"AppSecret","",false);app.setInputType(129);secret.setInputType(129);
  text(f,"서버 없이 폰에서 시세 연결. 공식 실시간 주소는 WS(평문)이며 접속 승인키가 이 연결에 사용됩니다. AppSecret은 HTTPS 인증에만 전송합니다.\nAndroid 15 이상: 백그라운드 dataSync 감시 시간 한도 적용. 하루 장 전체의 무중단 알림은 보장하지 않습니다.\n알림 OFF여도 감시 상태 알림은 남습니다.",12);
  form("연결 설정",f,()->{String k=app.getText().toString().trim(),s=secret.getText().toString().trim();if(k.isEmpty()||s.isEmpty())throw new Exception("두 키를 입력하세요");stopService(new Intent(this,WatchService.class));store.credentials(k,s);});
 }
}
