package com.unisync.schedule.coordination.algorithm;

import com.unisync.schedule.common.entity.Schedule;
import com.unisync.schedule.coordination.dto.FreeSlotDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("FreeSlotFinder 알고리즘 테스트")
class FreeSlotFinderTest {

    private FreeSlotFinder freeSlotFinder;

    @BeforeEach
    void setUp() {
        freeSlotFinder = new FreeSlotFinder();
    }

    // =======================================================================
    // mergeIntervals 테스트
    // =======================================================================

    @Test
    @DisplayName("Interval 병합 - 겹치는 구간들이 정확히 병합됨")
    void mergeIntervals_overlapping() {
        // given
        List<TimeInterval> intervals = Arrays.asList(
                new TimeInterval(
                        LocalDateTime.of(2025, 11, 25, 9, 0),
                        LocalDateTime.of(2025, 11, 25, 11, 0)
                ),
                new TimeInterval(
                        LocalDateTime.of(2025, 11, 25, 10, 0),
                        LocalDateTime.of(2025, 11, 25, 12, 0)
                ),
                new TimeInterval(
                        LocalDateTime.of(2025, 11, 25, 14, 0),
                        LocalDateTime.of(2025, 11, 25, 16, 0)
                )
        );

        // when
        List<TimeInterval> merged = freeSlotFinder.mergeIntervals(intervals);

        // then
        assertThat(merged).hasSize(2);
        assertThat(merged.get(0).getStart()).isEqualTo(LocalDateTime.of(2025, 11, 25, 9, 0));
        assertThat(merged.get(0).getEnd()).isEqualTo(LocalDateTime.of(2025, 11, 25, 12, 0));
        assertThat(merged.get(1).getStart()).isEqualTo(LocalDateTime.of(2025, 11, 25, 14, 0));
        assertThat(merged.get(1).getEnd()).isEqualTo(LocalDateTime.of(2025, 11, 25, 16, 0));
    }

    @Test
    @DisplayName("Interval 병합 - 겹치지 않는 구간은 그대로 유지")
    void mergeIntervals_noOverlap() {
        // given
        List<TimeInterval> intervals = Arrays.asList(
                new TimeInterval(
                        LocalDateTime.of(2025, 11, 25, 9, 0),
                        LocalDateTime.of(2025, 11, 25, 10, 0)
                ),
                new TimeInterval(
                        LocalDateTime.of(2025, 11, 25, 11, 0),
                        LocalDateTime.of(2025, 11, 25, 12, 0)
                ),
                new TimeInterval(
                        LocalDateTime.of(2025, 11, 25, 14, 0),
                        LocalDateTime.of(2025, 11, 25, 15, 0)
                )
        );

        // when
        List<TimeInterval> merged = freeSlotFinder.mergeIntervals(intervals);

        // then
        assertThat(merged).hasSize(3);
    }

    @Test
    @DisplayName("Interval 병합 - 모든 구간이 겹치면 하나로 병합")
    void mergeIntervals_allOverlapping() {
        // given
        List<TimeInterval> intervals = Arrays.asList(
                new TimeInterval(
                        LocalDateTime.of(2025, 11, 25, 9, 0),
                        LocalDateTime.of(2025, 11, 25, 11, 0)
                ),
                new TimeInterval(
                        LocalDateTime.of(2025, 11, 25, 10, 0),
                        LocalDateTime.of(2025, 11, 25, 13, 0)
                ),
                new TimeInterval(
                        LocalDateTime.of(2025, 11, 25, 12, 0),
                        LocalDateTime.of(2025, 11, 25, 15, 0)
                )
        );

        // when
        List<TimeInterval> merged = freeSlotFinder.mergeIntervals(intervals);

        // then
        assertThat(merged).hasSize(1);
        assertThat(merged.get(0).getStart()).isEqualTo(LocalDateTime.of(2025, 11, 25, 9, 0));
        assertThat(merged.get(0).getEnd()).isEqualTo(LocalDateTime.of(2025, 11, 25, 15, 0));
    }

    @Test
    @DisplayName("Interval 병합 - 빈 리스트는 빈 리스트 반환")
    void mergeIntervals_emptyList() {
        // when
        List<TimeInterval> merged = freeSlotFinder.mergeIntervals(new ArrayList<>());

        // then
        assertThat(merged).isEmpty();
    }

