package com.codrivelog.app.export

/**
 * Placeholder AcroForm field names for DR2324_2022.pdf.
 *
 * These are intentionally centralized so they can be replaced later with
 * the exact names discovered via desktop field inspection.
 */
object Dr2324FieldNames {
    const val STUDENT_NAME = "Students name"
    const val PERMIT_NUMBER = "Permit number"

    const val TOTALS_PAGE_DRIVING = "Driving Time_14"
    const val TOTALS_PAGE_NIGHT = "Minimum of 10 hours"

    const val GRAND_TOTAL_DRIVING = "Grand Total Driving Time"
    const val GRAND_TOTAL_NIGHT = "Grand Total Night Driving Time"

    const val SIGNATURE_NAME = "Name"
    const val SIGNATURE_DATE = "Date_2"

    fun rowDate(pageIndex: Int, rowIndex: Int): String = when (rowIndex) {
        0 -> "Date"
        1 -> "Date_02"
        else -> "Date_${rowIndex + 1}"
    }

    fun rowVerifierInitials(pageIndex: Int, rowIndex: Int): String = when (rowIndex) {
        0 -> "Verifiers Initials"
        else -> "Verifiers Initials_${rowIndex + 1}"
    }

    fun rowDrivingTime(pageIndex: Int, rowIndex: Int): String = when (rowIndex) {
        0 -> "Driving Time"
        else -> "Driving Time_${rowIndex + 1}"
    }

    fun rowNightDrivingTime(pageIndex: Int, rowIndex: Int): String = when (rowIndex) {
        0 -> "Night Driving"
        1 -> "Night Driving_02"
        else -> "Night Driving_${rowIndex + 1}"
    }

    fun rowComments(pageIndex: Int, rowIndex: Int): String = when (rowIndex) {
        0 -> "Comments"
        else -> "Comments_${rowIndex + 1}"
    }

    fun pageDrivingTotal(pageIndex: Int): String = TOTALS_PAGE_DRIVING
    fun pageNightTotal(pageIndex: Int): String = TOTALS_PAGE_NIGHT
}
