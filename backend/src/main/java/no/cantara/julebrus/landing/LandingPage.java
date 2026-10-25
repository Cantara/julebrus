package no.cantara.julebrus.landing;

import no.cantara.julebrus.badge.Badge;
import no.cantara.julebrus.beer.Beer;
import no.cantara.julebrus.config.ContactProperties;
import no.cantara.julebrus.config.SeasonProperties;
import no.cantara.julebrus.config.SponsorProperties;
import no.cantara.julebrus.tip.TipScoring;
import no.cantara.julebrus.weight.RaterWeightPolicy;
import org.springframework.web.util.HtmlUtils;

import java.text.NumberFormat;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * The public showcase page, as one self-contained HTML document in Norwegian or English.
 * Pure: everything it shows comes from its arguments. History numbers come from
 * {@link HistoryStats} (aggregates generated from data/), the rules from the season
 * configuration and the constants the app enforces, so the page cannot drift from the app.
 * No scripts, no external resources: the charts are CSS and inline SVG drawn here.
 */
public final class LandingPage {

    /** The rules table is built from these, the same constants the server enforces. */
    private final HistoryStats stats;
    private final SeasonProperties season;
    private final SponsorProperties sponsor;
    private final ContactProperties contact;
    /** When the daily push nudge goes out; null when push notifications are switched off, and then the page does not offer them. */
    private final LocalTime nudgeTime;
    private final boolean en;
    private final String self;
    private final Locale locale;

    private LandingPage(HistoryStats stats, SeasonProperties season, SponsorProperties sponsor, ContactProperties contact,
                        LocalTime nudgeTime, boolean english, String selfPath) {
        this.stats = stats;
        this.season = season;
        this.sponsor = sponsor;
        this.contact = contact;
        this.nudgeTime = nudgeTime;
        this.en = english;
        this.self = selfPath;
        this.locale = english ? Locale.UK : Locale.forLanguageTag("nb-NO");
    }

    /**
     * @param nudgeTime the local time of the daily push nudge, or null when push is off
     *                  (no VAPID keys): the page then says nothing about notifications.
     */
    public static String render(HistoryStats stats, SeasonProperties season, SponsorProperties sponsor,
                                ContactProperties contact, LocalTime nudgeTime, boolean english, String selfPath) {
        return new LandingPage(stats, season, sponsor, contact, nudgeTime, english, selfPath).html();
    }

    private static final Map<Badge, String> BADGE_NB = new EnumMap<>(Map.of(
            Badge.FIRST_BLOOD, "Sesongens første smaking",
            Badge.FULL_WEEK, "Smakt sju dager på rad",
            Badge.EXPLORER, "Scoret et øl færre enn tre andre hadde smakt",
            Badge.LONE_WOLF, "Brukt alle solodagene",
            Badge.SOCIAL_BUTTERFLY, "Smakt med ti forskjellige",
            Badge.CONTRARIAN, "Lengst unna flokken, på minst ti øl",
            Badge.ADVENT, "Scoret hver dag fra 1. til 23. desember",
            Badge.SOMMELIER, "Gjettet gruppens snitt innenfor et halvt poeng, ti ganger",
            Badge.CLOSING_TIME, "En score 23. desember",
            Badge.BLIND_TASTER, "Truffet på blindskjenkingen tre ganger"));

    /** Every badge needs Norwegian copy; a new badge without it fails LandingPageTest, not the page. */
    static boolean everyBadgeHasCopy() {
        return BADGE_NB.size() == Badge.values().length;
    }

    private String l(String nb, String english) {
        return en ? english : nb;
    }

    private static String esc(String s) {
        return HtmlUtils.htmlEscape(s, "UTF-8");
    }

    private String n(long v) {
        return NumberFormat.getIntegerInstance(locale).format(v);
    }

    private String d(double v, int digits) {
        NumberFormat f = NumberFormat.getNumberInstance(locale);
        f.setMinimumFractionDigits(digits);
        f.setMaximumFractionDigits(digits);
        return f.format(v);
    }

    /** "0.5" / "0,5" for a weight, without trailing zeros beyond one decimal. */
    private String w(double v) {
        return d(v, 1);
    }

    private String abv() {
        return d(Beer.GROCERY_MAX_ABV.doubleValue(), 1) + " %";
    }

    private String date(java.time.LocalDate day, boolean withYear) {
        String pattern = en ? (withYear ? "d MMMM yyyy" : "d MMMM") : (withYear ? "d. MMMM yyyy" : "d. MMMM");
        // Non-breaking spaces keep "1. november" together on a narrow screen.
        return day.format(DateTimeFormatter.ofPattern(pattern, locale)).replace(' ', '\u00a0');
    }

    private String seasonSpan() {
        return date(season.start(), false) + " – " + date(season.end(), true);
    }

