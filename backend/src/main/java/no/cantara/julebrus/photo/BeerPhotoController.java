package no.cantara.julebrus.photo;

import no.cantara.julebrus.user.CurrentUser;
import no.cantara.julebrus.web.ApiException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

/** Label photos. Everything here is under /api/**, so login, CSRF and the consent gate apply. */
@RestController
@RequestMapping("/api/beers")
public class BeerPhotoController {

    private final PhotoService photos;
    private final CurrentUser currentUser;

    public BeerPhotoController(PhotoService photos, CurrentUser currentUser) {
        this.photos = photos;
        this.currentUser = currentUser;
    }

    /** Multipart, part "file". The size limit is Spring's multipart limit, answered as 413 PHOTO_TOO_LARGE. */
    @PostMapping(value = "/{beerId}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public PhotoService.PhotoView upload(@PathVariable UUID beerId, @RequestPart("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "PHOTO_MISSING", "No picture was sent");
        return photos.upload(currentUser.get(), beerId, file.getBytes());
    }

    /** The beer's cover for this caller: the oldest approved photo, else their own pending one. */
    @GetMapping("/{beerId}/photo")
    public ResponseEntity<byte[]> cover(@PathVariable UUID beerId) {
        return serve(photos.cover(beerId, currentUser.get()));
    }

    /** One photo, for the uploader, an admin, or anyone once approved. */
    @GetMapping("/photos/{photoId}/image")
    public ResponseEntity<byte[]> image(@PathVariable UUID photoId) {
        return serve(photos.image(photoId, currentUser.get()));
    }

    @DeleteMapping("/photos/{photoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID photoId) {
        photos.delete(photoId, currentUser.get());
    }

    private static ResponseEntity<byte[]> serve(java.util.Optional<PhotoService.Image> image) {
        if (image.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PHOTO_NOT_FOUND", "No such picture");
        }
        byte[] jpeg = image.get().jpeg();
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .contentLength(jpeg.length)
                .cacheControl(CacheControl.maxAge(Duration.ofSeconds(60)).cachePrivate())
                .header(HttpHeaders.VARY, HttpHeaders.COOKIE)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .header("X-Content-Type-Options", "nosniff")
                // If this URL is ever opened as a page, nothing in it may run or load.
                .header("Content-Security-Policy", "default-src 'none'; sandbox")
                .body(jpeg);
    }
}