    @Test
    @DisplayName("Interval 병합 - 인접한 구간(끝과 시작이 같음)도 병합")
    void mergeIntervals_adjacent() {
        // given
        List<TimeInterval> intervals = Arrays.asList(
                new TimeInterval(
                        LocalDateTime.of(2025, 11, 25, 9, 0),
                        LocalDateTime.of(2025, 11, 25, 10, 0)
                ),
                new TimeInterval(
                        LocalDateTime.of(2025, 11, 25, 10, 0),  // 앞 구간 종료 = 이 구간 시작
                        LocalDateTime.of(2025, 11, 25, 11, 0)
                )
        );

        // when
        List<TimeInterval> merged = freeSlotFinder.mergeIntervals(intervals);

        // then
        assertThat(merged).hasSize(1);
        assertThat(merged.get(0).getStart()).isEqualTo(LocalDateTime.of(2025, 11, 25, 9, 0));
        assertThat(merged.get(0).getEnd()).isEqualTo(LocalDateTime.of(2025, 11, 25, 11, 0));
    }

    // =======================================================================
    // extractFreeIntervals 테스트
    // =======================================================================

    @Test
    @DisplayName("공강 추출 - 기본 시나리오")
    void extractFreeIntervals_basic() {
        // given
        List<TimeInterval> busyIntervals = Arrays.asList(
                new TimeInterval(
                        LocalDateTime.of(2025, 11, 25, 9, 0),
                        LocalDateTime.of(2025, 11, 25, 12, 0)
                ),
                new TimeInterval(
                        LocalDateTime.of(2025, 11, 25, 14, 0),
                        LocalDateTime.of(2025, 11, 25, 17, 0)
                )
        );

        LocalDateTime searchStart = LocalDateTime.of(2025, 11, 25, 0, 0);
        LocalDateTime searchEnd = LocalDateTime.of(2025, 11, 25, 23, 59);
        int minDuration = 60; // 1시간

        // when
        List<TimeInterval> freeIntervals = freeSlotFinder.extractFreeIntervals(
                busyIntervals, searchStart, searchEnd, minDuration
        );

        // then
        assertThat(freeIntervals).hasSize(3);
        // 0:00 - 9:00
        assertThat(freeIntervals.get(0).getStart()).isEqualTo(LocalDateTime.of(2025, 11, 25, 0, 0));
        assertThat(freeIntervals.get(0).getEnd()).isEqualTo(LocalDateTime.of(2025, 11, 25, 9, 0));
        // 12:00 - 14:00
        assertThat(freeIntervals.get(1).getStart()).isEqualTo(LocalDateTime.of(2025, 11, 25, 12, 0));
        assertThat(freeIntervals.get(1).getEnd()).isEqualTo(LocalDateTime.of(2025, 11, 25, 14, 0));
        // 17:00 - 23:59
        assertThat(freeIntervals.get(2).getStart()).isEqualTo(LocalDateTime.of(2025, 11, 25, 17, 0));
        assertThat(freeIntervals.get(2).getEnd()).isEqualTo(LocalDateTime.of(2025, 11, 25, 23, 59));
    }

    @Test
    @DisplayName("공강 추출 - 최소 지속 시간 필터링")
    void extractFreeIntervals_minDurationFiltering() {
        // given
        List<TimeInterval> busyIntervals = Arrays.asList(
                new TimeInterval(
                        LocalDateTime.of(2025, 11, 25, 10, 0),
                        LocalDateTime.of(2025, 11, 25, 11, 0)
                ),
                new TimeInterval(
                        LocalDateTime.of(2025, 11, 25, 11, 30),  // 30분 공강
                        LocalDateTime.of(2025, 11, 25, 13, 0)
                )
        );

        LocalDateTime searchStart = LocalDateTime.of(2025, 11, 25, 9, 0);
        LocalDateTime searchEnd = LocalDateTime.of(2025, 11, 25, 18, 0);
        int minDuration = 60; // 1시간

        // when
        List<TimeInterval> freeIntervals = freeSlotFinder.extractFreeIntervals(
                busyIntervals, searchStart, searchEnd, minDuration
        );

        // then
        // 30분 공강(11:00-11:30)은 필터링되고, 1시간 이상만 포함
        assertThat(freeIntervals).hasSize(2);
        assertThat(freeIntervals.get(0).getDurationMinutes()).isGreaterThanOrEqualTo(60);
        assertThat(freeIntervals.get(1).getDurationMinutes()).isGreaterThanOrEqualTo(60);
    }

