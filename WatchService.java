package krx.alert;
import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import org.json.*;
import okhttp3.*;
import java.io.*;
import java.nio.charset.Charset;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.*;
/** KIS credentials remain on device. Serial capped requests; all schema failures are explicit. */
public final class MarketData implements AutoCloseable {
 private final Store store;private final SQLiteDatabase db;private String token="";
 private final OkHttpClient http=new OkHttpClient.Builder().connectTimeout(15,TimeUnit.SECONDS).readTimeout(30,TimeUnit.SECONDS).callTimeout(45,TimeUnit.SECONDS).build();
 private long lastRequest,tokenExpiry;
 private static final String BASE="https://openapi.koreainvestment.com:9443";
 public static final class AuthException extends IOException {AuthException(){super("API 인증 실패 · 실전 키와 이용 권한 확인");}}
 public MarketData(Context c){store=new Store(c);db=c.openOrCreateDatabase("scanner.db",0,null);db.execSQL("CREATE TABLE IF NOT EXISTS daily(code TEXT PRIMARY KEY, asof TEXT, payload TEXT)");}
 private void pace()throws InterruptedException{long wait=400-(android.os.SystemClock.elapsedRealtime()-lastRequest);if(wait>0)Thread.sleep(wait);lastRequest=android.os.SystemClock.elapsedRealtime();}
 private JSONObject request(Request r)throws Exception {
  for(int i=0;i<3;i++){
   if(Thread.currentThread().isInterrupted())throw new InterruptedException();pace();
   try(Response response=http.newCall(r).execute()){
    if(response.code()==401||response.code()==403)throw new AuthException();
    if(response.code()==429||response.code()>=500){if(i==2)throw new IOException("서버 응답 지연");Thread.sleep((i+1)*1500);continue;}
    if(!response.isSuccessful()||response.body()==null)throw new IOException("자료 응답 오류 HTTP "+response.code());
    JSONObject j=new JSONObject(response.body().string());String error=j.optString("msg_cd");
    if(error.startsWith("EGW001")||error.equals("EGW00004"))throw new AuthException();
    if(error.equals("EGW00201")){if(i==2)throw new IOException("API 호출 제한");Thread.sleep((i+1)*1500);continue;}
    return j;
   }
  }throw new IOException("자료 조회 실패");
 }
 private synchronized String token()throws Exception {
  if(!token.isEmpty()&&tokenExpiry>System.currentTimeMillis()+60000)return token;
  JSONObject cred=store.credentials();long expiry=cred.optLong("tokenExpiry");
  if(expiry>System.currentTimeMillis()+60000&&!cred.optString("token").isEmpty()){token=cred.getString("token");tokenExpiry=expiry;return token;}
  JSONObject payload=new JSONObject().put("grant_type","client_credentials").put("appkey",cred.getString("app")).put("appsecret",cred.getString("secret"));
  JSONObject result=request(new Request.Builder().url(BASE+"/oauth2/tokenP").post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"),payload.toString())).build());
  token=result.optString("access_token");long life=result.optLong("expires_in");if(token.isEmpty()||life<=0)throw new AuthException();
  tokenExpiry=System.currentTimeMillis()+life*1000;cred.put("token",token).put("tokenExpiry",tokenExpiry);store.saveCredentials(cred);return token;
 }
 public static String cutoff(){ZonedDateTime now=ZonedDateTime.now(Rules.SEOUL);LocalDate d=now.toLocalDate();if(now.toLocalTime().isBefore(LocalTime.of(16,0)))d=d.minusDays(1);return d.format(DateTimeFormatter.BASIC_ISO_DATE);}
 public List<ScannerModel.Bar> history(String code,String end)throws Exception {
  String start=LocalDate.parse(end,DateTimeFormatter.BASIC_ISO_DATE).minusDays(220).format(DateTimeFormatter.BASIC_ISO_DATE);
  JSONObject cred=store.credentials();HttpUrl url=HttpUrl.parse(BASE+"/uapi/domestic-stock/v1/quotations/inquire-daily-itemchartprice").newBuilder().addQueryParameter("FID_COND_MRKT_DIV_CODE","J").addQueryParameter("FID_INPUT_ISCD",code).addQueryParameter("FID_INPUT_DATE_1",start).addQueryParameter("FID_INPUT_DATE_2",end).addQueryParameter("FID_PERIOD_DIV_CODE","D").addQueryParameter("FID_ORG_ADJ_PRC","1").build();
  Request r=new Request.Builder().url(url).header("authorization","Bearer "+token()).header("appkey",cred.getString("app")).header("appsecret",cred.getString("secret")).header("tr_id","FHKST03010100").header("custtype","P").build();
  JSONObject j=request(r);if(!"0".equals(j.optString("rt_cd")))throw new IOException("일봉 조회 거절 · 코드/서비스 확인");
  JSONArray raw=j.getJSONArray("output2");TreeMap<String,ScannerModel.Bar> sorted=new TreeMap<>();
  for(int n=0;n<raw.length();n++){JSONObject x=raw.getJSONObject(n);String date=x.optString("stck_bsop_date");if(date.isEmpty())continue;LocalDate.parse(date,DateTimeFormatter.BASIC_ISO_DATE);if(date.compareTo(end)>0)continue;
   ScannerModel.Bar b=new ScannerModel.Bar(date,x.getDouble("stck_oprc"),x.getDouble("stck_hgpr"),x.getDouble("stck_lwpr"),x.getDouble("stck_clpr"),x.getDouble("acml_vol"),x.getDouble("acml_tr_pbmn"),reportedChange(x));sorted.put(date,b);
  }
  if(sorted.isEmpty())throw new IOException("일봉 자료 없음");return new ArrayList<>(sorted.values());
 }
 private static double reportedChange(JSONObject x)throws Exception {
  if(x.has("prdy_ctrt")&&!x.optString("prdy_ctrt").isEmpty())return x.getDouble("prdy_ctrt");
  double close=x.getDouble("stck_clpr"),diff=Math.abs(x.getDouble("prdy_vrss"));String sign=x.getString("prdy_vrss_sign");
  if(sign.equals("4")||sign.equals("5"))diff=-diff;else if(sign.equals("3"))diff=0;else if(!sign.equals("1")&&!sign.equals("2"))throw new java.io.IOException("등락 부호 형식");
  double previous=close-diff;if(previous<=0)throw new java.io.IOException("비교 기준가 오류");return diff/previous*100;
 }
 public List<ScannerModel.Bar> cached(String code,String target)throws Exception {
  try(Cursor c=db.rawQuery("SELECT payload FROM daily WHERE code=? AND asof=?",new String[]{code,target})){if(!c.moveToFirst())return null;return decode(new JSONArray(c.getString(0)));}
 }
 public void save(String code,String target,List<ScannerModel.Bar> bars)throws Exception {ContentValues v=new ContentValues();v.put("code",code);v.put("asof",target);JSONArray a=new JSONArray();for(ScannerModel.Bar b:bars)a.put(new JSONArray().put(b.date).put(b.open).put(b.high).put(b.low).put(b.close).put(b.volume).put(b.amount).put(b.change));v.put("payload",a.toString());db.insertWithOnConflict("daily",null,v,SQLiteDatabase.CONFLICT_REPLACE);}
 private List<ScannerModel.Bar> decode(JSONArray a)throws Exception{List<ScannerModel.Bar> out=new ArrayList<>();for(int i=0;i<a.length();i++){JSONArray x=a.getJSONArray(i);out.add(new ScannerModel.Bar(x.getString(0),x.getDouble(1),x.getDouble(2),x.getDouble(3),x.getDouble(4),x.getDouble(5),x.getDouble(6),x.getDouble(7)));}return out;}
 public List<MasterParser.Stock> universe()throws Exception {
  List<MasterParser.Stock> out=new ArrayList<>();Set<String> seen=new HashSet<>();
  for(String market:new String[]{"KOSPI","KOSDAQ"}){
   String file=market.toLowerCase()+"_code.mst";int before=out.size();
   Request r=new Request.Builder().url("https://new.real.download.dws.co.kr/common/master/"+file+".zip").build();
   try(Response response=http.newCall(r).execute()){
    if(!response.isSuccessful()||response.body()==null)throw new IOException("종목 목록 다운로드 실패");
    try(ZipInputStream zip=new ZipInputStream(response.body().byteStream())){ZipEntry entry;boolean found=false;
     while((entry=zip.getNextEntry())!=null){if(!entry.getName().equals(file))continue;found=true;BufferedReader reader=new BufferedReader(new InputStreamReader(zip,Charset.forName("MS949")));String line;int rows=0;
      while((line=reader.readLine())!=null){if(++rows>20000)throw new IOException("종목 목록 형식 변경");if(line.isEmpty())continue;MasterParser.Stock stock=MasterParser.parse(line,market);if(stock!=null&&seen.add(stock.code))out.add(stock);}break;
     }if(!found)throw new IOException("종목 파일 형식 변경");
    }
   }if(out.size()==before)throw new IOException(market+" 적격 종목 목록 없음 · 형식 확인");
  }return out;
 }
 @Override public void close(){db.close();http.dispatcher().cancelAll();http.connectionPool().evictAll();}
}
