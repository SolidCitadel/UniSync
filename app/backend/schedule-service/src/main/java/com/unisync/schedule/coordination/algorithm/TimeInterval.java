package com.unisync.schedule.coordination.algorithm;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 시간 구간을 표현하는 클래스 (알고리즘용)
 */
@Data
@AllArgsConstructor
public class TimeInterval implements Comparable<TimeInterval> {
    private LocalDateTime start;
    private LocalDateTime end;

    @Override
    public int compareTo(TimeInterval other) {
        return this.start.compareTo(other.start);
    }

    /**
     * 두 구간이 겹치는지 확인
     */
    public boolean overlaps(TimeInterval other) {
        return this.start.isBefore(other.end) && other.start.isBefore(this.end);
    }

    /**
     * 두 구간을 병합
     */
    public TimeInterval mergeWith(TimeInterval other) {
        LocalDateTime mergedStart = this.start.isBefore(other.start) ? this.start : other.start;
        LocalDateTime mergedEnd = this.end.isAfter(other.end) ? this.end : other.end;
        return new TimeInterval(mergedStart, mergedEnd);
    }

    /**
     * 구간의 지속 시간 (분)
     */
    public long getDurationMinutes() {
        return java.time.Duration.between(start, end).toMinutes();
    }
}