    @Test
    @DisplayName("공강 추출 - busy가 없으면 전체 기간이 공강")
    void extractFreeIntervals_noBusy() {
        // given
        List<TimeInterval> busyIntervals = new ArrayList<>();
        LocalDateTime searchStart = LocalDateTime.of(2025, 11, 25, 9, 0);
        LocalDateTime searchEnd = LocalDateTime.of(2025, 11, 25, 18, 0);
        int minDuration = 60;

        // when
        List<TimeInterval> freeIntervals = freeSlotFinder.extractFreeIntervals(
                busyIntervals, searchStart, searchEnd, minDuration
        );

        // then
        assertThat(freeIntervals).hasSize(1);
        assertThat(freeIntervals.get(0).getStart()).isEqualTo(searchStart);
        assertThat(freeIntervals.get(0).getEnd()).isEqualTo(searchEnd);
    }

    // =======================================================================
    // applyWorkingHours 테스트
    // =======================================================================

    @Test
    @DisplayName("근무 시간 필터링 - 공강 구간이 근무 시간 범위로 제한됨")
    void applyWorkingHours_filtersCorrectly() {
        // given
        List<TimeInterval> freeIntervals = Arrays.asList(
                new TimeInterval(
                        LocalDateTime.of(2025, 11, 25, 8, 0),   // 근무 시간 전
                        LocalDateTime.of(2025, 11, 25, 11, 0)
                ),
                new TimeInterval(
                        LocalDateTime.of(2025, 11, 25, 14, 0),
                        LocalDateTime.of(2025, 11, 25, 20, 0)   // 근무 시간 후까지
                )
        );

        LocalTime workingStart = LocalTime.of(9, 0);
        LocalTime workingEnd = LocalTime.of(18, 0);
        int minDuration = 60;

        // when
        List<TimeInterval> filtered = freeSlotFinder.applyWorkingHours(
                freeIntervals, workingStart, workingEnd, minDuration
        );

        // then
        assertThat(filtered).hasSize(2);
        // 첫 번째: 9:00-11:00 (8:00은 잘림)
        assertThat(filtered.get(0).getStart()).isEqualTo(LocalDateTime.of(2025, 11, 25, 9, 0));
        assertThat(filtered.get(0).getEnd()).isEqualTo(LocalDateTime.of(2025, 11, 25, 11, 0));
        // 두 번째: 14:00-18:00 (20:00은 잘림)
        assertThat(filtered.get(1).getStart()).isEqualTo(LocalDateTime.of(2025, 11, 25, 14, 0));
        assertThat(filtered.get(1).getEnd()).isEqualTo(LocalDateTime.of(2025, 11, 25, 18, 0));
    }

    @Test
    @DisplayName("근무 시간 필터링 - 근무 시간 외 구간은 제외됨")
    void applyWorkingHours_excludesOutsideWorkingHours() {
        // given
        List<TimeInterval> freeIntervals = Arrays.asList(
                new TimeInterval(
                        LocalDateTime.of(2025, 11, 25, 6, 0),   // 근무 시간 전
                        LocalDateTime.of(2025, 11, 25, 7, 0)
                ),
                new TimeInterval(
                        LocalDateTime.of(2025, 11, 25, 20, 0),  // 근무 시간 후
                        LocalDateTime.of(2025, 11, 25, 22, 0)
                )
        );

        LocalTime workingStart = LocalTime.of(9, 0);
        LocalTime workingEnd = LocalTime.of(18, 0);
        int minDuration = 60;

        // when
        List<TimeInterval> filtered = freeSlotFinder.applyWorkingHours(
                freeIntervals, workingStart, workingEnd, minDuration
        );

        // then
        assertThat(filtered).isEmpty();
    }

    // =======================================================================
    // filterByDaysOfWeek 테스트
    // =======================================================================

    @Test
    @DisplayName("요일 필터링 - 지정된 요일만 포함")
    void filterByDaysOfWeek_filtersCorrectly() {
        // given (2025-11-25 = 화요일, 2025-11-26 = 수요일, 2025-11-27 = 목요일)
        List<TimeInterval> freeIntervals = Arrays.asList(
                new TimeInterval(
                        LocalDateTime.of(2025, 11, 25, 9, 0),   // 화요일 (2)
                        LocalDateTime.of(2025, 11, 25, 12, 0)
                ),
                new TimeInterval(
                        LocalDateTime.of(2025, 11, 26, 9, 0),   // 수요일 (3)
                        LocalDateTime.of(2025, 11, 26, 12, 0)
                ),
                new TimeInterval(
                        LocalDateTime.of(2025, 11, 27, 9, 0),   // 목요일 (4)
                        LocalDateTime.of(2025, 11, 27, 12, 0)
                )
        );

        List<Integer> daysOfWeek = Arrays.asList(2, 4);  // 화요일, 목요일만

        // when
        List<TimeInterval> filtered = freeSlotFinder.filterByDaysOfWeek(freeIntervals, daysOfWeek);

        // then
        assertThat(filtered).hasSize(2);
        assertThat(filtered.get(0).getStart().getDayOfWeek().getValue()).isEqualTo(2);  // 화요일
        assertThat(filtered.get(1).getStart().getDayOfWeek().getValue()).isEqualTo(4);  // 목요일
    }

