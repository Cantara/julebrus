package no.cantara.julebrus.history;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V15 puts one 2010 vote (Eirik, Aass Juleøl premium, 5) into a database that already holds 2010, because the history importer
 * reads a season once. A fresh database imports history/2010.csv, which has the vote, so V15 must do nothing there and must never run
 * a second time to the same effect. (Also run against PostgreSQL 16 by hand: fresh, production-like, rerun, already present.)
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class HistoryMigrationV15Test {

    @Autowired
    JdbcTemplate jdbc;

    String sql() throws Exception {
        return new ClassPathResource("db/migration/V15__history_2010_missing_vote.sql").getContentAsString(StandardCharsets.UTF_8);
    }

    long eirikPremium() {
        return jdbc.queryForObject("select count(*) from historic_ratings where season_year = 2010 and rater_name = 'Eirik' "
                + "and lower(beer_name) = 'aass juleøl premium'", Long.class);
    }

    @Test
    void theImportedSeasonAlreadyHasTheVoteSoTheMigrationLeavesItAlone() throws Exception {
        assertThat(eirikPremium()).isEqualTo(1);
        long before = jdbc.queryForObject("select count(*) from historic_ratings", Long.class);
        jdbc.execute(sql());
        assertThat(eirikPremium()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from historic_ratings", Long.class)).isEqualTo(before);
    }

    @Test
    void aDatabaseThatImportedTheOldSnapshotGetsTheVoteExactlyOnce() throws Exception {
        jdbc.update("delete from historic_ratings where season_year = 2010 and rater_name = 'Eirik' and lower(beer_name) = 'aass juleøl premium'");
        assertThat(eirikPremium()).isZero();
        jdbc.execute(sql());
        jdbc.execute(sql());
        assertThat(eirikPremium()).isEqualTo(1);
        var row = jdbc.queryForMap("select score, weight from historic_ratings where season_year = 2010 and rater_name = 'Eirik' "
                + "and lower(beer_name) = 'aass juleøl premium'");
        assertThat(((Number) row.get("score")).doubleValue()).isEqualTo(5.0);
        assertThat(((Number) row.get("weight")).doubleValue()).isEqualTo(1.0);
    }

    @Test
    void aDatabaseWithoutAny2010RowsIsLeftAlone() throws Exception {
        // a fresh database: the migration runs before the importer, and a row inserted here would make the importer skip the season
        jdbc.update("update historic_ratings set beer_id = null where season_year = 2010");
        jdbc.update("delete from historic_ratings where season_year = 2010");
        jdbc.execute(sql());
        assertThat(jdbc.queryForObject("select count(*) from historic_ratings where season_year = 2010", Long.class)).isZero();
    }
}
