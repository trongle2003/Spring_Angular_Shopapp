package com.project.shopapp.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.project.shopapp.Producers.MessageProducer;
import com.project.shopapp.components.LocalizationUtils;
import com.project.shopapp.dtos.*;
import com.project.shopapp.models.Product;
import com.project.shopapp.models.ProductImage;
import com.project.shopapp.responses.ProductListResponse;
import com.project.shopapp.responses.ProductResponse;
import com.project.shopapp.services.Product.IProductRedisService;
import com.project.shopapp.services.Product.IProductService;
import com.project.shopapp.utils.MessageKeys;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("${api.prefix}/products")
@RequiredArgsConstructor
public class ProductController {
    private static final Logger logger = LoggerFactory.getLogger(ProductController.class);
    private final IProductService productService;
    private final LocalizationUtils localizationUtils;
    private final MessageProducer messageProducer;
    private final IProductRedisService productRedisService;



    @PostMapping("")
    //POST http://localhost:8088/v1/api/products
    public ResponseEntity<?> createProduct(
            @Valid @RequestBody ProductDTO productDTO,
            BindingResult result
    ) {
        try {
            if (result.hasErrors()) {
                List<String> errorMessages = new ArrayList<>();
                for (FieldError error : result.getFieldErrors()) {
                    errorMessages.add(error.getDefaultMessage());
                }
                return ResponseEntity.badRequest().body(errorMessages);
            }

            Product newProduct = productService.createProduct(productDTO);
            logRabbit("Created product: " + newProduct.getName());

            return ResponseEntity.ok(newProduct);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
    @PostMapping(value = "uploads/{id}",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    //POST http://localhost:8088/v1/api/products
    public ResponseEntity<?> uploadImages(
            @PathVariable("id") Long productId,
            @ModelAttribute("files") List<MultipartFile> files
    ){
        try {
            Product existingProduct = productService.getProductById(productId);
            files = files == null ? new ArrayList<MultipartFile>() : files;
            if(files.size() > ProductImage.MAXIMUM_IMAGES_PER_PRODUCT) {
                return ResponseEntity.badRequest().body(localizationUtils
                        .getLocalizedMessage(MessageKeys.UPLOAD_IMAGES_MAX_5));
            }
            List<ProductImage> productImages = new ArrayList<>();
            for (MultipartFile file : files) {
                if(file.getSize() == 0) {
                    continue;
                }
                // Kiểm tra kích thước file và định dạng
                if(file.getSize() > 10 * 1024 * 1024) { // Kích thước > 10MB
                    return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                            .body(localizationUtils
                                    .getLocalizedMessage(MessageKeys.UPLOAD_IMAGES_FILE_LARGE));
                }
                String contentType = file.getContentType();
                if(contentType == null || !contentType.startsWith("image/")) {
                    return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                            .body(localizationUtils.getLocalizedMessage(MessageKeys.UPLOAD_IMAGES_FILE_MUST_BE_IMAGE));
                }
                // Lưu file và cập nhật thumbnail trong DTO
                String filename = storeFile(file); // Thay thế hàm này với code của bạn để lưu file
                //lưu vào đối tượng product trong DB
                ProductImage productImage = productService.createProductImage(
                        existingProduct.getId(),
                        ProductImageDTO.builder()
                                .imageUrl(filename)
                                .build()
                );
                productImages.add(productImage);
                if (existingProduct.getThumbnail() == null && !productImages.isEmpty()) {
                    ProductDTO productDTO = ProductDTO.builder()
                            .name(existingProduct.getName())
                            .description(existingProduct.getDescription())
                            .price(existingProduct.getPrice())
                            .thumbnail(productImages.get(0).getImageUrl())
                            .categoryId(existingProduct.getCategory().getId())
                            .build();
                    productService.updateProduct(existingProduct.getId(), productDTO);

                }

            }
            return ResponseEntity.ok().body(productImages);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
    @GetMapping("/images/{imageName}")
    public ResponseEntity<?> viewImage(@PathVariable String imageName) {
        try {
            java.nio.file.Path imagePath = Paths.get("uploads/"+imageName);
            UrlResource resource = new UrlResource(imagePath.toUri());

            if (resource.exists()) {
                return ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_JPEG)
                        .body(resource);
            } else {
                return ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_JPEG)
                        .body(new UrlResource(Paths.get("uploads/notfound.jpeg").toUri()));
                //return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
    private String storeFile(MultipartFile file) throws IOException {
        if (!isImageFile(file) || file.getOriginalFilename() == null) {
            throw new IOException("Invalid image format");
        }
        String filename = StringUtils.cleanPath(Objects.requireNonNull(file.getOriginalFilename()));

        String uniqueFilename = UUID.randomUUID().toString() + "_" + filename;

        java.nio.file.Path uploadDir = Paths.get("uploads");

        if (!Files.exists(uploadDir)) {
            Files.createDirectories(uploadDir);
        }

        java.nio.file.Path destination = Paths.get(uploadDir.toString(), uniqueFilename);

        Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
        return uniqueFilename;
    }
    private boolean isImageFile(MultipartFile file) {
        String contentType = file.getContentType();
        return contentType != null && contentType.startsWith("image/");
    }


    @GetMapping("")
    public ResponseEntity<ProductListResponse> getProducts(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "0", name = "category_id") Long categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int limit
    ) throws JsonProcessingException {

        PageRequest pageRequest = PageRequest.of(
                page, limit,
                Sort.by("id").ascending()
        );
        logger.info("keyword = {}, category_id = {}, page = {}, limit = {}",
                keyword, categoryId, page, limit);


        List<ProductResponse> products = productRedisService.getAllProducts(keyword, categoryId, pageRequest);


        int totalPages;
        if (products != null) {
            logger.info("Cache hit for key all_products:{}:{}:{}:{}:*", keyword, categoryId, page, limit);

            Page<ProductResponse> pageInfo = productService.getAllProducts(keyword, categoryId, pageRequest);
            totalPages = pageInfo.getTotalPages();
        } else {
            logger.info("Cache miss: fetching from DB");
            Page<ProductResponse> pageResult = productService.getAllProducts(keyword, categoryId, pageRequest);
            products = pageResult.getContent();
            totalPages = pageResult.getTotalPages();


            productRedisService.saveAllProducts(products, keyword, categoryId, pageRequest);
        }


        ProductListResponse response = ProductListResponse.builder()
                .products(products)
                .totalPages(totalPages)
                .build();

        return ResponseEntity.ok(response);
    }


    //http://localhost:8088/api/v1/products/6
    @GetMapping("/{id}")
    public ResponseEntity<?> getProductById(
            @PathVariable("id") Long productId
    ) {
        try {
            Product existingProduct = productService.getProductById(productId);
            return ResponseEntity.ok(ProductResponse.fromProduct(existingProduct));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }

    }
    @GetMapping("/by-ids")
    public ResponseEntity<?> getProductsByIds(@RequestParam("ids") String ids) {
        //eg: 1,3,5,7
        try {
            // Tách chuỗi ids thành một mảng các số nguyên
            List<Long> productIds = new ArrayList<>();
            for (String idStr : ids.split(",")) {
                productIds.add(Long.parseLong(idStr));
            }

            List<Product> products = productService.findProductsByIds(productIds);
            return ResponseEntity.ok(products);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteProduct(@PathVariable long id) {
        try {
            productService.deleteProduct(id);
            logRabbit("Deleted product ID: " + id);

            return ResponseEntity.ok(String.format("Product with id = %d deleted successfully", id));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
    //@PostMapping("/generateFakeProducts")
//    private ResponseEntity<String> generateFakeProducts() {
//        Faker faker = new Faker();
//        for (int i = 0; i < 1_000_000; i++) {
//            String productName = faker.commerce().productName();
//            if(productService.existsByName(productName)) {
//                continue;
//            }
//            ProductDTO productDTO = ProductDTO.builder()
//                    .name(productName)
//                    .price((float)faker.number().numberBetween(10, 90_000_000))
//                    .description(faker.lorem().sentence())
//                    .thumbnail("")
//                    .categoryId((long)faker.number().numberBetween(2, 5))
//                    .build();
//            try {
//                productService.createProduct(productDTO);
//            } catch (Exception e) {
//                return ResponseEntity.badRequest().body(e.getMessage());
//            }
//        }
//        return ResponseEntity.ok("Fake Products created successfully");
//    }
    //update a product
    @PutMapping("/{id}")
    public ResponseEntity<?> updateProduct(
            @PathVariable long id,
            @RequestBody ProductDTO productDTO) {
        try {
            Product updatedProduct = productService.updateProduct(id, productDTO);
            logRabbit("Updated product ID: " + id + " - " + updatedProduct.getName());

            return ResponseEntity.ok(updatedProduct);
        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/images/{id}")
    public ResponseEntity<?> deleteProductImage(@PathVariable("id") Long imageId) {
        try {
            productService.deleteProductImage(imageId); // Gọi service xử lý xóa ảnh
            return ResponseEntity.ok("Image deleted successfully.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    private void logRabbit(String msg) {
        try {
            messageProducer.sendMessage(msg);
        } catch (Exception e) {
            logger.error("Failed to send RabbitMQ message: {}", msg);
        }
    }

}
