package com.evertracker.coronatower.api.deviations.controllers;

import com.evertracker.coronatower.api.auth.annotations.Authorized;
import com.evertracker.coronatower.api.databinding.annotation.Include;
import com.evertracker.coronatower.api.deviations.dtos.DeviationOverviewResDto;
import com.evertracker.coronatower.api.deviations.dtos.PlanDeviationResDto;
import com.evertracker.coronatower.api.deviations.dtos.PlanDeviationSegmentResDto;
import com.evertracker.coronatower.api.deviations.dtos.PlanDeviationShipmentResDto;
import com.evertracker.coronatower.api.deviations.filters.DeviationFilters;
import com.evertracker.coronatower.api.deviations.services.TrackDeviationService;
import com.evertracker.coronatower.common.users.entities.User;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@AllArgsConstructor
@RequestMapping(value = "/deviations", produces = MediaType.APPLICATION_JSON_VALUE)
public class TrackDeviationController {
  private final TrackDeviationService trackDeviationService;

  @Authorized
  @RequestMapping(value = "/overview", method = RequestMethod.GET)
  public Mono<DeviationOverviewResDto> getDeviationOverview(
      @Include User user, @Include DeviationFilters filters) {
    filters.companyId = user.companyId;

    return trackDeviationService.getDeviationOverview(filters);
  }

  @Authorized
  @RequestMapping(value = "/plan-versions", method = RequestMethod.GET)
  public Flux<PlanDeviationResDto> getDeviatedPlanVersions(
      @Include User user,
      @Include DeviationFilters filters,
      @Parameter(hidden = true) Pageable pageable) {
    filters.companyId = user.companyId;

    return trackDeviationService.getDeviatedPlanVersions(filters, pageable);
  }

  @Authorized
  @RequestMapping(value = "/plan-versions/{planVersionId}/segments", method = RequestMethod.GET)
  public Flux<PlanDeviationSegmentResDto> getDeviatedPlanVersionSegments(
      @PathVariable String planVersionId, @Include User user) {

    return trackDeviationService.getDeviatedPlanVersionSegments(user.companyId, planVersionId);
  }

  @Authorized
  @RequestMapping(value = "/plan-versions/{planVersionId}/shipments", method = RequestMethod.GET)
  public Flux<PlanDeviationShipmentResDto> getDeviatedPlanVersionShipments(
      @PathVariable String planVersionId,
      @Include User user,
      @Include DeviationFilters filters,
      @Parameter(hidden = true) Pageable pageable) {
    filters.companyId = user.companyId;

    return trackDeviationService.getDeviatedPlanVersionShipments(planVersionId, filters, pageable);
  }
}