    private String html() {
        StringBuilder h = new StringBuilder(64_000);
        h.append("<!doctype html>\n<html lang=\"").append(en ? "en" : "nb").append("\">\n<head>\n")
                .append("<meta charset=\"utf-8\">\n")
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1, viewport-fit=cover\">\n")
                .append("<meta name=\"color-scheme\" content=\"light dark\">\n")
                .append("<meta name=\"theme-color\" content=\"#5a1822\">\n")
                .append("<meta name=\"description\" content=\"").append(esc(l(
                        "Cantaras juleølsmaking varer hele adventstiden: terningkast 1–6, små bord og en finale 23. desember.",
                        "Cantara's Christmas beer tasting lasts the whole of Advent: dice scores 1–6, small tables and a finale on 23 December.")))
                .append("\">\n")
                .append("<link rel=\"icon\" href=\"/icons/icon.svg\" type=\"image/svg+xml\">\n")
                .append("<link rel=\"alternate\" hreflang=\"nb\" href=\"").append(self).append("\">\n")
                .append("<link rel=\"alternate\" hreflang=\"en\" href=\"").append(self).append("?lang=en\">\n")
                .append("<title>").append(esc(l("Cantaras juleøl ", "Cantara's juleøl ")) + season.year()).append("</title>\n")
                .append("<style>").append(CSS).append("</style>\n</head>\n<body>\n")
                .append("<a class=\"skip\" href=\"#innhold\">").append(l("Hopp til innholdet", "Skip to content")).append("</a>\n");
        hero(h);
        h.append("<main id=\"innhold\">\n");
        idea(h);
        rules(h);
        how(h);
        history(h);
        privacy(h);
        h.append("</main>\n");
        footer(h);
        h.append("</body>\n</html>\n");
        return h.toString();
    }

    private String langSwitch() {
        return en
                ? "<a class=\"lang\" href=\"" + self + "\" hreflang=\"nb\" lang=\"nb\">Norsk</a>"
                : "<a class=\"lang\" href=\"" + self + "?lang=en\" hreflang=\"en\" lang=\"en\">English</a>";
    }

    private String sponsorLine() {
        if (!sponsor.visible()) return "";
        String name = esc(sponsor.name().trim());
        String link = sponsor.safeUrl() == null ? name
                : "<a href=\"" + esc(sponsor.safeUrl()) + "\" rel=\"noopener\">" + name + "</a>";
        return l("Støttet av ", "Sponsored by ") + link;
    }

    // ---------------------------------------------------------------- hero

    private void hero(StringBuilder h) {
        HistoryStats.Totals t = stats.totals();
        List<HistoryStats.Listing> vm = stats.vinmonopolet();
        h.append("<header class=\"hero\">\n<div class=\"snow\" aria-hidden=\"true\"></div><div class=\"snow far\" aria-hidden=\"true\"></div>\n")
                .append(lights())
                .append("<div class=\"wrap hero-in\">\n<nav class=\"top\" aria-label=\"").append(l("Språk", "Language")).append("\">")
                .append("<span class=\"brand\">Julebrus</span>").append(langSwitch()).append("</nav>\n")
                .append("<div class=\"hero-grid\">\n<div>\n")
                .append("<p class=\"kicker\">").append(l("Cantara · juleølsmaking · sesong ", "Cantara · Christmas beer tasting · season "))
                .append(season.year()).append("</p>\n")
                .append("<h1>Cantaras juleøl <span class=\"yr\">").append(season.year()).append("</span></h1>\n")
                .append("<p class=\"lead\">").append(l(
                        "En hel adventstid med juleøl, terningkast og godt selskap. Litt hver kveld, rundt små bord, fra "
                                + seasonSpan() + ". Spill for laget ditt, tipp pallen, og " + date(season.end(), false)
                                + " kårer vi årets beste.",
                        "A whole Advent of Christmas beer, dice and good company. A little every evening, at small tables, from "
                                + seasonSpan() + ". Play for your team, tip the podium, and on " + date(season.end(), false)
                                + " we name the best of the year.")).append("</p>\n")
                .append("<p class=\"cta\"><a class=\"btn\" href=\"/oauth2/authorization/google\">").append(SIGN_IN)
                .append(l("Logg inn med Google", "Sign in with Google")).append("</a>")
                .append("<a class=\"btn ghost\" href=\"#regler\">").append(l("Se reglene", "See the rules")).append("</a></p>\n");
        String line = sponsorLine();
        if (!line.isEmpty()) h.append("<p class=\"sponsor\">").append(line).append("</p>\n");
        h.append("</div>\n").append(DIE_SIX).append("</div>\n")
                .append("<ul class=\"facts\">\n")
                .append(fact(n(t.seasonsRecorded()), l("sesonger med resultater i protokollen", "seasons of results on record")))
                .append(fact(n(t.votesIncludingAggregate()), l("terningkast siden ", "dice scores since ") + t.firstYear()));
        if (!vm.isEmpty()) {
            HistoryStats.Listing first = vm.get(0), last = vm.get(vm.size() - 1);
            h.append(fact(n(first.listings()) + "<span class=\"arrow\" aria-hidden=\"true\">→</span>" + n(last.listings()),
                    l("juleøl på Vinmonopolets liste, " + first.year() + " og " + last.year(),
                            "Christmas beers on Vinmonopolet's list, " + first.year() + " and " + last.year())));
        }
        h.append(fact(n(season.maxTastingsPerDay()), l("smakinger per person per dag, ikke flere", "tastings per person per day, no more")))
                .append("</ul>\n</div>\n</header>\n");
    }

    private static String fact(String big, String small) {
        return "<li><strong>" + big + "</strong><span>" + small + "</span></li>\n";
    }

    /** A string of Christmas lights across the top of the hero; purely decorative. */
    private static String lights() {
        StringBuilder s = new StringBuilder("<div class=\"lights\" aria-hidden=\"true\">");
        for (int i = 0; i < 18; i++) s.append("<i></i>");
        return s.append("</div>\n").toString();
    }

    // ---------------------------------------------------------------- the idea

    private void idea(StringBuilder h) {
        HistoryStats.Season biggest = stats.seasons().stream().max(Comparator.comparingInt(HistoryStats.Season::beers)).orElseThrow();
        h.append("<section class=\"band\" aria-labelledby=\"ideen\">\n<div class=\"wrap two\">\n<div class=\"prose\">\n")
                .append("<p class=\"eyebrow\">").append(l("Ideen", "The idea")).append("</p>\n")
                .append("<h2 id=\"ideen\">").append(l("Hvorfor en hel sesong?", "Why a whole season?")).append("</h2>\n")
                .append("<p>").append(l(
                        "Cantara har smakt juleøl sammen i alle fall siden " + stats.totals().firstYear()
                                + ". Én kveld, ett bord, én flaske av hver, og terningen avgjør. Men hylla vokste. I "
                                + biggest.year() + " ble " + n(biggest.beers()) + " forskjellige øl scoret.",
                        "People at Cantara have tasted Christmas beer together since at least " + stats.totals().firstYear()
                                + ". One evening, one table, one bottle of each, and the dice decide. But the shelf kept growing. In "
                                + biggest.year() + ", " + n(biggest.beers()) + " different beers were scored.")).append("</p>\n")
                .append("<p>").append(l(
                        "Med en hel hylle på bordet får alle en skvett, ganen gir seg lenge før siste flaske, og de siste scorene er mest støy. "
                                + "Ølene som ble åpnet sist, hadde aldri en sjanse.",
                        "With a whole shelf on the table everyone gets a splash, palates fade long before the last bottle, and the late scores are mostly noise. "
                                + "The beers opened last never stood a chance.")).append("</p>\n")
                .append("<p>").append(l(
                        "Så nå varer smakingen hele adventstiden. Små bord, ekte glass og friske ganer, høyst "
                                + season.maxTastingsPerDay() + " øl om dagen. Resultatet kommer når alle har smakt seg gjennom hylla.",
                        "So now the tasting lasts all of Advent. Small tables, real pours and fresh palates, at most "
                                + season.maxTastingsPerDay() + " beers a day. The result comes when everyone has worked through the shelf.")).append("</p>\n")
                .append("</div>\n");
        vinmonopoletChart(h);
        h.append("</div>\n</section>\n");
    }

    private void vinmonopoletChart(StringBuilder h) {
        List<HistoryStats.Listing> vm = stats.vinmonopolet();
        if (vm.isEmpty()) {
            h.append("<div></div>\n");
            return;
        }
        int max = vm.stream().mapToInt(HistoryStats.Listing::listings).max().orElse(1);
        int from = vm.get(0).year(), to = vm.get(vm.size() - 1).year();
        Map<Integer, Integer> byYear = new java.util.HashMap<>();
        vm.forEach(x -> byYear.put(x.year(), x.listings()));
        HistoryStats.Listing peak = vm.stream().max(Comparator.comparingInt(HistoryStats.Listing::listings)).orElseThrow();
        String title = l("Juleøl på Vinmonopolets lanseringsliste", "Christmas beers on Vinmonopolet's launch list");
        h.append("<figure class=\"chart\">\n<figcaption><h3>").append(title).append("</h3><p class=\"muted\">")
                .append(l("Oppføringer per år, " + from + "–" + to, "Listings per year, " + from + "–" + to)).append("</p></figcaption>\n")
                .append("<div class=\"cols\" role=\"img\" aria-label=\"").append(esc(title + ": "
                        + l("fra " + n(vm.get(0).listings()) + " i " + from + " til " + n(vm.get(vm.size() - 1).listings()) + " i " + to
                                + ", flest i " + peak.year() + " med " + n(peak.listings()),
                        "from " + n(vm.get(0).listings()) + " in " + from + " to " + n(vm.get(vm.size() - 1).listings()) + " in " + to
                                + ", most in " + peak.year() + " with " + n(peak.listings())))).append("\">\n");
        for (int y = from; y <= to; y++) {
            Integer v = byYear.get(y);
            String yy = "’" + String.valueOf(y).substring(2);
            if (v == null) {
                h.append("<div class=\"col gap\" title=\"").append(y).append(": ").append(l("ingen liste", "no list")).append("\">")
                        .append("<span class=\"val\">–</span><span class=\"area\"><span class=\"bar\"></span></span><span class=\"x\">").append(yy).append("</span></div>\n");
                continue;
            }
            boolean label = y == from || y == to || y == peak.year();
            h.append("<div class=\"col\" title=\"").append(y).append(": ").append(n(v)).append("\">")
                    .append("<span class=\"val\">").append(label ? n(v) : "").append("</span>")
                    .append("<span class=\"area\"><span class=\"bar\" style=\"--v:").append(pct(v, max)).append("\"></span></span>")
                    .append("<span class=\"x\">").append(yy).append("</span></div>\n");
        }
        h.append("</div>\n<p class=\"note\">").append(l(
                        "Antall oppføringer i listene i <code>data/vinmonopolet</code>. " + from
                                + " er Vinmonopolets egen liste; årene etter er avskrifter av lanseringslistene fra ølblogger, så en skrivefeil der er en skrivefeil her. 2021 mangler.",
                        "Number of listings in <code>data/vinmonopolet</code>. " + from
                                + " is Vinmonopolet's own list; the later years are beer blogs' transcriptions of the launch lists, so a typo there is a typo here. 2021 is missing."))
                .append("</p>\n");
        table(h, l("Vis som tabell", "Show as a table"), new String[]{l("År", "Year"), l("Oppføringer", "Listings")},
                vm.stream().map(x -> new String[]{String.valueOf(x.year()), n(x.listings())}).toList());
        h.append("</figure>\n");
    }

    // ---------------------------------------------------------------- rules

    private void rules(StringBuilder h) {
        String full = w(RaterWeightPolicy.FULL), rookie = w(RaterWeightPolicy.ROOKIE), muted = w(RaterWeightPolicy.MUTED);
        h.append("<section aria-labelledby=\"regler\">\n<div class=\"wrap\">\n")
                .append("<p class=\"eyebrow\">").append(l("Reglene", "The rules")).append("</p>\n")
                .append("<h2 id=\"regler\">").append(l("Få regler, godt håndhevet", "A few rules, firmly kept")).append("</h2>\n")
                .append("<div class=\"table-wrap\"><table class=\"rules\">\n<caption class=\"sr\">")
                .append(l("Reglene for sesongen", "The rules of the season")).append("</caption>\n")
                .append("<thead><tr><th scope=\"col\">").append(l("Regel", "Rule")).append("</th><th scope=\"col\">")
                .append(l("Slik er det", "What it is")).append("</th><th scope=\"col\">").append(l("Hvorfor", "Why")).append("</th></tr></thead>\n<tbody>\n");
        rule(h, l("Skala", "Scale"), "<span class=\"pips\">" + face(1) + face(6) + "</span> " + l("Terningkast 1–6, hele tall", "Dice, 1 to 6, whole numbers"),
                l("Samme terning som i de gamle regnearkene.", "The same dice as in the old spreadsheets."));
        rule(h, l("Sesongen", "Season"), seasonSpan(),
                l("Følger juleølslippet. Vinduet håndheves på serveren: ingenting kan scores før eller etter.",
                        "Follows the juleøl release. The window is enforced on the server: nothing can be scored before or after."));
        rule(h, l("Per dag", "Per day"), l("Høyst " + season.maxTastingsPerDay() + " smakinger per person", "At most " + season.maxTastingsPerDay() + " tastings per person"),
                l("Ekte glass og frisk gane. En dag er en kalenderdag i Oslo. Ved et bord med " + season.bigTableSize() + " eller flere er grensen "
                                + season.maxTastingsPerDayBigTable() + ", for et selskap er en annen kveld.",
                        "Real pours and a fresh palate. A day is a calendar day in Oslo. At a table of " + season.bigTableSize() + " or more the limit is "
                                + season.maxTastingsPerDayBigTable() + ", because a party is a different evening."));
        rule(h, l("Alene", "Solo"), l("Høyst " + season.maxSoloDays() + " solodager", "At most " + season.maxSoloDays() + " solo days"),
                l("Smakingen skal skje sammen med andre. Det er hele poenget.", "The tasting is meant to happen in company. That is the point."));
        rule(h, l("Hva teller", "What counts"), l("Juleøl fra Vinmonopolet, over " + abv(), "Juleøl from Vinmonopolet, above " + abv()),
                l("Butikkøl avvises og rangeres aldri.", "Grocery-strength beer is refused and never ranked."));
        rule(h, l("Hjemmebrygg", "Homebrew"),
                l("Over " + abv() + ", scores bare ved et bord med " + Beer.HOMEBREW_MIN_GROUP + " eller flere, rangeres med minst "
                                + Beer.HOMEBREW_MIN_RATINGS + " scorer",
                        "Above " + abv() + ", rated only at a table of " + Beer.HOMEBREW_MIN_GROUP + " or more, ranked with at least "
                                + Beer.HOMEBREW_MIN_RATINGS + " scores"),
                l("Ingen rigger sitt eget.", "Nobody rigs their own."));
        rule(h, l("Tipp pallen", "The podium tip"),
                l("Gull, sølv og bronse: tre forskjellige øl som teller, ett tips per person, frist ved midnatt før " + date(season.end(), false),
                        "Gold, silver and bronze: three different beers that count, one tip each, closing at midnight before " + date(season.end(), false)),
                l(TipScoring.RIGHT_SPOT + " poeng for riktig plass, " + TipScoring.ON_PODIUM + " for et øl på pallen på feil plass, "
                                + TipScoring.MAX + " er perfekt. Står det likt, vinner den som tippet først.",
                        TipScoring.RIGHT_SPOT + " points for the right spot, " + TipScoring.ON_PODIUM + " for a podium beer in the wrong spot, "
                                + TipScoring.MAX + " is perfect. On a tie, the earlier tip wins."));
        rule(h, l("Vekt", "Weight"),
                "<span class=\"w\"><b>" + rookie + "</b> " + l("første sesong, eller under " + RaterWeightPolicy.MIN_SCORES_FOR_FULL_WEIGHT + " scorer sist",
                        "first season, or under " + RaterWeightPolicy.MIN_SCORES_FOR_FULL_WEIGHT + " scores last time") + "</span>"
                        + "<span class=\"w\"><b>" + full + "</b> " + l("ellers", "otherwise") + "</span>"
                        + "<span class=\"w\"><b>" + muted + "</b> " + l("flagget for juks i fjor", "flagged for rigging last time") + "</span>",
                l("Vekt " + muted + " er ingen utestengelse: du smaker, scorer og får merker, men flytter ikke snittet. Fast for hele sesongen. "
                                + "Var du med i de gamle regnearkene? Be om å koble navnet ditt til innloggingen, så teller sist gang du var med.",
                        "A weight of " + muted + " is not a ban: you taste, score and earn badges, you just do not move the average. Fixed for the season. "
                                + "In the old spreadsheets? Ask to link your name to your login, and the last season you took part in counts."));
        h.append("</tbody>\n</table></div>\n</div>\n</section>\n");
    }

    private static void rule(StringBuilder h, String name, String value, String why) {
        h.append("<tr><th scope=\"row\">").append(name).append("</th><td class=\"val\">").append(value)
                .append("</td><td class=\"why\">").append(why).append("</td></tr>\n");
    }

    // ---------------------------------------------------------------- how it works

    private void how(StringBuilder h) {
        String finale = clock(season.finaleTime());
        h.append("<section class=\"band\" aria-labelledby=\"slik\">\n<div class=\"wrap\">\n")
                .append("<p class=\"eyebrow\">").append(l("Slik fungerer det", "How it works")).append("</p>\n")
                .append("<h2 id=\"slik\">").append(l("En kveld rundt bordet", "An evening around the table")).append("</h2>\n")
                .append("<ol class=\"steps\">\n");
        step(h, ICON_QR, l("Åpne et bord", "Open a table"), l(
                "Én er vert. Telefonen viser en QR-kode og en kode på 6 tegn for mørke kjøkken og døde kameraer. De andre skanner eller taster, og dere sitter ved samme bord.",
                "Someone hosts. Their phone shows a QR code and a 6-character code for dim kitchens and dead cameras. The others scan or type it, and you are at the same table."));
        step(h, ICON_LOCK, l("Låst, blind avsløring", "Locked blind reveal"), l(
                "Alle scorer på sin egen telefon. Ingen ser noe før siste person har scoret. Da pakkes terningene opp på alle telefonene i samme sekund, uten at noen laster siden på nytt, sammen med det folk skrev om ølet. Ingen ankrer på den som roper først.",
                "Everyone scores on their own phone. Nobody sees anything until the last person has scored. Then the dice are unwrapped on every phone in the same second, no reloading, along with what people wrote about the beer. No anchoring on whoever shouts first."));
        step(h, ICON_DUEL, l("Dueller på siden", "Duels on the side"), l(
                "Etter to øl: hvilket var best? Svarene gir en egen rangering ved siden av, som ikke bryr seg om hvor raus du er med terningen. Den offisielle rangeringen er terningkastet.",
                "After two beers: which was better? The answers build a separate ranking on the side that does not care how generous you are with the dice. The official ranking is the dice."));
        step(h, ICON_GLASS, l("Blindskjenking", "Blind pour"), l(
                "Verten skjenker ett av ølene på bordet uten å si hvilket. De andre gjetter, og svaret kommer når siste gjetning er inne.",
                "The host pours one of the beers on the table without saying which. The others guess, and the answer comes out when the last guess is in."));
        h.append("</ol>\n");
        group(h, l("Hele adventstiden", "All through Advent"), l(
                "Smakingen er en sesong, ikke en kveld. Det er noe å spille for hele veien.",
                "The tasting is a season, not an evening. There is something to play for all the way."));
        step(h, ICON_TEAM, l("Cantara mot Rebel", "Cantara against Rebel"), l(
                "Etter samtykket velger du lag: Cantara, Rebel eller begge. Trykk på laget for å se hvem som spiller for det. Rangeringen kan vises per lag, hvert lag får sin egen vinner i finalen, "
                        + "og årets lag kåres på terningkast per hode, så et lite lag kan slå et stort. Vil dere stille med et eget lag, spør arrangørene.",
                "After the consent step you pick a team: Cantara, Rebel or both. Tap a team to see who plays for it. The ranking can be shown per team, each team gets its own winner in the finale, "
                        + "and the team of the year is decided by scores per head, so a small team can beat a big one. Want a team of your own? Ask the organisers."));
        step(h, ICON_PODIUM, l("Tipp pallen", "Tip the podium"), l(
                "Hvilke tre øl ender på pallen? Tipp gull, sølv og bronse, og endre tipset så ofte du vil til siste dag begynner. "
                        + "Ingen ser tipsene før finalen, og der kåres årets tipper.",
                "Which three beers make the podium? Tip gold, silver and bronze, and change your tip as often as you like until the last day begins. "
                        + "Nobody sees the tips before the finale, where the tipster of the year is named."));
        step(h, ICON_CAL, l("Adventskalenderen", "The advent calendar"), l(
                "Hjemskjermen er en adventskalender. Hver dag du smaker, åpner en luke, og den holder tellingen på dager på rad. Sitter du ved et bord, ligger det øverst til det er avsluttet. «I kveld» er veggen for hele huset: hvem som smaker hva akkurat nå, med skål og emoji til de andre, men aldri terningkastene deres.",
                "The home screen is an advent calendar. Every day you taste opens a door, and it keeps count of days in a row. While you sit at a table, it stays at the top until the host closes it. \"I kveld\" is the wall for the whole house: who is tasting what right now, with a cheer and an emoji for the others, but never their dice."));
        step(h, ICON_STAR, l("Finalen " + date(season.end(), false), "The finale on " + date(season.end(), false)), l(
                "Klokka " + finale + " tennes stjerna. Cantaras juleøl " + season.year() + " kåres, med duellrangeringen ved siden av, lagenes egne vinnere, tippeligaen og sesongens priser.",
                "At " + finale + " the star lights up. Cantara's juleøl " + season.year() + " is named, with the duel ranking beside it, each team's own winner, the tipping league and the season's awards."));
        h.append("</ol>\n");
        group(h, l("Gjør den til din", "Make it yours"), l(
                "Alt her er valgfritt. Terningen og et glass er nok.",
                "Everything here is optional. The dice and a glass are enough."));
        step(h, ICON_CAMERA, l("Bilde av etiketten", "A photo of the label"), l(
                "Ta gjerne et bilde av etiketten før du smaker. Bildet gjøres mindre og renses for sted og kameradata, og de andre ser det når en arrangør har godkjent det. "
                        + "Det første godkjente blir ølets bilde.",
                "Snap the label before you taste, if you like. The photo is made smaller and stripped of location and camera data, and the others see it once an organiser has approved it. "
                        + "The first approved one becomes the beer's picture."));
        if (nudgeTime != null) {
            step(h, ICON_BELL, l("Et dytt fra kalenderen", "A nudge from the calendar"), l(
                    "Skru på varsler hvis du vil, for hver nettleser: klokka " + clock(nudgeTime) + " de dagene luka di ennå er uåpnet, og én beskjed når stjerna tennes. "
                            + "På iPhone virker det når appen ligger på hjemskjermen.",
                    "Switch on notifications if you like, per browser: at " + clock(nudgeTime) + " on the days your door is still shut, and one message when the star lights up. "
                            + "On an iPhone it works once the app is on the home screen."));
        }
        h.append("</ol>\n<h3 class=\"badges-h\">").append(l("Merker på treet", "Baubles on the tree")).append("</h3>\n<ul class=\"badges\">\n");
        for (Badge b : Badge.values()) {
            h.append("<li><span class=\"bauble\" aria-hidden=\"true\"></span><span><b>").append(esc(b.title)).append("</b> ")
                    .append(esc(en ? b.description : Objects.requireNonNullElse(BADGE_NB.get(b), b.description))).append("</span></li>\n");
        }
        h.append("</ul>\n</div>\n</section>\n");
    }

    private String clock(LocalTime t) {
        return t.format(DateTimeFormatter.ofPattern(en ? "HH:mm" : "HH.mm"));
    }

    /** A sub-heading inside "how it works", with one line under it, then the next list of steps. */
    private static void group(StringBuilder h, String title, String lede) {
        h.append("<h3 class=\"group-h\">").append(title).append("</h3>\n<p class=\"group-p\">").append(lede).append("</p>\n<ol class=\"steps\">\n");
    }

    private static void step(StringBuilder h, String icon, String title, String text) {
        h.append("<li class=\"step\">").append(icon).append("<h4>").append(title).append("</h4><p>").append(text).append("</p></li>\n");
    }

    // ---------------------------------------------------------------- history

    private void history(StringBuilder h) {
        HistoryStats.Totals t = stats.totals();
        List<Integer> aggregateYears = stats.seasons().stream().filter(HistoryStats.Season::aggregate).map(HistoryStats.Season::year).toList();
        String agg = aggregateYears.stream().map(String::valueOf).reduce((a, b) -> a + ", " + b).orElse("");
        h.append("<section aria-labelledby=\"historien\">\n<div class=\"wrap\">\n")
                .append("<p class=\"eyebrow\">").append(l("Historien", "The history")).append("</p>\n")
                .append("<h2 id=\"historien\">").append(l("Protokollen, " + t.firstYear() + "–" + t.lastYear(), "The record, " + t.firstYear() + "–" + t.lastYear())).append("</h2>\n")
                .append("<p class=\"intro\">").append(l(
                        n(t.seasonsWithVotes()) + " sesonger har overlevd med hver eneste stemme, " + agg
                                + " bare som et sammendrag, og mange år er borte. Her er det som kan telles: summer og snitt per år og per øl, aldri hvem som ga hva.",
                        n(t.seasonsWithVotes()) + " seasons survive with every single vote, " + agg
                                + " only as a summary, and many years are lost. Here is what can be counted: totals and means per year and per beer, never who gave what."))
                .append("</p>\n");
        provenance(h, agg);
        h.append("<div class=\"grid\">\n");
        perSeasonChart(h);
        topList(h);
        diceChart(h);
        meanChart(h);
        regularsChart(h);
        h.append("</div>\n</div>\n</section>\n");
    }

    private void provenance(StringBuilder h, String agg) {
        h.append("<aside class=\"provenance\" aria-label=\"").append(l("Om dataene", "About the data")).append("\">\n<h3>")
                .append(l("Om dataene", "About the data")).append("</h3>\n<ul>\n")
                .append("<li>").append(l("Kilden er Cantaras wiki og regnearkene den lenker til. Hvert år og hver kjent feil står i <code>data/SOURCES.md</code>.",
                        "The source is Cantara's wiki and the spreadsheets it links to. Every year and every known quirk is in <code>data/SOURCES.md</code>.")).append("</li>\n")
                .append("<li>").append(l(agg + " finnes bare som en rangert liste med vektet score per øl og antall stemmer per person, uten enkeltstemmer.",
                        agg + " exists only as a ranked list with a weighted score per beer and the number of votes per person, no single votes.")).append("</li>\n")
                .append("<li>").append(l("2008 og 2010 kan ikke leses ut av wikisidene, 1997 har bare vinneren, og for mange år er ingenting funnet.",
                        "2008 and 2010 cannot be read out of the wiki pages, 1997 has only the winner, and for many years nothing was found.")).append("</li>\n")
                .append("<li>").append(l("Vektene varierte (nye teller 0,5, en gruppeoppføring i 2007 teller 2,5, stemmer uten oppgitt vekt teller 1), og både lag og utvalg skiftet fra år til år. Tallene sier noe om hvert år, men er ikke direkte sammenlignbare mellom årene.",
                        "Weights varied (newcomers count 0.5, a group entry in 2007 counts 2.5, votes without a stated weight count 1), and both the panel and the selection changed every year. The numbers describe each year, but are not directly comparable between years.")).append("</li>\n")
                .append("<li>").append(l("Ølnavn står slik de er skrevet hvert år, og er ikke slått sammen.",
                        "Beer names are as written each year, and are not merged.")).append("</li>\n")
                .append("</ul>\n</aside>\n");
    }

    private void perSeasonChart(StringBuilder h) {
        List<HistoryStats.Season> ss = stats.seasons();
        int maxBeers = ss.stream().mapToInt(HistoryStats.Season::beers).max().orElse(1);
        int maxVoters = ss.stream().mapToInt(HistoryStats.Season::voters).max().orElse(1);
        String title = l("Øl og stemmegivere per sesong", "Beers and voters per season");
        h.append("<figure class=\"chart wide\">\n<figcaption><h3>").append(title).append("</h3><p class=\"muted\">")
                .append(l("Hver rad er et år. Stemmegivere teller oppføringene i kilden.", "One row per year. Voters count the entries in the source."))
                .append("</p></figcaption>\n<div class=\"multi\" role=\"img\" aria-label=\"").append(esc(title + ". "
                        + l("Flest øl: ", "Most beers: ") + describeMax(ss, HistoryStats.Season::beers)
                        + ". " + l("Flest stemmegivere: ", "Most voters: ") + describeMax(ss, HistoryStats.Season::voters) + "."))
                .append("\">\n<div class=\"mh\" aria-hidden=\"true\"><span></span><span>").append(l("Øl", "Beers")).append("</span><span>")
                .append(l("Stemmegivere", "Voters")).append("</span></div>\n");
        for (HistoryStats.Season s : ss) {
            h.append("<div class=\"mr").append(s.aggregate() ? " agg" : "").append("\"><span class=\"y\">").append(s.year())
                    .append(s.aggregate() ? "<sup>*</sup>" : "").append("</span>")
                    .append(hbar(s.beers(), maxBeers, s.year() + ": " + n(s.beers()) + l(" øl", " beers"), "a"))
                    .append(hbar(s.voters(), maxVoters, s.year() + ": " + n(s.voters()) + l(" stemmegivere", " voters"), "b"))
                    .append("</div>\n");
        }
        h.append("</div>\n<p class=\"note\"><sup>*</sup> ").append(l("Bare sammendrag: øl fra den rangerte lista, stemmegivere fra antall stemmer per person.",
                "Summary only: beers from the ranked list, voters from the votes cast per person.")).append("</p>\n");
        table(h, l("Vis som tabell", "Show as a table"),
                new String[]{l("År", "Year"), l("Øl", "Beers"), l("Stemmegivere", "Voters"), l("Stemmer", "Votes")},
                ss.stream().map(s -> new String[]{s.year() + (s.aggregate() ? "*" : ""), n(s.beers()), n(s.voters()), n(s.votes())}).toList());
        h.append("</figure>\n");
    }

    private String describeMax(List<HistoryStats.Season> ss, java.util.function.ToIntFunction<HistoryStats.Season> f) {
        HistoryStats.Season m = ss.stream().max(Comparator.comparingInt(f)).orElseThrow();
        return n(f.applyAsInt(m)) + l(" i ", " in ") + m.year();
    }

    private String hbar(int v, int max, String tip, String cls) {
        return "<span class=\"hb " + cls + "\" title=\"" + esc(tip) + "\"><span class=\"fill\" style=\"--v:" + pct(v, max)
                + "\"></span><span class=\"num\">" + n(v) + "</span></span>";
    }

    private static String pct(double v, double max) {
        return String.format(Locale.ROOT, "%.1f%%", max <= 0 ? 0 : 100.0 * v / max);
    }

    private void topList(StringBuilder h) {
        h.append("<figure class=\"chart\">\n<figcaption><h3>").append(l("Årets beste, år for år", "The best of each year")).append("</h3><p class=\"muted\">")
                .append(l("Høyeste vektede snitt blant øl med minst " + stats.minVotesForTop() + " stemmer, på terningen 1–6. Kildenes egen kåring kan avvike.",
                        "Highest weighted mean among beers with at least " + stats.minVotesForTop() + " votes, on the 1–6 dice. The sources' own ranking may differ."))
                .append("</p></figcaption>\n<ol class=\"tops\">\n");
        for (HistoryStats.Season s : stats.seasons()) {
            for (HistoryStats.Top top : s.top()) {
                h.append("<li><span class=\"y\">").append(s.year()).append(s.aggregate() ? "<sup>*</sup>" : "").append("</span><span class=\"b\">")
                        .append(esc(top.beer())).append("</span><span class=\"m\">")
                        .append(top.votes() == null ? "" : "<small>" + n(top.votes()) + l(" stemmer", " votes") + "</small>")
                        .append("<b>").append(d(top.mean(), 2)).append("</b>")
                        .append("</span></li>\n");
            }
        }
        h.append("</ol>\n<p class=\"note\"><sup>*</sup> ").append(l("Arkets egen rangering; antall stemmer er ikke oppgitt, så minstekravet kan ikke sjekkes.",
                "The sheet's own ranking; the number of votes is not stated, so the minimum cannot be checked.")).append("</p>\n</figure>\n");
    }

    private void diceChart(StringBuilder h) {
        Map<String, Integer> dice = stats.dice();
        int max = dice.values().stream().mapToInt(Integer::intValue).max().orElse(1);
        int total = dice.values().stream().mapToInt(Integer::intValue).sum();
        String mode = dice.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("?");
        String title = l("Terningen", "The dice");
        h.append("<figure class=\"chart\">\n<figcaption><h3>").append(title).append("</h3><p class=\"muted\">")
                .append(l("Alle " + n(total) + " kast med hele tall. " + mode + " er det vanligste.",
                        "All " + n(total) + " whole-number scores. " + mode + " is the most common."))
                .append("</p></figcaption>\n<div class=\"cols dice-cols\" role=\"img\" aria-label=\"").append(esc(title + ": "
                        + String.join(", ", java.util.stream.IntStream.rangeClosed(1, 6).mapToObj(i -> i + ": " + n(dice.getOrDefault(String.valueOf(i), 0))).toList())))
                .append("\">\n");
        for (int i = 1; i <= 6; i++) {
            int v = dice.getOrDefault(String.valueOf(i), 0);
            h.append("<div class=\"col").append(String.valueOf(i).equals(mode) ? " hi" : "").append("\" title=\"").append(i).append(": ").append(n(v))
                    .append(" (").append(d(100.0 * v / Math.max(1, total), 0)).append(" %)\"><span class=\"val\">").append(n(v)).append("</span>")
                    .append("<span class=\"area\"><span class=\"bar\" style=\"--v:").append(pct(v, max)).append("\"></span></span><span class=\"x\">").append(face(i)).append("</span></div>\n");
        }
        h.append("</div>\n");
        if (stats.nonIntegerScores() > 0) {
            h.append("<p class=\"note\">").append(l(n(stats.nonIntegerScores()) + " kast med desimaler (for eksempel 4,5) er ikke med her.",
                    n(stats.nonIntegerScores()) + " scores with decimals (such as 4.5) are left out here.")).append("</p>\n");
        }
        table(h, l("Vis som tabell", "Show as a table"), new String[]{l("Kast", "Score"), l("Antall", "Count")},
                java.util.stream.IntStream.rangeClosed(1, 6).mapToObj(i -> new String[]{String.valueOf(i), n(dice.getOrDefault(String.valueOf(i), 0))}).toList());
        h.append("</figure>\n");
    }

    /** A die face as inline SVG, labelled with its number for screen readers via the column title. */
    private static String face(int i) {
        int[][] pips = switch (i) {
            case 1 -> new int[][]{{2, 2}};
            case 2 -> new int[][]{{1, 1}, {3, 3}};
            case 3 -> new int[][]{{1, 1}, {2, 2}, {3, 3}};
            case 4 -> new int[][]{{1, 1}, {3, 1}, {1, 3}, {3, 3}};
            case 5 -> new int[][]{{1, 1}, {3, 1}, {2, 2}, {1, 3}, {3, 3}};
            default -> new int[][]{{1, 1}, {3, 1}, {1, 2}, {3, 2}, {1, 3}, {3, 3}};
        };
        StringBuilder s = new StringBuilder("<svg class=\"face\" viewBox=\"0 0 24 24\" aria-hidden=\"true\"><rect x=\"1\" y=\"1\" width=\"22\" height=\"22\" rx=\"5\"/>");
        for (int[] p : pips) s.append("<circle cx=\"").append(p[0] * 6).append("\" cy=\"").append(p[1] * 6).append("\" r=\"2.1\"/>");
        return s.append("</svg>").toString();
    }

    private void meanChart(StringBuilder h) {
        List<HistoryStats.Season> ss = stats.seasons().stream().filter(s -> s.meanScore() != null).toList();
        if (ss.isEmpty()) return;
        double lo = Math.floor(ss.stream().mapToDouble(HistoryStats.Season::meanScore).min().orElse(1) * 2) / 2;
        double hi = Math.ceil(ss.stream().mapToDouble(HistoryStats.Season::meanScore).max().orElse(6) * 2) / 2;
        if (hi - lo < 1) hi = lo + 1;
        HistoryStats.Season kind = ss.stream().max(Comparator.comparingDouble(HistoryStats.Season::meanScore)).orElseThrow();
        HistoryStats.Season strict = ss.stream().min(Comparator.comparingDouble(HistoryStats.Season::meanScore)).orElseThrow();
        String title = l("Raus eller streng?", "Generous or strict?");
        h.append("<figure class=\"chart\">\n<figcaption><h3>").append(title).append("</h3><p class=\"muted\">")
                .append(l("Snittet av alle kast hvert år, vektet. Rausest: " + kind.year() + " (" + d(kind.meanScore(), 2) + "). Strengest: "
                                + strict.year() + " (" + d(strict.meanScore(), 2) + "). Nye øl og nye folk hvert år, så dette er stemningen, ikke ølkvaliteten.",
                        "The mean of all scores each year, weighted. Most generous: " + kind.year() + " (" + d(kind.meanScore(), 2) + "). Strictest: "
                                + strict.year() + " (" + d(strict.meanScore(), 2) + "). Different beers and people every year, so this is the mood, not the beer."))
                .append("</p></figcaption>\n<div class=\"dots\" role=\"img\" aria-label=\"").append(esc(title + ": "
                        + String.join(", ", ss.stream().map(s -> s.year() + " " + d(s.meanScore(), 2)).toList()))).append("\">\n")
                .append("<div class=\"axis\" aria-hidden=\"true\"><span></span><span class=\"ticks\">");
        for (double v = lo; v <= hi + 1e-9; v += 0.5) {
            h.append("<span style=\"--p:").append(pct(v - lo, hi - lo)).append("\">").append(d(v, 1)).append("</span>");
        }
        h.append("</span><span></span></div>\n");
        for (HistoryStats.Season s : ss) {
            String cls = s == kind ? " kind" : s == strict ? " strict" : "";
            h.append("<div class=\"dr").append(cls).append("\" title=\"").append(s.year()).append(": ").append(d(s.meanScore(), 2)).append("\">")
                    .append("<span class=\"y\">").append(s.year()).append("</span><span class=\"track\"><span class=\"dot\" style=\"--p:")
                    .append(pct(s.meanScore() - lo, hi - lo)).append("\"></span></span><span class=\"num\">").append(d(s.meanScore(), 2)).append("</span></div>\n");
        }
        h.append("</div>\n");
        table(h, l("Vis som tabell", "Show as a table"), new String[]{l("År", "Year"), l("Snitt", "Mean")},
                ss.stream().map(s -> new String[]{String.valueOf(s.year()), d(s.meanScore(), 2)}).toList());
        h.append("</figure>\n");
    }

    private void regularsChart(StringBuilder h) {
        List<HistoryStats.Regular> rs = stats.regulars();
        if (rs.isEmpty()) return;
        int of = stats.totals().seasonsWithVotes();
        String title = l("Stamgjestene", "The regulars");
        h.append("<figure class=\"chart\">\n<figcaption><h3>").append(title).append("</h3><p class=\"muted\">")
                .append(l("Øl som går igjen i flest av de " + of + " sesongene med enkeltstemmer. Navnene er slik de er skrevet, så «Hansa» og «Hansa Juleøl» telles hver for seg.",
                        "Beers that come back in most of the " + of + " seasons with single votes. Names as written, so “Hansa” and “Hansa Juleøl” count separately."))
                .append("</p></figcaption>\n<ul class=\"regs\" role=\"img\" aria-label=\"").append(esc(title + ": "
                        + String.join(", ", rs.stream().map(r -> r.beer() + " " + r.seasons() + l(" sesonger", " seasons")).toList()))).append("\">\n");
        for (HistoryStats.Regular r : rs) {
            h.append("<li title=\"").append(esc(r.beer() + ": " + r.years().stream().map(String::valueOf).reduce((a, b) -> a + ", " + b).orElse("")))
                    .append("\"><span class=\"b\">").append(esc(r.beer())).append("</span><span class=\"hb a\"><span class=\"fill\" style=\"--v:")
                    .append(pct(r.seasons(), of)).append("\"></span><span class=\"num\">").append(r.seasons()).append("/").append(of).append("</span></span>")
                    .append("<span class=\"v\">").append(n(r.votes())).append(l(" stemmer", " votes")).append("</span></li>\n");
        }
        h.append("</ul>\n");
        table(h, l("Vis som tabell", "Show as a table"), new String[]{l("Øl", "Beer"), l("Sesonger", "Seasons"), l("Stemmer", "Votes"), l("År", "Years")},
                rs.stream().map(r -> new String[]{esc(r.beer()), String.valueOf(r.seasons()), n(r.votes()),
                        r.years().stream().map(String::valueOf).reduce((a, b) -> a + ", " + b).orElse("")}).toList());
        h.append("</figure>\n");
    }

    /** The table view under each chart; cells must already be escaped. */
    private static void table(StringBuilder h, String summary, String[] head, List<String[]> rows) {
        h.append("<details><summary>").append(summary).append("</summary><table class=\"data\"><thead><tr>");
        for (String c : head) h.append("<th scope=\"col\">").append(c).append("</th>");
        h.append("</tr></thead><tbody>");
        for (String[] r : rows) {
            h.append("<tr>");
            for (String c : r) h.append("<td>").append(c).append("</td>");
            h.append("</tr>");
        }
        h.append("</tbody></table></details>\n");
    }

    // ---------------------------------------------------------------- privacy and footer

    private void privacy(StringBuilder h) {
        String who = contact.present()
                ? "<a href=\"mailto:" + esc(contact.email().trim()) + "\">" + esc(contact.email().trim()) + "</a>"
                : l("arrangørene", "the organisers");
        h.append("<section class=\"band\" aria-labelledby=\"personvern\">\n<div class=\"wrap two\">\n<div class=\"prose\">\n")
                .append("<p class=\"eyebrow\">").append(l("Personvern", "Privacy")).append("</p>\n")
                .append("<h2 id=\"personvern\">").append(l("Kort fortalt", "In plain words")).append("</h2>\n")
                .append("<p>").append(l("Denne siden henter ingenting fra andre nettsteder, har ingen analyse eller sporing, og viser bare summer fra historikken, ingen navn.",
                        "This page loads nothing from other sites, has no analytics or tracking, and shows only totals from the history, no names.")).append("</p>\n")
                .append("<p><a class=\"more\" href=\"/privacy").append(en ? "?lang=en" : "").append("\">")
                .append(l("Les hele personvernerklæringen", "Read the full privacy notice")).append("</a></p>\n</div>\n<ul class=\"plain\">\n")
                .append("<li>").append(l("Logger du inn, lagrer appen navnet og e-postadressen fra Google-kontoen din, og scorene og notatene du gir. Før du kan bruke appen, ber den om samtykke.",
                        "When you sign in, the app stores the name and email address from your Google account, and the scores and notes you give. Before you can use the app, it asks for your consent.")).append("</li>\n")
                .append("<li>").append(l("Bilder av etiketten er valgfrie. Du eier bildet ditt og kan slette det selv, og de andre ser det først når en arrangør har godkjent det.",
                        "Label photos are optional. The photo is yours and you can delete it yourself, and the others see it only once an organiser has approved it.")).append("</li>\n")
                .append("<li>").append(l("Ved et bord ser dere hverandres scorer når alle har scoret, og hvilket lag hver spiller for. Sesongresultatene vises per øl.",
                        "At a table you see each other's scores once everyone has scored, and which team each of you plays for. Season results are shown per beer.")).append("</li>\n")
                .append("<li>").append(l("Inne i appen viser historikken navnene fra de gamle regnearkene. Du kan be om å få navnet ditt fjernet.",
                        "Inside the app the history shows the names from the old spreadsheets. You can ask to have your name removed.")).append("</li>\n")
                .append("<li>").append(l("Vil du ha dataene dine slettet, ta kontakt med ", "To have your data deleted, get in touch with ")).append(who)
                .append(l(". Arrangørene sletter dem for hånd.", ". The organisers delete it by hand.")).append("</li>\n")
                .append("</ul>\n</div>\n</section>\n");
    }

    private void footer(StringBuilder h) {
        h.append("<footer class=\"foot\">\n<div class=\"wrap\">\n<p class=\"fbrand\">Julebrus <span>· ")
                .append(l("Cantaras juleølsmaking", "Cantara's Christmas beer tasting")).append("</span></p>\n")
                .append("<p class=\"cta\"><a class=\"btn\" href=\"/oauth2/authorization/google\">").append(SIGN_IN)
                .append(l("Logg inn med Google", "Sign in with Google")).append("</a></p>\n<p class=\"links\">");
        String line = sponsorLine();
        if (!line.isEmpty()) h.append("<span>").append(line).append("</span>");
        h.append("<a href=\"/privacy").append(en ? "?lang=en" : "").append("\">").append(l("Personvern", "Privacy")).append("</a>")
                .append("<a href=\"https://github.com/Cantara/julebrus\" rel=\"noopener\">").append(l("Kildekode og data", "Source code and data")).append("</a>")
                .append(langSwitch()).append("</p>\n</div>\n</footer>\n");
    }

    // ---------------------------------------------------------------- inline art and style

    /** A plain "enter" arrow for the sign-in button (no third-party logo). */
    private static final String SIGN_IN = "<svg class=\"g\" viewBox=\"0 0 24 24\" aria-hidden=\"true\"><path d=\"M10 6l6 6-6 6M16 12H3M14 3h5a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-5\"/></svg>";

    private static final String DIE_SIX = """
            <svg class="die" viewBox="0 0 120 120" aria-hidden="true">
            <defs><linearGradient id="dg" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#fffaf1"/><stop offset="1" stop-color="#ead9bd"/></linearGradient></defs>
            <rect x="10" y="10" width="100" height="100" rx="24" fill="url(#dg)"/>
            <g fill="#7a1f2b"><circle cx="38" cy="36" r="9"/><circle cx="82" cy="36" r="9"/><circle cx="38" cy="60" r="9"/><circle cx="82" cy="60" r="9"/><circle cx="38" cy="84" r="9"/><circle cx="82" cy="84" r="9"/></g>
            </svg>
            """;

    private static final String ICON_QR = icon("<path d=\"M4 4h6v6H4zM14 4h6v6h-6zM4 14h6v6H4z\"/><path d=\"M14 14h2v2h-2zM18 14h2v2h-2zM14 18h2v2h-2zM18 18h2v2h-2z\"/>");
    private static final String ICON_LOCK = icon("<rect x=\"5\" y=\"11\" width=\"14\" height=\"9\" rx=\"2\"/><path d=\"M8 11V8a4 4 0 0 1 8 0v3\"/>");
    private static final String ICON_DUEL = icon("<path d=\"M5 19 19 5M14 5h5v5M19 19 5 5M5 10V5h5\"/>");
    private static final String ICON_GLASS = icon("<path d=\"M7 4h10l-1 9a4 4 0 0 1-8 0zM12 17v3M9 20h6\"/><path d=\"M7.6 8h8.8\"/>");
    private static final String ICON_CAL = icon("<rect x=\"4\" y=\"5\" width=\"16\" height=\"15\" rx=\"2\"/><path d=\"M4 10h16M9 3v4M15 3v4M9 14h2v2H9z\"/>");
    private static final String ICON_TEAM = icon("<path d=\"M5 21V4h11l-2 4 2 4H5\"/>");
    private static final String ICON_PODIUM = icon("<path d=\"M3 20h18M9 20V8h6v12M3 20v-7h6M15 20v-9h6v9\"/>");
    private static final String ICON_CAMERA = icon("<path d=\"M4 8h3l2-3h6l2 3h3v11H4z\"/><circle cx=\"12\" cy=\"13\" r=\"3.5\"/>");
    private static final String ICON_BELL = icon("<path d=\"M6 16v-5a6 6 0 0 1 12 0v5l2 2H4zM10 21h4\"/>");
    private static final String ICON_STAR = icon("<path d=\"m12 3 2.6 5.6 6 .7-4.5 4.1 1.2 6L12 16.4 6.7 19.4l1.2-6L3.4 9.3l6-.7z\"/>");

    private static String icon(String body) {
        return "<svg class=\"ic\" viewBox=\"0 0 24 24\" aria-hidden=\"true\">" + body + "</svg>";
    }

    private static final String CSS = LandingCss.CSS;
}
