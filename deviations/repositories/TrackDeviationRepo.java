package com.evertracker.coronatower.api.deviations.repositories;

import com.evertracker.coronatower.api.deviations.dtos.PlanDeviationResDto;
import com.evertracker.coronatower.api.deviations.dtos.PlanDeviationSegmentResDto;
import com.evertracker.coronatower.api.deviations.dtos.PlanDeviationShipmentResDto;
import com.evertracker.coronatower.api.deviations.filters.DeviationFilters;
import com.evertracker.coronatower.common.base.BaseRepo;
import com.evertracker.coronatower.common.containers.entities.Container;
import com.evertracker.coronatower.common.deviations.deviations.DeviationType;
import com.evertracker.coronatower.common.monitoring.trackplans.entities.TrackPlan;
import com.evertracker.coronatower.common.pagingfilter.PagingHeaderFilter;
import com.evertracker.coronatower.common.pagingfilter.PagingMeta;
import com.evertracker.coronatower.common.shipments.containers.entities.MonitoringStatus;
import lombok.val;
import org.apache.logging.log4j.util.Strings;
import org.bson.Document;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.evertracker.coronatower.common.monitoring.trackplans.entities.TrackPlan.*;
import static com.evertracker.coronatower.common.utils.MongoUtils.$;
import static com.evertracker.coronatower.common.utils.MongoUtils._id;
import static org.springframework.data.mongodb.core.aggregation.ComparisonOperators.valueOf;
import static org.springframework.data.mongodb.core.aggregation.ConditionalOperators.Switch.CaseOperator.when;

@Repository
public class TrackDeviationRepo extends BaseRepo {

