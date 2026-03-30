package com.codrivelog.app.export

/**
 * Placeholder AcroForm field names for DR2324_2022.pdf.
 *
 * These are intentionally centralized so they can be replaced later with
 * the exact names discovered via desktop field inspection.
 */
object Dr2324FieldNames {
    const val STUDENT_NAME = "student_name"
    const val PERMIT_NUMBER = "permit_number"

    const val TOTALS_PAGE_DRIVING = "totals_page_driving"
    const val TOTALS_PAGE_NIGHT = "totals_page_night"

    const val GRAND_TOTAL_DRIVING = "grand_total_driving"
    const val GRAND_TOTAL_NIGHT = "grand_total_night"

    const val SIGNATURE_NAME = "signature_name"
    const val SIGNATURE_VALUE = "signature"
    const val SIGNATURE_DATE = "signature_date"

    fun rowDate(pageIndex: Int, rowIndex: Int): String = "page_${pageIndex + 1}_row_${rowIndex + 1}_date"
    fun rowVerifierInitials(pageIndex: Int, rowIndex: Int): String =
        "page_${pageIndex + 1}_row_${rowIndex + 1}_verifier_initials"

    fun rowDrivingTime(pageIndex: Int, rowIndex: Int): String =
        "page_${pageIndex + 1}_row_${rowIndex + 1}_driving_time"

    fun rowNightDrivingTime(pageIndex: Int, rowIndex: Int): String =
        "page_${pageIndex + 1}_row_${rowIndex + 1}_night_driving_time"

    fun rowComments(pageIndex: Int, rowIndex: Int): String =
        "page_${pageIndex + 1}_row_${rowIndex + 1}_comments"

    fun pageDrivingTotal(pageIndex: Int): String = "page_${pageIndex + 1}_totals_driving"
    fun pageNightTotal(pageIndex: Int): String = "page_${pageIndex + 1}_totals_night"
}
