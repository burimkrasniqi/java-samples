package com.evertracker.coronatower.api.deviations.filters;

import com.evertracker.coronatower.api.admin.filters.entities.FilterData;
import com.evertracker.coronatower.api.admin.filters.entities.FilterModule;
import com.evertracker.coronatower.api.admin.filters.entities.FilterParameterHolder;
import com.evertracker.coronatower.api.admin.filters.entities.FilterParameterInfo;
import com.evertracker.coronatower.common.monitoring.trackplans.entities.steps.TimeResult;
import lombok.val;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DeviationFilterParameters extends FilterParameterHolder {
  public static final String PLAN_VERSION_ID = "planVersionId";
  public static final String CARRIER_ID = "carrierId";
  public static final String DEVIATION_TYPE = "deviationType";
  public static final String DEPARTURE = "departure";
  public static final String ARRIVAL = "arrival";

  public DeviationFilterParameters() {
    super(FilterModule.DEVIATION);
  }

  @Override
  protected void registerChipSingle(Function func) {}

  @Override
  protected void registerChipMultiple(Function func) {}

  @Override
  protected void registerDropSingle(Function func) {}

  @Override
  protected void registerDropMultiple(Function func) {
    val filterData =
        List.of(
            new FilterData(TimeResult.EARLY.name(), "Too Early"),
            new FilterData(TimeResult.LATE.name(), "Too Late"));

    func.create(new FilterParameterInfo("DeviationType", DEVIATION_TYPE, null, filterData));
    func.create(
        new FilterParameterInfo("PlanVersion", PLAN_VERSION_ID, "/containers/plan-versions", null));
    func.create(new FilterParameterInfo("Carrier", CARRIER_ID, "/containers/carriers", null));
  }

  @Override
  protected void registerDate(Function func) {}

  @Override
  protected void registerDateRange(Function func) {
    func.create(new FilterParameterInfo("DepartureDate", DEPARTURE, null, null));
    func.create(new FilterParameterInfo("ArrivalDate", ARRIVAL, null, null));
  }
}