  public Flux<PlanDeviationResDto> getDeviatedPlanVersions(
      DeviationFilters filters, Pageable pageable) {

    val containerMatch = Aggregation.match(getContainerFilters(filters));
    val lookup =
        Aggregation.lookup(
            TrackPlan.COLLECTION, Container.TRACK_ID, _id(TrackPlan.TRACK_ID), "temp");
    val replaceRoot =
        Aggregation.replaceRoot(ArrayOperators.ArrayElemAt.arrayOf("temp").elementAt(0));

    SkipOperation skip = null;
    LimitOperation limit = null;

    if (pageable != null) {
      if (pageable.getPageNumber() != 0)
        skip = Aggregation.skip((long) pageable.getPageNumber() * pageable.getPageSize());
      limit = Aggregation.limit(pageable.getPageSize());
    }

    val planCriteria = getFiltersCriteria(filters, TrackPlan.PLAN_VERSION_NAME);
    val match = Aggregation.match(planCriteria);

    val project =
        Aggregation.project(TrackPlan.class)
            .andInclude(
                Fields.from(
                    Fields.field(TrackPlan.PLAN_VERSION_ID, _id(TrackPlan.PLAN_VERSION_ID)),
                    Fields.field(TrackPlan.PLAN_ID, _id(TrackPlan.PLAN_ID)),
                    Fields.field(TrackPlan.REFERENCE_ID, _id(TrackPlan.REFERENCE_ID)),
                    Fields.field(TrackPlan.PLAN_VERSION_NAME, TrackPlan.PLAN_VERSION_NAME),
                    Fields.field(TrackPlan.DEPART_AT, TrackPlan.DEPART_AT),
                    Fields.field(TrackPlan.ARRIVE_AT, TrackPlan.ARRIVE_AT),
                    Fields.field(CURRENT_DEVIATED_ADDRESS, CURRENT_DEVIATED_ADDRESS),
                    Fields.field(CURRENT_DEVIATED_PLAN_STEP_TYPE, CURRENT_DEVIATED_PLAN_STEP_TYPE)))
            .and(
                context ->
                    new Document(
                        "$year",
                        new Document(
                            "$add",
                            List.of(
                                $(DEPART_AT_TIMESTAMP),
                                new Document(
                                    "$multiply", List.of($(DEPART_AT_OFFSET_SECONDS), 1000))))))
            .as("departAtYear")
            .and(
                context ->
                    new Document(
                        "$dayOfYear",
                        new Document(
                            "$add",
                            List.of(
                                $(DEPART_AT_TIMESTAMP),
                                new Document(
                                    "$multiply", List.of($(DEPART_AT_OFFSET_SECONDS), 1000))))))
            .as("departAtDayOfYear")
            .and(
                context ->
                    new Document(
                        "$year",
                        new Document(
                            "$add",
                            List.of(
                                $(ARRIVE_AT_TIMESTAMP),
                                new Document(
                                    "$multiply", List.of($(ARRIVE_AT_OFFSET_SECONDS), 1000))))))
            .as("arriveAtYear")
            .and(
                context ->
                    new Document(
                        "$dayOfYear",
                        new Document(
                            "$add",
                            List.of(
                                $(ARRIVE_AT_TIMESTAMP),
                                new Document(
                                    "$multiply", List.of($(ARRIVE_AT_OFFSET_SECONDS), 1000))))))
            .as("arriveAtDayOfYear");

    val basicGroupOperation =
        Aggregation.group(
            Fields.from(
                Fields.field("g1", TrackPlan.PLAN_VERSION_ID),
                Fields.field("g2", TrackPlan.DEPART_AT_ZONE_ID),
                Fields.field("g3", TrackPlan.ARRIVE_AT_ZONE_ID),
                Fields.field("g4", "departAtYear"),
                Fields.field("g5", "departAtDayOfYear"),
                Fields.field("g6", "arriveAtYear"),
                Fields.field("g7", "arriveAtDayOfYear")));

    val groupOperation =
        basicGroupOperation
            .first(TrackPlan.PLAN_ID)
            .as(PlanDeviationResDto.PLAN_ID)
            .first(TrackPlan.PLAN_VERSION_ID)
            .as(PlanDeviationResDto.PLAN_VERSION_ID)
            .first(TrackPlan.PLAN_VERSION_NAME)
            .as(PlanDeviationResDto.PLAN_NAME)
            .first(TrackPlan.DEPART_AT)
            .as(PlanDeviationResDto.DEPART_AT)
            .first(TrackPlan.ARRIVE_AT)
            .as(PlanDeviationResDto.ARRIVE_AT)
            .sum(
                ConditionalOperators.switchCases(
                        when(valueOf(CURRENT_DEVIATED_PLAN_STEP_TYPE)
                                .equalToValue(DeviationType.EARLY.name()))
                            .then(1))
                    .defaultTo(0))
            .as(PlanDeviationResDto.EARLY_COUNT)
            .sum(
                ConditionalOperators.switchCases(
                        when(valueOf(CURRENT_DEVIATED_PLAN_STEP_TYPE)
                                .equalToValue(DeviationType.LATE.name()))
                            .then(1))
                    .defaultTo(0))
            .as(PlanDeviationResDto.LATE_COUNT)
            .addToSet(
                new Document(
                    "$switch",
                    new Document(
                            "branches",
                            List.of(
                                new Document()
                                    .append(
                                        "case", $(CURRENT_DEVIATED_ADDRESS_POSTAL_FORMATTED_NAME))
                                    .append(
                                        "then", $(CURRENT_DEVIATED_ADDRESS_POSTAL_FORMATTED_NAME)),
                                new Document()
                                    .append(
                                        "case", $(CURRENT_DEVIATED_ADDRESS_PLAIN_POSTAL_ADDRESS))
                                    .append(
                                        "then", $(CURRENT_DEVIATED_ADDRESS_PLAIN_POSTAL_ADDRESS)),
                                new Document()
                                    .append("case", $(CURRENT_DEVIATED_ADDRESS_UN_LO_CODE))
                                    .append("then", $(CURRENT_DEVIATED_ADDRESS_UN_LO_CODE)),
                                new Document()
                                    .append("case", $(CURRENT_DEVIATED_ADDRESS_LOCATION))
                                    .append(
                                        "then",
                                        new Document(
                                            "$concat",
                                            List.of(
                                                "GPS: ",
                                                new Document(
                                                    "$toString",
                                                    new Document(
                                                        "$arrayElemAt",
                                                        List.of(
                                                            $(
                                                                CURRENT_DEVIATED_ADDRESS_LOCATION_COORDINATES),
                                                            1))),
                                                ", ",
                                                new Document(
                                                    "$toString",
                                                    new Document(
                                                        "$arrayElemAt",
                                                        List.of(
                                                            $(
                                                                CURRENT_DEVIATED_ADDRESS_LOCATION_COORDINATES),
                                                            0))))))))
                        .append("default", null)))
            .as(PlanDeviationResDto.POINTS_OF_DEVIATION);

    val sort =
        Aggregation.sort(
            Sort.Direction.ASC,
            PlanDeviationResDto.PLAN_NAME,
            PlanDeviationResDto.DEPART_AT,
            PlanDeviationResDto.ARRIVE_AT,
            PlanDeviationResDto.EARLY_COUNT,
            PlanDeviationResDto.LATE_COUNT);

    val operations = new ArrayList<AggregationOperation>();
    operations.add(containerMatch);
    operations.add(lookup);
    operations.add(replaceRoot);
    operations.add(match);
    operations.add(project);
    operations.add(groupOperation);
    operations.add(sort);
    if (skip != null) operations.add(skip);
    if (limit != null) operations.add(limit);

    val aggregation = Aggregation.newAggregation(operations);
    val req = reactiveMongo.aggregate(aggregation, Container.class, PlanDeviationResDto.class);

    if (pageable != null) {
      val countOperations = new ArrayList<AggregationOperation>();
      countOperations.add(containerMatch);
      countOperations.add(lookup);
      countOperations.add(replaceRoot);
      countOperations.add(match);
      countOperations.add(project);
      countOperations.add(basicGroupOperation);

      val countAggregation = Aggregation.newAggregation(countOperations);
      return Flux.merge(setPaging(countAggregation, pageable, Container.class), req);
    }

    return req;
  }