    // =======================================================================
    // findFreeSlots 통합 테스트
    // =======================================================================

    @Test
    @DisplayName("공강 찾기 통합 - 전체 워크플로우")
    void findFreeSlots_fullWorkflow() {
        // given
        List<Schedule> schedules = createTestSchedules();
        LocalDate startDate = LocalDate.of(2025, 11, 25);
        LocalDate endDate = LocalDate.of(2025, 11, 25);
        int minDuration = 60;
        LocalTime workingStart = LocalTime.of(9, 0);
        LocalTime workingEnd = LocalTime.of(18, 0);
        List<Integer> daysOfWeek = null;  // 모든 요일

        // when
        List<FreeSlotDto> freeSlots = freeSlotFinder.findFreeSlots(
                schedules, startDate, endDate, minDuration,
                workingStart, workingEnd, daysOfWeek
        );

        // then
        assertThat(freeSlots).isNotEmpty();
        // 모든 공강이 근무 시간 내
        for (FreeSlotDto slot : freeSlots) {
            assertThat(slot.getStartTime().toLocalTime()).isAfterOrEqualTo(workingStart);
            assertThat(slot.getEndTime().toLocalTime()).isBeforeOrEqualTo(workingEnd);
            assertThat(slot.getDurationMinutes()).isGreaterThanOrEqualTo(minDuration);
        }
    }

    @Test
    @DisplayName("공강 찾기 - 일정이 없으면 근무 시간 전체가 공강")
    void findFreeSlots_noSchedules() {
        // given
        List<Schedule> schedules = new ArrayList<>();
        LocalDate startDate = LocalDate.of(2025, 11, 25);
        LocalDate endDate = LocalDate.of(2025, 11, 25);
        int minDuration = 60;
        LocalTime workingStart = LocalTime.of(9, 0);
        LocalTime workingEnd = LocalTime.of(18, 0);

        // when
        List<FreeSlotDto> freeSlots = freeSlotFinder.findFreeSlots(
                schedules, startDate, endDate, minDuration,
                workingStart, workingEnd, null
        );

        // then
        assertThat(freeSlots).hasSize(1);
        assertThat(freeSlots.get(0).getStartTime()).isEqualTo(LocalDateTime.of(2025, 11, 25, 9, 0));
        assertThat(freeSlots.get(0).getEndTime()).isEqualTo(LocalDateTime.of(2025, 11, 25, 18, 0));
        assertThat(freeSlots.get(0).getDurationMinutes()).isEqualTo(540);  // 9시간
    }

    // =======================================================================
    // Helper methods
    // =======================================================================

    private List<Schedule> createTestSchedules() {
        List<Schedule> schedules = new ArrayList<>();

        // User A: 9:00-11:00
        Schedule s1 = new Schedule();
        s1.setScheduleId(1L);
        s1.setCognitoSub("user-a");
        s1.setStartTime(LocalDateTime.of(2025, 11, 25, 9, 0));
        s1.setEndTime(LocalDateTime.of(2025, 11, 25, 11, 0));
        schedules.add(s1);

        // User B: 10:00-12:00 (User A와 겹침)
        Schedule s2 = new Schedule();
        s2.setScheduleId(2L);
        s2.setCognitoSub("user-b");
        s2.setStartTime(LocalDateTime.of(2025, 11, 25, 10, 0));
        s2.setEndTime(LocalDateTime.of(2025, 11, 25, 12, 0));
        schedules.add(s2);

        // User C: 14:00-16:00
        Schedule s3 = new Schedule();
        s3.setScheduleId(3L);
        s3.setCognitoSub("user-c");
        s3.setStartTime(LocalDateTime.of(2025, 11, 25, 14, 0));
        s3.setEndTime(LocalDateTime.of(2025, 11, 25, 16, 0));
        schedules.add(s3);

        return schedules;
    }
}
