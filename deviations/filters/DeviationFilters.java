package com.evertracker.coronatower.api.deviations.filters;

import com.evertracker.coronatower.api.databinding.AbstractFilterBinder;
import com.evertracker.coronatower.common.deviations.deviations.DeviationType;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.val;
import org.apache.logging.log4j.util.Strings;
import org.springframework.util.MultiValueMap;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class DeviationFilters extends AbstractFilterBinder {
  public Integer companyId;
  public Boolean activeShipments;

  @Parameter(in = ParameterIn.QUERY, description = "Departure Date", name = "departureDate")
  public Instant departureDate;

  @Parameter(
      in = ParameterIn.QUERY,
      description = "Departure ZoneId",
      name = "departZoneId",
      schema = @Schema(type = "string"))
  public ZoneId departZoneId;

  @Parameter(in = ParameterIn.QUERY, description = "Arrive Date", name = "arrivalDate")
  public Instant arrivalDate;

  @Parameter(
      in = ParameterIn.QUERY,
      description = "Arrive ZoneId",
      name = "arriveZoneId",
      schema = @Schema(type = "string"))
  public ZoneId arriveZoneId;

  @Parameter(
      in = ParameterIn.QUERY,
      description = "Plan Version Id",
      name = DeviationFilterParameters.PLAN_VERSION_ID)
  public List<String> planVersionIds;

  @Parameter(
      in = ParameterIn.QUERY,
      description = "Carrier Id",
      name = DeviationFilterParameters.CARRIER_ID)
  public List<String> carrierIds;

  @Parameter(
      in = ParameterIn.QUERY,
      description = "Deviation Type",
      name = DeviationFilterParameters.DEVIATION_TYPE)
  public List<DeviationType> deviationTypes;

  @Parameter(
      in = ParameterIn.QUERY,
      description = "Departure Date Interval",
      name = DeviationFilterParameters.DEPARTURE)
  public List<Instant> departures;

  @Parameter(
      in = ParameterIn.QUERY,
      description = "Arrival Date Interval",
      name = DeviationFilterParameters.ARRIVAL)
  public List<Instant> arrivals;

  @Override
  protected final void bindInternal(MultiValueMap<String, String> queryParameters) {
    planVersionIds = queryParameters.get(DeviationFilterParameters.PLAN_VERSION_ID);
    carrierIds = queryParameters.get(DeviationFilterParameters.CARRIER_ID);
    val deviationTypesStr = queryParameters.get(DeviationFilterParameters.DEVIATION_TYPE);
    if (deviationTypesStr != null) {
      deviationTypes = new ArrayList<>(deviationTypesStr.size());
      for (String s : deviationTypesStr) deviationTypes.add(DeviationType.valueOf(s));
    }

    val departureDateString = queryParameters.getFirst("departureDate");
    val departureZoneIdString = queryParameters.getFirst("departureZoneId");

    if (Strings.isNotBlank(departureDateString))
      this.departureDate = Instant.from(DateTimeFormatter.ISO_DATE_TIME.parse(departureDateString));
    if (Strings.isNotBlank(departureZoneIdString))
      this.departZoneId = ZoneId.of(departureZoneIdString);

    val arrivalDateString = queryParameters.getFirst("arrivalDate");
    val arrivalZoneIdString = queryParameters.getFirst("arrivalZoneId");

    if (Strings.isNotBlank(arrivalDateString))
      this.arrivalDate = Instant.from(DateTimeFormatter.ISO_DATE_TIME.parse(arrivalDateString));
    if (Strings.isNotBlank(arrivalZoneIdString)) this.arriveZoneId = ZoneId.of(arrivalZoneIdString);

    if (queryParameters.containsKey(DeviationFilterParameters.DEPARTURE)) {
      val departure = queryParameters.get(DeviationFilterParameters.DEPARTURE);
      if (departure.size() == 2) {
        departures = new ArrayList<>();
        departures.add(Instant.from(DateTimeFormatter.ISO_DATE_TIME.parse(departure.get(0))));
        departures.add(Instant.from(DateTimeFormatter.ISO_DATE_TIME.parse(departure.get(1))));
        departures.sort(Comparator.comparingLong(Instant::toEpochMilli));
      }
    }
    if (queryParameters.containsKey(DeviationFilterParameters.ARRIVAL)) {
      val arrival = queryParameters.get(DeviationFilterParameters.ARRIVAL);
      if (arrival.size() == 2) {
        arrivals = new ArrayList<>();
        arrivals.add(Instant.from(DateTimeFormatter.ISO_DATE_TIME.parse(arrival.get(0))));
        arrivals.add(Instant.from(DateTimeFormatter.ISO_DATE_TIME.parse(arrival.get(1))));
        arrivals.sort(Comparator.comparingLong(Instant::toEpochMilli));
      }
    }
  }
}
