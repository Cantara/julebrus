package no.cantara.julebrus.photo;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import no.cantara.julebrus.ConsentSupport;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static no.cantara.julebrus.photo.PhotoFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The photo endpoints end to end (dev profile, H2): authorisation, quotas, concurrency, what the list queries touch. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@TestPropertySource(properties = {"logging.level.org.hibernate.SQL=DEBUG",
        "logging.level.org.springframework.jdbc.core.JdbcTemplate=DEBUG"})
class PhotoApiTest {

    static final String ADMIN = "dev@cantara.no";
    static final AtomicInteger N = new AtomicInteger();

    @Autowired
    MockMvc mvc;
    @Autowired
    PhotoStore store;

    // ---- helpers

    private String person() throws Exception {
        String email = "ph" + System.nanoTime() + "x" + N.incrementAndGet() + "@cantara.no";
        ConsentSupport.accept(mvc, email);
        ConsentSupport.accept(mvc, ADMIN);
        return email;
    }

    private static String field(MvcResult r, String name) throws Exception {
        return r.getResponse().getContentAsString().replaceAll(".*\"" + name + "\":\"([^\"]+)\".*", "$1");
    }

    private String userId(String email) throws Exception {
        return field(mvc.perform(get("/api/me").header("X-Dev-User", email)).andReturn(), "id");
    }

    /** An approved beer (added by the admin). */
    private String beer() throws Exception {
        return field(mvc.perform(post("/api/beers").header("X-Dev-User", ADMIN).contentType(APPLICATION_JSON)
                .content("{\"name\":\"Photo beer " + System.nanoTime() + N.incrementAndGet() + "\",\"abv\":6.5}"))
                .andExpect(status().isCreated()).andReturn(), "id");
    }

    private ResultActions up(String user, String beerId, byte[] bytes, String filename, String contentType) throws Exception {
        return mvc.perform(multipart("/api/beers/" + beerId + "/photos")
                .file(new MockMultipartFile("file", filename, contentType, bytes)).header("X-Dev-User", user));
    }

    private ResultActions up(String user, String beerId) throws Exception {
        return up(user, beerId, jpeg(300, 200), "label.jpg", "image/jpeg");
    }

    private String upOk(String user, String beerId) throws Exception {
        return field(up(user, beerId).andExpect(status().isCreated()).andReturn(), "id");
    }

    private void decide(String photoId, String decision, int expected) throws Exception {
        mvc.perform(post("/api/admin/photos/" + photoId + "/decision").header("X-Dev-User", ADMIN)
                .contentType(APPLICATION_JSON).content("{\"decision\":\"" + decision + "\"}")).andExpect(status().is(expected));
    }

    private ResultActions beerRow(String user, String beerId) throws Exception {
        return mvc.perform(get("/api/beers").param("q", "").header("X-Dev-User", user));
    }

    // ---- upload, serving and visibility