  public Mono<Long> countTrackPlans(DeviationFilters filters) {
    val criteria = getContainerFilters(filters);
    return reactiveMongo.count(Query.query(criteria), Container.class);
  }

  public Flux<PlanDeviationSegmentResDto> getDeviatedPlanVersionSegments(
      Integer companyId, String planVersionId) {
    val criteria =
        Criteria.where(TrackPlan.COMPANY_ID)
            .is(companyId)
            .and(TrackPlan.PLAN_VERSION_ID)
            .is(planVersionId);

    val match = Aggregation.match(criteria);

    val unwind = Aggregation.unwind(TrackPlan.SEGMENTS);

    val sortDurations = Aggregation.sort(Sort.Direction.ASC, TrackPlan.SEGMENTS_DURATION);

    val groupOperation =
        Aggregation.group(Fields.from(Fields.field(TrackPlan.SEGMENTS_ID)))
            .first(TrackPlan.SEGMENTS_ID)
            .as(PlanDeviationSegmentResDto.ID)
            .first(TrackPlan.SEGMENTS_SAME_LOCATION)
            .as(PlanDeviationSegmentResDto.SAME_LOCATION)
            .first(TrackPlan.SEGMENTS_FROM_NAME)
            .as(PlanDeviationSegmentResDto.FROM)
            .first(TrackPlan.SEGMENTS_TO_NAME)
            .as(PlanDeviationSegmentResDto.TO)
            .min(TrackPlan.SEGMENTS_DURATION)
            .as(PlanDeviationSegmentResDto.MIN)
            .max(TrackPlan.SEGMENTS_DURATION)
            .as(PlanDeviationSegmentResDto.MAX)
            .avg(TrackPlan.SEGMENTS_DURATION)
            .as(PlanDeviationSegmentResDto.AVG)
            .push(TrackPlan.SEGMENTS_DURATION)
            .as("durations");

    val project =
        Aggregation.project(PlanDeviationSegmentResDto.class)
            .andInclude(
                Fields.from(
                    Fields.field(PlanDeviationSegmentResDto.ID, PlanDeviationSegmentResDto.ID),
                    Fields.field(PlanDeviationSegmentResDto.MIN, PlanDeviationSegmentResDto.MIN),
                    Fields.field(PlanDeviationSegmentResDto.MAX, PlanDeviationSegmentResDto.MAX),
                    Fields.field(PlanDeviationSegmentResDto.AVG, PlanDeviationSegmentResDto.AVG),
                    Fields.field(PlanDeviationSegmentResDto.FROM, PlanDeviationSegmentResDto.FROM),
                    Fields.field(PlanDeviationSegmentResDto.TO, PlanDeviationSegmentResDto.TO),
                    Fields.field(
                        PlanDeviationSegmentResDto.SAME_LOCATION,
                        PlanDeviationSegmentResDto.SAME_LOCATION)))
            .and(
                context ->
                    new Document(
                        "$ifNull",
                        List.of(
                            new Document(
                                "$avg",
                                List.of(
                                    new Document(
                                        "$arrayElemAt",
                                        List.of(
                                            "$durations",
                                            new Document(
                                                "$ceil",
                                                new Document(
                                                    "$divide",
                                                    List.of(
                                                        new Document(
                                                            "$add",
                                                            List.of(
                                                                new Document("$size", "$durations"),
                                                                1)),
                                                        2))))),
                                    new Document(
                                        "$arrayElemAt",
                                        List.of(
                                            "$durations",
                                            new Document(
                                                "$floor",
                                                new Document(
                                                    "$divide",
                                                    List.of(
                                                        new Document(
                                                            "$add",
                                                            List.of(
                                                                new Document("$size", "$durations"),
                                                                1)),
                                                        2))))))),
                            new Document("$arrayElemAt", List.of("$durations", 0)))))
            .as(PlanDeviationSegmentResDto.MEDIAN);

    val sort = Aggregation.sort(Sort.Direction.ASC, PlanDeviationSegmentResDto.ID);

    val aggregation =
        Aggregation.newAggregation(match, unwind, sortDurations, groupOperation, project, sort);

    return reactiveMongo.aggregate(aggregation, TrackPlan.class, PlanDeviationSegmentResDto.class);
  }

