package sn.grainettable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import static sn.grainettable.CatalogService.*;
@Service
public class OrderService {
 final JdbcTemplate db;final CatalogService catalog;final String whatsapp;final SettingsService settings;
 public OrderService(JdbcTemplate db,CatalogService catalog,@Value("${shop.whatsapp}") String whatsapp,SettingsService settings){this.settings=settings;this.db=db;this.catalog=catalog;if(!whatsapp.matches("[1-9][0-9]{7,14}"))throw new IllegalArgumentException("Invalid WhatsApp number");this.whatsapp=whatsapp;}
 public Map<String,Object> quote(List<Requests.Item> items){var merged=new TreeMap<Long,Integer>();for(var i:items)merged.merge(i.productId(),i.quantity(),Integer::sum);var result=new ArrayList<Map<String,Object>>();long total=0;for(var entry:merged.entrySet()){int quantity=entry.getValue();if(quantity>100)throw new ApiException(400,"Quantité maximale dépassée");var rows=db.queryForList("SELECT p.* FROM product p JOIN category c ON c.id=p.category_id WHERE p.id=? AND p.status='published' AND c.visible=TRUE",entry.getKey());if(rows.isEmpty())throw new ApiException(409,"Un article n’est plus disponible");var p=rows.getFirst();if(num(p.get("stock"))<quantity)throw new ApiException(409,"Stock insuffisant : "+p.get("name"));total+=num(p.get("price"))*quantity;result.add(Map.of("product_id",p.get("id"),"name",p.get("name"),"sku",p.get("sku"),"quantity",quantity,"unit_price",p.get("price"),"image",p.get("image"),"stock",p.get("stock")));}return Map.of("items",result,"total",total,"currency","XOF");}
 Map<String,Object> view(Map<String,Object> order){var result=new LinkedHashMap<>(order);result.remove("idempotency_key");result.remove("guest_key");result.put("items",db.queryForList("SELECT product_id,name,sku,quantity,unit_price FROM order_item WHERE order_id=?",order.get("id")));return result;}
 String link(Map<String,Object> order){var site=settings.get();var message=new StringBuilder("Bonjour "+site.get("store_name")+",\nDemande "+order.get("id")+"\n");for(var item:db.queryForList("SELECT * FROM order_item WHERE order_id=?",order.get("id")))message.append(item.get("quantity")).append(" × ").append(item.get("name")).append(" (").append(item.get("sku")).append(") : ").append(num(item.get("quantity"))*num(item.get("unit_price"))).append(" FCFA\n");message.append("Total articles : ").append(order.get("total")).append(" FCFA\nRetrait : ").append(site.get("address")).append("\nNom : ").append(order.get("customer_name")).append("\nTéléphone : ").append(order.get("phone")).append("\nNote : ").append(order.get("note")).append("\nMerci de confirmer les prix et disponibilités.");return "https://wa.me/"+site.getOrDefault("whatsapp",whatsapp)+"?text="+URLEncoder.encode(message.toString(),StandardCharsets.UTF_8);}
 @Transactional public Map<String,Object> create(String email,String key,Requests.Order p){
  try{UUID.fromString(key);}catch(Exception e){throw new ApiException(400,"Clé de demande invalide");}
  Object owner=null;List<Map<String,Object>> existing;
  if(email!=null){owner=db.queryForMap("SELECT id FROM shop_user WHERE email=? FOR UPDATE",email).get("id");existing=db.queryForList("SELECT * FROM shop_order WHERE user_id=? AND idempotency_key=?",owner,key);}
  else{db.queryForObject("SELECT id FROM guest_order_guard WHERE id=1 FOR UPDATE",Integer.class);existing=db.queryForList("SELECT * FROM shop_order WHERE guest_key=?",key);}
  Map<String,Object> order;
  if(!existing.isEmpty())order=existing.getFirst();else{
   var q=quote(p.items());if(num(q.get("total"))!=p.expectedTotal())throw new ApiException(409,"Le prix a changé. Actualisez votre panier.");
   String id="GT-"+UUID.randomUUID().toString().toUpperCase(Locale.ROOT);
   db.update("INSERT INTO shop_order(id,user_id,customer_name,phone,note,total,idempotency_key,guest_key) VALUES(?,?,?,?,?,?,?,?)",id,owner,p.customerName().trim(),p.phone().trim(),clean(p.note()),q.get("total"),key,email==null?key:null);
   @SuppressWarnings("unchecked") var items=(List<Map<String,Object>>)q.get("items");
   for(var item:items)db.update("INSERT INTO order_item(order_id,product_id,name,sku,quantity,unit_price) VALUES(?,?,?,?,?,?)",id,item.get("product_id"),item.get("name"),item.get("sku"),item.get("quantity"),item.get("unit_price"));
   order=db.queryForMap("SELECT * FROM shop_order WHERE id=?",id);
  }
  return Map.of("order",view(order),"whatsapp_url",link(order));
 }
 public Object orders(String email,int page,int limit,boolean admin){page(page,limit);String where=admin?"":" WHERE user_id=(SELECT id FROM shop_user WHERE email=?)";List<Object> args=admin?List.of():List.of(email);var result=new HashMap<>(catalog.list("SELECT * FROM shop_order"+where+" ORDER BY created_at DESC,id DESC","SELECT COUNT(*) FROM shop_order"+where,args,page,limit));@SuppressWarnings("unchecked") var orders=(List<Map<String,Object>>)result.get("items");result.put("items",orders.stream().map(this::view).toList());return result;}
 @Transactional public Object status(String id,String status){var rows=db.queryForList("SELECT * FROM shop_order WHERE id=? FOR UPDATE",id);if(rows.isEmpty())throw new ApiException(404,"Demande introuvable");var order=rows.getFirst();if(!status.equals(order.get("status"))){var items=db.queryForList("SELECT * FROM order_item WHERE order_id=? ORDER BY product_id",id);if(status.equals("confirmed")){for(var i:items){long before=db.queryForObject("SELECT stock FROM product WHERE id=? FOR UPDATE",Long.class,i.get("product_id"));if(db.update("UPDATE product SET stock=stock-? WHERE id=? AND stock>=?",i.get("quantity"),i.get("product_id"),i.get("quantity"))==0)throw new ApiException(409,"Stock insuffisant : "+i.get("name"));db.update("INSERT INTO stock_movement(product_id,previous_stock,new_stock,reason) VALUES(?,?,?,?)",i.get("product_id"),before,before-num(i.get("quantity")),"Commande "+id+" confirmée");}}if(order.get("status").equals("confirmed")){for(var i:items){long before=db.queryForObject("SELECT stock FROM product WHERE id=? FOR UPDATE",Long.class,i.get("product_id"));db.update("UPDATE product SET stock=stock+? WHERE id=?",i.get("quantity"),i.get("product_id"));db.update("INSERT INTO stock_movement(product_id,previous_stock,new_stock,reason) VALUES(?,?,?,?)",i.get("product_id"),before,before+num(i.get("quantity")),"Commande "+id+" déconfirmée");}}db.update("UPDATE shop_order SET status=?,confirmed_at=CASE WHEN ?='confirmed' THEN CURRENT_TIMESTAMP ELSE confirmed_at END WHERE id=?",status,status,id);}return view(db.queryForMap("SELECT * FROM shop_order WHERE id=?",id));}
}
