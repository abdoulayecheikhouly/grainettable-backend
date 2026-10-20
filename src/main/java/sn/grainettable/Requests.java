package sn.grainettable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public final class Requests {
 private Requests() {}
 public record Signup(@NotBlank @Size(max=100) String name,@NotBlank @Email @Size(max=254) String email,@Pattern(regexp="^$|^\\+?[0-9 ()-]{8,24}$") String phone,@NotBlank @Size(min=12,max=72) String password) {}
 public record Login(@NotBlank @Email String email,@NotBlank @Size(max=72) String password) {}
 public record Category(@NotBlank @Size(max=100) String name,@NotBlank @Size(max=120) @Pattern(regexp="[a-z0-9]+(?:-[a-z0-9]+)*") String slug,@Size(max=2000) String description,boolean visible) {}
 public record Product(@NotBlank @Size(max=160) String name,@NotBlank @Size(max=80) String sku,@Size(max=8000) String description,@NotNull @Positive Long categoryId,@NotNull @Min(0) @Max(100000000) Long price,@NotNull @Min(0) @Max(1000000) Integer stock,@NotBlank @Pattern(regexp="draft|published") String status,@NotBlank @Pattern(regexp="(?:/assets/[a-zA-Z0-9_.-]+\\.(?:jpeg|jpg|png|webp)|/api/media/[a-f0-9-]{36}\\.jpg)") String image) {}
 public record Item(@NotNull @Positive Long productId,@NotNull @Min(1) @Max(100) Integer quantity) {}
 public record Cart(@NotEmpty @Size(max=50) List<@Valid Item> items) {}
 public record Order(@NotEmpty @Size(max=50) List<@Valid Item> items,@NotBlank @Size(max=100) String customerName,@NotBlank @Pattern(regexp="^\\+?[0-9 ()-]{8,24}$") String phone,@Size(max=1000) String note,@NotNull @Min(0) Long expectedTotal) {}
 public record Status(@NotBlank @Pattern(regexp="awaiting_confirmation|confirmed|cancelled") String status) {}
 public record Stock(@NotNull @Min(0) @Max(1000000) Integer stock,@Size(max=200) String reason) {}
}
