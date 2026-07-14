-- V3: Unit B — 勤怠打刻テーブル
-- 担当: メンバー2（Unit B）。employee(id) を参照する。
-- 設計: docs/units/unit_b_attendance.md

CREATE TABLE attendance_record (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    employee_id  BIGINT NOT NULL,
    work_date    DATE NOT NULL,
    clock_in_at  TIMESTAMP,
    clock_out_at TIMESTAMP,
    version      BIGINT NOT NULL DEFAULT 0,
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_attendance_employee FOREIGN KEY (employee_id) REFERENCES employee(id),
    CONSTRAINT uq_attendance_employee_date UNIQUE (employee_id, work_date)
);

CREATE INDEX idx_attendance_employee ON attendance_record(employee_id);
CREATE INDEX idx_attendance_date ON attendance_record(work_date);
