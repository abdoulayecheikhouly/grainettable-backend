package sn.grainettable;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.Locale;
@Configuration
public class Provisioning {
 @Bean ApplicationRunner provision(JdbcTemplate db,PasswordEncoder encoder,Environment env){return args->{String email=env.getProperty("ADMIN_EMAIL"),password=env.getProperty("ADMIN_PASSWORD");if(email!=null&&!email.isBlank()&&password!=null&&!password.isBlank()){if(password.length()<16)throw new IllegalStateException("ADMIN_PASSWORD must be at least 16 characters");if(db.queryForObject("SELECT COUNT(*) FROM shop_user WHERE email=?",Long.class,email.toLowerCase(Locale.ROOT))==0)db.update("INSERT INTO shop_user(name,email,phone,password_hash,role) VALUES(?,?,?,?,'admin')","Équipe Grain & Table",email.toLowerCase(Locale.ROOT),"",encoder.encode(password));}if(env.getProperty("shop.seed-demo",Boolean.class,false)&&!java.util.Arrays.asList(env.getActiveProfiles()).contains("prod")&&db.queryForObject("SELECT COUNT(*) FROM product",Long.class)==0){String[] names={"Machine à café compacte","Cocotte à couvercle","Service de table en céramique","Assortiment de fruits secs","Tasses à café · lot de 2","Ustensiles de cuisine","Capsules de café","Verres à pied · lot de 6","Poêle de cuisine","Noix de cajou","Café premium","Amandes"};int[] prices={95000,42000,65000,8500,18000,12000,6500,24000,22000,5000,9000,6000},categories={1,2,3,4,3,2,1,3,2,4,1,4};String[] images={"cafe","cuisine","table","fruits"};for(int i=0;i<names.length;i++)db.update("INSERT INTO product(name,sku,description,category_id,price,stock,status,image) VALUES(?,?,?,?,?,20,'published',?)",names[i],"DEMO-"+(i+1),"Démonstration : photo, prix et référence à remplacer.",categories[i],prices[i],"/assets/"+images[categories[i]-1]+".jpeg");}};}
}