  // region
  public Flux<PlanDeviationShipmentResDto> getDeviatedPlanVersionShipments(
      DeviationFilters filters, Pageable pageable) {

    return getDeviatedPlanVersionShipmentsInternal(filters, pageable)
        .map(
            trackPlan -> {
              val dto = new PlanDeviationShipmentResDto();
              dto.name = trackPlan.containerNo;
              dto.reference = trackPlan.reference;
              if (trackPlan.segments != null) {
                dto.segments = new ArrayList<>();

                for (int i = 0; i < trackPlan.segments.size(); i++) {
                  val planSegment = trackPlan.segments.get(i);

                  if (planSegment.sameLocation) {
                    // duration how long has stayed in the same location

                    if (i == 0) {
                      // if the same location segment is in very beginning

                      // from deviation
                      val fromDtoSegment = new PlanDeviationShipmentResDto.SegmentDuration();
                      fromDtoSegment.type =
                          PlanDeviationShipmentResDto.SegmentDurationType.DEVIATION;
                      fromDtoSegment.executed = planSegment.from.executed;
                      fromDtoSegment.timeResult = planSegment.from.timeResult;
                      dto.segments.add(fromDtoSegment);

                      // duration between from and to
                      val durationDtoSegment = new PlanDeviationShipmentResDto.SegmentDuration();
                      durationDtoSegment.type =
                          PlanDeviationShipmentResDto.SegmentDurationType.DURATION;
                      durationDtoSegment.durationInSeconds = planSegment.durationInSeconds;
                      dto.segments.add(durationDtoSegment);

                    } else if (i == trackPlan.segments.size() - 1) {
                      // if the same location segment is in last position

                      // duration between from and to
                      val durationDtoSegment = new PlanDeviationShipmentResDto.SegmentDuration();
                      durationDtoSegment.type =
                          PlanDeviationShipmentResDto.SegmentDurationType.DURATION;
                      durationDtoSegment.durationInSeconds = planSegment.durationInSeconds;
                      dto.segments.add(durationDtoSegment);

                      // to deviation
                      val toDtoSegment = new PlanDeviationShipmentResDto.SegmentDuration();
                      toDtoSegment.type = PlanDeviationShipmentResDto.SegmentDurationType.DEVIATION;
                      toDtoSegment.executed = planSegment.to.executed;
                      toDtoSegment.timeResult = planSegment.to.timeResult;
                      dto.segments.add(toDtoSegment);

                    } else {

                      val dtoSegment = new PlanDeviationShipmentResDto.SegmentDuration();
                      dtoSegment.type = PlanDeviationShipmentResDto.SegmentDurationType.DURATION;
                      dtoSegment.durationInSeconds = planSegment.durationInSeconds;
                      dto.segments.add(dtoSegment);
                    }

                  } else {

                    // from deviation
                    val fromDtoSegment = new PlanDeviationShipmentResDto.SegmentDuration();
                    fromDtoSegment.type = PlanDeviationShipmentResDto.SegmentDurationType.DEVIATION;
                    fromDtoSegment.executed = planSegment.from.executed;
                    fromDtoSegment.timeResult = planSegment.from.timeResult;
                    dto.segments.add(fromDtoSegment);

                    // duration between from and to
                    val durationDtoSegment = new PlanDeviationShipmentResDto.SegmentDuration();
                    durationDtoSegment.type =
                        PlanDeviationShipmentResDto.SegmentDurationType.DURATION;
                    durationDtoSegment.durationInSeconds = planSegment.durationInSeconds;
                    dto.segments.add(durationDtoSegment);

                    // to deviation
                    val toDtoSegment = new PlanDeviationShipmentResDto.SegmentDuration();
                    toDtoSegment.type = PlanDeviationShipmentResDto.SegmentDurationType.DEVIATION;
                    toDtoSegment.executed = planSegment.to.executed;
                    toDtoSegment.timeResult = planSegment.to.timeResult;
                    dto.segments.add(toDtoSegment);
                  }
                }
              }

              return dto;
            });
  }

