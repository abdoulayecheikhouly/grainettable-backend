package sn.grainettable;

import org.springframework.web.bind.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import jakarta.validation.Valid;
import java.util.*;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.sql.Timestamp;

@RestController @RequestMapping("/api")
public class ManagementController {
 final SettingsService settings;final JdbcTemplate db;final CatalogService catalog;
 public ManagementController(SettingsService settings,JdbcTemplate db,CatalogService catalog){this.settings=settings;this.db=db;this.catalog=catalog;}
 @GetMapping({"/settings","/admin/settings"}) Object settings(){return settings.get();}
 @PutMapping("/admin/settings") Object saveSettings(@RequestBody Map<String,String> values){return settings.save(values);}
 @GetMapping("/admin/analytics") Object analytics(@RequestParam(defaultValue="month") String period,@RequestParam(required=false) String from,@RequestParam(required=false) String to,@RequestParam(required=false) String month,@RequestParam(required=false) String week){
  LocalDate today=LocalDate.now(ZoneId.of("Africa/Dakar")),start,end=today;
  try{start=switch(period){case "week"->{if(week==null||week.isBlank())yield today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));if(!week.matches("[0-9]{4}-W[0-9]{2}"))throw new IllegalArgumentException();int year=Integer.parseInt(week.substring(0,4)),number=Integer.parseInt(week.substring(6));var monday=LocalDate.of(year,1,4).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(number-1);var iso=java.time.temporal.WeekFields.ISO;if(number<1||monday.get(iso.weekBasedYear())!=year||monday.get(iso.weekOfWeekBasedYear())!=number)throw new IllegalArgumentException();end=monday.plusDays(6);yield monday;}case "month"->{if(month==null||month.isBlank())yield today.withDayOfMonth(1);var selected=YearMonth.parse(month);end=selected.atEndOfMonth();yield selected.atDay(1);}case "range"->{end=LocalDate.parse(to);yield LocalDate.parse(from);}default->throw new IllegalArgumentException();};if(start.isAfter(end)||start.getYear()<2000||end.getYear()>2100||java.time.temporal.ChronoUnit.DAYS.between(start,end)>3660)throw new IllegalArgumentException();}catch(Exception e){throw new ApiException(400,"Période invalide : choisissez une semaine, un mois ou une plage de dates");}
  var args=new Object[]{Timestamp.valueOf(start.atStartOfDay()),Timestamp.valueOf(end.plusDays(1).atStartOfDay())};
  String where=" WHERE o.status='confirmed' AND o.confirmed_at>=? AND o.confirmed_at<?";
  var summary=db.queryForMap("SELECT COUNT(*) AS orders,COALESCE(SUM(o.total),0) AS revenue FROM shop_order o"+where,args);
  var top=db.queryForList("SELECT i.product_id,MAX(i.name) AS name,SUM(i.quantity) AS quantity,SUM(i.quantity*i.unit_price) AS revenue FROM order_item i JOIN shop_order o ON o.id=i.order_id"+where+" GROUP BY i.product_id ORDER BY quantity DESC,revenue DESC,i.product_id LIMIT 10",args);
  long units=db.queryForObject("SELECT COALESCE(SUM(i.quantity),0) FROM order_item i JOIN shop_order o ON o.id=i.order_id"+where,Long.class,args);
  var inventory=db.queryForMap("SELECT COUNT(*) AS products,COALESCE(SUM(stock),0) AS units,COALESCE(SUM(CASE WHEN stock=0 THEN 1 ELSE 0 END),0) AS out_of_stock,COALESCE(SUM(CASE WHEN stock BETWEEN 1 AND 5 THEN 1 ELSE 0 END),0) AS low_stock FROM product");
  return Map.of("from",start.toString(),"to",end.toString(),"period",period,"summary",summary,"units_sold",units,"top_products",top,"inventory",inventory,"basis","confirmed_orders");
 }
 @GetMapping("/admin/stock") Object stock(@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="all") String level,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="10") int limit){
  String condition=switch(level){case "all"->"1=1";case "low"->"p.stock BETWEEN 1 AND 5";case "out"->"p.stock=0";default->throw new ApiException(400,"Filtre de stock invalide");};var args=new ArrayList<Object>();if(!q.isBlank()){condition+=" AND (LOWER(p.name) LIKE ? OR LOWER(p.sku) LIKE ?)";args.add("%"+q.toLowerCase(Locale.ROOT)+"%");args.add(args.getFirst());}String from=" FROM product p JOIN category c ON c.id=p.category_id WHERE "+condition;return catalog.list("SELECT p.*,c.name AS category_name"+from+" ORDER BY p.stock,p.id","SELECT COUNT(*)"+from,args,page,limit);
 }
 @PatchMapping("/admin/products/{id}/stock") @Transactional Object adjust(@PathVariable long id,@Valid @RequestBody Requests.Stock change){var rows=db.queryForList("SELECT stock FROM product WHERE id=? FOR UPDATE",id);if(rows.isEmpty())throw new ApiException(404,"Produit introuvable");long previous=CatalogService.num(rows.getFirst().get("stock"));if(db.update("UPDATE product SET stock=?,updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE id=? AND version=?",change.stock(),id,change.version())==0)throw CatalogService.conflict();if(previous!=change.stock())db.update("INSERT INTO stock_movement(product_id,previous_stock,new_stock,reason) VALUES(?,?,?,?)",id,previous,change.stock(),CatalogService.clean(change.reason()).isEmpty()?"Ajustement manuel":change.reason().trim());return Map.of("stock",change.stock(),"version",change.version()+1);}
 @GetMapping("/admin/stock/movements") Object movements(@RequestParam long productId){return db.queryForList("SELECT previous_stock,new_stock,reason,created_at FROM stock_movement WHERE product_id=? ORDER BY created_at DESC,id DESC LIMIT 30",productId);}
}
