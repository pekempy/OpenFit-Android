package com.openfit.mobile.ui.common

import com.openfit.mobile.data.settings.AppUnitSettings
import com.openfit.mobile.data.settings.DistanceUnit
import com.openfit.mobile.data.settings.EnergyUnit
import com.openfit.mobile.data.settings.TemperatureUnit
import com.openfit.mobile.data.settings.WaterUnit
import com.openfit.mobile.data.settings.WeightUnit
import kotlin.math.roundToInt

object UnitFormatter {
    fun formatWeight(weightKg: Double?, units: AppUnitSettings): String {
        if (weightKg == null) return "—"
        return when (units.weightUnit) {
            WeightUnit.KG -> "%.1f kg".format(weightKg)
            WeightUnit.LBS -> "%.1f lbs".format(weightKg * 2.20462262)
            WeightUnit.STONE -> {
                val totalLbs = weightKg * 2.20462262
                val st = (totalLbs / 14).toInt()
                val lbs = (totalLbs % 14).roundToInt()
                "$st st $lbs lbs"
            }
        }
    }

    fun formatDistance(meters: Double?, units: AppUnitSettings): String {
        if (meters == null) return "—"
        return when (units.distanceUnit) {
            DistanceUnit.KILOMETERS -> "%.2f km".format(meters / 1000.0)
            DistanceUnit.MILES -> "%.2f mi".format(meters / 1609.344)
        }
    }

    fun formatWater(liters: Double?, units: AppUnitSettings): String {
        if (liters == null) return "—"
        return when (units.waterUnit) {
            WaterUnit.LITERS -> "%.2f L".format(liters)
            WaterUnit.MILLILITERS -> "%,d ml".format((liters * 1000).roundToInt())
            WaterUnit.FLUID_OUNCES -> "%.1f fl oz".format(liters * 33.8140227)
        }
    }

    fun formatCalories(kcal: Double?, units: AppUnitSettings): String {
        if (kcal == null) return "—"
        return when (units.energyUnit) {
            EnergyUnit.KCAL -> "%,d kcal".format(kcal.roundToInt())
            EnergyUnit.KILOJOULES -> "%,d kJ".format((kcal * 4.184).roundToInt())
        }
    }

    fun formatTemperatureDelta(deltaC: Double?, units: AppUnitSettings): String {
        if (deltaC == null) return "—"
        return when (units.temperatureUnit) {
            TemperatureUnit.CELSIUS -> "%+.1f°C".format(deltaC)
            TemperatureUnit.FAHRENHEIT -> "%+.1f°F".format(deltaC * 1.8)
        }
    }
}
