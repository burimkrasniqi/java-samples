package com.evertracker.coronatower.api.deviations.dtos;

import com.evertracker.coronatower.common.monitoring.trackplans.entities.steps.TimeResult;
import com.evertracker.coronatower.common.monitoring.tracks.entities.EntityReference;
import lombok.ToString;

import java.util.List;

@ToString
public class PlanDeviationShipmentResDto {
  public String name;
  public EntityReference reference;
  public List<SegmentDuration> segments;
  public List<String> columns;

  @ToString
  public static class SegmentDuration {
    public Long durationInSeconds;
    public TimeResult timeResult;
    public boolean executed;

    public SegmentDurationType type;
  }

  public enum SegmentDurationType {
    DEVIATION,
    DURATION
  }
}
