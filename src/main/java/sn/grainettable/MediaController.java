package sn.grainettable;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.core.io.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.Color;
@RestController @RequestMapping("/api")
public class MediaController {
 final Path dir;public MediaController(@Value("${shop.uploads}") String path)throws Exception{dir=Path.of(path).toAbsolutePath().normalize();Files.createDirectories(dir);}
 @PostMapping("/admin/uploads") Object upload(@RequestParam("file") MultipartFile file)throws Exception{if(file.isEmpty()||file.getSize()>5*1024*1024||!Set.of("image/jpeg","image/png").contains(Objects.toString(file.getContentType(),"")))throw new ApiException(400,"Photo JPEG ou PNG, 5 Mo maximum");try(var input=ImageIO.createImageInputStream(file.getInputStream())){var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())throw new ApiException(400,"Image invalide");var reader=readers.next();try{reader.setInput(input);int w=reader.getWidth(0),h=reader.getHeight(0);if((long)w*h>25000000||w<1||h<1)throw new ApiException(400,"Dimensions de photo trop grandes");var image=reader.read(0);double factor=Math.min(1,1600.0/Math.max(w,h));var output=new BufferedImage(Math.max(1,(int)(w*factor)),Math.max(1,(int)(h*factor)),BufferedImage.TYPE_INT_RGB);var g=output.createGraphics();g.setColor(Color.WHITE);g.fillRect(0,0,output.getWidth(),output.getHeight());g.drawImage(image,0,0,output.getWidth(),output.getHeight(),null);g.dispose();String name=UUID.randomUUID()+".jpg";ImageIO.write(output,"jpg",dir.resolve(name).toFile());return Map.of("image","/api/media/"+name);}finally{reader.dispose();}}}
 @GetMapping("/media/{name}") ResponseEntity<Resource> image(@PathVariable String name){if(!name.matches("[a-f0-9-]{36}\\.jpg"))throw new ApiException(404,"Photo introuvable");Path path=dir.resolve(name);if(!Files.isRegularFile(path))throw new ApiException(404,"Photo introuvable");return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).cacheControl(CacheControl.maxAge(java.time.Duration.ofDays(365)).cachePublic().immutable()).body(new FileSystemResource(path));}
}
