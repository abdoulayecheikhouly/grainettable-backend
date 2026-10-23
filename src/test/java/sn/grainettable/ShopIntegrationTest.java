package sn.grainettable;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.mock.web.MockHttpSession;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:shoptests;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","shop.uploads=./target/test-uploads","shop.origin=http://localhost:4200"})
@AutoConfigureMockMvc @Transactional
class ShopIntegrationTest {
 @Autowired MockMvc mvc;@Autowired JdbcTemplate db;@Autowired ObjectMapper json;@Autowired OrderService orders;
 final String origin="http://localhost:4200";
 long product(String sku,long price,int stock){long cat=db.queryForObject("SELECT id FROM category ORDER BY id LIMIT 1",Long.class);db.update("INSERT INTO product(name,sku,category_id,price,stock,status,image) VALUES(?,?,?, ?,?,'published','/assets/cafe.jpeg')","Café "+sku,sku,cat,price,stock);return db.queryForObject("SELECT id FROM product WHERE sku=?",Long.class,sku);}
 void customer(){db.update("INSERT INTO shop_user(name,email,phone,password_hash,role) VALUES('Awa','awa@example.com','772506666','unused','customer')");}
 @Test void healthAndCatalogFiltering()throws Exception{product("A",500,5);product("B",1500,0);mvc.perform(get("/api/health")).andExpect(status().isOk()).andExpect(jsonPath("$.payments_enabled").value(false));mvc.perform(get("/api/products").param("max","600").param("available","true")).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));mvc.perform(get("/api/products").param("page","2").param("limit","1")).andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.pages").value(2));mvc.perform(get("/api/products").param("page","0")).andExpect(status().isBadRequest());}
 @Test void csrfOriginAndRolesAreEnforced()throws Exception{mvc.perform(post("/api/auth/login").header("Origin",origin).contentType("application/json").content("{}")).andExpect(status().isForbidden());mvc.perform(post("/api/auth/login").with(csrf()).header("Origin","https://attacker.invalid").contentType("application/json").content("{}")).andExpect(status().isForbidden());mvc.perform(get("/api/admin/products").with(user("client").roles("CUSTOMER"))).andExpect(status().isForbidden());mvc.perform(get("/api/orders")).andExpect(status().isUnauthorized());}
 @Test void registrationCreatesCustomerSessionWithoutRoleInjection()throws Exception{var response=mvc.perform(post("/api/auth/register").with(csrf()).header("Origin",origin).contentType("application/json").content("{\"name\":\"Awa\",\"email\":\"awa@example.com\",\"phone\":\"772506666\",\"password\":\"A-long-test-password-123\"}")).andExpect(status().isCreated()).andExpect(jsonPath("$.user.role").value("customer")).andReturn();var session=(MockHttpSession)response.getRequest().getSession(false);mvc.perform(get("/api/session").session(session)).andExpect(jsonPath("$.user.email").value("awa@example.com"));mvc.perform(post("/api/auth/register").with(csrf()).header("Origin",origin).contentType("application/json").content("{\"name\":\"Intrus\",\"email\":\"intrus@example.com\",\"password\":\"A-long-password-1234\",\"role\":\"admin\"}")).andExpect(status().isBadRequest());}
 @Test void orderTotalsAreServerVerifiedAndRetriesAreIdempotent()throws Exception{customer();long id=product("ORDER",1000,5);String key=UUID.randomUUID().toString();String body="{\"items\":[{\"product_id\":"+id+",\"quantity\":2}],\"customer_name\":\"Awa\",\"phone\":\"772506666\",\"note\":\"Bonjour\",\"expected_total\":2000}";
  mvc.perform(post("/api/orders").with(user("awa@example.com")).with(csrf()).header("Origin",origin).header("Idempotency-Key",key).contentType("application/json").content(body.replace("2000","1"))).andExpect(status().isConflict());
  var first=mvc.perform(post("/api/orders").with(user("awa@example.com")).with(csrf()).header("Origin",origin).header("Idempotency-Key",key).contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.order.total").value(2000)).andExpect(jsonPath("$.order.status").value("awaiting_confirmation")).andReturn();
  mvc.perform(post("/api/orders").with(user("awa@example.com")).with(csrf()).header("Origin",origin).header("Idempotency-Key",key).contentType("application/json").content(body)).andExpect(jsonPath("$.order.total").value(2000));Assertions.assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM shop_order",Integer.class));Assertions.assertTrue(first.getResponse().getContentAsString().contains("https://wa.me/221772506666"));Assertions.assertEquals(5,db.queryForObject("SELECT stock FROM product WHERE id=?",Integer.class,id));}
 @Test void confirmationDecrementsStockOnceAndCancellationRestoresIt(){customer();long id=product("STOCK",1000,3);var p=new Requests.Order(List.of(new Requests.Item(id,2)),"Awa","772506666","",2000L);var result=orders.create("awa@example.com",UUID.randomUUID().toString(),p);@SuppressWarnings("unchecked") var order=(Map<String,Object>)result.get("order");String oid=(String)order.get("id");orders.status(oid,"confirmed");orders.status(oid,"confirmed");Assertions.assertEquals(1,db.queryForObject("SELECT stock FROM product WHERE id=?",Integer.class,id));orders.status(oid,"cancelled");Assertions.assertEquals(3,db.queryForObject("SELECT stock FROM product WHERE id=?",Integer.class,id));}
 @Test void invalidAdminProductAndUsedCategoryAreRejected()throws Exception{long id=product("USED",10,2);long cat=db.queryForObject("SELECT category_id FROM product WHERE id=?",Long.class,id);mvc.perform(delete("/api/admin/categories/"+cat).with(user("admin").roles("ADMIN")).with(csrf()).header("Origin",origin)).andExpect(status().isConflict());mvc.perform(post("/api/admin/products").with(user("admin").roles("ADMIN")).with(csrf()).header("Origin",origin).contentType("application/json").content("{\"name\":\"Bad\",\"sku\":\"BAD\",\"category_id\":1,\"price\":-1,\"stock\":2,\"status\":\"published\",\"image\":\"/assets/cafe.jpeg\"}")).andExpect(status().isBadRequest());}

 @Test void guestCheckoutNeedsNoAccountAndKeepsServerValidation()throws Exception{
  long id=product("GUEST",1500,10);long users=db.queryForObject("SELECT COUNT(*) FROM shop_user",Long.class);String key=UUID.randomUUID().toString();String body=json.writeValueAsString(Map.of("items",List.of(Map.of("product_id",id,"quantity",2)),"customer_name","Fatou","phone","772506666","note","Retrait demain","expected_total",3000));
  mvc.perform(post("/api/guest/orders").with(csrf()).header("Origin",origin).header("Idempotency-Key",key).contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.order.total").value(3000)).andExpect(jsonPath("$.order.guest_key").doesNotExist()).andExpect(jsonPath("$.whatsapp_url").value(org.hamcrest.Matchers.containsString("https://wa.me/221772506666")));
  mvc.perform(post("/api/guest/orders").with(csrf()).header("Origin",origin).header("Idempotency-Key",key).contentType("application/json").content(body)).andExpect(status().isOk());
  Assertions.assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM shop_order WHERE user_id IS NULL",Integer.class));Assertions.assertEquals(users,db.queryForObject("SELECT COUNT(*) FROM shop_user",Long.class));
  mvc.perform(post("/api/guest/orders").with(csrf()).header("Origin",origin).header("Idempotency-Key",UUID.randomUUID().toString()).contentType("application/json").content(body.replace("3000","1"))).andExpect(status().isConflict());
  mvc.perform(post("/api/guest/orders").header("Origin",origin).header("Idempotency-Key",key).contentType("application/json").content(body)).andExpect(status().isForbidden());mvc.perform(get("/api/admin/orders")).andExpect(status().isUnauthorized());
 }
 @Test void settingsAreAdminOnlyAndValidateSocialLinksAndGps()throws Exception{
  mvc.perform(get("/api/settings")).andExpect(status().isOk()).andExpect(jsonPath("$.store_name").value("GRAIN & TABLE"));
  mvc.perform(put("/api/admin/settings").with(user("client").roles("CUSTOMER")).with(csrf()).header("Origin",origin).contentType("application/json").content("{}")).andExpect(status().isForbidden());
  mvc.perform(put("/api/admin/settings").with(user("admin").roles("ADMIN")).with(csrf()).header("Origin",origin).contentType("application/json").content(json.writeValueAsString(Map.of("instagram","javascript:alert(1)")))).andExpect(status().isBadRequest());
  mvc.perform(put("/api/admin/settings").with(user("admin").roles("ADMIN")).with(csrf()).header("Origin",origin).contentType("application/json").content(json.writeValueAsString(Map.of("latitude","NaN","longitude","1")))).andExpect(status().isBadRequest());
  mvc.perform(put("/api/admin/settings").with(user("admin").roles("ADMIN")).with(csrf()).header("Origin",origin).contentType("application/json").content(json.writeValueAsString(Map.of("instagram","https://instagram.com/grainettable","latitude","14.44","longitude","-17.01","whatsapp","221775375499")))).andExpect(status().isOk());
  mvc.perform(get("/api/settings")).andExpect(jsonPath("$.latitude").value("14.44")).andExpect(jsonPath("$.instagram").value("https://instagram.com/grainettable"));long id=product("SETTINGS",100,2);var created=orders.create(null,UUID.randomUUID().toString(),new Requests.Order(List.of(new Requests.Item(id,1)),"Awa","772506666","",100L));Assertions.assertTrue(created.get("whatsapp_url").toString().startsWith("https://wa.me/221775375499"));
 }
 @Test void confirmedSalesDriveTopTenAndStockHistory()throws Exception{
  String last="";for(int i=1;i<=12;i++){long id=product("RANK-"+i,100,100);var created=orders.create(null,UUID.randomUUID().toString(),new Requests.Order(List.of(new Requests.Item(id,i)),"Awa","772506666","",100L*i));@SuppressWarnings("unchecked") var order=(Map<String,Object>)created.get("order");last=order.get("id").toString();orders.status(last,"confirmed");}
  mvc.perform(get("/api/admin/analytics").with(user("admin").roles("ADMIN"))).andExpect(status().isOk()).andExpect(jsonPath("$.summary.orders").value(12)).andExpect(jsonPath("$.units_sold").value(78)).andExpect(jsonPath("$.top_products.length()").value(10)).andExpect(jsonPath("$.top_products[0].quantity").value(12));
  orders.status(last,"cancelled");mvc.perform(get("/api/admin/analytics").param("period","week").with(user("admin").roles("ADMIN"))).andExpect(jsonPath("$.units_sold").value(66));Assertions.assertEquals(13,db.queryForObject("SELECT COUNT(*) FROM stock_movement",Integer.class));
  mvc.perform(get("/api/admin/analytics").param("period","range").param("from","2020-02-02").param("to","2020-01-01").with(user("admin").roles("ADMIN"))).andExpect(status().isBadRequest());
  long id=product("ADJUST",100,3);mvc.perform(patch("/api/admin/products/"+id+"/stock").with(user("admin").roles("ADMIN")).with(csrf()).header("Origin",origin).contentType("application/json").content("{\"stock\":7,\"version\":0,\"reason\":\"Réception fournisseur\"}")).andExpect(status().isOk());mvc.perform(get("/api/admin/stock/movements").param("productId",String.valueOf(id)).with(user("admin").roles("ADMIN"))).andExpect(jsonPath("$[0].previous_stock").value(3)).andExpect(jsonPath("$[0].new_stock").value(7));
 }

 @Test void historicalMonthsAndIsoWeeksAreSelectable()throws Exception{
  mvc.perform(get("/api/admin/analytics").param("period","month").param("month","2020-02").with(user("admin").roles("ADMIN"))).andExpect(status().isOk()).andExpect(jsonPath("$.from").value("2020-02-01")).andExpect(jsonPath("$.to").value("2020-02-29")).andExpect(jsonPath("$.summary.orders").value(0));
  mvc.perform(get("/api/admin/analytics").param("period","week").param("week","2020-W01").with(user("admin").roles("ADMIN"))).andExpect(status().isOk()).andExpect(jsonPath("$.from").value("2019-12-30")).andExpect(jsonPath("$.to").value("2020-01-05"));
  mvc.perform(get("/api/admin/analytics").param("period","week").param("week","2021-W53").with(user("admin").roles("ADMIN"))).andExpect(status().isBadRequest());
 }

 @Test void productGalleryPersistsOrderedPhotosAndValidatesInput()throws Exception{
  long category=db.queryForObject("SELECT id FROM category ORDER BY id LIMIT 1",Long.class);
  var body=new HashMap<String,Object>(Map.of("name","Galerie","sku","GALLERY","category_id",category,"price",1000,"stock",5,"status","published","image","/assets/table.jpeg","images",List.of("/assets/table.jpeg","/assets/cafe.jpeg")));
  var response=mvc.perform(post("/api/admin/products").with(user("admin").roles("ADMIN")).with(csrf()).header("Origin",origin).contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().isCreated()).andExpect(jsonPath("$.images[0]").value("/assets/table.jpeg")).andExpect(jsonPath("$.images[1]").value("/assets/cafe.jpeg")).andReturn();
  long id=json.readTree(response.getResponse().getContentAsString()).get("id").asLong();
  mvc.perform(get("/api/products/"+id)).andExpect(jsonPath("$.images.length()").value(2)).andExpect(jsonPath("$.rating_count").value(0));
  body.put("version",0);body.put("image","/assets/cafe.jpeg");body.put("images",List.of("/assets/cafe.jpeg"));
  mvc.perform(put("/api/admin/products/"+id).with(user("admin").roles("ADMIN")).with(csrf()).header("Origin",origin).contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().isOk()).andExpect(jsonPath("$.images.length()").value(1)).andExpect(jsonPath("$.image").value("/assets/cafe.jpeg"));
  body.put("images",List.of("https://attacker.invalid/photo.jpg"));
  mvc.perform(put("/api/admin/products/"+id).with(user("admin").roles("ADMIN")).with(csrf()).header("Origin",origin).contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().isBadRequest());
  body.put("images",List.of("/api/media/00000000-0000-0000-0000-000000000000.jpg"));
  mvc.perform(put("/api/admin/products/"+id).with(user("admin").roles("ADMIN")).with(csrf()).header("Origin",origin).contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().isBadRequest());
 }
 @Test void guestRatingsUpdateOneVotePerBrowserAndRemainProtected()throws Exception{
  long id=product("RATING",500,5);
  var first=mvc.perform(post("/api/products/"+id+"/ratings").with(csrf()).header("Origin",origin).contentType("application/json").content("{\"score\":5}")).andExpect(status().isOk()).andExpect(jsonPath("$.rating_count").value(1)).andExpect(jsonPath("$.rating_average").value(5)).andReturn();
  String cookie=first.getResponse().getHeader("Set-Cookie").split(";",2)[0].split("=",2)[1];
  mvc.perform(post("/api/products/"+id+"/ratings").cookie(new jakarta.servlet.http.Cookie("gt_rating",cookie)).with(csrf()).header("Origin",origin).contentType("application/json").content("{\"score\":3}")).andExpect(status().isOk()).andExpect(jsonPath("$.rating_count").value(1)).andExpect(jsonPath("$.rating_average").value(3));
  mvc.perform(post("/api/products/"+id+"/ratings").with(csrf()).header("Origin",origin).contentType("application/json").content("{\"score\":5}")).andExpect(status().isOk()).andExpect(jsonPath("$.rating_count").value(2)).andExpect(jsonPath("$.rating_average").value(4));
  mvc.perform(get("/api/products").param("q","RATING")).andExpect(jsonPath("$.items[0].rating_count").value(2));
  mvc.perform(post("/api/products/"+id+"/ratings").with(csrf()).header("Origin",origin).contentType("application/json").content("{\"score\":6}")).andExpect(status().isBadRequest());
  mvc.perform(post("/api/products/"+id+"/ratings").header("Origin",origin).contentType("application/json").content("{\"score\":5}")).andExpect(status().isForbidden());
  db.update("UPDATE product SET status='draft' WHERE id=?",id);
  mvc.perform(post("/api/products/"+id+"/ratings").with(csrf()).header("Origin",origin).contentType("application/json").content("{\"score\":5}")).andExpect(status().isNotFound());
 }
}
