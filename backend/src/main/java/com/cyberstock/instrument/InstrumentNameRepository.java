package com.cyberstock.instrument;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InstrumentNameRepository extends JpaRepository<InstrumentName, InstrumentName.Key> {

    @Query("select n from InstrumentName n")
    List<InstrumentName> findAllNames();

    /**
     * Matches the query against names in every locale, so searching in Simplified Chinese
     * also finds instruments whose Traditional name differs (e.g. 腾讯 vs 騰訊).
     */
    @Query("""
            select distinct n.id.symbol from InstrumentName n
            where lower(n.name) like lower(concat('%', :q, '%'))
               or lower(n.shortName) like lower(concat('%', :q, '%'))
               or lower(n.id.symbol) like lower(concat('%', :q, '%'))
            """)
    List<String> searchSymbols(@Param("q") String q);
}
