package no.cantara.julebrus.vinmonopolet;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface VoteMatchRepository extends JpaRepository<VoteMatch, UUID>, JpaSpecificationExecutor<VoteMatch> {

    List<VoteMatch> findByYearAndMachineStatusAndDecisionIsNull(int year, String machineStatus);

    @Query("select m.machineStatus, count(m), sum(case when m.decision is null then 1 else 0 end) from VoteMatch m group by m.machineStatus")
    List<Object[]> countByStatus();

    @Query("select m.decision, count(m) from VoteMatch m where m.decision is not null group by m.decision")
    List<Object[]> countByDecision();

    @Query("select m from VoteMatch m where m.decision is not null order by m.year, m.voteBeer")
    List<VoteMatch> findDecided();
}
