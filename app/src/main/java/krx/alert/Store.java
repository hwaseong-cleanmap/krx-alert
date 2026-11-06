package krx.alert;
import android.content.*;
import android.security.keystore.*;
import android.util.Base64;
import org.json.*;
import java.security.KeyStore;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
public final class Store {
 public final android.content.SharedPreferences prefs;
 public Store(Context c){prefs=c.getSharedPreferences("local",Context.MODE_PRIVATE);}
 public synchronized JSONArray items(){try{return new JSONArray(prefs.getString("items","[]"));}catch(Exception e){return new JSONArray();}}
 public synchronized void save(JSONArray a){prefs.edit().putString("items",a.toString()).commit();}
 private javax.crypto.SecretKey key() throws Exception {
  KeyStore k=KeyStore.getInstance("AndroidKeyStore");k.load(null);
  if(!k.containsAlias("kis-local")){KeyGenerator g=KeyGenerator.getInstance("AES","AndroidKeyStore");g.init(new KeyGenParameterSpec.Builder("kis-local",KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());g.generateKey();}
  return (javax.crypto.SecretKey)k.getKey("kis-local",null);
 }
 public void credentials(String app,String secret)throws Exception {
  JSONObject obj=new JSONObject().put("app",app).put("secret",secret);
  Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key());
  prefs.edit().putString("iv",Base64.encodeToString(c.getIV(),Base64.NO_WRAP)).putString("credentials",Base64.encodeToString(c.doFinal(obj.toString().getBytes("UTF-8")),Base64.NO_WRAP)).commit();
 }
 public JSONObject credentials()throws Exception {
  Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(prefs.getString("iv",""),Base64.NO_WRAP)));
  return new JSONObject(new String(c.doFinal(Base64.decode(prefs.getString("credentials",""),Base64.NO_WRAP)),"UTF-8"));
 }
 public void status(String s){prefs.edit().putString("status",s).putLong("statusAt",System.currentTimeMillis()).apply();}
}
