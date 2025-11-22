package com.unisync.schedule.coordination.algorithm;

import com.unisync.schedule.common.entity.Schedule;
import com.unisync.schedule.coordination.dto.FreeSlotDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 공강 시간 찾기 알고리즘 구현
 *
 * 주요 기능:
 * 1. Interval Merging: 겹치는 busy 구간 병합
 * 2. Free Slot Extraction: busy 구간 사이의 공강 시간 추출
 * 3. Filtering: 근무 시간, 요일 필터링
 */
@Component
@Slf4j
public class FreeSlotFinder {

    /**
     * 공강 시간 찾기 메인 메서드
     *
     * @param schedules 일정 목록
     * @param startDate 검색 시작일
     * @param endDate 검색 종료일
     * @param minDurationMinutes 최소 지속 시간 (분)
     * @param workingHoursStart 근무 시간 시작 (optional)
     * @param workingHoursEnd 근무 시간 종료 (optional)
     * @param daysOfWeek 요일 필터 (optional, 1=월, 7=일)
     * @return 공강 시간 목록
     */
    public List<FreeSlotDto> findFreeSlots(
            List<Schedule> schedules,
            LocalDate startDate,
            LocalDate endDate,
            int minDurationMinutes,
            LocalTime workingHoursStart,
            LocalTime workingHoursEnd,
            List<Integer> daysOfWeek
    ) {
        log.info("공강 시간 검색 시작 - 일정 개수: {}, 기간: {} ~ {}, 최소 지속: {}분",
                schedules.size(), startDate, endDate, minDurationMinutes);

        // 1. Schedule을 TimeInterval로 변환
        List<TimeInterval> busyIntervals = schedules.stream()
                .map(s -> new TimeInterval(s.getStartTime(), s.getEndTime()))
                .collect(Collectors.toList());

        // 2. Interval 병합
        List<TimeInterval> mergedBusy = mergeIntervals(busyIntervals);
        log.debug("Busy 구간 병합 완료 - 병합 전: {}, 병합 후: {}", busyIntervals.size(), mergedBusy.size());

        // 3. 공강 시간 추출
        LocalDateTime searchStart = startDate.atStartOfDay();
        LocalDateTime searchEnd = endDate.atTime(23, 59, 59);
        List<TimeInterval> freeIntervals = extractFreeIntervals(mergedBusy, searchStart, searchEnd, minDurationMinutes);
        log.debug("공강 시간 추출 완료 - 공강 개수: {}", freeIntervals.size());

        // 4. 근무 시간 필터링 (optional)
        if (workingHoursStart != null && workingHoursEnd != null) {
            freeIntervals = applyWorkingHours(freeIntervals, workingHoursStart, workingHoursEnd, minDurationMinutes);
            log.debug("근무 시간 필터링 완료 - 남은 공강: {}", freeIntervals.size());
        }

        // 5. 요일 필터링 (optional)
        if (daysOfWeek != null && !daysOfWeek.isEmpty()) {
            freeIntervals = filterByDaysOfWeek(freeIntervals, daysOfWeek);
            log.debug("요일 필터링 완료 - 남은 공강: {}", freeIntervals.size());
        }

        // 6. DTO 변환
        List<FreeSlotDto> result = freeIntervals.stream()
                .map(FreeSlotDto::from)
                .collect(Collectors.toList());

        log.info("공강 시간 검색 완료 - 총 {}개 발견", result.size());
        return result;
    }

    /**
     * 겹치는 구간 병합 (Interval Merging Algorithm)
     *
     * @param intervals 병합할 구간 목록
     * @return 병합된 구간 목록
     */
    public List<TimeInterval> mergeIntervals(List<TimeInterval> intervals) {
        if (intervals == null || intervals.isEmpty()) {
            return new ArrayList<>();
        }

        // 시작 시간 기준 정렬
        List<TimeInterval> sorted = new ArrayList<>(intervals);
        Collections.sort(sorted);

        List<TimeInterval> merged = new ArrayList<>();
        merged.add(sorted.get(0));

        for (int i = 1; i < sorted.size(); i++) {
            TimeInterval current = sorted.get(i);
            TimeInterval last = merged.get(merged.size() - 1);

            // 겹치거나 인접하면 병합
            if (current.getStart().isBefore(last.getEnd()) || current.getStart().equals(last.getEnd())) {
                LocalDateTime newEnd = current.getEnd().isAfter(last.getEnd()) ? current.getEnd() : last.getEnd();
                last.setEnd(newEnd);
            } else {
                // 겹치지 않으면 새로운 구간 추가
                merged.add(current);
            }
        }

        return merged;
    }

