package sn.grainettable;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import jakarta.validation.Valid;
@RestController @RequestMapping("/api")
public class OrderController {
 final OrderService service;public OrderController(OrderService s){service=s;}
 @PostMapping("/cart/quote") Object quote(@Valid @RequestBody Requests.Cart p){return service.quote(p.items());}
 @PostMapping("/orders") Object create(Authentication user,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody Requests.Order p){return service.create(user.getName(),key,p);}
 @GetMapping("/orders") Object orders(Authentication user,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="6") int limit){return service.orders(user.getName(),page,limit,false);}
 @GetMapping("/admin/orders") Object all(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="6") int limit){return service.orders(null,page,limit,true);}
 @PatchMapping("/admin/orders/{id}") Object status(@PathVariable String id,@Valid @RequestBody Requests.Status p){return service.status(id,p.status());}
}
