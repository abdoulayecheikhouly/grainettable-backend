package sn.grainettable;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import jakarta.validation.Valid;
import java.util.*;
@RestController @RequestMapping("/api")
public class CatalogController {
 final CatalogService service;public CatalogController(CatalogService service){this.service=service;}
 @GetMapping("/health") Map<String,Object> health(){service.health();return Map.of("status","ok","payments_enabled",false);}
 @GetMapping("/categories") Object categories(){return service.categories(false);}
 @GetMapping("/products") Object products(@RequestParam(defaultValue="") String q,@RequestParam(required=false) Long category,@RequestParam(required=false) Long min,@RequestParam(required=false) Long max,@RequestParam(defaultValue="false") boolean available,@RequestParam(defaultValue="selection") String sort,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="6") int limit){return service.products(q,category,min,max,available,sort,page,limit,false,"");}
 @GetMapping("/products/{id}") Object product(@PathVariable long id){return service.product(id);}
 @PostMapping("/products/{id}/ratings") Object rate(@PathVariable long id,@Valid @RequestBody Requests.Rating rating,@CookieValue(name="gt_rating",defaultValue="") String visitor,jakarta.servlet.http.HttpServletRequest req,jakarta.servlet.http.HttpServletResponse res){if(!visitor.matches("[a-f0-9-]{36}")){visitor=UUID.randomUUID().toString();res.addHeader("Set-Cookie",org.springframework.http.ResponseCookie.from("gt_rating",visitor).httpOnly(true).secure(req.isSecure()).sameSite("Strict").path("/api/products").maxAge(java.time.Duration.ofDays(365)).build().toString());}return service.rate(id,visitor,rating.score());}
 @GetMapping("/admin/categories") Object adminCategories(){return service.categories(true);}
 @PostMapping("/admin/categories") @ResponseStatus(HttpStatus.CREATED) Object createCategory(@Valid @RequestBody Requests.Category p){return service.saveCategory(null,p);}
 @PutMapping("/admin/categories/{id}") Object editCategory(@PathVariable long id,@Valid @RequestBody Requests.Category p){return service.saveCategory(id,p);}
 @DeleteMapping("/admin/categories/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) void deleteCategory(@PathVariable long id){service.deleteCategory(id);}
 @GetMapping("/admin/products") Object adminProducts(@RequestParam(defaultValue="") String q,@RequestParam(required=false) Long category,@RequestParam(defaultValue="") String status,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="6") int limit){return service.products(q,category,null,null,false,"selection",page,limit,true,status);}
 @PostMapping("/admin/products") @ResponseStatus(HttpStatus.CREATED) Object createProduct(@Valid @RequestBody Requests.Product p){return service.saveProduct(null,p);}
 @PutMapping("/admin/products/{id}") Object editProduct(@PathVariable long id,@Valid @RequestBody Requests.Product p){return service.saveProduct(id,p);}
 @DeleteMapping("/admin/products/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) void deleteProduct(@PathVariable long id){service.archive(id);}
}