    @Test
    void uploaderSeesOwnPendingPhotoAtOnceOthersOnlyAfterApproval() throws Exception {
        String a = person(), b = person(), beer = beer();
        String photo = upOk(a, beer);

        MvcResult served = mvc.perform(get("/api/beers/" + beer + "/photo").header("X-Dev-User", a))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Disposition", "inline"))
                .andExpect(header().string("Cache-Control", containsString("private")))
                .andExpect(header().string("Content-Security-Policy", containsString("default-src 'none'")))
                .andReturn();
        byte[] bytes = served.getResponse().getContentAsByteArray();
        assertThat(served.getResponse().getHeader("Content-Length")).isEqualTo(String.valueOf(bytes.length));
        assertThat(bytes[0] & 0xFF).isEqualTo(0xFF);
        assertThat(bytes[1] & 0xFF).isEqualTo(0xD8);

        // hasPhoto per caller
        mvc.perform(get("/api/beers").header("X-Dev-User", a))
                .andExpect(jsonPath("$[?(@.id=='%s')].hasPhoto".formatted(beer)).value(true))
                .andExpect(jsonPath("$[?(@.id=='%s')].myPhotoStatus".formatted(beer)).value("PENDING"))
                .andExpect(jsonPath("$[?(@.id=='%s')].myPhotoId".formatted(beer)).value(photo));
        mvc.perform(get("/api/beers").header("X-Dev-User", b))
                .andExpect(jsonPath("$[?(@.id=='%s')].hasPhoto".formatted(beer)).value(false))
                .andExpect(jsonPath("$[?(@.id=='%s')].myPhotoId".formatted(beer)).value(org.hamcrest.Matchers.contains((Object) null)));
        mvc.perform(get("/api/beers/" + beer + "/photo").header("X-Dev-User", b)).andExpect(status().isNotFound());
        mvc.perform(get("/api/beers/photos/" + photo + "/image").header("X-Dev-User", b)).andExpect(status().isNotFound());
        // the admin can see it to decide
        mvc.perform(get("/api/beers/photos/" + photo + "/image").header("X-Dev-User", ADMIN)).andExpect(status().isOk());

        decide(photo, "APPROVE", 200);
        mvc.perform(get("/api/beers/" + beer + "/photo").header("X-Dev-User", b)).andExpect(status().isOk());
        mvc.perform(get("/api/beers/photos/" + photo + "/image").header("X-Dev-User", b)).andExpect(status().isOk());
        mvc.perform(get("/api/beers").header("X-Dev-User", b))
                .andExpect(jsonPath("$[?(@.id=='%s')].hasPhoto".formatted(beer)).value(true));
        // BeerView never renders other people's photo ids
        mvc.perform(get("/api/beers").header("X-Dev-User", b))
                .andExpect(content().string(not(containsString(photo))));
    }

    @Test
    void storedBytesAreTheReencodedJpegWithoutExif() throws Exception {
        String a = person(), beer = beer();
        byte[] in = withExif(jpeg(1200, 900), 1);
        assertThat(contains(in, SECRET_GPS)).isTrue();
        String photo = field(up(a, beer, in, "IMG_0001.JPG", "image/jpeg").andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING")).andExpect(jsonPath("$.width").value(800))
                .andReturn(), "id");
        byte[] stored = store.rawData(UUID.fromString(photo)).get(0);
        assertThat(stored.length).isLessThan(in.length + 1).isGreaterThan(100);
        assertThat(contains(stored, SECRET_GPS)).isFalse();
        assertThat(contains(stored, SECRET_MAKE)).isFalse();
        assertThat(markersBeforeScan(stored)).doesNotContain(0xE1, 0xFE);
    }