  private Flux<TrackPlan> getDeviatedPlanVersionShipmentsInternal(
      DeviationFilters filters, Pageable pageable) {

    val containerFilters =
        getContainerFilters(
            filters, Container.CONTAINER_NO, Container.BILL_ID, Container.BOOKING_ID);

    val containerMatch = Aggregation.match(containerFilters);
    val lookup =
        Aggregation.lookup(
            TrackPlan.COLLECTION, Container.TRACK_ID, _id(TrackPlan.TRACK_ID), "temp");

    val fieldMapping = Map.of(TrackPlan.CONTAINER_NO, $(Container.CONTAINER_NO));

    val mergeObjects =
        ObjectOperators.valueOf(ArrayOperators.ArrayElemAt.arrayOf("temp").elementAt(0))
            .mergeWith(fieldMapping);
    val replaceRoot = Aggregation.replaceRoot().withValueOf(mergeObjects);

    SkipOperation skip = null;
    LimitOperation limit = null;

    if (pageable != null) {
      if (pageable.getPageNumber() != 0)
        skip = Aggregation.skip((long) pageable.getPageNumber() * pageable.getPageSize());
      limit = Aggregation.limit(pageable.getPageSize());
    }

    val match = Aggregation.match(getFiltersCriteria(filters));

    val project =
        Aggregation.project(TrackPlan.class)
            .andInclude(
                Fields.from(
                    Fields.field(TrackPlan.SEGMENTS, TrackPlan.SEGMENTS),
                    Fields.field(TrackPlan.REFERENCE, TrackPlan.REFERENCE),
                    Fields.field(TrackPlan.CONTAINER_NO, TrackPlan.CONTAINER_NO)));
    val sort = Aggregation.sort(Sort.Direction.ASC, TrackPlan.CONTAINER_NO);

    val operations = new ArrayList<AggregationOperation>();
    operations.add(containerMatch);
    operations.add(lookup);
    operations.add(replaceRoot);
    operations.add(match);
    operations.add(project);
    operations.add(sort);
    if (skip != null) operations.add(skip);
    if (limit != null) operations.add(limit);

    val aggregation = Aggregation.newAggregation(operations);

    val req = reactiveMongo.aggregate(aggregation, Container.class, TrackPlan.class);

    if (pageable != null) {
      val countOperation = new ArrayList<AggregationOperation>();
      countOperation.add(containerMatch);
      countOperation.add(lookup);
      countOperation.add(replaceRoot);
      countOperation.add(match);

      val countAggregation = Aggregation.newAggregation(countOperation);
      return Flux.merge(setPaging(countAggregation, pageable, Container.class), req);
    }

    return req;
  }
  // endregion

