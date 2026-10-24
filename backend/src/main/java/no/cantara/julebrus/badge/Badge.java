package no.cantara.julebrus.badge;

public enum Badge {
    FIRST_BLOOD("First Blood", "Your first tasting of the season"),
    FULL_WEEK("Full Week", "Tasted on seven days in a row"),
    EXPLORER("Explorer", "Rated a beer fewer than three others had tasted"),
    LONE_WOLF("Lone Wolf", "Used all your solo days"),
    SOCIAL_BUTTERFLY("Social Butterfly", "Tasted with ten different people"),
    CONTRARIAN("Contrarian", "Furthest from the crowd, on at least ten beers"),
    ADVENT("Advent", "Rated every day from 1 to 23 December"),
    SOMMELIER("Sommelier", "Predicted the group average within half a point, ten times"),
    CLOSING_TIME("Closing Time", "A rating on 23 December");

    public final String title;
    public final String description;

    Badge(String title, String description) {
        this.title = title;
        this.description = description;
    }
}