    @Test
    void hostileUploadsAreRefusedWithStableCodes() throws Exception {
        String a = person(), beer = beer();
        up(a, beer, ascii("<?php system($_GET['c']); ?>"), "evil.jpg", "image/jpeg")
                .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.code").value("PHOTO_FORMAT"));
        up(a, beer, ascii("<html><script>alert(1)</script>"), "x.jpg", "image/jpeg")
                .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.code").value("PHOTO_FORMAT"));
        up(a, beer, ascii("<svg xmlns='http://www.w3.org/2000/svg'/>"), "x.svg", "image/svg+xml")
                .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.code").value("PHOTO_FORMAT"));
        up(a, beer, jpegClaiming(jpeg(16, 16), 30000, 30000), "bomb.jpg", "image/jpeg")
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("PHOTO_DIMENSIONS"));
        up(a, beer, pngClaiming(png(marked(16, 16)), 30000, 30000), "bomb.png", "image/png")
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("PHOTO_DIMENSIONS"));
        up(a, beer, truncated(jpeg(300, 200), 0.5), "cut.jpg", "image/jpeg")
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("PHOTO_UNREADABLE"));
        // an honest picture sent with a lying content type is still taken for what its bytes are
        up(a, beer, jpeg(100, 100), "x.txt", "text/plain").andExpect(status().isCreated());
        // none of the refusals used up a quota slot or left a row: only the last one exists
        mvc.perform(get("/api/beers").header("X-Dev-User", a))
                .andExpect(jsonPath("$[?(@.id=='%s')].myPhotoStatus".formatted(beer)).value("PENDING"));
    }

    @Test
    void missingPartEmptyFileAndNotMultipartAreCleanErrors() throws Exception {
        String a = person(), beer = beer();
        mvc.perform(multipart("/api/beers/" + beer + "/photos").file(new MockMultipartFile("other", "x.jpg", "image/jpeg", jpeg(10, 10)))
                .header("X-Dev-User", a)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PHOTO_MISSING"));
        up(a, beer, new byte[0], "x.jpg", "image/jpeg").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PHOTO_MISSING"));
        mvc.perform(post("/api/beers/" + beer + "/photos").header("X-Dev-User", a).contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void consentIsRequired() throws Exception {
        String beer = beer();
        String nobody = "noconsent" + System.nanoTime() + "@cantara.no";
        up(nobody, beer).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CONSENT_REQUIRED"));
        mvc.perform(get("/api/beers/" + beer + "/photo").header("X-Dev-User", nobody)).andExpect(status().isForbidden());
        mvc.perform(get("/api/beers/photos/" + UUID.randomUUID() + "/image").header("X-Dev-User", nobody)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/beers/photos/" + UUID.randomUUID()).header("X-Dev-User", nobody)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/photos").header("X-Dev-User", nobody)).andExpect(status().isForbidden());
    }

    @Test
    void unknownAndRejectedBeersCannotGetPhotos() throws Exception {
        String a = person();
        up(a, UUID.randomUUID().toString()).andExpect(status().isNotFound());
        mvc.perform(get("/api/beers/" + UUID.randomUUID() + "/photo").header("X-Dev-User", a)).andExpect(status().isNotFound());
        String mine = field(mvc.perform(post("/api/beers").header("X-Dev-User", a).contentType(APPLICATION_JSON)
                .content("{\"name\":\"Rejected photo beer " + N.incrementAndGet() + System.nanoTime() + "\",\"abv\":6}"))
                .andExpect(status().isCreated()).andReturn(), "id");
        mvc.perform(post("/api/admin/beers/" + mine + "/decision").header("X-Dev-User", ADMIN).contentType(APPLICATION_JSON)
                .content("{\"decision\":\"REJECT\"}")).andExpect(status().isOk());
        up(a, mine).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("BEER_REJECTED"));
    }

    @Test
    void homebrewAndPendingBeersCanHavePhotos() throws Exception {
        String a = person();
        String homebrew = field(mvc.perform(post("/api/beers").header("X-Dev-User", a).contentType(APPLICATION_JSON)
                .content("{\"name\":\"Homebrew photo " + N.incrementAndGet() + System.nanoTime() + "\",\"abv\":7.5,\"homebrew\":true}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING")).andReturn(), "id");
        up(a, homebrew).andExpect(status().isCreated());
        mvc.perform(get("/api/beers/" + homebrew + "/photo").header("X-Dev-User", a)).andExpect(status().isOk());
    }

    // ---- quotas

    @Test
    void onlyOnePendingPhotoPerPersonPerBeer() throws Exception {
        String a = person(), b = person(), beer = beer();
        String first = upOk(a, beer);
        up(a, beer).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PHOTO_PENDING_EXISTS"));
        up(b, beer).andExpect(status().isCreated()); // someone else may
        decide(first, "REJECT", 200);
        up(a, beer).andExpect(status().isCreated()); // decided, so a new one is fine
    }

    @Test
    void fivePerDayThenTheDailyLimitEvenAfterDeleting() throws Exception {
        String a = person();
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < PhotoService.MAX_PER_DAY; i++) ids.add(upOk(a, beer()));
        up(a, beer()).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("PHOTO_DAILY_LIMIT"));
        // deleting does not give the slots back (otherwise upload-delete-upload never ends)
        for (String id : ids) mvc.perform(delete("/api/beers/photos/" + id).header("X-Dev-User", a)).andExpect(status().isNoContent());
        up(a, beer()).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("PHOTO_DAILY_LIMIT"));
        // another person is unaffected
        up(person(), beer()).andExpect(status().isCreated());
    }

    @Test
    void thirtyPerSeasonThenTheSeasonLimit() throws Exception {
        String a = person();
        UUID uid = UUID.fromString(userId(a));
        UUID oldBeer = UUID.fromString(beer());
        byte[] jpg = jpeg(20, 20);
        for (int i = 0; i < PhotoService.MAX_PER_SEASON - 1; i++) {
            // earlier days this year: counted for the season, not for today
            store.insert(UUID.randomUUID(), oldBeer, uid, BeerPhoto.Status.APPROVED, 20, 20, jpg,
                    Instant.parse("2026-02-01T10:00:00Z").plusSeconds(i), null);
        }
        upOk(a, beer()); // number 30
        up(a, beer()).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("PHOTO_SEASON_LIMIT"));
    }

    @Test
    void parallelUploadsCannotExceedTheDailyQuota() throws Exception {
        String a = person();
        List<String> beers = new ArrayList<>();
        for (int i = 0; i < 12; i++) beers.add(beer());
        byte[] jpg = jpeg(200, 150);
        ExecutorService pool = Executors.newFixedThreadPool(12);
        try {
            List<Future<Integer>> fs = new ArrayList<>();
            for (String b : beers) fs.add(pool.submit((Callable<Integer>) () -> up(a, b, jpg, "p.jpg", "image/jpeg").andReturn().getResponse().getStatus()));
            int created = 0, refused = 0;
            for (Future<Integer> f : fs) {
                int s = f.get();
                if (s == 201) created++; else if (s == 422) refused++;
            }
            assertThat(created).isEqualTo(PhotoService.MAX_PER_DAY);
            assertThat(refused).isEqualTo(12 - PhotoService.MAX_PER_DAY);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void parallelUploadsToOneBeerLeaveOnePending() throws Exception {
        String a = person(), beer = beer();
        byte[] jpg = jpeg(200, 150);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<Integer>> fs = new ArrayList<>();
            for (int i = 0; i < 8; i++) fs.add(pool.submit((Callable<Integer>) () -> up(a, beer, jpg, "p.jpg", "image/jpeg").andReturn().getResponse().getStatus()));
            int created = 0, conflict = 0;
            for (Future<Integer> f : fs) {
                int s = f.get();
                if (s == 201) created++; else if (s == 409) conflict++;
            }
            assertThat(created).isEqualTo(1);
            assertThat(conflict).isEqualTo(7);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void adminUploadsAreApprovedAtOnceAndNotQuotaLimited() throws Exception {
        String other = person();
        String beer = beer();
        up(ADMIN, beer).andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("APPROVED"));
        mvc.perform(get("/api/beers/" + beer + "/photo").header("X-Dev-User", other)).andExpect(status().isOk());
        for (int i = 0; i < PhotoService.MAX_PER_DAY + 2; i++) up(ADMIN, beer()).andExpect(status().isCreated());
    }

    // ---- admin

    @Test
    void adminQueueListsMetadataOnlyAndOnlyForAdmins() throws Exception {
        String a = person(), beer = beer();
        String photo = upOk(a, beer);
        mvc.perform(get("/api/admin/photos").header("X-Dev-User", a)).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/photos/" + photo + "/decision").header("X-Dev-User", a).contentType(APPLICATION_JSON)
                .content("{\"decision\":\"APPROVE\"}")).andExpect(status().isForbidden());
        MvcResult r = mvc.perform(get("/api/admin/photos?status=PENDING").header("X-Dev-User", ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='%s')].beerId".formatted(photo)).value(beer))
                .andExpect(jsonPath("$[?(@.id=='%s')].uploaderEmail".formatted(photo)).value(a))
                .andExpect(jsonPath("$[?(@.id=='%s')].uploaderName".formatted(photo)).exists())
                .andExpect(jsonPath("$[?(@.id=='%s')].beerName".formatted(photo)).exists())
                .andExpect(jsonPath("$[?(@.id=='%s')].createdAt".formatted(photo)).exists())
                .andReturn();
        assertThat(r.getResponse().getContentAsString()).doesNotContain("data").doesNotContain("jpeg");
        mvc.perform(get("/api/admin/photos?status=NOPE").header("X-Dev-User", ADMIN)).andExpect(status().is4xxClientError());
        decide(photo, "APPROVE", 200);
        mvc.perform(get("/api/admin/photos").header("X-Dev-User", ADMIN)).andExpect(jsonPath("$[*].id", not(hasItem(photo))));
        mvc.perform(get("/api/admin/photos?status=APPROVED").header("X-Dev-User", ADMIN)).andExpect(jsonPath("$[*].id", hasItem(photo)));
    }

    @Test
    void decisionsAreFinalAndUnknownIdsAre404() throws Exception {
        String a = person(), beer = beer();
        String photo = upOk(a, beer);
        mvc.perform(post("/api/admin/photos/" + photo + "/decision").header("X-Dev-User", ADMIN).contentType(APPLICATION_JSON)
                .content("{\"decision\":\"REJECT\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
        decide(photo, "APPROVE", 409);
        decide(photo, "REJECT", 409);
        decide(UUID.randomUUID().toString(), "APPROVE", 404);
        mvc.perform(post("/api/admin/photos/" + photo + "/decision").header("X-Dev-User", ADMIN).contentType(APPLICATION_JSON)
                .content("{\"decision\":\"MAYBE\"}")).andExpect(status().isBadRequest());
        // rejected: the uploader still sees it, nobody else, and it is no cover
        mvc.perform(get("/api/beers/photos/" + photo + "/image").header("X-Dev-User", a)).andExpect(status().isOk());
        mvc.perform(get("/api/beers/photos/" + photo + "/image").header("X-Dev-User", person())).andExpect(status().isNotFound());
        mvc.perform(get("/api/beers/" + beer + "/photo").header("X-Dev-User", a)).andExpect(status().isNotFound());
        mvc.perform(get("/api/beers").header("X-Dev-User", a))
                .andExpect(jsonPath("$[?(@.id=='%s')].hasPhoto".formatted(beer)).value(false))
                .andExpect(jsonPath("$[?(@.id=='%s')].myPhotoStatus".formatted(beer)).value("REJECTED"));
    }

    @Test
    void parallelDecisionsOnePhotoExactlyOneWins() throws Exception {
        String a = person(), beer = beer();
        String photo = upOk(a, beer);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<Integer>> fs = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                String d = i % 2 == 0 ? "APPROVE" : "REJECT";
                fs.add(pool.submit((Callable<Integer>) () -> mvc.perform(post("/api/admin/photos/" + photo + "/decision")
                        .header("X-Dev-User", ADMIN).contentType(APPLICATION_JSON).content("{\"decision\":\"" + d + "\"}"))
                        .andReturn().getResponse().getStatus()));
            }
            int ok = 0, conflict = 0;
            for (Future<Integer> f : fs) {
                int s = f.get();
                if (s == 200) ok++; else if (s == 409) conflict++;
            }
            assertThat(ok).isEqualTo(1);
            assertThat(conflict).isEqualTo(7);
        } finally {
            pool.shutdownNow();
        }
    }

    // ---- idor and delete

    @Test
    void otherPeoplesPhotosAreNotReachableByIdAndDeleteIsOwnerOrAdmin() throws Exception {
        String a = person(), b = person(), beer = beer();
        String photo = upOk(a, beer);
        mvc.perform(get("/api/beers/photos/" + photo + "/image").header("X-Dev-User", b)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/beers/photos/" + photo).header("X-Dev-User", b)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/beers/photos/" + UUID.randomUUID()).header("X-Dev-User", b)).andExpect(status().isNotFound());
        mvc.perform(get("/api/beers/photos/" + UUID.randomUUID() + "/image").header("X-Dev-User", b)).andExpect(status().isNotFound());
        // the 404 for a stranger's photo and for a missing one look the same
        String x = mvc.perform(get("/api/beers/photos/" + photo + "/image").header("X-Dev-User", b)).andReturn().getResponse().getContentAsString();
        String y = mvc.perform(get("/api/beers/photos/" + UUID.randomUUID() + "/image").header("X-Dev-User", b)).andReturn().getResponse().getContentAsString();
        assertThat(x).isEqualTo(y);
        mvc.perform(get("/api/beers/photos/" + photo + "/image").header("X-Dev-User", a)).andExpect(status().isOk());
    }

    @Test
    void uploaderAndAdminCanDeleteAndTheBytesAreWiped() throws Exception {
        String a = person(), beerA = beer(), beerB = beer();
        String p1 = upOk(a, beerA);
        mvc.perform(delete("/api/beers/photos/" + p1).header("X-Dev-User", a)).andExpect(status().isNoContent());
        assertThat(store.rawData(UUID.fromString(p1)).get(0)).isEmpty();
        mvc.perform(get("/api/beers/photos/" + p1 + "/image").header("X-Dev-User", a)).andExpect(status().isNotFound());
        mvc.perform(get("/api/beers/" + beerA + "/photo").header("X-Dev-User", a)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/beers/photos/" + p1).header("X-Dev-User", a)).andExpect(status().isNotFound());
        mvc.perform(get("/api/beers").header("X-Dev-User", a))
                .andExpect(jsonPath("$[?(@.id=='%s')].hasPhoto".formatted(beerA)).value(false))
                .andExpect(jsonPath("$[?(@.id=='%s')].myPhotoId".formatted(beerA)).value(org.hamcrest.Matchers.contains((Object) null)));

        String p2 = upOk(a, beerB);
        decide(p2, "APPROVE", 200);
        mvc.perform(delete("/api/beers/photos/" + p2).header("X-Dev-User", ADMIN)).andExpect(status().isNoContent());
        assertThat(store.rawData(UUID.fromString(p2)).get(0)).isEmpty();
        mvc.perform(get("/api/beers/" + beerB + "/photo").header("X-Dev-User", person())).andExpect(status().isNotFound());
        decide(p2, "APPROVE", 404); // a deleted photo cannot be decided
    }

    // ---- cover

    @Test
    void theCoverIsTheOldestApprovedPhotoElseTheCallersOwnPending() throws Exception {
        String a = person(), b = person(), c = person(), beer = beer();
        String pa = field(up(a, beer, jpeg(marked(300, 200)), "a.jpg", "image/jpeg").andExpect(status().isCreated()).andReturn(), "id");
        Thread.sleep(20);
        String pb = field(up(b, beer, png(noise(200, 200)), "b.png", "image/png").andExpect(status().isCreated()).andReturn(), "id");
        byte[] bytesA = store.data(UUID.fromString(pa)).orElseThrow();
        byte[] bytesB = store.data(UUID.fromString(pb)).orElseThrow();
        assertThat(bytesA).isNotEqualTo(bytesB);

        // nothing approved: each sees their own pending
        assertThat(cover(a, beer)).isEqualTo(bytesA);
        assertThat(cover(b, beer)).isEqualTo(bytesB);
        mvc.perform(get("/api/beers/" + beer + "/photo").header("X-Dev-User", c)).andExpect(status().isNotFound());

        // B is approved first, but A is older: once both are approved A is the cover
        decide(pb, "APPROVE", 200);
        assertThat(cover(c, beer)).isEqualTo(bytesB);
        assertThat(cover(a, beer)).as("an approved photo wins over the caller's own pending one").isEqualTo(bytesB);
        decide(pa, "APPROVE", 200);
        assertThat(cover(c, beer)).isEqualTo(bytesA);
        assertThat(cover(b, beer)).isEqualTo(bytesA);
        mvc.perform(delete("/api/beers/photos/" + pa).header("X-Dev-User", a)).andExpect(status().isNoContent());
        assertThat(cover(c, beer)).isEqualTo(bytesB);
    }

    private byte[] cover(String user, String beer) throws Exception {
        return mvc.perform(get("/api/beers/" + beer + "/photo").header("X-Dev-User", user)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
    }

    // ---- listing never reads the bytes

    @Test
    void listingBeersAndTheAdminQueueNeverSelectTheDataColumn() throws Exception {
        String a = person(), beer = beer();
        upOk(a, beer);

        Logger sql = (Logger) LoggerFactory.getLogger("org.hibernate.SQL");
        Logger jdbc = (Logger) LoggerFactory.getLogger("org.springframework.jdbc.core.JdbcTemplate");
        ListAppender<ILoggingEvent> log = new ListAppender<>();
        log.start();
        sql.setLevel(Level.DEBUG);
        jdbc.setLevel(Level.DEBUG);
        sql.addAppender(log);
        jdbc.addAppender(log);
        try {
            mvc.perform(get("/api/beers").header("X-Dev-User", a)).andExpect(status().isOk());
            mvc.perform(get("/api/beers/least-tasted").header("X-Dev-User", a)).andExpect(status().isOk());
            mvc.perform(get("/api/admin/photos").header("X-Dev-User", ADMIN)).andExpect(status().isOk());
            List<String> photoSql = log.list.stream().map(ILoggingEvent::getFormattedMessage)
                    .filter(m -> m.contains("beer_photos")).toList();
            assertThat(photoSql).as("the list queries do touch beer_photos").isNotEmpty();
            assertThat(photoSql).allSatisfy(m -> assertThat(m.toLowerCase()).doesNotContainPattern("\\bdata\\b"));

            // the capture is real: serving the cover does select the column
            log.list.clear();
            mvc.perform(get("/api/beers/" + beer + "/photo").header("X-Dev-User", a)).andExpect(status().isOk());
            assertThat(log.list.stream().map(ILoggingEvent::getFormattedMessage))
                    .anySatisfy(m -> assertThat(m.toLowerCase()).containsPattern("select data from beer_photos"));
        } finally {
            sql.detachAppender(log);
            jdbc.detachAppender(log);
        }
    }

    @Test
    void theEntityAndItsViewsCannotCarryImageBytes() {
        for (Class<?> c : new Class<?>[]{BeerPhoto.class, PhotoService.PhotoView.class,
                no.cantara.julebrus.photo.BeerPhotoRepository.AdminRow.class, no.cantara.julebrus.beer.BeerController.BeerView.class}) {
            for (var f : c.getDeclaredFields()) {
                assertThat(f.getType()).as(c.getSimpleName() + "." + f.getName()).isNotEqualTo(byte[].class);
            }
        }
    }

    @Test
    void adminQueueOmitsPeopleWhoWithdrewConsent() throws Exception {
        String a = person(), beer = beer();
        String photo = upOk(a, beer);
        mvc.perform(post("/api/consent/withdraw").header("X-Dev-User", a)).andExpect(status().isOk());
        mvc.perform(get("/api/admin/photos").header("X-Dev-User", ADMIN)).andExpect(jsonPath("$[*].id", not(hasItem(photo))));
    }
}
