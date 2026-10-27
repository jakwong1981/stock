package com.cyberstock.instrument;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InstrumentRepository extends JpaRepository<Instrument, String> {

    List<Instrument> findByActiveTrueOrderBySymbol();
}
