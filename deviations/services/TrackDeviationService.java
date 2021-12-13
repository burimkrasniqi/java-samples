package com.evertracker.coronatower.api.deviations.services;

import com.evertracker.coronatower.api.deviations.dtos.DeviationOverviewResDto;
import com.evertracker.coronatower.api.deviations.dtos.PlanDeviationResDto;
import com.evertracker.coronatower.api.deviations.dtos.PlanDeviationSegmentResDto;
import com.evertracker.coronatower.api.deviations.dtos.PlanDeviationShipmentResDto;
import com.evertracker.coronatower.api.deviations.filters.DeviationFilters;
import com.evertracker.coronatower.api.deviations.repositories.TrackDeviationRepo;
import com.evertracker.coronatower.api.monitoring.plans.services.PlanVersionService;
import com.evertracker.coronatower.common.deviations.deviations.DeviationType;
import lombok.AllArgsConstructor;
import lombok.val;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

@Service
@AllArgsConstructor
public class TrackDeviationService {
  private final TrackDeviationRepo repo;
  private final PlanVersionService planVersionService;

  public Mono<DeviationOverviewResDto> getDeviationOverview(DeviationFilters filters) {
    val dto = new DeviationOverviewResDto();
    dto.deviations =
        List.of(
            new DeviationOverviewResDto.Deviation(DeviationType.EARLY, 0),
            new DeviationOverviewResDto.Deviation(DeviationType.LATE, 0));

    return repo.getDeviatedPlanVersions(filters, null)
        .reduce(
            dto,
            (result, planDeviationResDto) -> {
              val early = result.deviations.get(0);
              val late = result.deviations.get(1);
              early.count += planDeviationResDto.earlyCount;
              late.count += planDeviationResDto.lateCount;

              return result;
            })
        .zipWith(repo.countTrackPlans(filters))
        .map(
            tuple -> {
              val result = tuple.getT1();
              result.totalMatchesCount = tuple.getT2();

              for (DeviationOverviewResDto.Deviation deviation : result.deviations)
                result.deviationsCount += deviation.count;

              return result;
            });
  }

  public Flux<PlanDeviationResDto> getDeviatedPlanVersions(
      DeviationFilters filters, Pageable pageable) {
    return repo.getDeviatedPlanVersions(filters, pageable);
  }

  public Flux<PlanDeviationSegmentResDto> getDeviatedPlanVersionSegments(
      Integer companyId, String planVersionId) {

    return repo.getDeviatedPlanVersionSegments(companyId, planVersionId);
  }

  public Flux<PlanDeviationShipmentResDto> getDeviatedPlanVersionShipments(
      String planVersionId, DeviationFilters filters, Pageable pageable) {
    filters.planVersionIds = List.of(planVersionId);

    return getDeviatedPlanVersionShipmentsColumns(planVersionId)
        .flatMapMany(
            columns ->
                repo.getDeviatedPlanVersionShipments(filters, pageable)
                    .switchOnFirst(
                        (signal, it) -> {
                          if (signal.hasValue()) signal.get().columns = columns;
                          return it;
                        }));
  }

  /**
   * @param planVersionId - Plan version id
   * @return columns for the shipment deviations.
   */
  private Mono<List<String>> getDeviatedPlanVersionShipmentsColumns(String planVersionId) {
    return planVersionService
        .getPlanVersionSegments(planVersionId)
        .map(
            planSegments -> {
              val columns = new ArrayList<String>();

              for (int i = 0; i < planSegments.size(); i++) {
                val planSegment = planSegments.get(i);
                if (planSegment.sameLocation) {
                  if (i == 0) {
                    // if the same location segment is in very beginning
                    columns.add(planSegment.from.name);
                    columns.add("Duration");

                  } else if (i == planSegments.size() - 1) {
                    // if the same location segment is in last position
                    columns.add("Duration");
                    columns.add(planSegment.to.name);
                  } else {
                    columns.add("Duration");
                  }
                } else {
                  columns.add(planSegment.from.name);
                  columns.add("Duration"); // todo add translation key
                  columns.add(planSegment.to.name);
                }
              }

              return columns;
            });
  }
}
