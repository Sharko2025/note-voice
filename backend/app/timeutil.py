"""Days are Vietnam calendar days; storage is UTC."""
from datetime import date, datetime, time, timedelta, timezone
from zoneinfo import ZoneInfo

VN_TZ = ZoneInfo("Asia/Ho_Chi_Minh")

_WEEKDAYS = [
    ("Thứ Hai", "Monday"), ("Thứ Ba", "Tuesday"), ("Thứ Tư", "Wednesday"), ("Thứ Năm", "Thursday"),
    ("Thứ Sáu", "Friday"), ("Thứ Bảy", "Saturday"), ("Chủ Nhật", "Sunday"),
]


def today_vn() -> date:
    return datetime.now(VN_TZ).date()


def vn_date(moment: datetime) -> date:
    return moment.astimezone(VN_TZ).date()


def day_range(first: date, last: date) -> tuple[datetime, datetime]:
    """UTC [start, end) covering Vietnam days first..last inclusive."""
    start = datetime.combine(first, time.min, VN_TZ)
    end = datetime.combine(last + timedelta(days=1), time.min, VN_TZ)
    return start.astimezone(timezone.utc), end.astimezone(timezone.utc)


def format_now_for_prompt(moment: datetime) -> str:
    """'Thứ Hai (Monday), 19/10/2026 14:30' in Vietnam time (SPEC: Pipeline AI, step 2)."""
    local = moment.astimezone(VN_TZ)
    vi, en = _WEEKDAYS[local.weekday()]
    return f"{vi} ({en}), {local:%d/%m/%Y %H:%M}"
