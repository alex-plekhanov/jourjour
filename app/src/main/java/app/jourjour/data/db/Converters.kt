package app.jourjour.data.db

import androidx.room.TypeConverter
import app.jourjour.data.entity.IconTarget
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

class Converters {

    @TypeConverter
    fun localDateToEpochDay(value: LocalDate?): Long? = value?.toEpochDay()

    @TypeConverter
    fun epochDayToLocalDate(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)

    /**
     * An entry's time is wall-clock: the money went out on that local date, whatever
     * time zone the app is later opened in. That is why LocalDateTime converts through a
     * UTC offset rather than through the device's zone — the zone would shift the day.
     */
    @TypeConverter
    fun localDateTimeToEpochSecond(value: LocalDateTime?): Long? =
        value?.toEpochSecond(ZoneOffset.UTC)

    @TypeConverter
    fun epochSecondToLocalDateTime(value: Long?): LocalDateTime? =
        value?.let { LocalDateTime.ofEpochSecond(it, 0, ZoneOffset.UTC) }

    @TypeConverter
    fun iconTargetToName(value: IconTarget?): String? = value?.name

    @TypeConverter
    fun nameToIconTarget(value: String?): IconTarget? = value?.let(
        IconTarget::valueOf)
}