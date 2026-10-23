package sn.grainettable;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:versiontests;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","shop.uploads=./target/test-uploads","shop.origin=http://localhost:4200"})
@AutoConfigureMockMvc @Transactional
class ProductVersionTest {
 @Autowired JdbcTemplate db; @Autowired CatalogService catalog; @Autowired OrderService orders;
 @Autowired MockMvc mvc; @Autowired ObjectMapper json;
 long product(){long c=db.queryForObject("SELECT id FROM category ORDER BY id LIMIT 1",Long.class);db.update("INSERT INTO product(name,sku,category_id,price,stock,status,image) VALUES('Version test','VERSION',?,1000,20,'published','/assets/cafe.jpeg')",c);long id=db.queryForObject("SELECT id FROM product WHERE sku='VERSION'",Long.class);db.update("INSERT INTO product_image(product_id,position,image) VALUES(?,0,'/assets/cafe.jpeg')",id);return id;}
 Requests.Product body(long id,Long version,String name){long c=db.queryForObject("SELECT category_id FROM product WHERE id=?",Long.class,id);return new Requests.Product(name,"VERSION","description",c,1000L,20,"published","/assets/table.jpeg",List.of("/assets/table.jpeg"),version);}
 void rejected(Runnable action,int status){var e=assertThrows(ApiException.class,action::run);assertEquals(status,e.status);}
 @Test void orderConfirmationInvalidatesOpenFormWithoutChangingPhotosOrHistory(){
  long id=product();var request=orders.create(null,UUID.randomUUID().toString(),new Requests.Order(List.of(new Requests.Item(id,3)),"Client","772506666","",3000L));
  String orderId=((Map<?,?>)request.get("order")).get("id").toString();orders.status(orderId,"confirmed");
  assertEquals(1L,db.queryForObject("SELECT version FROM product WHERE id=?",Long.class,id));
  rejected(()->catalog.saveProduct(id,body(id,0L,"Écriture périmée")),409);
  assertEquals(17,db.queryForObject("SELECT stock FROM product WHERE id=?",Integer.class,id));
  assertEquals("Version test",db.queryForObject("SELECT name FROM product WHERE id=?",String.class,id));
  assertEquals("/assets/cafe.jpeg",db.queryForObject("SELECT image FROM product_image WHERE product_id=?",String.class,id));
  assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM stock_movement WHERE product_id=?",Integer.class,id));
  orders.status(orderId,"confirmed");assertEquals(1L,db.queryForObject("SELECT version FROM product WHERE id=?",Long.class,id));
  orders.status(orderId,"cancelled");assertEquals(2L,db.queryForObject("SELECT version FROM product WHERE id=?",Long.class,id));
 }
 @Test void onlyOneAdminCanSaveSameVersionAndArchiveRejectsStaleVersion(){
  long id=product();catalog.saveProduct(id,body(id,0L,"Premier admin"));
  rejected(()->catalog.saveProduct(id,body(id,0L,"Second admin")),409);
  rejected(()->catalog.saveProduct(id,body(id,null,"Sans version")),400);
  assertEquals("Premier admin",db.queryForObject("SELECT name FROM product WHERE id=?",String.class,id));
  rejected(()->catalog.archive(id,0),409);catalog.archive(id,1);
  assertEquals(2L,db.queryForObject("SELECT version FROM product WHERE id=?",Long.class,id));
 }
 @Test void stockApiRequiresVersionAndRejectsSecondStaleAdjustment()throws Exception{
  long id=product();String endpoint="/api/admin/products/"+id+"/stock";
  mvc.perform(patch(endpoint).with(user("admin").roles("ADMIN")).with(csrf()).header("Origin","http://localhost:4200").contentType("application/json").content("{\"stock\":15,\"version\":0}")).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
  mvc.perform(patch(endpoint).with(user("admin").roles("ADMIN")).with(csrf()).header("Origin","http://localhost:4200").contentType("application/json").content("{\"stock\":19,\"version\":0}")).andExpect(status().isConflict());
  mvc.perform(patch(endpoint).with(user("admin").roles("ADMIN")).with(csrf()).header("Origin","http://localhost:4200").contentType("application/json").content("{\"stock\":20}")).andExpect(status().isBadRequest());
  mvc.perform(get("/api/admin/products/"+id)).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/admin/products/"+id).with(user("admin").roles("ADMIN"))).andExpect(jsonPath("$.version").value(1));
  assertEquals(15,db.queryForObject("SELECT stock FROM product WHERE id=?",Integer.class,id));
  assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM stock_movement WHERE product_id=?",Integer.class,id));
 }
}
