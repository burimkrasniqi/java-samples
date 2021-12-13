@Slf4j
@Component
@AllArgsConstructor
public class TrackPlanStepSorter {

  public Wrapper processTrackPlanStepIndexes(Wrapper wrapper) {
    log.info("[TrackPlanStepSorter, 1, trackId={}]", wrapper.track.id);
    List<TrackPlanStep> sortedSteps = new ArrayList<>();
    List<TrackPlanStep> stepsWithoutPlan = new ArrayList<>();

    for (TrackPlanStep trackPlanStep : wrapper.trackPlanSteps) {
      if (trackPlanStep.planStep != null)
        // add steps that have the plan
        sortedSteps.add(trackPlanStep);
      else
        // add steps that have no plan
        stepsWithoutPlan.add(trackPlanStep);
    }

    sortStepsByPlanSequence(sortedSteps);
    sortStepsByTrackTimestamp(stepsWithoutPlan);
    mergeSteps(stepsWithoutPlan, sortedSteps);
    updateStepsIndex(sortedSteps);
    // reorderFirstLastPlanStepPosition(sortedSteps);
    // assign sorted steps in wrapper
    wrapper.trackPlanSteps = sortedSteps;
    log.info("[TrackPlanStepSorter, 2, trackId={}]", wrapper.track.id);
    return wrapper;
  }

  private void updateStepsIndex(List<TrackPlanStep> sortedSteps) {
    for (int i = 0; i < sortedSteps.size(); i++) sortedSteps.get(i).index = i;
  }

  /**
   * Merges two type of steps: 1. "track steps" that are ordered by event time 2. "plan steps" that
   * are ordered same as in plan. The merge result is saved in sortedSteps reference. Initially
   * sortedSteps reference contains only plan steps then after applying to merge algorithm it will be
   * merged with track steps.
   *
   * @param stepsWithoutPlan - steps without plan ordered by event time
   * @param sortedSteps - plan steps sorted by plan step indexes
   */
  private void mergeSteps(List<TrackPlanStep> stepsWithoutPlan, List<TrackPlanStep> sortedSteps) {
    for (TrackPlanStep noPlanStep : stepsWithoutPlan) {
      val timestamp = noPlanStep.trackStep.zonedTime.timestamp;
      val position = getMergeStepPosition(timestamp, sortedSteps);
      sortedSteps.add(position, noPlanStep);
    }
  }

  /**
   * This function is an algorithm which calculates the position of steps that are not part of the
   * plan.
   *
   * @param stepTimeStamp - event timesStamp of the step which need to be merged
   * @param sortedSteps - calculated steps order
   * @return the position calculated for the given stepTimeStamp
   */
  private int getMergeStepPosition(Instant stepTimeStamp, List<TrackPlanStep> sortedSteps) {
    long minDelta = Long.MAX_VALUE;
    int position = -1;
    // 1. Find the nearest plan step position
    for (int i = 0; i < sortedSteps.size(); i++) {
      val step = sortedSteps.get(i);
      if (step.planStep != null) // check if it is plan step
      {
        if (step.trackStep != null) // calculate based on step event time
        {
          val timestamp = step.trackStep.zonedTime.timestamp.toEpochMilli();
          val deltaDiff = stepTimeStamp.toEpochMilli() - timestamp;
          if (Math.abs(deltaDiff) < Math.abs(minDelta)) {
            minDelta = deltaDiff;
            // if delta is positive put the step on the right of the current step
            position = minDelta >= 0 ? i + 1 : i;
          }
        } else if (step.planedTime != null
            && step.planedTime.expectedTime != null) // calculate based on plan interval
        {
          val from = step.planedTime.expectedTime.from.timestamp.toEpochMilli();
          val to = step.planedTime.expectedTime.to.timestamp.toEpochMilli();
          val fromDeltaDiff = stepTimeStamp.toEpochMilli() - from;
          val toDeltaDiff = stepTimeStamp.toEpochMilli() - to;
          if (Math.abs(fromDeltaDiff) < Math.abs(minDelta)) {
            minDelta = fromDeltaDiff;
            position = minDelta >= 0 ? i + 1 : i;
          }
          if (Math.abs(toDeltaDiff) < Math.abs(minDelta)) {
            minDelta = toDeltaDiff;
            position = minDelta >= 0 ? i + 1 : i;
          }
        }
      }
    }
    // if no plan step is found put it at beginning position
    if (position == -1) position = 0;

    // 2. Sort with neighbour steps without plan
    // knowing that steps without plan are ordered and older than current step, then we just shift
    // this
    // current step position to the end
    int shiftedPosition = position;
    for (int i = position; i < sortedSteps.size(); i++) {
      val step = sortedSteps.get(i);
      if (step.planStep == null) shiftedPosition = i + 1;
      else break;
    }

    return shiftedPosition;
  }

  private void sortStepsByTrackTimestamp(List<TrackPlanStep> steps) {
    if (steps.size() == 0) return;
    steps.sort(Comparator.comparing(o -> o.trackStep.zonedTime.timestamp));
  }

  // region Sort steps by plan references. Only for the steps that are part of the plan

  /**
   * plan steps by default are referenced by plan id, determine the order based on those ids and put
   * ordered indexes
   */
  private void sortStepsByPlanSequence(List<TrackPlanStep> steps) {
    TrackPlanStep previousStep = getFirstStep(steps);
    previousStep.index = 0;
    for (int i = 1; i < steps.size(); i++) {
      val step = getNeighborSteps(previousStep.planStep.id, steps).get(0);
      step.index = i;
      previousStep = step;
    }
  }

  private TrackPlanStep getFirstStep(List<TrackPlanStep> steps) {
    for (TrackPlanStep step : steps) {
      if (step.planStep != null && step.planStep.parentRefs == null) return step;
    }

    throw new RuntimeException("not found initial plan step");
  }

  private List<TrackPlanStep> getNeighborSteps(String stepId, List<TrackPlanStep> steps) {
    List<TrackPlanStep> neighborSteps = new ArrayList<>();
    for (TrackPlanStep step : steps) {
      // check if this step is a neighbour by checking if the stepId is parent of this
      if (step.planStep != null && step.planStep.parentRefs != null) {
        for (String parentRef : step.planStep.parentRefs) {
          if (stepId.equals(parentRef)) {
            neighborSteps.add(step);
            break;
          }
        }
      }
    }

    return neighborSteps;
  }
  // endregion
}
