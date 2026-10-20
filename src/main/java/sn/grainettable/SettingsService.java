package sn.grainettable;

import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.net.URI;
import java.util.*;

@Service
public class SettingsService {
 final JdbcTemplate db;
 static final Set<String> SOCIAL=Set.of("facebook","instagram","youtube","tiktok","linkedin");
 public SettingsService(JdbcTemplate db){this.db=db;}
 public Map<String,String> get(){var result=new LinkedHashMap<String,String>();db.query("SELECT setting_key,setting_value FROM site_setting ORDER BY setting_key",rs->{result.put(rs.getString(1),rs.getString(2));});return result;}
 @Transactional public Map<String,String> save(Map<String,String> input){
  if(input==null||input.size()>30)throw new ApiException(400,"Réglages invalides");
  var current=get();var next=new LinkedHashMap<>(current);
  for(var entry:input.entrySet()){
   String key=entry.getKey(),value=entry.getValue();if(!current.containsKey(key)||value==null)throw new ApiException(400,"Champ de réglage inconnu");value=value.trim();
   int max=switch(key){case "about"->4000;case "hero_subtitle","opening_hours"->1000;case "logo","address"->300;default->200;};
   if(value.length()>max)throw new ApiException(400,"Champ trop long : "+key);
   if(Set.of("store_name","tagline","hero_title","address","phone","whatsapp","logo").contains(key)&&value.isBlank())throw new ApiException(400,"Champ obligatoire : "+key);
   if(SOCIAL.contains(key)&&!value.isEmpty()){try{var uri=URI.create(value);if(!"https".equals(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null)throw new IllegalArgumentException();}catch(Exception e){throw new ApiException(400,"Lien HTTPS invalide : "+key);}}
   if(key.equals("email")&&!value.isEmpty()&&!value.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))throw new ApiException(400,"Adresse e-mail invalide");
   if(Set.of("phone","secondary_phone").contains(key)&&!value.isEmpty()&&!value.matches("\\+?[0-9 ()-]{8,24}"))throw new ApiException(400,"Téléphone invalide");
   if(key.equals("whatsapp")&&!value.matches("[1-9][0-9]{7,14}"))throw new ApiException(400,"Numéro WhatsApp invalide");
   if(key.equals("logo")&&!value.matches("/assets/emblem\\.svg|/api/media/[a-f0-9-]{36}\\.jpg"))throw new ApiException(400,"Logo invalide");
   next.put(key,value);
  }
  if(next.get("latitude").isEmpty()!=next.get("longitude").isEmpty())throw new ApiException(400,"Renseignez les deux coordonnées GPS");
  if(!next.get("latitude").isEmpty()){try{double lat=Double.parseDouble(next.get("latitude")),lng=Double.parseDouble(next.get("longitude"));if(!Double.isFinite(lat)||!Double.isFinite(lng)||Math.abs(lat)>90||Math.abs(lng)>180)throw new IllegalArgumentException();}catch(Exception e){throw new ApiException(400,"Coordonnées GPS invalides");}}
  next.forEach((key,value)->db.update("UPDATE site_setting SET setting_value=? WHERE setting_key=?",value,key));return next;
 }
}