    /**
     * Busy 구간 사이의 공강 시간 추출
     *
     * @param mergedBusy 병합된 busy 구간
     * @param searchStart 검색 시작 시간
     * @param searchEnd 검색 종료 시간
     * @param minDurationMinutes 최소 지속 시간 (분)
     * @return 공강 구간 목록
     */
    public List<TimeInterval> extractFreeIntervals(
            List<TimeInterval> mergedBusy,
            LocalDateTime searchStart,
            LocalDateTime searchEnd,
            int minDurationMinutes
    ) {
        List<TimeInterval> freeIntervals = new ArrayList<>();
        LocalDateTime currentTime = searchStart;

        for (TimeInterval busy : mergedBusy) {
            // busy 시작 전 공강 확인
            if (busy.getStart().isAfter(currentTime)) {
                long freeMinutes = java.time.Duration.between(currentTime, busy.getStart()).toMinutes();
                if (freeMinutes >= minDurationMinutes) {
                    freeIntervals.add(new TimeInterval(currentTime, busy.getStart()));
                }
            }

            // 다음 검색 시작 시간 = busy 종료 시간
            currentTime = busy.getEnd().isAfter(currentTime) ? busy.getEnd() : currentTime;
        }

        // 마지막 busy 구간 이후 공강 확인
        if (searchEnd.isAfter(currentTime)) {
            long freeMinutes = java.time.Duration.between(currentTime, searchEnd).toMinutes();
            if (freeMinutes >= minDurationMinutes) {
                freeIntervals.add(new TimeInterval(currentTime, searchEnd));
            }
        }

        return freeIntervals;
    }

    /**
     * 근무 시간 필터링
     *
     * 각 공강 구간을 근무 시간 범위로 제한
     *
     * @param freeIntervals 공강 구간 목록
     * @param workingStart 근무 시간 시작
     * @param workingEnd 근무 시간 종료
     * @param minDurationMinutes 최소 지속 시간
     * @return 필터링된 공강 구간
     */
    public List<TimeInterval> applyWorkingHours(
            List<TimeInterval> freeIntervals,
            LocalTime workingStart,
            LocalTime workingEnd,
            int minDurationMinutes
    ) {
        List<TimeInterval> filtered = new ArrayList<>();

        for (TimeInterval slot : freeIntervals) {
            LocalDate slotDate = slot.getStart().toLocalDate();
            LocalDateTime dayWorkingStart = slotDate.atTime(workingStart);
            LocalDateTime dayWorkingEnd = slotDate.atTime(workingEnd);

            // 공강 구간과 근무 시간의 교집합 계산
            LocalDateTime adjustedStart = slot.getStart().isBefore(dayWorkingStart) ? dayWorkingStart : slot.getStart();
            LocalDateTime adjustedEnd = slot.getEnd().isAfter(dayWorkingEnd) ? dayWorkingEnd : slot.getEnd();

            // 유효한 구간인지 확인
            if (adjustedStart.isBefore(adjustedEnd)) {
                long durationMinutes = java.time.Duration.between(adjustedStart, adjustedEnd).toMinutes();
                if (durationMinutes >= minDurationMinutes) {
                    filtered.add(new TimeInterval(adjustedStart, adjustedEnd));
                }
            }
        }

        return filtered;
    }

    /**
     * 요일 필터링
     *
     * @param freeIntervals 공강 구간 목록
     * @param daysOfWeek 허용 요일 (1=월, 2=화, ..., 7=일)
     * @return 필터링된 공강 구간
     */
    public List<TimeInterval> filterByDaysOfWeek(
            List<TimeInterval> freeIntervals,
            List<Integer> daysOfWeek
    ) {
        List<TimeInterval> filtered = new ArrayList<>();

        for (TimeInterval slot : freeIntervals) {
            int dayValue = slot.getStart().getDayOfWeek().getValue(); // 1=월, 7=일
            if (daysOfWeek.contains(dayValue)) {
                filtered.add(slot);
            }
        }

        return filtered;
    }
}