  private Criteria getContainerFilters(DeviationFilters filters, String... searchFields) {
    val criteria = new Criteria();
    val andCriteria = new ArrayList<Criteria>();

    if (filters.companyId != null) criteria.and(Container.COMPANY_ID).is(filters.companyId);

    if (filters.activeShipments != null)
      criteria
          .and(Container.MONITORING_STATUS)
          .is(filters.activeShipments ? MonitoringStatus.ACTIVE : MonitoringStatus.INACTIVE);

    if (filters.planVersionIds != null && !filters.planVersionIds.isEmpty())
      criteria.and(Container.PLAN_VERSION_ID).in(filters.planVersionIds);
    else criteria.and(Container.PLAN_VERSION_ID).exists(true);

    if (filters.carrierIds != null && !filters.carrierIds.isEmpty())
      criteria.and(Container.CARRIER_ID).in(filters.carrierIds);

    // region coming from custom query parameters
    if (filters.departureDate != null && filters.departZoneId != null) {
      val localDateTime =
          LocalDateTime.ofInstant(filters.departureDate, filters.departZoneId)
              .toLocalDate()
              .atTime(0, 0);
      val timestamp = ZonedDateTime.of(localDateTime, filters.departZoneId).toInstant();
      andCriteria.add(Criteria.where(Container.DEPART_AT_TIMESTAMP).gte(timestamp));
      andCriteria.add(
          Criteria.where(Container.DEPART_AT_TIMESTAMP).lt(timestamp.plus(Duration.ofDays(1))));
    }

    if (filters.arrivalDate != null && filters.arriveZoneId != null) {
      val localDateTime =
          LocalDateTime.ofInstant(filters.arrivalDate, filters.arriveZoneId)
              .toLocalDate()
              .atTime(0, 0);
      val timestamp = ZonedDateTime.of(localDateTime, filters.arriveZoneId).toInstant();
      andCriteria.add(Criteria.where(Container.ARRIVE_AT_TIMESTAMP).gte(timestamp));
      andCriteria.add(
          Criteria.where(Container.ARRIVE_AT_TIMESTAMP).lt(timestamp.plus(Duration.ofDays(1))));
    }
    // endregion

    // region coming from filters
    if (filters.departures != null && !filters.departures.isEmpty()) {
      andCriteria.add(Criteria.where(Container.DEPART_AT_TIMESTAMP).gte(filters.departures.get(0)));
      andCriteria.add(Criteria.where(Container.DEPART_AT_TIMESTAMP).lte(filters.departures.get(1)));
    }

    if (filters.arrivals != null && !filters.arrivals.isEmpty()) {
      andCriteria.add(Criteria.where(Container.ARRIVE_AT_TIMESTAMP).gte(filters.arrivals.get(0)));
      andCriteria.add(Criteria.where(Container.ARRIVE_AT_TIMESTAMP).lte(filters.arrivals.get(1)));
    }
    // endregion

    val searchTerm = Strings.trimToNull(filters.q);
    if (searchTerm != null && searchFields.length > 0) {
      val containerSearch = generateOrCriteria(searchTerm, searchFields);
      andCriteria.add(new Criteria().orOperator(containerSearch));
    }

    if (andCriteria.size() > 0) criteria.andOperator(andCriteria.toArray(new Criteria[0]));
    return criteria;
  }

  /** Track Plans Filters */
  private Criteria getFiltersCriteria(DeviationFilters filters, String... searchFields) {
    val criteria = new Criteria();
    val andCriteria = new ArrayList<Criteria>();

    criteria.and(TrackPlan.CURRENT_DEVIATED_PLAN_STEP_TYPE).exists(true);

    if (filters.deviationTypes != null && !filters.deviationTypes.isEmpty()) {
      val orCriteria = new ArrayList<Criteria>(filters.deviationTypes.size());
      for (val deviationType : filters.deviationTypes)
        orCriteria.add(Criteria.where(CURRENT_DEVIATED_PLAN_STEP_TYPE).is(deviationType));
      andCriteria.add(new Criteria().orOperator(orCriteria.toArray(new Criteria[0])));
    }

    val searchTerm = Strings.trimToNull(filters.q);

    if (searchTerm != null && searchFields.length > 0) {
      val planSearch = generateOrCriteria(searchTerm, searchFields);
      andCriteria.add(new Criteria().orOperator(planSearch));
    }

    if (andCriteria.size() > 0) criteria.andOperator(andCriteria.toArray(new Criteria[0]));

    return criteria;
  }

  public Criteria[] generateOrCriteria(String value, String... fields) {
    val criteria = new Criteria[fields.length];

    for (int i = 0; i < fields.length; i++)
      criteria[i] = Criteria.where(fields[i]).regex('^' + value, "i");

    return criteria;
  }

  private <T> Mono<T> setPaging(Aggregation aggregation, Pageable pageable, Class<?> tClass) {
    aggregation.getPipeline().add(Aggregation.count().as("count"));
    return reactiveMongo
        .aggregate(aggregation, tClass, Document.class)
        .transformDeferredContextual(
            (mono, context) ->
                mono.map(document -> document.getInteger("count"))
                    .doOnNext(
                        count -> {
                          PagingMeta meta = context.get(PagingHeaderFilter.PAGING_META);
                          meta.isPaging = true;
                          meta.pageable = pageable;
                          meta.totalElements = count;
                        }))
        .then(Mono.empty());
  }
}
